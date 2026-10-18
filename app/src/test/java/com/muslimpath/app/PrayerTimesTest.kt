package com.muslimpath.app

import com.muslimpath.app.data.AsrSchool
import com.muslimpath.app.data.CalcMethod
import com.muslimpath.app.data.KBR_CITY
import com.muslimpath.app.data.Prayer
import com.muslimpath.app.data.PrayerCalculator
import com.muslimpath.app.data.PrayerSettings
import com.muslimpath.app.data.withCity
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

/** Compares the astronomical calculation for Nalchik with the official DUM KBR timetable for the whole year. */
class PrayerTimesTest {

    private val official: Map<LocalDate, List<LocalTime>> by lazy {
        val days = JSONObject(File("src/main/assets/prayer/kbr-2026.json").readText()).getJSONObject("days")
        days.keys().asSequence().associate { k ->
            LocalDate.parse(k) to (0 until 6).map { LocalTime.parse(days.getJSONArray(k).getString(it)) }
        }
    }

    private fun report(label: String, settings: PrayerSettings) {
        val order = Prayer.entries
        val diffs = order.associateWith { mutableListOf<Long>() }
        official.forEach { (date, times) ->
            val day = PrayerCalculator.day(settings, date) ?: return@forEach
            order.forEachIndexed { i, p ->
                val calc = day.times[p]?.toLocalTime() ?: return@forEachIndexed
                diffs.getValue(p) += Duration.between(times[i], calc).toMinutes()
            }
        }
        val line = order.joinToString("  ") { p ->
            val d = diffs.getValue(p)
            "${p.name}: avg %+.1f max %d".format(d.average(), d.maxOf { abs(it) })
        }
        println("%-28s %s".format(label, line))
    }

    @Test
    fun nalchikUsesOfficialTimetableEveryDay() {
        com.muslimpath.app.data.OfficialSchedules.setKbrForTests(official)
        val nalchik = com.muslimpath.app.data.CITIES.first { it.name == "Нальчик" }
        val settings = PrayerSettings().withCity(nalchik)
        official.forEach { (date, times) ->
            val day = PrayerCalculator.day(settings, date)!!
            org.junit.Assert.assertTrue("$date must be official", day.official)
            org.junit.Assert.assertEquals("$date", times, Prayer.entries.map { day.times.getValue(it).toLocalTime() })
        }
    }

    @Test
    fun compareWithKbr() {
        val base = PrayerSettings(lat = KBR_CITY.lat, lon = KBR_CITY.lon, zone = KBR_CITY.zone)
        val oct18 = PrayerCalculator.day(base.copy(school = AsrSchool.HANAFI), LocalDate.of(2026, 10, 18))!!
        println("Default app settings, 2026-10-18: " + oct18.times.entries.joinToString { "${it.key}=${it.value.toLocalTime()}" })
        println("Official KBR,         2026-10-18: " + official[LocalDate.of(2026, 10, 18)])
        for (m in CalcMethod.entries) for (s in AsrSchool.entries) {
            report("${m.name}/${s.name}", base.copy(method = m, school = s))
        }
    }
}
