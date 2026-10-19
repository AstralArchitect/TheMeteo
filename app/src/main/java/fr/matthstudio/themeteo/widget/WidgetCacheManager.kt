/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.widget

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.ShortTermStepUI
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.DailyForecastUI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.ZoneOffset

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_cache")

@Serializable
data class WidgetWeatherData(
    val hourly: List<ShortTermStepUI>? = null,
    val daily: List<DailyForecastUI>? = null,
    val lastUpdatedEpochSeconds: Long,
    val locationIdentifier: LocationIdentifier,
    val cityName: String? = null
)

class WidgetCacheManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; allowStructuredMapKeys = true }

    companion object {
        private val KEY_WIDGET_DATA = stringPreferencesKey("widget_weather_data")
        private const val CACHE_EXPIRATION_MINUTES = 60
    }

    private fun getKeyForLocation(location: LocationIdentifier): Preferences.Key<String> {
        val locString = Json.encodeToString(location)
        return stringPreferencesKey("widget_weather_data_$locString")
    }

    suspend fun saveWidgetData(location: LocationIdentifier, data: WidgetWeatherData) {
        context.widgetDataStore.edit { prefs ->
            prefs[getKeyForLocation(location)] = json.encodeToString(data)
        }
    }

    fun getWidgetData(location: LocationIdentifier): Flow<WidgetWeatherData?> = context.widgetDataStore.data.map { prefs ->
        val key = getKeyForLocation(location)
        prefs[key]?.let { try { json.decodeFromString<WidgetWeatherData>(it) } catch (e: Exception) { null } }
            ?: prefs[KEY_WIDGET_DATA]?.let { try { json.decodeFromString<WidgetWeatherData>(it) } catch (e: Exception) { null } }
    }

    suspend fun refreshIfNeeded(location: LocationIdentifier, forceRefresh: Boolean = false): WidgetWeatherData? {
        val current = getWidgetData(location).first()
        val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)

        // Ne rafraîchir que si nécessaire (1 heure = 3600 secondes)
        if (!forceRefresh && current != null && current.locationIdentifier == location && (now - current.lastUpdatedEpochSeconds) < CACHE_EXPIRATION_MINUTES * 60) {
            return current
        }

        return try {
            val app = context.applicationContext as TheMeteo
            var detectedCityName: String? = current?.cityName

            // Si la localisation est la position GPS, on la rafraîchit en arrière-plan via LocationProvider
            if (location is LocationIdentifier.CurrentUserLocation) {
                val coords = app.container.locationProvider.getCurrentLocation()
                if (coords != null) {
                    AppStateManager.updateGpsPosition(coords)
                    val name = app.container.weatherParser.getCityName(coords.latitude, coords.longitude)
                    if (name != null) {
                        detectedCityName = name
                        AppStateManager.updateCityName(name)
                    }
                }
            }

            val weatherParser = app.container.weatherParser
            val startTime = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)

            var hourly: List<ShortTermStepUI>? = null
            weatherParser.getShortTerm(startTime, 24, location).collect { state ->
                if (state is WeatherDataState.SuccessShortTermHourly) hourly = state.data
            }

            var daily: List<DailyForecastUI>? = null
            weatherParser.getDaily(startTime.toLocalDate(), 7, location).collect { state ->
                if (state is WeatherDataState.SuccessDaily) daily = state.data
            }

            if (hourly != null || daily != null) {
                val newData = WidgetWeatherData(
                    hourly = hourly ?: current?.hourly,
                    daily = daily ?: current?.daily,
                    lastUpdatedEpochSeconds = now,
                    locationIdentifier = location,
                    cityName = detectedCityName
                )
                saveWidgetData(location, newData)
                newData
            } else current
        } catch (e: Exception) {
            Log.e("WidgetCache", "Refresh failed", e)
            current
        }
    }
}
