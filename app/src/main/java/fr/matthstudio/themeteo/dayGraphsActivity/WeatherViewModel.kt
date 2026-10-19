/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.dayGraphsActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherParser
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Ce ViewModel sert d'intermédiaire entre l'UI (WeatherScreen) et la logique de données (WeatherCache).
 * Il expose les états de l'application de manière simple et réactive pour que l'UI puisse les afficher.
 * Il gère également la logique de recherche de villes.
 */
@OptIn(FlowPreview::class) // Nécessaire pour l'opérateur debounce
class WeatherViewModel(
    private val weatherParser: WeatherParser,
    initialStartDateTime: LocalDateTime,
    val fullPeriod: Boolean,
    telemetryManager: TelemetryManager
) : ViewModel() {

    private val weatherService = WeatherService(telemetryManager)

    // --- 1. ÉTATS PRINCIPAUX EXPOSÉS À L'UI ---

    val currentStartDateTime = MutableStateFlow(initialStartDateTime)

    /**
     * Expose les paramètres utilisateur depuis AppStateManager.
     */
    val userSettings: StateFlow<UserSettings?> = AppStateManager.userSettings

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
     * Forecast horaire/synoptique du jour sélectionné ou des prochaines 24h.
     * Gestion du Court Terme (J0-J2), Moyen Terme (J2-J7) et Long Terme Synoptique (> J+7).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val hourlyForecast: StateFlow<WeatherDataState> = combine(
        userSettings,
        currentStartDateTime,
        AppStateManager.selectedLocation,
        AppStateManager.currentGpsPosition
    ) { _, startDate, _, _ ->
        if (startDate.hour == 0 && startDate.minute == 0) {
            startDate.toLocalDate().atStartOfDay()
        } else {
            startDate.withMinute(0).withSecond(0).withNano(0)
        }
    }.flatMapLatest { start ->
        val durationHours = if (fullPeriod) 48L else 24L
        val reqDate = start.toLocalDate()
        val today = LocalDate.now()
        val daysDiff = ChronoUnit.DAYS.between(today, reqDate)

        val minMedSteps = 6
        weatherParser.getShortTerm(start, durationHours.toInt()).flatMapLatest { shortState ->
            if (shortState is WeatherDataState.SuccessShortTermHourly && shortState.data.size >= 12) {
                flowOf(shortState)
            } else {
                weatherParser.getMediumTerm(start, start.plusHours(durationHours)).flatMapLatest { medState ->
                    if (medState is WeatherDataState.SuccessMediumTermHourly && medState.data.size >= minMedSteps) {
                        flowOf(medState)
                    } else {
                        weatherParser.getLongTerm(reqDate, reqDate).map { longState ->
                            if (longState is WeatherDataState.Error && medState is WeatherDataState.SuccessMediumTermHourly && medState.data.isNotEmpty()) {
                                medState
                            } else {
                                longState
                            }
                        }
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeatherDataState.Loading)

    /**
     * Liste des jours disponibles pour le sélecteur.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableDays: StateFlow<WeatherDataState> = combine(
        userSettings,
        AppStateManager.selectedLocation,
        AppStateManager.currentGpsPosition
    ) { _, _, _ ->
    }.flatMapLatest {
        weatherParser.getDaily(LocalDate.now(), 14)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeatherDataState.Loading)

    fun updateStartDate(dateTime: LocalDateTime) {
        currentStartDateTime.value = dateTime
    }

    /**
     * États centralisés depuis AppStateManager.
     */
    val isNight: StateFlow<Boolean> = AppStateManager.isNight
    val currentWmo: StateFlow<Int?> = AppStateManager.currentWmoCode
    val currentCityName: StateFlow<String?> = AppStateManager.currentCityName

    override fun onCleared() {
        super.onCleared()
        weatherService.close()
    }
}
