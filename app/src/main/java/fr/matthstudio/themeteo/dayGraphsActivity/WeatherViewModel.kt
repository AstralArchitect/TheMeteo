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
import fr.matthstudio.themeteo.getHourlyData
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
     * Un flux qui émet toutes les secondes pour les mises à jour en temps réel.
     */
    private val ticker = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(1000)
        }
    }

    /**
     * Forecast pour 24 heures à partir de l'heure actuelle, ou pour toute la durée du modèle.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
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

    /**
     * État "Nuit" centralisé.
     */
    val isNight: StateFlow<Boolean> = combine(hourlyForecast, ticker) { state, _ ->
        val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val reading = state.getHourlyData()?.find { it.time == now }
        val radiation = reading?.skyInfo?.shortwaveRadiation
        (radiation ?: 1.0) < 1.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Code WMO actuel pour le thème.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentWmo: StateFlow<Int?> = combine(
        weatherCache.selectedLocation,
        weatherCache.userSettings
    ) { _, _ ->
    }.flatMapLatest {
        weatherCache.get(java.time.LocalDateTime.now(), 1)
    }.map { state ->
        when (state) {
            is WeatherDataState.SuccessHourly -> state.data.firstOrNull()?.wmo
            is WeatherDataState.Error -> (state.staleData as? WeatherDataState.SuccessHourly)?.data?.firstOrNull()?.wmo
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
