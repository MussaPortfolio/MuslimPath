package com.muslimpath.app.data

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class Prayer(val title: String, val notifiable: Boolean) {
    FAJR("Фаджр", true),
    SUNRISE("Восход", false), // not a prayer: marks the end of Fajr time
    DHUHR("Зухр", true),
    ASR("Аср", true),
    MAGHRIB("Магриб", true),
    ISHA("Иша", true),
}

enum class CalcMethod(val label: String, val method: CalculationMethod) {
    MWL("Всемирная исламская лига", CalculationMethod.MUSLIM_WORLD_LEAGUE),
    EGYPT("Египетское управление", CalculationMethod.EGYPTIAN),
    KARACHI("Университет Карачи", CalculationMethod.KARACHI),
    UMM_AL_QURA("Умм аль-Кура (Мекка)", CalculationMethod.UMM_AL_QURA),
    MOONSIGHTING("Moonsighting Committee", CalculationMethod.MOON_SIGHTING_COMMITTEE),
    ISNA("ISNA (Северная Америка)", CalculationMethod.NORTH_AMERICA),
    DUBAI("Дубай", CalculationMethod.DUBAI),
    KUWAIT("Кувейт", CalculationMethod.KUWAIT),
    QATAR("Катар", CalculationMethod.QATAR),
    SINGAPORE("Сингапур", CalculationMethod.SINGAPORE),
}

enum class AsrSchool(val label: String) {
    SHAFI("Шафиитский, маликитский, ханбалитский (раньше)"),
    HANAFI("Ханафитский (позже)"),
}

data class City(
    val name: String,
    val lat: Double,
    val lon: Double,
    val zone: String,
    val school: AsrSchool = AsrSchool.HANAFI,
    val official: PrayerSource? = null,
)

/** Places offered in the city picker (the user may also use device location). */
val CITIES = listOf(
    City("Москва", 55.7558, 37.6173, "Europe/Moscow"),
    City("Санкт-Петербург", 59.9343, 30.3351, "Europe/Moscow"),
    City("Казань", 55.7963, 49.1088, "Europe/Moscow"),
    City("Набережные Челны", 55.7436, 52.3958, "Europe/Moscow"),
    City("Уфа", 54.7388, 55.9721, "Asia/Yekaterinburg"),
    City("Грозный", 43.3178, 45.6982, "Europe/Moscow", AsrSchool.SHAFI),
    City("Махачкала", 42.9849, 47.5047, "Europe/Moscow", AsrSchool.SHAFI),
    City("Дербент", 42.0578, 48.2887, "Europe/Moscow", AsrSchool.SHAFI),
    City("Нальчик", 43.4853, 43.6071, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR),
    City("Баксан", 43.6819, 43.5345, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR),
    City("Прохладный", 43.7575, 44.0297, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR),
    City("Нарткала", 43.5578, 43.8575, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR),
    City("Чегем", 43.5675, 43.5875, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR),
    City("Майский", 43.6283, 44.0517, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR),
    City("Терек", 43.4839, 44.1367, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR),
    City("Тырныауз", 43.3981, 42.9214, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR),
    City("Владикавказ", 43.0367, 44.6678, "Europe/Moscow", AsrSchool.SHAFI),
    City("Магас", 43.1667, 44.8000, "Europe/Moscow", AsrSchool.SHAFI),
    City("Черкесск", 44.2269, 42.0467, "Europe/Moscow", AsrSchool.SHAFI),
    City("Майкоп", 44.6098, 40.1006, "Europe/Moscow", AsrSchool.SHAFI),
    City("Ставрополь", 45.0428, 41.9734, "Europe/Moscow"),
    City("Краснодар", 45.0355, 38.9753, "Europe/Moscow"),
    City("Ростов-на-Дону", 47.2357, 39.7015, "Europe/Moscow"),
    City("Астрахань", 46.3497, 48.0408, "Europe/Astrakhan"),
    City("Волгоград", 48.7080, 44.5133, "Europe/Volgograd"),
    City("Саратов", 51.5331, 46.0342, "Europe/Saratov"),
    City("Самара", 53.1959, 50.1002, "Europe/Samara"),
    City("Нижний Новгород", 56.3269, 44.0059, "Europe/Moscow"),
    City("Пермь", 58.0105, 56.2502, "Asia/Yekaterinburg"),
    City("Оренбург", 51.7682, 55.0969, "Asia/Yekaterinburg"),
    City("Екатеринбург", 56.8389, 60.6057, "Asia/Yekaterinburg"),
    City("Челябинск", 55.1644, 61.4368, "Asia/Yekaterinburg"),
    City("Тюмень", 57.1530, 65.5343, "Asia/Yekaterinburg"),
    City("Омск", 54.9885, 73.3242, "Asia/Omsk"),
    City("Новосибирск", 55.0084, 82.9357, "Asia/Novosibirsk"),
    City("Красноярск", 56.0153, 92.8932, "Asia/Krasnoyarsk"),
    City("Минск", 53.9006, 27.5590, "Europe/Minsk"),
    City("Алматы", 43.2220, 76.8512, "Asia/Almaty"),
    City("Астана", 51.1694, 71.4491, "Asia/Almaty"),
    City("Ташкент", 41.2995, 69.2401, "Asia/Tashkent"),
    City("Бишкек", 42.8746, 74.5698, "Asia/Bishkek"),
    City("Душанбе", 38.5598, 68.7870, "Asia/Dushanbe"),
    City("Баку", 40.4093, 49.8671, "Asia/Baku"),
)

