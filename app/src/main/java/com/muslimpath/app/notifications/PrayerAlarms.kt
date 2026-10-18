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

    /** Next notification moment for the given settings, or null if notifications are off. */
    fun nextAlarm(s: PrayerSettings, now: ZonedDateTime = ZonedDateTime.now(s.zoneId())): Pair<Prayer, ZonedDateTime>? {
        if (!s.hasLocation || s.notify.isEmpty()) return null
        val today = now.toLocalDate()
        return (0L..2L).asSequence()
            .mapNotNull { PrayerCalculator.day(s, today.plusDays(it)) }
            .flatMap { d -> d.times.entries.asSequence().filter { it.key in s.notify }.map { it.key to it.value } }
            .map { (p, t) -> p to t }
            .firstOrNull { (_, t) -> t.minusMinutes(s.notifyBeforeMin.toLong()).isAfter(now) }
    }

    fun reschedule(context: Context) {
        com.muslimpath.app.data.OfficialSchedules.init(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val settings = UserStore(context).state.value.prayer
        val next = nextAlarm(settings)
        val pending = { prayer: Prayer?, time: ZonedDateTime? ->
            val intent = Intent(context, PrayerAlarmReceiver::class.java)
            if (prayer != null && time != null) intent.putExtra("prayer", prayer.name).putExtra("time", time.toInstant().toEpochMilli())
            PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        if (next == null) {
            am.cancel(pending(null, null))
            return
        }
        val (prayer, time) = next
        val triggerAt = time.minusMinutes(settings.notifyBeforeMin.toLong()).toInstant().toEpochMilli()
        val pi = pending(prayer, time)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // Without the exact-alarm permission Android may deliver this a few minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun show(context: Context, prayer: Prayer, time: ZonedDateTime, beforeMin: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val hhmm = time.format(DateTimeFormatter.ofPattern("HH:mm"))
        val title = if (beforeMin > 0) "Скоро ${prayer.title} — в $hhmm" else "Время намаза ${prayer.title}"
        val text = if (beforeMin > 0) "Через $beforeMin мин наступит время намаза." else "Наступило время намаза ($hhmm)."
        val open = PendingIntent.getActivity(
            context, prayer.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("open", "prayer"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_path)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(100 + prayer.ordinal, n)
    }
}

class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra("prayer")?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        val time = intent.getLongExtra("time", 0L)
        val settings = UserStore(context).state.value.prayer
        if (prayer != null && time > 0 && prayer in settings.notify) {
            val at = java.time.Instant.ofEpochMilli(time).atZone(settings.zoneId())
            // Skip stale alarms (e.g. delivered long after the device was off).
            if (java.time.Duration.between(at, ZonedDateTime.now(settings.zoneId())).toMinutes() < 30) {
                PrayerAlarms.show(context, prayer, at, settings.notifyBeforeMin)
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
