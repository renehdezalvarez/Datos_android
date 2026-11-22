package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.local.AppDatabase
import com.example.domain.DataForecastCalculator
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Checks the prepaid line once a day and posts a local notification when the
 * package is close to (or past) its deadline, so the user does not have to
 * open the app to learn that the leftover balance is about to be forfeited.
 *
 * Day 35 is the deadline: whatever balance is left when the cycle ends is
 * lost, so buying on time is what the notification is about.
 */
class PrepaidExpiryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val dao = AppDatabase.getInstance(context).dataDao()
        val plan = dao.getDataPlan(PREPAID_LINE_ID).first() ?: return Result.success()

        val daysFromPurchase = ((System.currentTimeMillis() - plan.purchaseTimestamp) / DAY_MS).toInt() + 1
        // The prepaid line has no contracted package size; only the countdown
        // matters here, so the current balance is used as a neutral reference.
        val forecast = DataForecastCalculator.calculate(
            packageTotalMB = plan.remainingBalanceMB,
            remainingBalanceMB = plan.remainingBalanceMB,
            totalCycleDays = plan.totalCycleDays,
            daysElapsed = daysFromPurchase
        )

        val daysLeft = forecast.daysRemaining
        if (daysLeft > WARNING_DAYS) return Result.success()

        val (title, body) = when {
            daysLeft <= 0 -> "Paquete prepago vencido" to
                "El saldo restante se perdió. Compra un nuevo paquete para recuperar tus datos."
            daysLeft == 1 -> "Paquete prepago: último día" to
                "Al terminar hoy el saldo restante se pierde. Compra ahora para conservarlo."
            else -> "Quedan $daysLeft días del paquete prepago" to
                "El día ${forecast.totalCycleDays} es la fecha límite: el saldo que quede al vencer se pierde."
        }

        if (!canPostNotifications(context)) return Result.success()
        postNotification(context, title, body)
        return Result.success()
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

    private fun postNotification(context: Context, title: String, body: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vencimiento del paquete prepago",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisa cuando el paquete prepago está por vencer (saldo a punto de perderse)."
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prepaid)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "prepaid_expiry"
        private const val NOTIFICATION_ID = 2001
        private const val PREPAID_LINE_ID = 2
        private const val WARNING_DAYS = 5
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val UNIQUE_WORK_NAME = "prepaid_expiry_daily_check"

        /**
         * Schedules the daily check. KEEP means an already-scheduled periodic
         * work is left untouched, so this is cheap to call on every app start
         * and the schedule survives reboots (WorkManager persists it).
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PrepaidExpiryWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
