/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.dayGraphsActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherCache
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime

/**
 * Ce ViewModel sert d'intermédiaire entre l'UI (WeatherScreen) et la logique de données (WeatherCache).
 * Il expose les états de l'application de manière simple et réactive pour que l'UI puisse les afficher.
 * Il gère également la logique de recherche de villes.
 */
@OptIn(FlowPreview::class) // Nécessaire pour l'opérateur debounce
class WeatherViewModel(
    weatherCache: WeatherCache,
    startDateTime: LocalDateTime,
    fullPeriod: Boolean,
    telemetryManager: TelemetryManager
) : ViewModel() {

    private val weatherService = WeatherService(telemetryManager)

    // --- 1. ÉTATS PRINCIPAUX EXPOSÉS À L'UI ---

    /**
     * Expose les paramètres utilisateur (modèle, arrondi, etc.) directement depuis le WeatherCache.
     * L'UI se mettra à jour automatiquement si les paramètres changent dans le DataStore.
     */
    val userSettings: StateFlow<UserSettings> = weatherCache.userSettings

    /**
     * Forecast pour 24 heures à partir de l'heure actuelle, ou pour toute la durée du modèle.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val hourlyForecast = userSettings.flatMapLatest { settings ->
        val durationHours = if (fullPeriod) {
            val model = WeatherModelRegistry.getModel(
                settings.model,
                settings.forecastType == ForecastType.ENSEMBLE
            )
            model.predictionDays * 24L
        } else {
            24L
        }
        weatherCache.get(startDateTime, durationHours.toInt())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeatherDataState.Loading)

    // --- 2. NETTOYAGE ---
    /**
     * S'assure de fermer les connexions réseau (client Ktor) lorsque le ViewModel est détruit
     * pour éviter les fuites de ressources.
     */
    override fun onCleared() {
        super.onCleared()
        weatherService.close()
    }
}