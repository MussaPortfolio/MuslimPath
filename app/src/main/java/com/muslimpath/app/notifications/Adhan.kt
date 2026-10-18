package com.muslimpath.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.muslimpath.app.R
import com.muslimpath.app.data.Prayer
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The adhan played when a prayer time comes. Recordings live in `assets/adhan/`:
 * `adhan.mp3` for every prayer and, optionally, `adhan_fajr.mp3` (the Fajr adhan has the extra
 * phrase «ас-саляту хайрун мин ан-наум»). Without the files the plain notification is used.
 */
object Adhan {
    const val CHANNEL = "prayer_adhan"
    private const val DIR = "adhan"

    fun asset(context: Context, prayer: Prayer): String? {
        val files = context.assets.list(DIR).orEmpty().toSet()
        return listOf("adhan_fajr.mp3".takeIf { prayer == Prayer.FAJR }, "adhan.mp3")
            .firstOrNull { it != null && it in files }?.let { "$DIR/$it" }
    }

    fun available(context: Context) = asset(context, Prayer.DHUHR) != null

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            // Silent channel: the service plays the adhan itself, at full length.
            NotificationChannel(CHANNEL, "Азан", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Азан при наступлении времени намаза"
                setSound(null, null)
            }
        )
    }

    /**
     * Starts the adhan; returns false when it can't be played (no recording, phone on silent or
     * vibrate, notifications off, or Android refused a background start) so the caller falls back
     * to the plain notification.
     */
    fun start(context: Context, prayer: Prayer, time: ZonedDateTime): Boolean {
        if (asset(context, prayer) == null || !PrayerAlarms.canNotify(context)) return false
        // Respect silent/vibrate mode: the phone may be in a mosque or a meeting.
        if (context.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        val intent = Intent(context, AdhanService::class.java)
            .putExtra("prayer", prayer.name).putExtra("time", time.toInstant().toEpochMilli()).putExtra("zone", time.zone.id)
        return runCatching { ContextCompat.startForegroundService(context, intent) }.isSuccess
    }
}

class AdhanService : Service() {
    companion object {
        const val ACTION_STOP = "com.muslimpath.app.adhan.STOP"
    }

    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var prayer: Prayer? = null
    private var time: ZonedDateTime? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish()
            return START_NOT_STICKY
        }
        val p = intent?.getStringExtra("prayer")?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        val asset = p?.let { Adhan.asset(this, it) }
        if (p == null || asset == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val zone = runCatching { ZoneId.of(intent.getStringExtra("zone")) }.getOrDefault(ZoneId.systemDefault())
        val t = Instant.ofEpochMilli(intent.getLongExtra("time", System.currentTimeMillis())).atZone(zone)
        release()
        prayer = p
        time = t
        Adhan.ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, PrayerAlarms.notificationId(p), notification(p, t, playing = true), type)
        play(asset)
        return START_NOT_STICKY
    }

    private fun play(asset: String) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val am = getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            // A call or another app taking over the audio stops the adhan.
            .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) finish() }
            .build()
        focus = request
        if (am.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            finish()
            return
        }
        val mp = MediaPlayer()
        player = mp
        runCatching {
            assets.openFd(asset).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setAudioAttributes(attrs)
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.prepare()
            mp.start()
        }.onFailure { finish() }
    }

    /** Stops playback but leaves an ordinary notification that the prayer time has come. */
    private fun finish() {
        release()
        val p = prayer
        val t = time
        if (p != null && t != null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java).notify(PrayerAlarms.notificationId(p), notification(p, t, playing = false))
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        prayer = null
        time = null
        stopSelf()
    }

    private fun release() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun notification(p: Prayer, t: ZonedDateTime, playing: Boolean) =
        NotificationCompat.Builder(this, Adhan.CHANNEL).apply {
            val (title, text) = PrayerAlarms.texts(p, t, 0)
            setSmallIcon(R.drawable.ic_stat_path)
            setContentTitle(title)
            setContentText(text)
            setCategory(NotificationCompat.CATEGORY_ALARM)
            setPriority(NotificationCompat.PRIORITY_HIGH)
            setContentIntent(PrayerAlarms.openPrayer(this@AdhanService, p))
            setAutoCancel(!playing)
            setOngoing(playing)
            if (playing) {
                val stop = PendingIntent.getService(
                    this@AdhanService, 7201,
                    Intent(this@AdhanService, AdhanService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                addAction(0, "Остановить азан", stop)
                // Swiping the notification away also stops the adhan.
                setDeleteIntent(stop)
            }
        }.build()

    override fun onDestroy() {
        release()
        super.onDestroy()
    }
}
