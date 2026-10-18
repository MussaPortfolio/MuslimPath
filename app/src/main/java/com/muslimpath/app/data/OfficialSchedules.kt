package com.muslimpath.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

/**
 * Official prayer timetables published by regional muftiates.
 *
 * ДУМ КБР: the yearly timetable (PDF from kbrdum.ru) is converted to `assets/prayer/kbr-YYYY.json`
 * with `tools/parse_kbr.py`. Days not covered by a bundled file (e.g. the next year before an app
 * update) are filled from the daily post on kbrdum.ru, which is fetched at most once a day.
 */
object OfficialSchedules {
    const val KBR_SOURCE = "Духовное управление мусульман КБР (kbrdum.ru)"
    private const val KBR_PAGE = "https://www.kbrdum.ru/8-grafik-namazov"
    private val ORDER = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    @Volatile private var kbr: Map<LocalDate, List<LocalTime>> = emptyMap()
    @Volatile private var loaded = false
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val app = context.applicationContext
            prefs = app.getSharedPreferences("official_schedules", Context.MODE_PRIVATE)
            val map = HashMap<LocalDate, List<LocalTime>>()
            app.assets.list("prayer").orEmpty().filter { it.startsWith("kbr-") && it.endsWith(".json") }.forEach { file ->
                val days = JSONObject(app.assets.open("prayer/$file").bufferedReader().use { it.readText() }).getJSONObject("days")
                days.keys().forEach { k -> map[LocalDate.parse(k)] = days.getJSONArray(k).strings().map(LocalTime::parse) }
            }
            // Days downloaded from the website (only those not bundled).
            prefs.getString("kbr_online", null)?.let { raw ->
                val o = JSONObject(raw)
                o.keys().forEach { k -> map.putIfAbsent(LocalDate.parse(k), o.getJSONArray(k).strings().map(LocalTime::parse)) }
            }
            kbr = map
            loaded = true
        }
    }

    /** Unit tests load the timetable from the JSON file directly (no Android context). */
    internal fun setKbrForTests(days: Map<LocalDate, List<LocalTime>>) { kbr = days; loaded = true }

    fun kbr(date: LocalDate): Map<Prayer, LocalTime>? = kbr[date]?.let { ORDER.zip(it).toMap() }

    fun kbrCovers(date: LocalDate) = kbr.containsKey(date)

    /**
     * Fetches today's/tomorrow's post from kbrdum.ru if the bundled timetable does not cover it.
     * Returns true when new days were added.
     */
    suspend fun refreshKbr(context: Context): Boolean = withContext(Dispatchers.IO) {
        init(context)
        val today = LocalDate.now(java.time.ZoneId.of("Europe/Moscow"))
        if (kbrCovers(today) && kbrCovers(today.plusDays(1))) return@withContext false
        if (prefs.getString("kbr_checked", null) == today.toString()) return@withContext false
        val html = runCatching {
            (URL(KBR_PAGE).openConnection() as HttpURLConnection).run {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("User-Agent", "MuslimPath/1.0 (Android; prayer times)")
                try { inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } } finally { disconnect() }
            }
        }.getOrNull() ?: return@withContext false
        prefs.edit().putString("kbr_checked", today.toString()).apply()

        val found = parseKbrPage(html)
        if (found.isEmpty()) return@withContext false
        val stored = prefs.getString("kbr_online", null)?.let(::JSONObject) ?: JSONObject()
        found.forEach { (date, times) -> stored.put(date.toString(), org.json.JSONArray(times.map { it.toString() })) }
        // Keep the cache small: drop days older than a week.
        stored.keys().asSequence().toList().filter { LocalDate.parse(it) < today.minusDays(7) }.forEach(stored::remove)
        prefs.edit().putString("kbr_online", stored.toString()).apply()
        kbr = kbr + found
        true
    }

    private val MONTHS = listOf("январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр")

    /** Parses daily posts: «<strong>18 октябрь 2026</strong>» followed by a 6-row table of HH.MM times. */
    internal fun parseKbrPage(html: String): Map<LocalDate, List<LocalTime>> {
        val result = linkedMapOf<LocalDate, List<LocalTime>>()
        val header = Regex("""<strong>\s*(\d{1,2})\s+([а-яё]+)\s+(\d{4})\s*</strong>""", RegexOption.IGNORE_CASE)
        header.findAll(html).forEach { m ->
            val word = m.groupValues[2].lowercase()
            // "ма" (май/мая) must be checked last to avoid matching "март".
            val month = MONTHS.indexOfFirst { it != "ма" && word.startsWith(it) }.takeIf { it >= 0 }
                ?: if (word.startsWith("ма")) 4 else return@forEach
            val date = runCatching { LocalDate.of(m.groupValues[3].toInt(), month + 1, m.groupValues[1].toInt()) }.getOrNull() ?: return@forEach
            val tableStart = html.indexOf("<table", m.range.last)
            val tableEnd = html.indexOf("</table>", tableStart)
            if (tableStart < 0 || tableEnd < 0) return@forEach
            val times = Regex("""(?<![\d.:])(\d{1,2})[.:](\d{2})(?![\d.:])""").findAll(html.substring(tableStart, tableEnd))
                .mapNotNull { runCatching { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.getOrNull() }
                .take(6).toList()
            if (times.size == 6 && times.zipWithNext().all { (a, b) -> a < b }) result[date] = times
        }
        return result
    }
}
