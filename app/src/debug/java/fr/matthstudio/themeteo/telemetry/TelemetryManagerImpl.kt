/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.content.Context

import fr.matthstudio.themeteo.BuildConfig

class TelemetryManagerImpl(context: Context) : TelemetryManager {
    private val crashlytics = if (BuildConfig.FIREBASE_ENABLED) FirebaseCrashlytics.getInstance() else null

    init {
        // Only exceptions via Crashlytics are supported.
        crashlytics?.isCrashlyticsCollectionEnabled = false
    }

    override fun setConsentGranted(granted: Boolean) {
        // Analytics/Crashlytics disabled on debug
    }

    override fun logException(throwable: Throwable) {
        // Analytics/Crashlytics disabled on debug
    }

    override fun logEvent(name: String, params: Map<String, Any>?) {
        // Analytics/Crashlytics disabled on debug
    }
}
