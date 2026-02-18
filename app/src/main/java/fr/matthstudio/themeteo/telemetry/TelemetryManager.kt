package fr.matthstudio.themeteo.telemetry

interface TelemetryManager {
    fun setConsentGranted(granted: Boolean)
    fun logException(throwable: Throwable)
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
}
