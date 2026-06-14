/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastMainActivity.ForecastMainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_VIGILANCE = "channel_vigilance"
        const val CHANNEL_RAIN = "channel_rain"
        const val VIGILANCE_NOTIFICATION_ID = 1001
        const val RAIN_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val vigilanceChannel = NotificationChannel(
            CHANNEL_VIGILANCE,
            context.getString(R.string.vigilance_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.vigilance_channel_description)
        }

        val rainChannel = NotificationChannel(
            CHANNEL_RAIN,
            context.getString(R.string.rain_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.rain_channel_description)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(vigilanceChannel)
        notificationManager.createNotificationChannel(rainChannel)
    }

    fun showVigilanceNotification(title: String, message: String) {
        val intent = Intent(context, ForecastMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_VIGILANCE)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use proper icon when available
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(VIGILANCE_NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }
    }

    fun showRainNotification(title: String, message: String) {
        val intent = Intent(context, ForecastMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_RAIN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(RAIN_NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                // Permission not granted
            }
        }
    }
}
