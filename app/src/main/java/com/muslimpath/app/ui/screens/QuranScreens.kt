package com.muslimpath.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.muslimpath.app.AppViewModel
import com.muslimpath.app.data.Ayah
import com.muslimpath.app.data.QuranMode
import com.muslimpath.app.data.QuranRepository
import com.muslimpath.app.data.Sura
import com.muslimpath.app.data.UserState
import com.muslimpath.app.ui.Nav
import com.muslimpath.app.ui.components.AppCard
import com.muslimpath.app.ui.components.EmptyHint
import com.muslimpath.app.ui.components.IconBadge
import com.muslimpath.app.ui.components.ListScreen
import com.muslimpath.app.ui.components.SectionTitle
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.theme.ArabicStyle
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** Content of the «Коран» tab inside «Обучение». */
fun LazyListScope.quranTab(vm: AppViewModel, state: UserState, nav: Nav, query: String, quranLoaded: Boolean) {
    val suras = vm.quranRepo.suras
    val q = query.trim()

    if (q.isEmpty()) {
        state.quran.lastSura?.let { last ->
            val sura = suras[last - 1]
            item {
                AppCard(Modifier.padding(top = 4.dp, bottom = 4.dp), onClick = { nav.sura(last, state.quran.lastAyah) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.AutoMirrored.Outlined.MenuBook)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Продолжить чтение", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("${sura.number}. ${sura.name}, аят ${state.quran.lastAyah}", style = MaterialTheme.typography.titleSmall)
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null)
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = nav::quranBookmarks) {
                    Icon(Icons.Outlined.Bookmarks, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Закладки (${state.quran.bookmarks.size})")
                }
                TextButton(onClick = nav::reciters) {
                    Icon(Icons.Outlined.RecordVoiceOver, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Чтец", maxLines = 1)
                }
            }
        }
    }

    val filtered = if (q.isEmpty()) suras else suras.filter {
        it.number.toString() == q || it.name.contains(q, ignoreCase = true) ||
            it.meaning.contains(q, ignoreCase = true) || it.arabic.contains(q)
    }
    items(filtered, key = { "sura" + it.number }) { s -> SuraRow(s, onClick = { nav.sura(s.number) }) }

    if (q.length >= 3) {
        val found = vm.searchQuran(q)
        item { SectionTitle("В тексте перевода") }
        if (!quranLoaded) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
        else if (found.isEmpty()) item { EmptyHint("Совпадений в переводе не найдено.") }
        items(found, key = { "f${it.sura}:${it.number}" }) { a ->
            AppCard(Modifier.padding(vertical = 4.dp), onClick = { nav.sura(a.sura, a.number) }) {
                Column(Modifier.padding(14.dp)) {
                    Text("${suras[a.sura - 1].name} ${a.sura}:${a.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(a.translation, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                }
            }
        }
    }

    item {
        VSpace(16)
        Text(QuranRepository.ATTRIBUTION, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuraRow(s: Sura, onClick: () -> Unit) {
    AppCard(Modifier.padding(vertical = 3.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text("${s.number}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${s.meaning} · ${s.ayahCount} ${plural(s.ayahCount, "аят", "аята", "аятов")} · ${if (s.meccan) "мекканская" else "мединская"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(s.arabic, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun SuraScreen(vm: AppViewModel, state: UserState, nav: Nav, suraNumber: Int, startAyah: Int) {
    val sura = vm.quranRepo.suras.getOrNull(suraNumber - 1) ?: return
    LaunchedEffect(Unit) { vm.loadQuran() }
    val quran by vm.quran.collectAsStateWithLifecycle()
    val ayahs = quran?.get(suraNumber)
    val mode = state.quran.mode
    var menu by remember { mutableStateOf(false) }
    val audio by vm.audio.state.collectAsStateWithLifecycle()
    val playingAyah = if (audio.sura == suraNumber) audio.ayah?.coerceAtLeast(1) else null
    // Item 0 is the header, ayah N is item N.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (startAyah > 1) startAyah else 0)

    LaunchedEffect(ayahs) {
        if (ayahs == null) return@LaunchedEffect
        if (startAyah > 1) listState.scrollToItem(startAyah.coerceAtMost(ayahs.size))
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(400)
            .collect { vm.setQuranPosition(suraNumber, it.coerceIn(1, ayahs.size)) }
    }

    // Follow the recitation: keep the ayah being read on screen.
    LaunchedEffect(playingAyah) {
        if (playingAyah != null && audio.playing) listState.animateScrollToItem(playingAyah)
    }

    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * state.textScale)) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = { AudioBar(vm, state, sura, onChooseReciter = nav::reciters) },
            topBar = {
                TopAppBar(
                    title = { Text("${sura.number}. ${sura.name}", maxLines = 1) },
                    navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад") } },
                    actions = {
                        IconButton(onClick = { vm.setTextScale(state.textScale - 0.1f) }) { Icon(Icons.Outlined.TextDecrease, "Уменьшить текст") }
                        IconButton(onClick = { vm.setTextScale(state.textScale + 0.1f) }) { Icon(Icons.Outlined.TextIncrease, "Увеличить текст") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Tune, "Режим отображения") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                QuranMode.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.label) },
                                        leadingIcon = { RadioButton(selected = m == mode, onClick = null) },
                                        onClick = { vm.setQuranMode(m); menu = false },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
        ) { padding ->
            if (ayahs == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Scaffold
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            ) {
                item { SuraHeader(sura) }
                items(ayahs, key = { it.number }) { a ->
                    AyahItem(
                        a, mode,
                        bookmarked = "${a.sura}:${a.number}" in state.quran.bookmarks,
                        current = a.number == playingAyah,
                        onBookmark = { vm.toggleAyahBookmark(a.sura, a.number) },
                        onPlay = { vm.playSura(a.sura, a.number) },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (suraNumber > 1) TextButton(onClick = { nav.back(); nav.sura(suraNumber - 1) }) { Text("← Сура ${suraNumber - 1}") } else Spacer(Modifier)
                        if (suraNumber < 114) TextButton(onClick = { nav.back(); nav.sura(suraNumber + 1) }) { Text("Сура ${suraNumber + 1} →") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuraHeader(s: Sura) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.arabic, style = ArabicStyle.copy(color = MaterialTheme.colorScheme.primary))
            Text("Сура ${s.name} — «${s.meaning}»", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                "${s.ayahCount} ${plural(s.ayahCount, "аят", "аята", "аятов")} · ${if (s.meccan) "ниспослана в Мекке" else "ниспослана в Медине"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AyahItem(a: Ayah, mode: QuranMode, bookmarked: Boolean, current: Boolean, onBookmark: () -> Unit, onPlay: () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(
                if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else androidx.compose.ui.graphics.Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text("${a.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Outlined.PlayCircle,
                    "Слушать с аята ${a.number}",
                    tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onBookmark) {
                Icon(
                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    if (bookmarked) "Убрать закладку" else "Добавить закладку",
                    tint = if (bookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (mode != QuranMode.TRANSLATION) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    a.arabic,
                    style = ArabicStyle.copy(textDirection = TextDirection.Rtl, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        if (mode != QuranMode.ARABIC) {
            Text(a.translation, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun QuranBookmarksScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    LaunchedEffect(Unit) { vm.loadQuran() }
    val quran by vm.quran.collectAsStateWithLifecycle()
    val suras = vm.quranRepo.suras
    ListScreen(title = "Закладки Корана", onBack = { nav.back() }) {
        if (state.quran.bookmarks.isEmpty()) item { EmptyHint("Нажмите на значок закладки рядом с аятом, чтобы сохранить его здесь.") }
        items(state.quran.bookmarks, key = { it }) { key ->
            val (s, n) = key.split(":").map(String::toInt)
            val ayah = quran?.get(s)?.getOrNull(n - 1)
            AppCard(Modifier.padding(vertical = 4.dp), onClick = { nav.sura(s, n) }) {
                Column(Modifier.padding(14.dp)) {
                    Text("${suras[s - 1].name} $s:$n", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    ayah?.let { Text(it.translation, style = MaterialTheme.typography.bodyMedium, maxLines = 3) }
                }
            }
        }
    }
}

@Composable
fun QuranListScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadQuran() }
    val quran by vm.quran.collectAsStateWithLifecycle()
    ListScreen(title = "Коран") {
        item { SearchField(query, { query = it }, "Сура, номер или слово из перевода"); VSpace(8) }
        quranTab(vm, state, nav, query, quran != null)
    }
}

/** Recitation controls shown under the sura text. */
@Composable
private fun AudioBar(vm: AppViewModel, state: UserState, sura: Sura, onChooseReciter: () -> Unit) {
    val audio by vm.audio.state.collectAsStateWithLifecycle()
    val active = audio.sura == sura.number
    val reciter = vm.reciter()

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.AssistChip(
                    onClick = onChooseReciter,
                    label = {
                        Text(
                            "Чтец: ${reciter.name}" + if (reciter.style != "Мураттал") " (${reciter.style.lowercase()})" else "",
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.RecordVoiceOver, null, Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Outlined.ExpandMore, "Сменить чтеца", Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !active || audio.ayah == null -> ""
                        audio.ayah == 0 -> "Басмала"
                        else -> "Аят ${audio.ayah}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconToggleButton(checked = audio.repeatAyah, onCheckedChange = { vm.audio.setRepeatAyah(it) }) {
                    Icon(
                        Icons.Outlined.RepeatOne,
                        if (audio.repeatAyah) "Не повторять аят" else "Повторять аят",
                        tint = if (audio.repeatAyah) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.audio.previous() }, enabled = active) { Icon(Icons.Outlined.SkipPrevious, "Предыдущий аят") }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (active) vm.audio.togglePause()
                        else vm.playSura(sura.number, state.quran.lastSura?.takeIf { it == sura.number }?.let { state.quran.lastAyah } ?: 1)
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    if (active && audio.buffering) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (active && audio.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (active && audio.playing) "Пауза" else "Слушать",
                            Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.audio.next() }, enabled = active) { Icon(Icons.Outlined.SkipNext, "Следующий аят") }
                Spacer(Modifier.width(56.dp))
            }
            audio.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

/** Full-screen reciter chooser with a short sample of each voice. */
@Composable
fun ReciterScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    val audio by vm.audio.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.audio.stopPreview() } }
    ListScreen(title = "Выбор чтеца", onBack = { nav.back() }) {
        item {
            Text(
                "Нажмите ▶, чтобы послушать начало суры «Аль-Фатиха», и выберите голос, который вам ближе. " +
                    "Для изучения чтения удобнее медленное чтение — мураттал.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(8)
        }
        items(vm.audio.reciters, key = { it.id }) { r ->
            val selected = r.id == state.quran.reciterId
            val previewing = audio.previewReciter == r.id
            OutlinedCard(
                onClick = { vm.setReciter(r.id) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            ) {
                Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected, onClick = { vm.setReciter(r.id) })
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(r.style, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        Text(r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.audio.preview(r) }) {
                        if (previewing && audio.buffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(
                            if (previewing) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircle,
                            if (previewing) "Остановить образец" else "Послушать образец",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
        item {
            audio.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            VSpace(12)
            Text(
                "Аудио: EveryAyah.com. Для прослушивания нужен интернет; прослушанные аяты сохраняются на устройстве.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
