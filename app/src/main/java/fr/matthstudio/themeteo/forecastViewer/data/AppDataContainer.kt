package fr.matthstudio.themeteo.forecastViewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Conteneur de dépendances pour l'application. Il fournit les repositories et le cache.
 */
interface AppContainer {
    val userLocationsRepository: UserLocationsRepository
    val userSettingsRepository: UserSettingsRepository
    val locationProvider: LocationProvider // Ajout du LocationProvider
}

class AppDataContainer(private val context: Context) : AppContainer {
    override val userLocationsRepository: UserLocationsRepository by lazy {
        UserLocationsRepository(context.dataStore)
    }
    override val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(context.dataStore)
    }
    // Ajout de l'instance du LocationProvider
    override val locationProvider: LocationProvider by lazy {
        LocationProvider(context)
    }
}

