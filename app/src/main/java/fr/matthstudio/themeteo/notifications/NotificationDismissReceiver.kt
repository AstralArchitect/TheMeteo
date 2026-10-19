/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.matthstudio.themeteo.TheMeteo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationDismissReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RAIN_NOTIFICATION_DISMISSED = "fr.matthstudio.themeteo.ACTION_RAIN_NOTIFICATION_DISMISSED"
        const val EXTRA_LOCATION_KEY = "extra_location_key"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RAIN_NOTIFICATION_DISMISSED) {
            val locationKey = intent.getStringExtra(EXTRA_LOCATION_KEY)
            Log.d("NotificationDismiss", "Rain notification dismissed by user for locationKey: $locationKey")

            if (locationKey != null) {
                val app = context.applicationContext as? TheMeteo
                val alertRepo = app?.container?.alertStateRepository
                if (alertRepo != null) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            alertRepo.updateRainDismissalTime(locationKey, System.currentTimeMillis())
                            Log.d("NotificationDismiss", "Successfully recorded rain dismissal in AlertStateRepository for $locationKey")
                        } catch (e: Exception) {
                            Log.e("NotificationDismiss", "Error updating rain dismissal time", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
