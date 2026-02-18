package fr.matthstudio.themeteo.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.content.Context

import fr.matthstudio.themeteo.BuildConfig

class TelemetryManagerImpl(context: Context) : TelemetryManager {
    private val crashlytics = if (BuildConfig.FIREBASE_ENABLED) FirebaseCrashlytics.getInstance() else null

    init {
        // Analytics dependency removed. Only exceptions via Crashlytics are supported.
        crashlytics?.setCrashlyticsCollectionEnabled(false)
    }

    override fun setConsentGranted(granted: Boolean) {
        crashlytics?.setCrashlyticsCollectionEnabled(granted)
    }

    override fun logException(throwable: Throwable) {
        crashlytics?.recordException(throwable)
        if (crashlytics == null) {
            android.util.Log.e("Telemetry", "Exception would be sent to Crashlytics if enabled:", throwable)
        }
    }

    override fun logEvent(name: String, params: Map<String, Any?>) {
        // Analytics is disabled, but we can log to logcat for debug
        android.util.Log.d("Telemetry", "Event: $name, Params: $params")
    }
}
