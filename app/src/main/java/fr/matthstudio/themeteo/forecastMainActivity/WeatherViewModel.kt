package fr.matthstudio.themeteo.forecastMainActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.GeocodingResult
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherCache
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Ce ViewModel sert d'intermédiaire entre l'UI (WeatherScreen) et la logique de données (WeatherCache).
 * Il expose les états de l'application de manière simple et réactive pour que l'UI puisse les afficher.
 * Il gère également la logique de recherche de villes.
 */
@OptIn(FlowPreview::class) // Nécessaire pour l'opérateur debounce
class WeatherViewModel(
    private val weatherCache: WeatherCache,
    private val telemetryManager: TelemetryManager
) : ViewModel() {

    val weatherService = WeatherService(telemetryManager)

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
     * Expose les positions enregistrées par l'utilisateur depuis le WeatherCache.
     */
    val savedLocations: StateFlow<List<SavedLocation>> = weatherCache.savedLocations

    /**
     * Expose la position GPS actuelle directement depuis le WeatherCache.
     * La valeur sera `null` si la localisation n'est pas activée ou pas encore disponible.
     */
    val userLocation: StateFlow<GpsCoordinates?> = weatherCache.currentGpsPosition

    /**
     * Expose si la permission de localisation est accordée.
     */
    val isLocationPermissionGranted: StateFlow<Boolean> = weatherCache.isLocationPermissionGranted

    /**
     * Variable servant à forcer le rafraichissement, elle est incrémentée à chaque appel de refresh()
     */
    private val _refreshCounter = MutableStateFlow(0)
    val refreshCounter: StateFlow<Int> = _refreshCounter.asStateFlow()


    /**
     * Forecast pour 24 heures à partir de l'heure actuelle
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val hourlyForecast: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        userSettings,
        refreshCounter
    ) { _, _, _ ->
        // On combine les trois. Peu importe la valeur reçue,
        // flatMapLatest relancera le flux ci-dessous.
    }.flatMapLatest {
        weatherCache.get(LocalDateTime.now(), 24)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WeatherDataState.Loading
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyForecast: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        userSettings,
        refreshCounter
    ) { _, settings, _ ->
        // On récupère les settings ici pour calculer la durée
        WeatherModelRegistry.getModel(settings.model, userSettings.value.forecastType == ForecastType.ENSEMBLE).predictionDays.toLong()
    }.flatMapLatest { duration ->
        weatherCache.get(LocalDate.now(), duration)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WeatherDataState.Loading
    )

    /**
     * Flow de WeatherDataState pour les donnnées actuelles à toutes les villes enregistrées.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentWeather: StateFlow<WeatherDataState> = combine(
        savedLocations,
        refreshCounter
    ) { _, _ ->
        // On combine les deux. Peu importe la valeur reçue,
        // flatMapLatest relancera le flux ci-dessous.
    }.flatMapLatest {
        weatherCache.getCurrentWeatherForSavedLocations()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WeatherDataState.Loading
    )

    /**
     * Flow de WeatherDataState pour les données de qualité de l'air à la localisation selectionnée.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val airQualityResponse: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        refreshCounter
    ) { _, _ ->
        // On combine les deux. Peu importe la valeur reçue,
        // flatMapLatest relancera le flux ci-dessous.
    }.flatMapLatest {
        weatherCache.getAirQuality()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WeatherDataState.Loading
    )

    /**
     * Flow de WeatherDataState pour les vigilances à la localisation selectionnée.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weatherVigilanceInfo: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        refreshCounter
    ) { _, _ ->
        // On combine les deux. Peu importe la valeur reçue,
        // flatMapLatest relancera le flux ci-dessous.
    }.flatMapLatest {
        weatherCache.getLocalVigilance()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WeatherDataState.Loading
    )

    // --- 2. GESTION DE LA RECHERCHE DE VILLES (GEOCODING) ---

    // Le terme de recherche entré par l'utilisateur.
    private val _searchQuery = MutableStateFlow("")

    // Les résultats de la recherche retournés par l'API Geocoding.
    private val _geocodingResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val geocodingResults: StateFlow<List<GeocodingResult>> = _geocodingResults.asStateFlow()


    init {
        // On observe le terme de recherche pour lancer un appel à l'API de geocoding.
        viewModelScope.launch {
            _searchQuery
                .debounce(300) // Attend 300ms de silence de l'utilisateur avant de lancer la recherche pour éviter les appels inutiles.
                .collect { query ->
                    if (query.length > 2) {
                        val results = weatherService.searchCity(query)
                        _geocodingResults.value = results ?: emptyList()
                    } else {
                        _geocodingResults.value = emptyList()
                    }
                }
        }
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
        _geocodingResults.value = emptyList()
    }

    /**
     * Méthode appelée par l'UI pour demander un changement de localisation.
     * Le ViewModel transmet cette demande au WeatherCache, qui est la source de vérité.
     */
    fun selectLocation(location: LocationIdentifier) {
        weatherCache.setCurrentLocation(location)
        _refreshCounter.value = 0
    }

    /**
     * Méthode appelée par l'UI pour supprimer une localisation.
     * Le ViewModel transmet cette demande au WeatherCache, qui est la source de vérité.
     */
     fun removeLocation(location: SavedLocation) {
        weatherCache.removeLocation(location)
    }

    /**
     * Méthode appelée par l'UI pour ajouter une nouvelle localisation.
     * Le ViewModel transmet cette demande au WeatherCache, qui est la source de vérité.
     */
     fun addLocation(location: SavedLocation) {
        weatherCache.addLocation(location)
    }

    /**
     * Méthode appelée par l'UI pour réorganiser les lieux.
     */
    fun reorderLocations(newList: List<SavedLocation>) {
        weatherCache.reorderLocations(newList)
    }

    fun addLocationFromMap(coords: GpsCoordinates, name: String) {
        val newLocation = SavedLocation(
            name = name,
            latitude = coords.latitude,
            longitude = coords.longitude,
            country = "Unknown"
        )
        addLocation(newLocation)
        // Optionnel : Sélectionner immédiatement cette nouvelle position
        selectLocation(LocationIdentifier.Saved(newLocation))
    }

    /**
     * Méthode pour définir la position par défaut
     */
    fun setDefaultLocation(location: LocationIdentifier) {
        weatherCache.setDefaultLocation(location)
    }

    /**
     * Retourne un Flow de WeatherDataState pour une période précise.
     * Combine la localisation, les paramètres et le compteur de rafraîchissement
     * pour garantir que les données sont à jour.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getForecastForRange(startDateTime: LocalDateTime, endDateTime: LocalDateTime): Flow<WeatherDataState> {
        return combine(
            selectedLocation,
            userSettings,
            refreshCounter
        ) { _, _, _ ->
        }.flatMapLatest {
            // On calcule le nombre d'heures entre les deux dates pour l'API du cache
            val durationInHours = java.time.Duration.between(startDateTime, endDateTime).toHours()
            weatherCache.get(startDateTime, durationInHours.toInt())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            WeatherDataState.Loading
        )
    }

    /**
     * Rafraîchit uniquement la localisation et force la mise à jour des données
     * sans invalider le cache météo (contrairement à refresh()).
     */
    fun refreshLocation() {
        weatherCache.refreshCurrentLocation()
        _refreshCounter.value++
    }

    /**
     * Méthode appelée par l'UI pour invalider le cache
     */
    fun refresh() {
        weatherCache.invalidateCache()
        weatherCache.refreshCurrentLocation()
        _refreshCounter.value++
    }

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