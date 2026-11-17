package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.repository.DataPlanRepository
import com.example.domain.BalanceReadingReminder
import com.example.domain.BalanceReadingSample
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Late-day nudge to register today's balance reading.
 *
 * This worker only posts a local notification. It never starts a USSD
 * consultation, a dialer intent or the accessibility catcher in the
 * background: that would break the privacy model of the manual "Consultar
 * Saldo" flow. Tapping the notification just returns to the app so the user
 * decides when to consult.
 */
class BalanceReadingReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val repository = DataPlanRepository(AppDatabase.getInstance(context).dataDao())
        val (dayStart, dayEnd) = todayRange()

        val readings = repository.getAllLogs().first().map {
            BalanceReadingSample(planId = it.planId, timestampMs = it.timestamp)
        }
        val missingLineIds = BalanceReadingReminder.missingLineIds(readings, dayStart, dayEnd)
        if (missingLineIds.isEmpty()) return Result.success()

        if (!canPostNotifications(context)) return Result.success()
        postNotification(context, missingLineIds)
        return Result.success()
    }

    /** Local-day boundaries in milliseconds; the upper bound is exclusive. */
    private fun todayRange(nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    private fun canPostNotifications(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun postNotification(context: Context, missingLineIds: List<Int>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recordatorio de saldo",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisa al final del día si falta la lectura de saldo de alguna línea."
            }
            manager.createNotificationChannel(channel)
        }

        val lineNames = missingLineIds.joinToString(", ") { lineId ->
            if (lineId == BalanceReadingReminder.MONTHLY_LINE_ID) {
                "Línea 1 (Mensual)"
            } else {
                "Línea 2 (Prepago 35d)"
            }
        }
        val title = "Recordatorio: consulta tu saldo"
        val body = "Consulta tu saldo para afinar el pronóstico"
        val detail = "No hay lectura registrada hoy de: $lineNames."

        // The PendingIntent only brings the app to the foreground. No USSD
        // code is dialed and no capture is armed from this notification.
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prepaid)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail $body"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "balance_reading_reminder"
        private const val NOTIFICATION_ID = 2002
        private const val REMINDER_HOUR = 22
        private const val UNIQUE_WORK_NAME = "balance_reading_daily_reminder"

        private fun delayUntilNextReminder(
            nowMs: Long = System.currentTimeMillis()
        ): Long {
            val reminder = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (reminder.timeInMillis <= nowMs) {
                reminder.add(Calendar.DAY_OF_YEAR, 1)
            }
            return reminder.timeInMillis - nowMs
        }

        /** Schedules the daily nudge for 22:00 local time. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BalanceReadingReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextReminder(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** Called when the app already has today's readings for both lines. */
        fun cancel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        }
    }
}
