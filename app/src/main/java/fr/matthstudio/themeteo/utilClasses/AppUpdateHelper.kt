/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.utilClasses

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import fr.matthstudio.themeteo.AppStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object AppUpdateHelper {

    private const val REQUEST_CODE_UPDATE = 1003

    suspend fun isPlayStoreUpdateAvailable(context: Context): Boolean = withContext(Dispatchers.Main) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(context.applicationContext)
            val appUpdateInfo = suspendCancellableCoroutine<AppUpdateInfo?> { cont ->
                appUpdateManager.appUpdateInfo
                    .addOnSuccessListener { info -> cont.resumeWith(Result.success(info)) }
                    .addOnFailureListener { cont.resumeWith(Result.success(null)) }
            }
            val available = appUpdateInfo?.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            Log.d("AppUpdateHelper", "Play Store update available: $available")
            available
        } catch (e: Exception) {
            Log.e("AppUpdateHelper", "Error checking Play Store update availability", e)
            false
        }
    }

    suspend fun promptUpdateIfAvailable(context: Context) = withContext(Dispatchers.Main) {
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(context.applicationContext)
            val appUpdateInfo = suspendCancellableCoroutine<AppUpdateInfo?> { cont ->
                appUpdateManager.appUpdateInfo
                    .addOnSuccessListener { info -> cont.resumeWith(Result.success(info)) }
                    .addOnFailureListener { cont.resumeWith(Result.success(null)) }
            }

            if (appUpdateInfo != null && appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                val activity = context as? Activity
                if (activity != null) {
                    Log.d("AppUpdateHelper", "In-App Update available. Opening Play Store update flow...")
                    val updateType = if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        AppUpdateType.IMMEDIATE
                    } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        AppUpdateType.FLEXIBLE
                    } else null

                    if (updateType != null) {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            activity,
                            AppUpdateOptions.defaultOptions(updateType),
                            REQUEST_CODE_UPDATE
                        )
                    } else {
                        openPlayStoreWeb(context)
                    }
                } else {
                    openPlayStoreWeb(context)
                }
            } else {
                Log.d("AppUpdateHelper", "No Play Store update available. Allowing current version.")
                AppStateManager.updateIsForcedUpdateRequired(false)
            }
        } catch (e: Exception) {
            Log.e("AppUpdateHelper", "Error prompting Play Store update", e)
            AppStateManager.updateIsForcedUpdateRequired(false)
        }
    }

    private fun openPlayStoreWeb(context: Context) {
        try {
            val packageName = context.packageName
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val packageName = context.packageName
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (ignored: Exception) { }
        }
    }
}
