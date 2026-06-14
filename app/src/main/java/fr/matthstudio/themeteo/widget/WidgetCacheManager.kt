/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.widget

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.matthstudio.themeteo.AllHourlyVarsReading
import fr.matthstudio.themeteo.DailyReading
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.getDailyData
import fr.matthstudio.themeteo.getHourlyData
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
    val hourly: List<AllHourlyVarsReading>? = null,
    val daily: List<DailyReading>? = null,
    val lastUpdatedEpochSeconds: Long,
    val locationIdentifier: LocationIdentifier
)

class WidgetCacheManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; allowStructuredMapKeys = true }

    companion object {
        private val KEY_WIDGET_DATA = stringPreferencesKey("widget_weather_data")
        private const val CACHE_EXPIRATION_MINUTES = 60
    }

    suspend fun saveWidgetData(data: WidgetWeatherData) {
        context.widgetDataStore.edit { prefs ->
            prefs[KEY_WIDGET_DATA] = json.encodeToString(data)
        }
    }

    fun getWidgetData(): Flow<WidgetWeatherData?> = context.widgetDataStore.data.map { prefs ->
        prefs[KEY_WIDGET_DATA]?.let { try { json.decodeFromString<WidgetWeatherData>(it) } catch (e: Exception) { null } }
    }

    suspend fun refreshIfNeeded(location: LocationIdentifier): WidgetWeatherData? {
        val current = getWidgetData().first()
        val now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)

        // Efficacité : Ne rafraîchir que si nécessaire (ex: 2h)
        if (current != null && current.locationIdentifier == location && (now - current.lastUpdatedEpochSeconds) < CACHE_EXPIRATION_MINUTES * 120) {
            return current
        }

        return try {
            val app = context.applicationContext as TheMeteo
            val startTime = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)

            // Récupération via le cache principal (qui gère le réseau)
            var hourly: List<AllHourlyVarsReading>? = null
            app.weatherCache.get(startTime, 24, location).collect { state ->
                if (state is WeatherDataState.SuccessHourly) hourly = state.data
                if (state !is WeatherDataState.Loading) return@collect
            }

            var daily: List<DailyReading>? = null
            app.weatherCache.get(startTime.toLocalDate(), 7, location).collect { state ->
                if (state is WeatherDataState.SuccessDaily) daily = state.data
                if (state !is WeatherDataState.Loading) return@collect
            }

            if (hourly != null || daily != null) {
                val newData = WidgetWeatherData(hourly, daily, now, location)
                saveWidgetData(newData)
                newData
            } else current
        } catch (e: Exception) {
            Log.e("WidgetCache", "Refresh failed", e); current
        }
    }
}