package fr.matthstudio.themeteo.dayChoserActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.GeocodingResult
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherCache
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
     * Expose les positions enregistrées par l'utilisateur depuis le WeatherCache.
     */
    val savedLocations: StateFlow<List<SavedLocation>> = weatherCache.savedLocations

    /**
     * Expose la position GPS actuelle directement depuis le WeatherCache.
     * La valeur sera `null` si la localisation n'est pas activée ou pas encore disponible.
     */
    val userLocation: StateFlow<GpsCoordinates?> = weatherCache.currentGpsPosition

    /**
     * Flow de WeatherDataState pour les donnnées actuelles à toutes les villes enregistrées.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentWeather: StateFlow<WeatherDataState> = combine(
        savedLocations
    ) { _ ->
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
     * Forecast pour les prochains jours
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val forecast: StateFlow<WeatherDataState> = combine(
        userSettings
    ) { _ ->
        // On combine les deux. Peu importe la valeur reçue,
        // flatMapLatest relancera le flux ci-dessous.
    }.flatMapLatest {
        weatherCache.get(LocalDate.now(),
            weatherModelPredictionTime[userSettings.value.model]?.toLong() ?: 3
        )
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