package com.calltheitguy.monitor.service

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
import com.calltheitguy.monitor.R
import kotlin.math.absoluteValue

object NotificationHelper {

    private const val CHANNEL_ID = "monitor_alerts"
    private const val CHANNEL_NAME = "Monitor Alerts"
    private const val CHANNEL_DESCRIPTION = "Server, SSL, DNS, and socket monitoring alerts."
    private const val GROUP_MONITOR_ALERTS = "com.calltheitguy.monitor.GROUP_MONITOR_ALERTS"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableVibration(true)
            enableLights(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun notifyOutage(
        context: Context,
        serverLabel: String,
        reason: String,
    ) {
        ensureNotificationChannel(context)
        if (!canPostNotifications(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$serverLabel is down")
            .setContentText(reason.ifBlank { "A configured monitor check failed." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason.ifBlank { "A configured monitor check failed." }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setGroup(GROUP_MONITOR_ALERTS)
            .setAutoCancel(true)
            .setContentIntent(buildLaunchPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(
            stableNotificationId("outage:$serverLabel"),
            notification,
        )
    }

    fun notifyRecovery(
        context: Context,
        serverLabel: String,
        detail: String,
    ) {
        ensureNotificationChannel(context)
        if (!canPostNotifications(context)) return

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$serverLabel recovered")
            .setContentText(detail.ifBlank { "All configured checks are healthy." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail.ifBlank { "All configured checks are healthy." }))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(GROUP_MONITOR_ALERTS)
            .setAutoCancel(true)
            .setContentIntent(buildLaunchPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(
            stableNotificationId("recovery:$serverLabel"),
            notification,
        )
    }

    fun notifySslWarning(
        context: Context,
        serverLabel: String,
        daysRemaining: Long,
    ) {
        ensureNotificationChannel(context)
        if (!canPostNotifications(context)) return

        val body = when {
            daysRemaining < 0L -> "SSL certificate is expired."
            daysRemaining == 0L -> "SSL certificate expires today."
            daysRemaining == 1L -> "SSL certificate expires in 1 day."
            else -> "SSL certificate expires in $daysRemaining days."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SSL warning: $serverLabel")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setGroup(GROUP_MONITOR_ALERTS)
            .setAutoCancel(true)
            .setContentIntent(buildLaunchPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(
            stableNotificationId("ssl:$serverLabel"),
            notification,
        )
    }

    fun cancelServerNotifications(
        context: Context,
        serverLabel: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(stableNotificationId("outage:$serverLabel"))
        manager.cancel(stableNotificationId("recovery:$serverLabel"))
        manager.cancel(stableNotificationId("ssl:$serverLabel"))
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildLaunchPendingIntent(context: Context): PendingIntent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setPackage(context.packageName)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(context, 0, launchIntent, flags)
    }

    private fun stableNotificationId(key: String): Int {
        val hash = key.hashCode()
        return if (hash == Int.MIN_VALUE) 1 else hash.absoluteValue
    }
}
