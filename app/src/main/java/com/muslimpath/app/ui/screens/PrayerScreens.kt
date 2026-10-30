package com.muslimpath.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.muslimpath.app.AppViewModel
import com.muslimpath.app.data.AsrSchool
import com.muslimpath.app.data.CITIES
import com.muslimpath.app.data.CalcMethod
import com.muslimpath.app.data.Prayer
import com.muslimpath.app.data.PrayerCalculator
import com.muslimpath.app.data.PrayerSettings
import com.muslimpath.app.data.UserState
import com.muslimpath.app.data.zoneId
import com.muslimpath.app.data.withCity
import com.muslimpath.app.notifications.Adhan
import com.muslimpath.app.notifications.PrayerAlarms
import com.muslimpath.app.ui.Nav
import com.muslimpath.app.ui.components.AppCard
import com.muslimpath.app.ui.components.EmptyHint
import com.muslimpath.app.ui.components.IconBadge
import com.muslimpath.app.ui.components.ListScreen
import com.muslimpath.app.ui.components.SectionTitle
import com.muslimpath.app.ui.components.VSpace
import com.muslimpath.app.ui.theme.GoldLight
import com.muslimpath.app.ui.theme.Green600
import com.muslimpath.app.ui.theme.Green800
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))

fun formatCountdown(d: Duration): String {
    val h = d.toHours(); val m = d.toMinutes() % 60
    return when {
        h > 0 -> "через $h ч $m мин"
        m > 0 -> "через $m мин"
        else -> "меньше минуты"
    }
}

/** Ticks every 20 s so countdowns stay current. */
@Composable
fun rememberNow(settings: PrayerSettings): ZonedDateTime {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); tick++ } }
    return remember(tick, settings.zone) { ZonedDateTime.now(settings.zoneId()) }
}

/** Half-width home tile: label on top, the one value worth seeing, then a short hint. */
@Composable
fun HomeTile(
    icon: ImageVector,
    label: String,
    value: String?,
    hint: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            VSpace(14)
            if (value != null) Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.75f))
        }
    }
}