/** [official] is false when an official timetable was selected but has no data for this date. */
data class DayTimes(val date: LocalDate, val times: Map<Prayer, ZonedDateTime>, val official: Boolean = false)

/** Nalchik — used for the astronomical fallback when the KBR timetable has no data. */
val KBR_CITY = City("Кабардино-Балкария", 43.4853, 43.6071, "Europe/Moscow", AsrSchool.SHAFI, PrayerSource.KBR)

/** Rough bounding box of Kabardino-Balkaria: a detected location inside it uses the DUM KBR timetable. */
fun isInKbr(lat: Double, lon: Double) = lat in 42.85..44.05 && lon in 42.35..44.60

/** Default Asr school for a detected location: Shafi'i across the North Caucasus, Hanafi elsewhere. */
fun defaultSchoolFor(lat: Double, lon: Double) =
    if (lat in 41.0..45.5 && lon in 38.5..48.7) AsrSchool.SHAFI else AsrSchool.HANAFI

/** Settings for choosing [city]: official timetable where one exists, otherwise calculation. */
fun PrayerSettings.withCity(city: City) = copy(
    source = city.official ?: PrayerSource.CALCULATED,
    placeName = if (city.official == PrayerSource.KBR) "${city.name} · график ДУМ КБР" else city.name,
    lat = city.lat, lon = city.lon, zone = city.zone, school = city.school,
)

object PrayerCalculator {

    fun day(settings: PrayerSettings, date: LocalDate): DayTimes? {
        if (settings.source == PrayerSource.KBR) {
            OfficialSchedules.kbr(date)?.let { t ->
                val zone = settings.zoneId()
                val map = linkedMapOf<Prayer, ZonedDateTime>()
                t.forEach { (p, time) -> map[p] = date.atTime(time).atZone(zone) }
                // Official timetable is shown as published, without manual corrections.
                return DayTimes(date, map, official = true)
            }
        }
        val lat = settings.lat ?: return null
        val lon = settings.lon ?: return null
        val zone = settings.zoneId()
        val params = settings.method.method.parameters.apply {
            madhab = if (settings.school == AsrSchool.HANAFI) Madhab.HANAFI else Madhab.SHAFI
            // Needed in the north (Moscow, St. Petersburg) where twilight never ends in summer.
            highLatitudeRule = HighLatitudeRule.SEVENTH_OF_THE_NIGHT
        }
        val pt = PrayerTimes(Coordinates(lat, lon), DateComponents(date.year, date.monthValue, date.dayOfMonth), params)
        fun z(d: java.util.Date?) = d?.toInstant()?.atZone(zone)?.withSecond(0)?.withNano(0)
        val map = linkedMapOf<Prayer, ZonedDateTime>()
        z(pt.fajr)?.let { map[Prayer.FAJR] = it }
        z(pt.sunrise)?.let { map[Prayer.SUNRISE] = it }
        z(pt.dhuhr)?.let { map[Prayer.DHUHR] = it }
        z(pt.asr)?.let { map[Prayer.ASR] = it }
        z(pt.maghrib)?.let { map[Prayer.MAGHRIB] = it }
        z(pt.isha)?.let { map[Prayer.ISHA] = it }
        settings.adjustments.forEach { (p, min) -> map[p]?.let { map[p] = it.plusMinutes(min.toLong()) } }
        return DayTimes(date, map)
    }

    /** Next prayer (excluding sunrise) after [now], looking into tomorrow if needed. */
    fun next(settings: PrayerSettings, now: ZonedDateTime = ZonedDateTime.now(settings.zoneId())): Pair<Prayer, ZonedDateTime>? {
        val today = now.toLocalDate()
        for (d in listOf(today, today.plusDays(1))) {
            val day = day(settings, d) ?: return null
            day.times.entries.firstOrNull { it.key != Prayer.SUNRISE && it.value.isAfter(now) }?.let { return it.key to it.value }
        }
        return null
    }
}

fun PrayerSettings.zoneId(): ZoneId = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.systemDefault())
