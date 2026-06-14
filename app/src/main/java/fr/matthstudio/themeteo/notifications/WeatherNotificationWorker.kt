/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

class WeatherNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val app = context.applicationContext as TheMeteo
    private val weatherService = WeatherService(app.container.telemetryManager)
    private val notificationHelper = NotificationHelper(context)

    override suspend fun doWork(): Result {
        Log.d("WeatherWorker", "Starting background weather check")

        app.weatherCache.refreshCurrentLocationSuspend()
        
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
        checkVigilance(allLocations, alertState)
        checkRain(defaultLocation, alertState)

        return Result.success()
    }

    private suspend fun checkVigilance(locations: List<LocationIdentifier>, alertStateRepository: fr.matthstudio.themeteo.data.AlertStateRepository) {
        val lastLevels = alertStateRepository.lastVigilanceLevels.first()
        
        for (location in locations) {
            val coords = when (location) {
                is LocationIdentifier.CurrentUserLocation -> app.weatherCache.currentGpsPosition.value
                is LocationIdentifier.Saved -> GpsCoordinates(location.location.latitude, location.location.longitude)
            } ?: continue

            val vigilance = weatherService.getVigilanceForLocation(coords.latitude, coords.longitude) ?: continue
            val locationKey = Json.encodeToString(location)
            val lastLevel = lastLevels[locationKey] ?: 1

            if (vigilance.maxColorId > lastLevel && vigilance.maxColorId >= 1) {
                val title = applicationContext.getString(R.string.vigilance_alert_title, getVigilanceLevelName(vigilance.maxColorId))
                val mainAlert = vigilance.alerts.maxByOrNull { it.maxColorId }
                val phenomenon = if (mainAlert != null) {
                    applicationContext.getString(mapPhenomenonIdToName(mainAlert.phenomenonId))
                } else ""
                
                notificationHelper.showVigilanceNotification(title, phenomenon)
                alertStateRepository.updateVigilanceLevel(locationKey, vigilance.maxColorId)
            } else if (vigilance.maxColorId < lastLevel) {
                // Update state if level decreased
                alertStateRepository.updateVigilanceLevel(locationKey, vigilance.maxColorId)
            }
        }
    }

    private suspend fun checkRain(defaultLocation: LocationIdentifier, alertStateRepository: fr.matthstudio.themeteo.data.AlertStateRepository) {
        val targets = mutableListOf<LocationIdentifier>()
        targets.add(defaultLocation)
        if (defaultLocation !is LocationIdentifier.CurrentUserLocation) {
            targets.add(LocationIdentifier.CurrentUserLocation)
        }

        val lastNotifications = alertStateRepository.lastRainNotifications.first()
        val now = System.currentTimeMillis()

        for (location in targets) {
            val coords = when (location) {
                is LocationIdentifier.CurrentUserLocation -> app.weatherCache.currentGpsPosition.value
                is LocationIdentifier.Saved -> fr.matthstudio.themeteo.data.GpsCoordinates(location.location.latitude, location.location.longitude)
            } ?: continue

            val locationKey = Json.encodeToString(location)
            val lastTime = lastNotifications[locationKey] ?: 0L
            
            // Don't notify more than once every 3 hours for rain
            if (now - lastTime < 3 * 60 * 60 * 1000) continue

            // Fetch hourly forecast for the next 6 hours
            val models = listOf("best_match")
            val forecast = weatherService.getForecast(
                coords.latitude, 
                coords.longitude, 
                models, 
                LocalDate.now(), 
                LocalDate.now().plusDays(1)
            )

            val hourlyData = forecast?.get("best_match")?.first
            if (hourlyData != null) {
                val currentTime = LocalDateTime.now()
                val nextRain = hourlyData.firstOrNull { 
                    it.time.isAfter(currentTime.minusHours(1)) &&
                    it.time.isBefore(currentTime.plusHours(6)) && 
                    (it.precipitationData.precipitation ?: 0.0) > 0.1
                }

                if (nextRain != null) {
                    val timeStr = String.format("%02d:%02d", nextRain.time.hour, nextRain.time.minute)
                    val message = applicationContext.getString(R.string.rain_at_format, timeStr)
                    notificationHelper.showRainNotification(applicationContext.getString(R.string.rain_alert_title), message)
                    alertStateRepository.updateRainNotificationTime(locationKey, now)
                }
            }
        }
    }

    private fun getVigilanceLevelName(level: Int): String {
        return when (level) {
            2 -> applicationContext.getString(R.string.weather_alert).lowercase()
            3 -> applicationContext.getString(R.string.weather_warning).lowercase()
            4 -> applicationContext.getString(R.string.severe_weather_warning).lowercase()
            else -> ""
        }
    }

    private fun mapPhenomenonIdToName(id: String): Int {
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
