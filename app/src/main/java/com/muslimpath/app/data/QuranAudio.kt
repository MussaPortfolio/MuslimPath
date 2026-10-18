package com.muslimpath.app.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class Reciter(
    val id: String,
    val name: String,
    val folder: String,
    val style: String,   // манера чтения
    val note: String,    // кому подойдёт
)

data class AudioState(
    val sura: Int? = null,
    val ayah: Int? = null,       // currently playing ayah (0 = basmala before the sura)
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val repeatAyah: Boolean = false,
    val error: String? = null,
    val previewReciter: String? = null, // reciter whose sample (Al-Fatiha) is playing
)

/**
 * Ayah-by-ayah Quran recitation streamed from EveryAyah.com (one mp3 per ayah: SSSAAA.mp3).
 * Played ayahs are cached on the device (LRU, 300 MB), so repeated listening works offline.
 */
@OptIn(UnstableApi::class)
class QuranAudio private constructor(context: Context) {

    companion object {
        @Volatile private var instance: QuranAudio? = null
        /** One instance per process: the on-disk cache may be opened only once. */
        fun get(context: Context): QuranAudio =
            instance ?: synchronized(this) { instance ?: QuranAudio(context).also { instance = it } }
    }

    val reciters = listOf(
        Reciter("husary", "Махмуд Халиль аль-Хусари", "Husary_128kbps", "Мураттал", "Медленно и очень чётко — лучший выбор для обучения чтению"),
        Reciter("alafasy", "Мишари Рашид аль-Афаси", "Alafasy_128kbps", "Мураттал", "Красивый и ясный голос, умеренный темп"),
        Reciter("minshawy", "Мухаммад Сиддик аль-Миншави", "Minshawy_Murattal_128kbps", "Мураттал", "Классическое спокойное чтение"),
        Reciter("abdulbasit", "Абдуль-Басит Абдус-Самад", "Abdul_Basit_Murattal_192kbps", "Мураттал", "Неторопливое чтение, удобно повторять"),
        Reciter("abdulbasit_mujawwad", "Абдуль-Басит Абдус-Самад", "Abdul_Basit_Mujawwad_128kbps", "Муджаввад", "Мелодичное протяжное чтение"),
        Reciter("ghamadi", "Саад аль-Гамиди", "Ghamadi_40kbps", "Мураттал", "Мягкий голос; лёгкие файлы — экономит трафик"),
        Reciter("sudais", "Абдуррахман ас-Судейс", "Abdurrahmaan_As-Sudais_192kbps", "Мураттал", "Имам Заповедной мечети в Мекке, быстрый темп"),
        Reciter("shuraym", "Сауд аш-Шурейм", "Saood_ash-Shuraym_128kbps", "Мураттал", "Имам Заповедной мечети в Мекке"),
        Reciter("muaiqly", "Махер аль-Муайкли", "MaherAlMuaiqly128kbps", "Мураттал", "Имам Заповедной мечети в Мекке, ровный темп"),
        Reciter("ajamy", "Ахмад аль-Аджами", "ahmed_ibn_ali_al_ajamy_128kbps", "Мураттал", "Выразительное чтение"),
        Reciter("shaatree", "Абу Бакр аш-Шатри", "Abu_Bakr_Ash-Shaatree_128kbps", "Мураттал", "Спокойный умеренный темп"),
        Reciter("dussary", "Ясир ад-Даусари", "Yasser_Ad-Dussary_128kbps", "Мураттал", "Глубокий выразительный голос"),
        Reciter("qatami", "Насир аль-Катами", "Nasser_Alqatami_128kbps", "Мураттал", "Эмоциональное чтение"),
        Reciter("rifai", "Хани ар-Рифаи", "Hani_Rifai_192kbps", "Мураттал", "Проникновенное чтение"),
    )

    private val _state = MutableStateFlow(AudioState())
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private val app = context.applicationContext
    private val cache: SimpleCache by lazy {
        SimpleCache(File(app.cacheDir, "quran_audio"), LeastRecentlyUsedCacheEvictor(300L * 1024 * 1024), StandaloneDatabaseProvider(app))
    }

