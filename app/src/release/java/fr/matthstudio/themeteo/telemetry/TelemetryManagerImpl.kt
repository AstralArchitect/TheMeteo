package fr.matthstudio.themeteo.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.content.Context

import fr.matthstudio.themeteo.BuildConfig
import java.util.concurrent.CancellationException
import java.net.UnknownHostException

class TelemetryManagerImpl(context: Context) : TelemetryManager {
    private val crashlytics = if (BuildConfig.FIREBASE_ENABLED) FirebaseCrashlytics.getInstance() else null

    init {
        // Analytics dependency removed. Only exceptions via Crashlytics are supported.
        crashlytics?.isCrashlyticsCollectionEnabled = false
    }

    override fun setConsentGranted(granted: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = granted
    }

    override fun logException(throwable: Throwable) {
        if (throwable is CancellationException || 
            throwable is UnknownHostException || 
            throwable is java.net.ConnectException || 
            throwable is java.net.SocketTimeoutException) return
        crashlytics?.recordException(throwable)
        if (crashlytics == null) {
            android.util.Log.e("Telemetry", "Exception would be sent to Crashlytics if enabled:", throwable)
        }
    }

    override fun logEvent(name: String, params: Map<String, Any>?) {
        // Analytics dependency removed. Only exceptions via Crashlytics are supported.
        crashlytics?.log("$name: $params")
        if (crashlytics == null) {
            android.util.Log.d("Telemetry", "Event would be sent to Crashlytics if enabled: $name, $params")
        }
    }
}
