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
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastMainActivity.ForecastMainActivity
import fr.matthstudio.themeteo.utilsActivities.LauncherActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_VIGILANCE_YELLOW = "channel_vigilance_yellow"
        const val CHANNEL_VIGILANCE_HIGH = "channel_vigilance_high"
        const val CHANNEL_RAIN = "channel_rain"
        const val VIGILANCE_NOTIFICATION_ID = 1001
        const val RAIN_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val highVigilanceChannel = NotificationChannel(
            CHANNEL_VIGILANCE_HIGH,
            context.getString(R.string.vigilance_high_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.vigilance_high_channel_description)
        }

        val yellowVigilanceChannel = NotificationChannel(
            CHANNEL_VIGILANCE_YELLOW,
            context.getString(R.string.vigilance_yellow_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.vigilance_yellow_channel_description)
        }

        val rainChannel = NotificationChannel(
            CHANNEL_RAIN,
            context.getString(R.string.rain_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.rain_channel_description)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(yellowVigilanceChannel)
        notificationManager.createNotificationChannel(highVigilanceChannel)
        notificationManager.createNotificationChannel(rainChannel)
    }

    fun showVigilanceNotification(title: String, message: String, level: Int) {
        val isHigh = level > 2
        val channelId = if (isHigh) CHANNEL_VIGILANCE_HIGH else CHANNEL_VIGILANCE_YELLOW
        val priority = if (isHigh) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

        val intent = Intent(context, LauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.code_black)
            .setLargeIcon(
                when (level) {
                    2 -> ContextCompat.getDrawable(context, R.drawable.code_yellow)?.toBitmap()
                    3 -> ContextCompat.getDrawable(context, R.drawable.code_orange)?.toBitmap()
                    4 -> ContextCompat.getDrawable(context, R.drawable.code_red)?.toBitmap()
                    else -> ContextCompat.getDrawable(context, R.drawable.code_black)?.toBitmap()
                }
            )
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority)
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
        val intent = Intent(context, LauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_RAIN)
            .setSmallIcon(R.drawable.rainy_3)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
