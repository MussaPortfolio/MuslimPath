package com.muslimpath.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.muslimpath.app.MainActivity
import com.muslimpath.app.R
import com.muslimpath.app.data.OfficialSchedules
import com.muslimpath.app.data.Prayer
import com.muslimpath.app.data.PrayerCalculator
import com.muslimpath.app.data.PrayerSettings
import com.muslimpath.app.data.UserStore
import com.muslimpath.app.data.zoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-screen widgets with prayer times. They are redrawn whenever prayer alarms are rescheduled
 * (settings change, timetable refresh, reboot, clock change) and by an inexact alarm at the next
 * prayer and at midnight, so the highlighted prayer and the date stay current.
 */
object PrayerWidgets {
    private const val REQUEST_TICK = 7101
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))

    private val DAY_CELLS = listOf(
        Triple(Prayer.FAJR, R.id.cell_fajr, R.id.time_fajr),
        Triple(Prayer.SUNRISE, R.id.cell_sunrise, R.id.time_sunrise),
        Triple(Prayer.DHUHR, R.id.cell_dhuhr, R.id.time_dhuhr),
        Triple(Prayer.ASR, R.id.cell_asr, R.id.time_asr),
        Triple(Prayer.MAGHRIB, R.id.cell_maghrib, R.id.time_maghrib),
        Triple(Prayer.ISHA, R.id.cell_isha, R.id.time_isha),
    )
    private val DAY_LABELS = listOf(R.id.name_fajr, R.id.name_sunrise, R.id.name_dhuhr, R.id.name_asr, R.id.name_maghrib, R.id.name_isha)

    fun update(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val nextIds = manager.getAppWidgetIds(ComponentName(app, NextPrayerWidget::class.java))
        val dayIds = manager.getAppWidgetIds(ComponentName(app, DayPrayerWidget::class.java))
        if (nextIds.isEmpty() && dayIds.isEmpty()) {
            cancelTick(app)
            return
        }
        OfficialSchedules.init(app)
        val settings = UserStore(app).state.value.prayer
        val now = ZonedDateTime.now(settings.zoneId())
        if (nextIds.isNotEmpty()) manager.updateAppWidget(nextIds, nextViews(app, settings, now))
        if (dayIds.isNotEmpty()) manager.updateAppWidget(dayIds, dayViews(app, settings, now))
        scheduleTick(app, settings, now)
    }

    private fun nextViews(context: Context, s: PrayerSettings, now: ZonedDateTime): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_next_prayer)
        v.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        val next = if (s.hasLocation) PrayerCalculator.next(s, now) else null
        if (next == null) {
            v.setTextViewText(R.id.prayer_name, "Время намаза")
            v.setTextViewText(R.id.prayer_time, "")
            v.setViewVisibility(R.id.prayer_time, View.GONE)
            v.setViewVisibility(R.id.countdown, View.GONE)
            v.setViewVisibility(R.id.place, View.VISIBLE)
            v.setTextViewText(R.id.place, if (s.hasLocation) "Нет данных на сегодня" else "Выберите город в приложении")
            return v
        }
        val (prayer, time) = next
        v.setTextViewText(R.id.prayer_name, prayer.title)
        v.setViewVisibility(R.id.prayer_time, View.VISIBLE)
        v.setTextViewText(R.id.prayer_time, time.format(HHMM))
        val left = java.time.Duration.between(now, time).toMillis().coerceAtLeast(0)
        v.setViewVisibility(R.id.countdown, View.VISIBLE)
        v.setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + left, "через %s", true)
        v.setChronometerCountDown(R.id.countdown, true)
        // The place is only used for the empty-state hint; the compact widget has no room for it.
        v.setViewVisibility(R.id.place, View.GONE)
        return v
    }

    private fun dayViews(context: Context, s: PrayerSettings, now: ZonedDateTime): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_day_prayers)
        v.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        val day = if (s.hasLocation) PrayerCalculator.day(s, now.toLocalDate()) else null
        // "Нальчик · график ДУМ КБР" → "Нальчик": the wide widget has room for the city only.
        v.setTextViewText(R.id.place, s.placeName?.substringBefore(" · ") ?: "Время намаза")
        v.setTextViewText(R.id.date, now.format(DATE).replaceFirstChar { it.uppercase() })
        if (day == null) {
            v.setViewVisibility(R.id.times_row, View.GONE)
            v.setViewVisibility(R.id.empty, View.VISIBLE)
            v.setTextViewText(R.id.empty, if (s.hasLocation) "Нет данных на сегодня" else "Выберите город в приложении")
            return v
        }
        v.setViewVisibility(R.id.times_row, View.VISIBLE)
        v.setViewVisibility(R.id.empty, View.GONE)
        // Highlight the upcoming prayer; after Isha nothing is left today, so none is highlighted.
        val upcoming = day.times.entries.firstOrNull { it.key != Prayer.SUNRISE && it.value.isAfter(now) }?.key
        DAY_CELLS.forEachIndexed { i, (prayer, cell, timeId) ->
            v.setTextViewText(timeId, day.times[prayer]?.format(HHMM) ?: "—")
            val active = prayer == upcoming
            v.setInt(cell, "setBackgroundResource", if (active) R.drawable.widget_cell_active else 0)
            val color = context.getColor(if (active) R.color.widget_on_accent else R.color.widget_text)
            v.setTextColor(timeId, color)
            v.setTextColor(DAY_LABELS[i], if (active) color else context.getColor(R.color.widget_muted))
        }
        return v
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 7100,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open", "prayer"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun tickIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST_TICK, Intent(context, PrayerWidgetTickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Non-waking alarm: if the screen is off, the redraw simply happens when the device wakes. */
    private fun scheduleTick(context: Context, s: PrayerSettings, now: ZonedDateTime) {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        val next = if (s.hasLocation) PrayerCalculator.next(s, now)?.second else null
        val at = listOfNotNull(next, midnight).min().plusSeconds(5)
        context.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.RTC, at.toInstant().toEpochMilli(), tickIntent(context))
    }

    private fun cancelTick(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
    }
}

/** 2×1: the next prayer with a live countdown. */
class NextPrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = PrayerWidgets.update(context)
    override fun onDisabled(context: Context) = PrayerWidgets.update(context)
}

/** 4×1: all of today's times, the upcoming prayer highlighted. */
class DayPrayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = PrayerWidgets.update(context)
    override fun onDisabled(context: Context) = PrayerWidgets.update(context)
}

class PrayerWidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = PrayerWidgets.update(context)
}
