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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Prepaid-line-only reminder for the balance recharge date obtained from
 * *222*732#. It only posts a local notification: no USSD is dialed from the
 * background. Registering a new package clears the date and stops the reminder.
 */
class RechargeReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val dao = AppDatabase.getInstance(context).dataDao()
        val plan = dao.getDataPlan(PREPAID_LINE_ID).first()
        val eligibleMs = plan?.rechargeEligibleDateMs

        if (eligibleMs != null && startOfToday() >= eligibleMs && canPostNotifications(context)) {
            postNotification(context)
        }

        // Una tarea periódica con initialDelay se ejecuta tarde (el retardo se
        // suma al primer periodo), así que encadenamos tareas únicas diarias.
        scheduleNext(context)
        return Result.success()
    }

    private fun startOfToday(nowMs: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

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

    private fun postNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recarga de saldo (prepago)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisa desde el día en que la línea prepago permite recargar saldo."
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Ya puedes recargar la línea prepago"
        val body = "Según el operador, desde hoy puedes recargar saldo en la Línea 2 (Prepago 35d)."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prepaid)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "prepaid_recharge_reminder"
        private const val NOTIFICATION_ID = 2003
        private const val PREPAID_LINE_ID = 2
        private const val REMINDER_HOUR = 9
        private const val UNIQUE_WORK_NAME = "prepaid_recharge_daily_reminder_once"
        private const val LEGACY_WORK_NAME = "prepaid_recharge_daily_reminder"

        internal fun delayUntilNextReminder(
            nowMs: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault()
        ): Long {
            val reminder = Calendar.getInstance(timeZone).apply {
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

        private fun enqueueNext(context: Context, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<RechargeReminderWorker>()
                .setInitialDelay(delayUntilNextReminder(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                request
            )
        }

        /** Arms the first 09:00 check; later runs are chained by the worker. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(LEGACY_WORK_NAME)
            enqueueNext(context, ExistingWorkPolicy.KEEP)
        }

        private fun scheduleNext(context: Context) {
            enqueueNext(context, ExistingWorkPolicy.REPLACE)
        }

        /** Called when a new prepaid package is registered. */
        fun cancel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        }
    }
}