@Composable
fun NextPrayerCard(state: UserState, nav: Nav, modifier: Modifier = Modifier) {
    val s = state.prayer
    val now = rememberNow(s)
    // Just the prayer and the time left; the full timetable is one tap away.
    val next = if (s.hasLocation) PrayerCalculator.next(s, now) else null
    HomeTile(
        icon = Icons.Outlined.Schedule,
        label = next?.first?.title ?: "Намаз",
        value = next?.second?.format(HHMM),
        hint = when {
            !s.hasLocation -> "Укажите город"
            next != null -> formatCountdown(Duration.between(now, next.second))
            else -> "Нет данных на сегодня"
        },
        onClick = nav::prayer,
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen(vm: AppViewModel, state: UserState, nav: Nav) {
    val s = state.prayer
    val context = LocalContext.current
    val now = rememberNow(s)
    var dayOffset by rememberSaveable { mutableStateOf(0L) }
    var exactAllowed by remember { mutableStateOf(PrayerAlarms.canScheduleExact(context)) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Re-check the exact-alarm permission when returning from system settings.
    LaunchedEffect(now) { exactAllowed = PrayerAlarms.canScheduleExact(context) }

    ListScreen(title = "Время намаза") {
        item {
            AppCard(onClick = nav::prayerCity) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.placeName ?: "Выберите город", style = MaterialTheme.typography.titleSmall)
                        if (s.hasLocation) Text(
                            if (s.source == com.muslimpath.app.data.PrayerSource.KBR) "Официальный график ДУМ КБР"
                            else "Расчёт: ${s.method.label}, Аср по ${if (s.school == AsrSchool.SHAFI) "шафиитскому" else "ханафитскому"} мазхабу",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Изменить", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (!s.hasLocation) {
            item {
                VSpace(16)
                EmptyHint("Чтобы рассчитать время намаза, укажите город или разрешите определить местоположение. Координаты остаются только на вашем телефоне.")
                Button(onClick = nav::prayerCity) { Text("Указать город") }
            }
            return@ListScreen
        }

        val date = now.toLocalDate().plusDays(dayOffset)
        val day = PrayerCalculator.day(s, date)
        val next = PrayerCalculator.next(s, now)

        if (dayOffset == 0L && next != null) item {
            VSpace(12)
            Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = Green800), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Green800, Green600))).fillMaxWidth().padding(20.dp)) {
                    Text("Следующий намаз", style = MaterialTheme.typography.labelLarge, color = GoldLight)
                    Text("${next.first.title} · ${next.second.format(HHMM)}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(formatCountdown(Duration.between(now, next.second)), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }

        item {
            VSpace(12)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dayOffset-- }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Предыдущий день") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (dayOffset) { 0L -> "Сегодня"; 1L -> "Завтра"; -1L -> "Вчера"; else -> "" }.ifEmpty { date.format(DAY) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (dayOffset in -1L..1L) Text(date.format(DAY), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { dayOffset++ }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Следующий день") }
            }
        }

        val official = s.source == com.muslimpath.app.data.PrayerSource.KBR
        if (official) item {
            Surface(
                color = if (day?.official == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Text(
                    if (day?.official == true) "Официальный график ДУМ КБР"
                    else "Официального графика на эту дату пока нет — показан астрономический расчёт для Нальчика. Он обновится, когда ДУМ КБР опубликует расписание.",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (day != null) itemsIndexed(day.times.entries.toList(), key = { _, e -> e.key.name }) { i, (prayer, time) ->
            val isNext = dayOffset == 0L && next?.first == prayer && next.second.toLocalDate() == date
            val enabled = prayer in s.notify
            // Rows read as one grouped card: only the outer corners are fully rounded.
            val top = if (i == 0) 20.dp else 4.dp
            val bottom = if (i == day.times.size - 1) 20.dp else 4.dp
            Surface(
                color = if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(top, top, bottom, bottom),
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        prayer.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        color = if (prayer == Prayer.SUNRISE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(time.format(HHMM), style = MaterialTheme.typography.titleLarge, color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    if (prayer.notifiable) {
                        IconButton(onClick = {
                            if (!enabled && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            vm.updatePrayer { it.copy(notify = if (enabled) it.notify - prayer else it.notify + prayer) }
                        }) {
                            Icon(
                                if (enabled) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                                if (enabled) "Выключить оповещение (${prayer.title})" else "Включить оповещение (${prayer.title})",
                                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                    } else Spacer(Modifier.width(48.dp))
                }
            }
        }

        item {
            VSpace(4)
            TextButton(onClick = {
                val all = Prayer.entries.filter { it.notifiable }.toSet()
                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                vm.updatePrayer { it.copy(notify = if (it.notify.containsAll(all)) emptySet() else all) }
            }) {
                Text(if (s.notify.containsAll(Prayer.entries.filter { it.notifiable })) "Выключить все оповещения" else "Включить оповещения для всех намазов")
            }

            if (s.notify.isNotEmpty() && !exactAllowed) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Чтобы оповещения приходили точно вовремя, разрешите приложению «Будильники и напоминания».",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 31) runCatching {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                            }
                        }) { Text("Открыть настройки") }
                    }
                }
            }

            SectionTitle("Оповещать")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0, 5, 10, 15, 30).forEach { m ->
                    FilterChip(
                        selected = s.notifyBeforeMin == m,
                        onClick = { vm.updatePrayer { it.copy(notifyBeforeMin = m) } },
                        label = { Text(if (m == 0) "Вовремя" else "за $m мин") },
                    )
                }
            }
            AdhanSetting(s, vm)

            if (official) {
                VSpace(16)
                Text(
                    "Источник: ${com.muslimpath.app.data.OfficialSchedules.KBR_SOURCE}. Время приводится так, как его публикует ДУМ КБР, без поправок.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@item
            }

            SectionTitle("Способ расчёта")
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = s.method.label, onValueChange = {}, readOnly = true,
                    label = { Text("Метод (углы Фаджра и Иши)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    CalcMethod.entries.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { vm.updatePrayer { it.copy(method = m) }; expanded = false })
                    }
                }
            }
            VSpace(8)
            Text("Время Асра", style = MaterialTheme.typography.labelLarge)
            AsrSchool.entries.forEach { school ->
                Row(
                    Modifier.fillMaxWidth().selectable(s.school == school, role = Role.RadioButton) { vm.updatePrayer { it.copy(school = school) } }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.school == school, onClick = null)
                    Text(school.label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionTitle("Поправки, мин")
            Text(
                "Если расписание вашей мечети отличается, сдвиньте время нужного намаза.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Prayer.entries.forEach { p ->
                val adj = s.adjustments[p] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(p.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconButton(onClick = { vm.updatePrayer { it.copy(adjustments = it.adjustments + (p to (adj - 1).coerceAtLeast(-30))) } }) {
                        Icon(Icons.Outlined.Remove, "Раньше на минуту")
                    }
                    Text(if (adj > 0) "+$adj" else "$adj", Modifier.width(36.dp), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { vm.updatePrayer { it.copy(adjustments = it.adjustments + (p to (adj + 1).coerceAtMost(30))) } }) {
                        Icon(Icons.Outlined.Add, "Позже на минуту")
                    }
                }
            }
            VSpace(12)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            VSpace(8)
            Text(
                "Время рассчитывается астрономически по координатам и может отличаться от расписания местной мечети на несколько минут. " +
                    "Если сомневаетесь, сверьтесь с мечетью или имамом.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PrayerCityScreen(vm: AppViewModel, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun locate() {
        locating = true; error = null
        scope.launch {
            val loc = currentLocation(context)
            if (loc == null) {
                error = "Не удалось определить местоположение. Включите геолокацию или выберите город из списка."
            } else {
                val name = placeName(context, loc) ?: "Моё местоположение"
                vm.updatePrayer {
                    if (com.muslimpath.app.data.isInKbr(loc.latitude, loc.longitude)) {
                        it.withCity(com.muslimpath.app.data.KBR_CITY.copy(name = name))
                    } else {
                        it.copy(
                            source = com.muslimpath.app.data.PrayerSource.CALCULATED, placeName = name,
                            lat = loc.latitude, lon = loc.longitude, zone = java.time.ZoneId.systemDefault().id,
                            school = com.muslimpath.app.data.defaultSchoolFor(loc.latitude, loc.longitude),
                        )
                    }
                }
                nav.back()
            }
            locating = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else error = "Без доступа к геолокации выберите город из списка."
    }

    ListScreen(title = "Город", onBack = { nav.back() }) {
        item {
            OutlinedButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) locate()
                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                enabled = !locating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Определить автоматически")
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            Text(
                "Используется только приблизительное местоположение, и оно не покидает телефон.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            SectionTitle("Официальные графики")
            com.muslimpath.app.ui.components.AppCard(onClick = {
                vm.updatePrayer { it.withCity(com.muslimpath.app.data.KBR_CITY) }
                nav.back()
            }) {
                Column(Modifier.padding(14.dp)) {
                    Text("Кабардино-Балкария", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Официальный график намазов Духовного управления мусульман КБР",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionTitle("Расчёт по городу")
            SearchField(query, { query = it }, "Найти город")
            VSpace(8)
        }
        val list = CITIES.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        if (list.isEmpty()) item { EmptyHint("Города нет в списке — используйте автоматическое определение.") }
        items(list, key = { it.name }) { c ->
            Column(
                Modifier.fillMaxWidth()
                    .selectable(false) {
                        vm.updatePrayer { it.withCity(c) }
                        nav.back()
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            ) {
                Text(c.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (c.official == com.muslimpath.app.data.PrayerSource.KBR) "Официальный график ДУМ КБР"
                    else "Расчёт · Аср по ${if (c.school == AsrSchool.SHAFI) "шафиитскому" else "ханафитскому"} мазхабу",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context): Location? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    if (last != null && System.currentTimeMillis() - last.time < 6 * 3600_000L) return last
    if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                lm.getCurrentLocation(providers.first(), signal, context.mainExecutor) { cont.resume(it) }
            }
        }
        if (fresh != null) return fresh
    }
    return last
}

private suspend fun placeName(context: Context, loc: Location): String? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.forLanguageTag("ru")).getFromLocation(loc.latitude, loc.longitude, 1)
            ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()
}

/** "Азан" switch with a preview; the adhan plays when the time comes, never with the early reminder. */
@Composable
private fun AdhanSetting(s: PrayerSettings, vm: AppViewModel) {
    val context = LocalContext.current
    val available = remember { Adhan.available(context) }
    var preview by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.release() } }
    fun stopPreview() { preview?.release(); preview = null }

    VSpace(12)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Азан", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    !available -> "Запись азана пока не добавлена в приложение"
                    s.notifyBeforeMin > 0 -> "Прозвучит, когда наступит время, — после напоминания за ${s.notifyBeforeMin} мин"
                    else -> "Прозвучит, когда наступит время намаза"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = s.adhan && available,
            enabled = available,
            onCheckedChange = { on -> vm.updatePrayer { it.copy(adhan = on) } },
        )
    }
    if (available) {
        TextButton(onClick = {
            if (preview != null) return@TextButton stopPreview()
            val asset = Adhan.asset(context, Prayer.DHUHR) ?: return@TextButton
            preview = runCatching {
                android.media.MediaPlayer().apply {
                    context.assets.openFd(asset).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                    setOnCompletionListener { stopPreview() }
                    prepare()
                    start()
                }
            }.getOrNull()
        }) { Text(if (preview != null) "Остановить" else "Прослушать") }
        Text(
            "В беззвучном режиме и при вибрации азан не звучит — придёт обычное оповещение. Остановить азан можно кнопкой в уведомлении.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
