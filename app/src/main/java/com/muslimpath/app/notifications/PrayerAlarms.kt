package com.muslimpath.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.muslimpath.app.MainActivity
import com.muslimpath.app.R
import com.muslimpath.app.data.Prayer
import com.muslimpath.app.data.PrayerCalculator
import com.muslimpath.app.data.PrayerSettings
import com.muslimpath.app.data.UserStore
import com.muslimpath.app.data.zoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Prayer-time notifications. Only the nearest enabled prayer is scheduled; when it fires,
 * the receiver shows the notification and schedules the next one. Alarms are restored after
 * reboot, time or timezone changes and app updates.
 */
object PrayerAlarms {
    const val CHANNEL = "prayer_times"
    private const val REQUEST = 7001

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Время намаза", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Оповещения о наступлении времени намаза" }
        )
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /**
     * One scheduled moment: [early] is the reminder [PrayerSettings.notifyBeforeMin] minutes ahead,
     * otherwise it fires when the time comes (and may play the adhan).
     */
    data class Alarm(val prayer: Prayer, val time: ZonedDateTime, val early: Boolean) {
        fun triggerAt(s: PrayerSettings): ZonedDateTime = if (early) time.minusMinutes(s.notifyBeforeMin.toLong()) else time
    }

    /** Next notification moment for the given settings, or null if notifications are off. */
    fun nextAlarm(s: PrayerSettings, now: ZonedDateTime = ZonedDateTime.now(s.zoneId())): Alarm? {
        if (!s.hasLocation || s.notify.isEmpty()) return null
        val today = now.toLocalDate()
        return (0L..2L).asSequence()
            .mapNotNull { PrayerCalculator.day(s, today.plusDays(it)) }
            .flatMap { d -> d.times.entries.asSequence().filter { it.key in s.notify } }
            .flatMap { (p, t) ->
                // With an early reminder the on-time alarm is only needed for the adhan.
                listOfNotNull(
                    Alarm(p, t, early = true).takeIf { s.notifyBeforeMin > 0 },
                    Alarm(p, t, early = false).takeIf { s.notifyBeforeMin == 0 || s.adhan },
                ).asSequence()
            }
            .firstOrNull { it.triggerAt(s).isAfter(now) }
    }

    fun reschedule(context: Context) {
        com.muslimpath.app.data.OfficialSchedules.init(context)
        // Widgets depend on exactly the same inputs as the alarms, so they are redrawn here too.
        com.muslimpath.app.widgets.PrayerWidgets.update(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val settings = UserStore(context).state.value.prayer
        val next = nextAlarm(settings)
        val pending = { alarm: Alarm? ->
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            if (alarm != null) intent.putExtra("prayer", alarm.prayer.name)
                .putExtra("time", alarm.time.toInstant().toEpochMilli()).putExtra("early", alarm.early)
            PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        if (next == null) {
            am.cancel(pending(null))
            return
        }
        val triggerAt = next.triggerAt(settings).toInstant().toEpochMilli()
        val pi = pending(next)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Without the exact-alarm permission Android may deliver this a few minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun show(context: Context, prayer: Prayer, time: ZonedDateTime, beforeMin: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val (title, text) = texts(prayer, time, beforeMin)
        val open = openPrayer(context, prayer)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_path)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(prayer), n)
    }

    fun notificationId(prayer: Prayer) = 100 + prayer.ordinal

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun texts(prayer: Prayer, time: ZonedDateTime, beforeMin: Int): Pair<String, String> {
        val hhmm = time.format(DateTimeFormatter.ofPattern("HH:mm"))
        return if (beforeMin > 0) "Скоро ${prayer.title} — в $hhmm" to "Через $beforeMin мин наступит время намаза."
        else "Время намаза ${prayer.title}" to "Наступило время намаза ($hhmm)."
    }

    fun openPrayer(context: Context, prayer: Prayer): PendingIntent = PendingIntent.getActivity(
        context, prayer.ordinal,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("open", "prayer"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra("prayer")?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        val time = intent.getLongExtra("time", 0L)
        val early = intent.getBooleanExtra("early", false)
        val settings = UserStore(context).state.value.prayer
        if (prayer != null && time > 0 && prayer in settings.notify) {
            val at = java.time.Instant.ofEpochMilli(time).atZone(settings.zoneId())
            val fired = if (early) at.minusMinutes(settings.notifyBeforeMin.toLong()) else at
            // Skip stale alarms (e.g. delivered long after the device was off).
            if (java.time.Duration.between(fired, ZonedDateTime.now(settings.zoneId())).toMinutes() < 30) {
                when {
                    early -> PrayerAlarms.show(context, prayer, at, settings.notifyBeforeMin)
                    settings.adhan && Adhan.start(context, prayer, at) -> Unit
                    else -> PrayerAlarms.show(context, prayer, at, 0)
                }
            }
        }
        PrayerAlarms.reschedule(context)
    }
}

/** Restores the prayer alarm after reboot, clock/timezone changes and app updates. */
class PrayerRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PrayerAlarms.reschedule(context)
    }
}
