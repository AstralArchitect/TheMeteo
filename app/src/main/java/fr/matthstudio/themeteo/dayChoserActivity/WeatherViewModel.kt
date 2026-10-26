/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.dayChoserActivity

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.SearchState
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherParser
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.data.UserLocationsRepository
import fr.matthstudio.themeteo.data.UserSettingsRepository
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Ce ViewModel sert d'intermédiaire entre l'UI (WeatherScreen) et la logique de données (WeatherCache).
 * Il expose les états de l'application de manière simple et réactive pour que l'UI puisse les afficher.
 * Il gère également la logique de recherche de villes.
 */
@OptIn(FlowPreview::class) // Nécessaire pour l'opérateur debounce
class WeatherViewModel(
    private val weatherParser: WeatherParser,
    private val userLocationsRepository: UserLocationsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val telemetryManager: TelemetryManager
) : ViewModel() {

    val weatherService: WeatherService = TheMeteo.instance.container.weatherService

    // --- 1. ÉTATS PRINCIPAUX EXPOSÉS À L'UI VIA APPSTATEMANAGER ---

    private val defaultUserSettings = UserSettings(true, LocationIdentifier.CurrentUserLocation, DefaultScreen.FORECAST_MAIN, true, TemperatureUnit.CELSIUS, WindUnit.KPH, "PENDING", false, null, null, false, true, false, ThemeMode.FIXED, true, true, false)

    /**
     * Expose les paramètres utilisateur depuis AppStateManager.
     */
    val userSettings: StateFlow<UserSettings> = AppStateManager.userSettings.map {
        it ?: defaultUserSettings
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultUserSettings)

    /**
     * Expose la localisation actuellement sélectionnée depuis AppStateManager.
     */
    val selectedLocation: StateFlow<LocationIdentifier> = AppStateManager.selectedLocation

    /**
     * Un flux qui émet toutes les secondes pour les mises à jour en temps réel.
     */
    private val ticker = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(1000.milliseconds)
        }
    }

    /**
     * État "Nuit" centralisé depuis AppStateManager.
     */
    val isNight: StateFlow<Boolean> = AppStateManager.isNight

    /**
     * Code WMO actuel pour le thème.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentWmo: StateFlow<Int?> = AppStateManager.currentWmoCode

    /**
     * Expose les positions enregistrées par l'utilisateur depuis le WeatherCache.
     */
    val savedLocations: StateFlow<List<SavedLocation>> = AppStateManager.savedLocations

    val defaultLocation: LocationIdentifier = AppStateManager.userSettings.value?.defaultLocation ?: LocationIdentifier.CurrentUserLocation

    /**
     * Expose la position GPS actuelle depuis AppStateManager.
     */
    val userLocation: StateFlow<GpsCoordinates?> = AppStateManager.currentGpsPosition

    /**
     * Expose le nom de la ville actuelle depuis AppStateManager.
     */
    val currentCityName: StateFlow<String?> = AppStateManager.currentCityName

    /**
     * Expose si la permission de localisation est accordée depuis AppStateManager.
     */
    val isLocationPermissionGranted: StateFlow<Boolean> = AppStateManager.isLocationPermissionGranted

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
     * Forecast pour les prochains jours
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val forecast: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        AppStateManager.currentGpsPosition
    ) { loc, gps ->
        resolveCoords(loc, gps)
    }.distinctUntilChanged().flatMapLatest { coords ->
        if (coords != null) {
            weatherParser.getDaily(LocalDate.now(), 14, coords.latitude, coords.longitude)
        } else {
            flowOf(WeatherDataState.Loading)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WeatherDataState.Loading
    )

    // --- 2. GESTION DE LA RECHERCHE DE VILLES (GEOCODING) ---

    // Le terme de recherche entré par l'utilisateur.
    private val _searchQuery = MutableStateFlow("")

    /**
     * État de la recherche de villes, réactif au changement de _searchQuery.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchState: StateFlow<SearchState> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.length >= 2) {
                kotlinx.coroutines.flow.flow<SearchState> {
                    Log.d("WeatherViewModel", "Recherche lancée pour : $query (DayChooser)")
                    emit(SearchState.Loading)
                    val results = weatherService.searchCity(query)
                    if (results == null) {
                        emit(SearchState.Error("Erreur réseau"))
                    } else if (results.isEmpty()) {
                        emit(SearchState.Empty)
                    } else {
                        emit(SearchState.Success(results))
                    }
                }
            } else {
                kotlinx.coroutines.flow.flowOf(SearchState.Idle)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchState.Idle
        )


    init {
    }

    // --- 3. ACTIONS INITIÉES PAR L'UI ---

    /**
     * Met à jour la requête de recherche, ce qui déclenchera la recherche via le Flow collecté dans le `init`.
     */
    fun searchCity(query: String) {
        _searchQuery.value = query
    }

    /**
     * Efface la recherche et les résultats.
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /**
     * Méthode appelée par l'UI pour demander un changement de localisation.
     * Le ViewModel transmet cette demande au WeatherCache, qui est la source de vérité.
     */
    fun selectLocation(location: LocationIdentifier) {
        AppStateManager.updateSelectedLocation(location)
    }

    /**
     * Méthode appelée par l'UI pour supprimer une localisation.
     */
    fun removeLocation(location: SavedLocation) {
        viewModelScope.launch {
            userLocationsRepository.removeLocation(location)
        }
    }

    /**
     * Méthode appelée par l'UI pour ajouter une nouvelle localisation.
     */
    fun addLocation(location: SavedLocation) {
        viewModelScope.launch {
            userLocationsRepository.addLocation(location)
        }
    }
    
    /**
     * Méthode appelée par l'UI pour réorganiser les lieux.
     */
    fun reorderLocations(newList: List<SavedLocation>) {
        viewModelScope.launch {
            userLocationsRepository.reorderLocations(newList)
        }
    }

    /**
     * Méthode appelée par l'UI pour renommer un lieu.
     */
    fun renameLocation(location: SavedLocation, newName: String) {
        viewModelScope.launch {
            userLocationsRepository.renameLocation(location, newName)
        }
    }
    
    fun addLocationFromMap(coords: GpsCoordinates, name: String) {
        val newLocation = SavedLocation(
            name = name,
            latitude = coords.latitude,
            longitude = coords.longitude,
            country = "Unknown"
        )

        addLocation(newLocation)
        selectLocation(LocationIdentifier.Saved(newLocation))
    }

    /**
     * Méthode pour définir la position par défaut
     */
    fun setDefaultLocation(location: LocationIdentifier) {
        viewModelScope.launch {
            userSettingsRepository.updateDefaultLocation(location)
        }
    }

    // --- 4. NETTOYAGE ---

    override fun onCleared() {
        super.onCleared()
    }
}