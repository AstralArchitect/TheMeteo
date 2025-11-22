package fr.matthstudio.themeteo.forecastViewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.matthstudio.themeteo.forecastViewer.WeatherViewModel

// Extension pour créer une instance unique de DataStore pour toute l'application
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Conteneur de dépendances pour l'application.
 */
interface AppContainer {
    val userLocationsRepository: UserLocationsRepository
    val userSettingsRepository: UserSettingsRepository
}

class AppDataContainer(private val context: Context) : AppContainer {
    override val userLocationsRepository: UserLocationsRepository by lazy {
        UserLocationsRepository(context.dataStore)
    }
    override val userSettingsRepository: UserSettingsRepository by lazy {
        UserSettingsRepository(context.dataStore)
    }
}

/**
 * Factory pour créer une instance de WeatherViewModel avec son repository.
 */
object WeatherViewModelFactory : ViewModelProvider.Factory {
    private lateinit var appContainer: AppContainer

    fun initialize(context: Context) {
        if (!::appContainer.isInitialized) {
            appContainer = AppDataContainer(context.applicationContext)
        }
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(
                appContainer.userLocationsRepository,
                appContainer.userSettingsRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
