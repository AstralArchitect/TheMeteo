/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.ui.res.stringResource
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.BinaryDecoder
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.GpsCoordinates
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class WeatherNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val app = context.applicationContext as TheMeteo
    private val weatherService = WeatherService(app.container.telemetryManager)
    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result {
        Log.d("WeatherWorker", "Starting background weather check")

        val intervalMinutes = getScheduledIntervalMinutes(applicationContext)
        val is15MinMode = intervalMinutes == 15L

        val userSettings = app.container.userSettingsRepository
        val userLocations = app.container.userLocationsRepository
        val alertState = app.container.alertStateRepository

        // 1. Collect locations
        val savedLocations = userLocations.savedLocations.first()
        val defaultLocation = userSettings.defaultLocation.first() ?: LocationIdentifier.CurrentUserLocation
        
        val allLocations = mutableListOf<LocationIdentifier>()
        allLocations.add(LocationIdentifier.CurrentUserLocation)
        savedLocations.forEach { allLocations.add(LocationIdentifier.Saved(it)) }

        // 2. Fetch data and analyze
        // En mode 15mn, SEULEMENT la pluie dans l'heure est téléchargée
        if (!is15MinMode && userSettings.enableVigilanceNotifications.first()) {
            checkVigilance(allLocations, alertState)
        }
        if (userSettings.enableRainNotifications.first()) {
            checkRain(defaultLocation, alertState, is15MinMode)
        }

        return Result.success()
    }

    private suspend fun checkVigilance(locations: List<LocationIdentifier>, alertStateRepository: fr.matthstudio.themeteo.data.AlertStateRepository) {
        val lastLevels = alertStateRepository.lastVigilanceLevels.first()
        
        for (location in locations) {
            val coords = when (location) {
                is LocationIdentifier.CurrentUserLocation -> AppStateManager.currentGpsPosition.value
                is LocationIdentifier.Saved -> GpsCoordinates(location.location.latitude, location.location.longitude)
            } ?: continue
            val locName = when (location) {
                is LocationIdentifier.CurrentUserLocation -> applicationContext.getString(R.string.current_location)
                is LocationIdentifier.Saved -> location.location.name
            }

            val vigilance = weatherService.getVigilanceForLocation(coords.latitude, coords.longitude) ?: continue
            val locationKey = Json.encodeToString(location)
            val lastLevel = lastLevels[locationKey] ?: 1

            if (vigilance.maxColorId > lastLevel && vigilance.maxColorId >= 1) {
                val mainAlert = vigilance.alerts.maxByOrNull { it.maxColorId }
                val phenomenonName = applicationContext.getString(mapPhenomenonIdToName(mainAlert?.phenomenonId ?: ""))
                val title = "${getVigilanceLevelName(vigilance.maxColorId, true).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} : ${phenomenonName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
                
                val relevantStep = mainAlert?.steps?.firstOrNull { it.colorId == vigilance.maxColorId }
                    ?: mainAlert?.steps?.firstOrNull()
                
                val text = if (relevantStep != null) {
                    try {
                        val formatter = DateTimeFormatter.ofPattern("HH:mm")
                        val start = OffsetDateTime.parse(relevantStep.beginTime).format(formatter)
                        val end = OffsetDateTime.parse(relevantStep.endTime).format(formatter)
                        applicationContext.getString(R.string.vigilance_duration, start, end, locName)
                    } catch (e: Exception) {
                        ""
                    }
                } else ""
                
                notificationHelper.showVigilanceNotification(title, text, vigilance.maxColorId, location)
                alertStateRepository.updateVigilanceLevel(locationKey, vigilance.maxColorId)
            } else if (vigilance.maxColorId < lastLevel) {
                // Update state if level decreased
                alertStateRepository.updateVigilanceLevel(locationKey, vigilance.maxColorId)
            }
        }
    }

    private suspend fun checkRain(
        defaultLocation: LocationIdentifier,
        alertStateRepository: fr.matthstudio.themeteo.data.AlertStateRepository,
        is15MinMode: Boolean
    ) {
        val targets = mutableListOf<LocationIdentifier>()
        targets.add(defaultLocation)
        if (defaultLocation !is LocationIdentifier.CurrentUserLocation) {
            targets.add(LocationIdentifier.CurrentUserLocation)
        }

        val lastNotifications = alertStateRepository.lastRainNotifications.first()
        val lastDismissals = alertStateRepository.lastRainDismissals.first()
        val now = System.currentTimeMillis()

        var isRainDetectedInAnyLocation = false

        for (location in targets) {
            val coords = when (location) {
                is LocationIdentifier.CurrentUserLocation -> AppStateManager.currentGpsPosition.value
                is LocationIdentifier.Saved -> GpsCoordinates(location.location.latitude, location.location.longitude)
            } ?: continue

            val locationKey = Json.encodeToString(location)
            val lastNotifTime = lastNotifications[locationKey] ?: 0L
            val lastDismissTime = lastDismissals[locationKey] ?: 0L

            // Si l'utilisateur a supprimé la notification après l'avoir reçue, délai de 6h
            // Sinon (notification pas supprimée), mise à jour toutes les 5 minutes
            val isDismissedAfterNotif = lastDismissTime > lastNotifTime
            val requiredCooldown = if (isDismissedAfterNotif) {
                6 * 60 * 60 * 1000L // 6 heures si supprimée
            } else {
                5 * 60 * 1000L // 5 minutes si conservée (mise à jour)
            }

            val canSendNotification = (now - lastNotifTime >= requiredCooldown)

            // 1. Check rain within the hour (Météo-France)
            val rainWithinHour = weatherService.getRainWithinHour(coords.latitude, coords.longitude)
            val rainForecast1h = rainWithinHour?.properties?.forecast?.firstOrNull { it.rainIntensity > 1 }

            if (rainForecast1h != null) {
                isRainDetectedInAnyLocation = true
                if (canSendNotification) {
                    val timeStr = try {
                        val zdt = OffsetDateTime.parse(rainForecast1h.time)
                        val local = zdt.atZoneSameInstant(ZoneId.systemDefault())
                        String.format("%02d:%02d", local.hour, local.minute)
                    } catch (e: Exception) {
                        rainForecast1h.time
                    }
                    val locationStr = if (location is LocationIdentifier.Saved) location.location.name else applicationContext.getString(R.string.current_location)
                    val message = applicationContext.getString(R.string.rain_at_format, "$timeStr ($locationStr)")
                    notificationHelper.showRainNotification(applicationContext.getString(R.string.rain_alert_title), message, locationKey, location)
                    alertStateRepository.updateRainNotificationTime(locationKey, now)
                }
                continue
            }

            // 2. Check short term forecast (AROME / IFS) for the next 6 hours (uniquement si NON en mode 15mn)
            if (!is15MinMode) {
                val rawShortTerm = weatherService.getShortTermForecast(coords.longitude, coords.latitude, applicationContext)
                if (rawShortTerm != null) {
                    val shortForecast = BinaryDecoder.parseFullShortTerm(rawShortTerm.first, rawShortTerm.second)
                    val currentTime = LocalDateTime.now(ZoneOffset.UTC)

                    val nextRainStep = shortForecast.steps.firstOrNull { step ->
                        step.time.isAfter(currentTime) &&
                        step.time.isBefore(currentTime.plusHours(6)) &&
                        step.precipitation > 0.1f
                    }

                    if (nextRainStep != null) {
                        if (nextRainStep.time.isBefore(currentTime.plusHours(1))) {
                            isRainDetectedInAnyLocation = true
                        }

                        if (canSendNotification) {
                            val localTime = nextRainStep.time.atZone(ZoneOffset.UTC)
                                .withZoneSameInstant(ZoneId.systemDefault())
                            val timeStr = String.format("%02d:%02d", localTime.hour, localTime.minute)
                            val locationStr = if (location is LocationIdentifier.Saved) location.location.name else applicationContext.getString(R.string.current_location)
                            val message = applicationContext.getString(R.string.rain_at_format, "$timeStr ($locationStr)")
                            notificationHelper.showRainNotification(applicationContext.getString(R.string.rain_alert_title), message, locationKey, location)
                            alertStateRepository.updateRainNotificationTime(locationKey, now)
                        }
                    }
                }
            }
        }

        // Ajustement dynamique de l'intervalle WorkManager :
        // Si de la pluie est détectée dans l'heure -> intervalle court de 15 minutes (minimum WorkManager)
        // Si aucune pluie n'est prévue -> retour à l'intervalle normal de 60 minutes (1 heure)
        if (isRainDetectedInAnyLocation) {
            updateWorkInterval(applicationContext, 15)
        } else {
            updateWorkInterval(applicationContext, 60)
        }
    }

    companion object {
        const val WORK_NAME = "WeatherAlertWork"
        private const val PREFS_NAME = "weather_worker_prefs"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"

        private var currentScheduledIntervalMinutes: Long = -1L

        fun getScheduledIntervalMinutes(context: Context): Long {
            if (currentScheduledIntervalMinutes != -1L) {
                return currentScheduledIntervalMinutes
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            currentScheduledIntervalMinutes = prefs.getLong(KEY_INTERVAL_MINUTES, 60L)
            return currentScheduledIntervalMinutes
        }

        fun updateWorkInterval(context: Context, intervalMinutes: Long) {
            if (currentScheduledIntervalMinutes == intervalMinutes) return
            currentScheduledIntervalMinutes = intervalMinutes

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong(KEY_INTERVAL_MINUTES, intervalMinutes).apply()

            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(
                    intervalMinutes, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    weatherWorkRequest
                )
                Log.d("WeatherWorker", "Intervalle dynamique WorkManager changé à $intervalMinutes minutes")
            } catch (e: Throwable) {
                Log.e("WeatherWorker", "WorkManager unavailable or initialization error: ${e.message}")
            }
        }
    }

    fun getVigilanceLevelName(level: Int, lower: Boolean = false): String {
        return if (lower)
            when (level) {
                2 -> applicationContext.getString(R.string.weather_alert)
                3 -> applicationContext.getString(R.string.weather_warning)
                4 -> applicationContext.getString(R.string.severe_weather_warning)
                else -> ""
            }.lowercase()
        else
            when (level) {
                2 -> applicationContext.getString(R.string.weather_alert)
                3 -> applicationContext.getString(R.string.weather_warning)
                4 -> applicationContext.getString(R.string.severe_weather_warning)
                else -> ""
            }
    }

    fun mapPhenomenonIdToName(id: String): Int {
        return when (id) {
            "1" -> R.string.violent_wind
            "2" -> R.string.rain_flood
            "3" -> R.string.thunderstorms_phenomenon
            "4" -> R.string.floods
            "5" -> R.string.snow_ice
            "6" -> R.string.heatwave
            "7" -> R.string.extreme_cold
            "8" -> R.string.flooding
            "9" -> R.string.waves_submersion
            else -> R.string.unknown_phenomenon
        }
    }
}
