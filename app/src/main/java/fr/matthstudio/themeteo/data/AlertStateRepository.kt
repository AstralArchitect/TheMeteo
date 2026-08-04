/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AlertStateRepository(private val dataStore: DataStore<Preferences>) {

    private object PreferencesKeys {
        val LAST_VIGILANCE_LEVELS = stringPreferencesKey("last_vigilance_levels")
        val LAST_RAIN_NOTIFICATIONS = stringPreferencesKey("last_rain_notifications")
    }

    /**
     * Map of LocationIdentifier (as string) to last notified maxColorId (Int)
     */
    val lastVigilanceLevels: Flow<Map<String, Int>> = dataStore.data.map { preferences ->
        val data = preferences[PreferencesKeys.LAST_VIGILANCE_LEVELS] ?: return@map emptyMap()
        try {
            Json.decodeFromString<Map<String, Int>>(data)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Map of LocationIdentifier (as string) to last notification timestamp (Long)
     */
    val lastRainNotifications: Flow<Map<String, Long>> = dataStore.data.map { preferences ->
        val data = preferences[PreferencesKeys.LAST_RAIN_NOTIFICATIONS] ?: return@map emptyMap()
        try {
            Json.decodeFromString<Map<String, Long>>(data)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun updateVigilanceLevel(locationKey: String, level: Int) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.LAST_VIGILANCE_LEVELS]?.let {
                try { Json.decodeFromString<Map<String, Int>>(it) } catch (e: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current + (locationKey to level)
            preferences[PreferencesKeys.LAST_VIGILANCE_LEVELS] = Json.encodeToString(next)
        }
    }

    suspend fun updateRainNotificationTime(locationKey: String, timestamp: Long) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.LAST_RAIN_NOTIFICATIONS]?.let {
                try { Json.decodeFromString<Map<String, Long>>(it) } catch (e: Exception) { emptyMap() }
            } ?: emptyMap()
            val next = current + (locationKey to timestamp)
            preferences[PreferencesKeys.LAST_RAIN_NOTIFICATIONS] = Json.encodeToString(next)
        }
    }
}