    private var player: ExoPlayer? = null

    private fun player(): ExoPlayer = player ?: run {
        val http = DefaultHttpDataSource.Factory().setUserAgent("MuslimPath/1.0 (Android)")
        val dataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { p ->
                p.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) = sync(p)
                    override fun onIsPlayingChanged(isPlaying: Boolean) = sync(p)
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) _state.value = _state.value.copy(playing = false)
                        sync(p)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        _state.value = _state.value.copy(
                            playing = false, buffering = false,
                            error = "Не удалось загрузить аудио. Проверьте подключение к интернету.",
                        )
                    }
                })
                player = p
            }
    }

    private fun sync(p: Player) {
        val id = p.currentMediaItem?.mediaId
        if (id != null && id.startsWith("preview:")) {
            val playing = p.isPlaying || p.playbackState == Player.STATE_BUFFERING
            _state.value = _state.value.copy(
                sura = null, ayah = null, playing = p.isPlaying,
                buffering = p.playbackState == Player.STATE_BUFFERING,
                previewReciter = if (playing) id.removePrefix("preview:").substringBefore(":") else null,
            )
            return
        }
        val (s, a) = id?.split(":")?.map(String::toInt) ?: listOf(null, null)
        _state.value = _state.value.copy(
            previewReciter = null,
            sura = s, ayah = a,
            playing = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            error = if (p.isPlaying) null else _state.value.error,
        )
    }

    private fun url(reciter: Reciter, sura: Int, ayah: Int) =
        "https://everyayah.com/data/${reciter.folder}/%03d%03d.mp3".format(sura, ayah)

    /** Plays [sura] from [fromAyah] to the end. Before ayah 1 the basmala is recited (except suras 1 and 9). */
    fun play(reciter: Reciter, sura: Int, ayahCount: Int, fromAyah: Int = 1) {
        val items = buildList {
            if (fromAyah == 1 && sura != 1 && sura != 9) {
                add(MediaItem.Builder().setMediaId("$sura:0").setUri(url(reciter, 1, 1)).build())
            }
            for (a in fromAyah..ayahCount) add(MediaItem.Builder().setMediaId("$sura:$a").setUri(url(reciter, sura, a)).build())
        }
        val p = player()
        _state.value = _state.value.copy(error = null)
        p.setMediaItems(items)
        p.repeatMode = if (_state.value.repeatAyah) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.prepare()
        p.play()
    }

    /** Plays the first ayahs of Al-Fatiha so the user can hear the voice before choosing. */
    fun preview(reciter: Reciter) {
        val p = player()
        if (_state.value.previewReciter == reciter.id && p.isPlaying) {
            p.stop(); p.clearMediaItems()
            _state.value = _state.value.copy(previewReciter = null, playing = false)
            return
        }
        val items = (1..3).map { MediaItem.Builder().setMediaId("preview:${reciter.id}:$it").setUri(url(reciter, 1, it)).build() }
        _state.value = _state.value.copy(error = null, previewReciter = reciter.id)
        p.setMediaItems(items)
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.prepare()
        p.play()
    }

    fun stopPreview() {
        if (_state.value.previewReciter != null || player?.currentMediaItem?.mediaId?.startsWith("preview:") == true) {
            player?.stop(); player?.clearMediaItems()
            _state.value = _state.value.copy(previewReciter = null, playing = false)
        }
    }

    fun togglePause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_ENDED || p.playbackState == Player.STATE_IDLE) { p.seekToDefaultPosition(0); p.prepare() }
            p.play()
        }
    }

    fun next() = player?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    fun previous() = player?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()

    fun setRepeatAyah(on: Boolean) {
        _state.value = _state.value.copy(repeatAyah = on)
        player?.repeatMode = if (on) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun stop() {
        player?.stop()
        player?.clearMediaItems()
        _state.value = AudioState(repeatAyah = _state.value.repeatAyah)
    }

    fun release() {
        player?.release()
        player = null
    }
}
