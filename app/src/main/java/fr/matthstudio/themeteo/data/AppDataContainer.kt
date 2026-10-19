/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.data

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import fr.matthstudio.themeteo.WeatherParser
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import fr.matthstudio.themeteo.telemetry.TelemetryManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Conteneur de dépendances pour l'application. Il fournit les repositories et le cache.
 */
interface AppContainer {
    val userLocationsRepository: UserLocationsRepository
    val userSettingsRepository: UserSettingsRepository
    val alertStateRepository: AlertStateRepository
    val locationProvider: LocationProvider // Ajout du LocationProvider
    val telemetryManager: TelemetryManager
    val weatherParser: WeatherParser
}

class AppDataContainer(private val context: Context) : AppContainer {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val alertDataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = {
                File(context.cacheDir, "datastore/alerts.preferences_pb")
            }
        )
    }

    override val userLocationsRepository: UserLocationsRepository by lazy {
        UserLocationsRepository(context.dataStore)
    }
    override val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(context.dataStore)
    }
    override val alertStateRepository: AlertStateRepository by lazy {
        AlertStateRepository(alertDataStore)
    }
    // Ajout de l'instance du LocationProvider
    override val locationProvider: LocationProvider by lazy {
        LocationProvider(context)
    }
    override val telemetryManager: TelemetryManager by lazy {
        TelemetryManagerImpl(context)
    }

    override val weatherParser: WeatherParser by lazy {
        WeatherParser(context.applicationContext as Application, applicationScope)
    }
}

