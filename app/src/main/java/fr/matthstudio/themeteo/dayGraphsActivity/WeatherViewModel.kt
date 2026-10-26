/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.dayGraphsActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherParser
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    @Suppress("UNUSED_PARAMETER") telemetryManager: TelemetryManager
) : ViewModel() {

    // --- 1. ÉTATS PRINCIPAUX EXPOSÉS À L'UI ---

    val currentStartDateTime = MutableStateFlow(initialStartDateTime)

    /**
     * Expose les paramètres utilisateur depuis AppStateManager.
     */
    val userSettings: StateFlow<UserSettings?> = AppStateManager.userSettings

    private fun resolveCoords(location: LocationIdentifier, gpsCoords: GpsCoordinates?): GpsCoordinates? {
        val raw = when (location) {
            is LocationIdentifier.Saved -> GpsCoordinates(location.location.latitude, location.location.longitude)
            is LocationIdentifier.CurrentUserLocation -> gpsCoords
        } ?: return null

        val roundedLat = (raw.latitude * 1000.0).roundToInt() / 1000.0
        val roundedLon = (raw.longitude * 1000.0).roundToInt() / 1000.0
        return GpsCoordinates(roundedLat, roundedLon)
    }

    /**
     * Forecast horaire/synoptique du jour sélectionné ou des prochaines 24h.
     * Gestion du Court Terme (J0-J2), Moyen Terme (J2-J7) et Long Terme Synoptique (> J+7).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val hourlyForecast: StateFlow<WeatherDataState> = combine(
        currentStartDateTime,
        AppStateManager.selectedLocation,
        AppStateManager.currentGpsPosition
    ) { startDate, loc, gps ->
        val normalizedStart = if (startDate.hour == 0 && startDate.minute == 0) {
            startDate.toLocalDate().atStartOfDay()
        } else {
            startDate.withMinute(0).withSecond(0).withNano(0)
        }
        Pair(normalizedStart, resolveCoords(loc, gps))
    }.distinctUntilChanged().flatMapLatest { (start, coords) ->
        if (coords == null) {
            flowOf(WeatherDataState.Loading)
        } else {
            val durationHours = if (fullPeriod) 48L else 24L
            val reqDate = start.toLocalDate()
            val lat = coords.latitude
            val lon = coords.longitude

            val minMedSteps = 6
            weatherParser.getShortTerm(start, durationHours.toInt(), lat, lon).flatMapLatest { shortState ->
                if (shortState is WeatherDataState.SuccessShortTermHourly && shortState.data.size >= 12) {
                    flowOf(shortState)
                } else {
                    weatherParser.getMediumTerm(start, start.plusHours(durationHours), lat, lon).flatMapLatest { medState ->
                        if (medState is WeatherDataState.SuccessMediumTermHourly && medState.data.size >= minMedSteps) {
                            flowOf(medState)
                        } else {
                            weatherParser.getLongTerm(reqDate, reqDate, lat, lon).map { longState ->
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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeatherDataState.Loading)

    /**
     * Liste des jours disponibles pour le sélecteur.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableDays: StateFlow<WeatherDataState> = combine(
        AppStateManager.selectedLocation,
        AppStateManager.currentGpsPosition
    ) { loc, gps ->
        resolveCoords(loc, gps)
    }.distinctUntilChanged().flatMapLatest { coords ->
        if (coords != null) {
            weatherParser.getDaily(LocalDate.now(), 14, coords.latitude, coords.longitude)
        } else {
            flowOf(WeatherDataState.Loading)
        }
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
}
