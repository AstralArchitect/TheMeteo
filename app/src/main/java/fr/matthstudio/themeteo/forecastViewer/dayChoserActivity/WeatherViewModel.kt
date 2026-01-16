package fr.matthstudio.themeteo.forecastViewer.dayChoserActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherCache
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.forecastViewer.WeatherService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Ce ViewModel sert d'intermédiaire entre l'UI (WeatherScreen) et la logique de données (WeatherCache).
 * Il expose les états de l'application de manière simple et réactive pour que l'UI puisse les afficher.
 * Il gère également la logique de recherche de villes.
 */
@OptIn(FlowPreview::class) // Nécessaire pour l'opérateur debounce
class WeatherViewModel(private val weatherCache: WeatherCache) : ViewModel() {

    private val weatherService = WeatherService()

    // --- 1. ÉTATS PRINCIPAUX EXPOSÉS À L'UI ---

    /**
     * Expose les paramètres utilisateur (modèle, arrondi, etc.) directement depuis le WeatherCache.
     * L'UI se mettra à jour automatiquement si les paramètres changent dans le DataStore.
     */
    val userSettings: StateFlow<UserSettings> = weatherCache.userSettings

    /**
     * Expose la localisation actuellement sélectionnée depuis le WeatherCache.
     */
    val selectedLocation: StateFlow<LocationIdentifier> = weatherCache.selectedLocation

    /**
     * Forecast pour 24 heures à partir de l'heure actuelle
     */
    val forecast = weatherCache.get(LocalDate.now(),
        weatherModelPredictionTime[userSettings.value.model]?.toLong() ?: 3
    )
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeatherDataState.Loading)

    // --- 4. NETTOYAGE ---

    /**
     * S'assure de fermer les connexions réseau (client Ktor) lorsque le ViewModel est détruit
     * pour éviter les fuites de ressources.
     */
    override fun onCleared() {
        super.onCleared()
        weatherService.close()
    }
}