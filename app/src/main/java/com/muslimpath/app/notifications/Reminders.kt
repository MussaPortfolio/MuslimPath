package com.muslimpath.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.muslimpath.app.MainActivity
import com.muslimpath.app.R
import com.muslimpath.app.data.ReminderFrequency
import java.util.concurrent.TimeUnit

/**
 * Local learning reminders. Wording is deliberately gentle — no guilt, no religious pressure
 * (ТЗ 3.8). Server pushes (answers, new materials) go through FCM once the backend is connected.
 */
object Reminders {
    private const val WORK = "learning_reminder"
    const val CHANNEL = "reminders"

    private val messages = listOf(
        "Когда будет удобно — следующий урок ждёт вас.",
        "Пара спокойных минут? Можно продолжить с того места, где вы остановились.",
        "Небольшой шаг тоже шаг. Урок займёт всего несколько минут.",
        "Ваш маршрут сохранён. Возвращайтесь, когда будете готовы.",
    )

    fun schedule(context: Context, freq: ReminderFrequency) {
        val wm = WorkManager.getInstance(context)
        if (freq == ReminderFrequency.OFF) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(freq.days.toLong(), TimeUnit.DAYS)
            .setInitialDelay(freq.days.toLong(), TimeUnit.DAYS)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_reminders_desc) }
        )
    }

    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_path)
            .setContentTitle("Путь")
            .setContentText(messages.random())
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Reminders.show(applicationContext)
        return Result.success()
    }
}
