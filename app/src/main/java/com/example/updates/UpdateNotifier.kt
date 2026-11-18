package com.example.updates

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
import com.example.MainActivity
import com.example.R

/** Local notifications for the two moments that matter: found and downloaded. */
object UpdateNotifier {

    fun postAvailable(context: Context, info: UpdateInfo) {
        post(
            context,
            NOTIFICATION_ID_AVAILABLE,
            "Actualización disponible",
            "Versión ${info.versionName} lista para descargar."
        )
    }

    fun postReady(context: Context, info: UpdateInfo) {
        post(
            context,
            NOTIFICATION_ID_READY,
            "Actualización descargada",
            "Versión ${info.versionName} lista para instalar."
        )
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_AVAILABLE)
        manager.cancel(NOTIFICATION_ID_READY)
    }

    private fun post(context: Context, notificationId: Int, title: String, body: String) {
        if (!canPostNotifications(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Actualizaciones de la app",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Avisa cuando hay una versión nueva o ya está descargada."
                }
            )
        }

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_prepaid)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId, notification)
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

    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID_AVAILABLE = 2004
    private const val NOTIFICATION_ID_READY = 2005
}
