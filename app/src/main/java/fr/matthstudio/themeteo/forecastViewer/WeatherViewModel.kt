package fr.matthstudio.themeteo.forecastViewer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import fr.matthstudio.themeteo.forecastViewer.data.UserLocationsRepository
import fr.matthstudio.themeteo.forecastViewer.data.SavedLocation
import fr.matthstudio.themeteo.forecastViewer.data.UserSettingsRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.parcelize.Parcelize
import kotlin.collections.get

/**
 * Représente le lieu dont on veut afficher la météo.
 * Soit la position GPS actuelle, soit un lieu sauvegardé.
 */
@Parcelize
sealed class LocationIdentifier : Parcelable {
    object CurrentUserLocation : LocationIdentifier()
    data class Saved(val location: SavedLocation) : LocationIdentifier()
}

data class UserSettings (
    val model: String?,
    val roundToInt: Boolean?
)

@OptIn(FlowPreview::class) // Nécessaire pour debounce
class WeatherViewModel(
    private val userLocationsRepository: UserLocationsRepository,
    private val userSettingsRepository: UserSettingsRepository
) : ViewModel() {

    private val weatherService = WeatherService()

    // --- StateFlow pour toutes les données ---

    private val _temperatureForecast = MutableStateFlow<List<TemperatureReading>>(emptyList())
    val temperatureForecast: StateFlow<List<TemperatureReading>> = _temperatureForecast.asStateFlow()

    private val _apparentTemperatureForecast = MutableStateFlow<List<ApparentTemperatureReading>?>(emptyList())
    val apparentTemperatureForecast: StateFlow<List<ApparentTemperatureReading>?> = _apparentTemperatureForecast.asStateFlow()

    private val _precipitationForecast = MutableStateFlow<List<PrecipitationReading>>(emptyList())
    val precipitationForecast: StateFlow<List<PrecipitationReading>> = _precipitationForecast.asStateFlow()

    private val _precipitationProbabilityForecast = MutableStateFlow<List<PrecipitationProbabilityReading>?>(emptyList())
    val precipitationProbabilityForecast: StateFlow<List<PrecipitationProbabilityReading>?> = _precipitationProbabilityForecast.asStateFlow()

    private val _skyInfoForecast = MutableStateFlow<List<SkyInfoReading>>(emptyList())
    val skyInfoForecast: StateFlow<List<SkyInfoReading>> = _skyInfoForecast.asStateFlow()

    private val _windspeedForecast = MutableStateFlow<List<WindspeedReading>>(emptyList()) 
    val windspeedForecast: StateFlow<List<WindspeedReading>> = _windspeedForecast.asStateFlow()

    private val _windDirectionForecast = MutableStateFlow<List<WindDirectionReading>>(emptyList())
    val windDirectionForecast: StateFlow<List<WindDirectionReading>> = _windDirectionForecast.asStateFlow()

    private val _pressureForecast = MutableStateFlow<List<PressureReading>>(emptyList()) 
    val pressureForecast: StateFlow<List<PressureReading>> = _pressureForecast.asStateFlow()

    private val _humidityForecast = MutableStateFlow<List<HumidityReading>>(emptyList()) 
    val humidityForecast: StateFlow<List<HumidityReading>> = _humidityForecast.asStateFlow()

    private val _dewpointForecast = MutableStateFlow<List<DewpointReading>>(emptyList()) 
    val dewpointForecast: StateFlow<List<DewpointReading>> = _dewpointForecast.asStateFlow()

    private val _wmoForecast = MutableStateFlow<List<WMOReading>>(emptyList())
    val wmoForecast: StateFlow<List<WMOReading>> = _wmoForecast.asStateFlow()

    // StateFlow for daily temperature forecast
    private val _dailyTemperatureForecast = MutableStateFlow<List<DailyReading>>(emptyList())
    val dailyTemperatureForecast: StateFlow<List<DailyReading>> = _dailyTemperatureForecast.asStateFlow()

    // Le lieu actuellement sélectionné par l'utilisateur
    private val _selectedLocation = MutableStateFlow<LocationIdentifier>(LocationIdentifier.CurrentUserLocation)
    val selectedLocation: StateFlow<LocationIdentifier> = _selectedLocation.asStateFlow()

    // La liste des lieux enregistrés, chargée depuis le repository
    val savedLocations: StateFlow<List<SavedLocation>> = userLocationsRepository.savedLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Les résultats de la recherche de ville
    private val _geocodingResults = MutableStateFlow<List<GeocodingResult>>(emptyList())
    val geocodingResults: StateFlow<List<GeocodingResult>> = _geocodingResults.asStateFlow()

    // Le terme de recherche actuel
    private val _searchQuery = MutableStateFlow("")

    // Gestion du chargement
    val _isLoadingTemperature = MutableStateFlow(false)
    val _isLoadingPrecipitation = MutableStateFlow(false)
    val _isLoadingApparentTemperature = MutableStateFlow(false)
    val _isLoadingPrecipitationProbability = MutableStateFlow(false)
    val _isLoadingCloudcover = MutableStateFlow(false)
    val _isLoadingWindspeed = MutableStateFlow(false)
    val _isLoadingPressure = MutableStateFlow(false)
    val _isLoadingHumidity = MutableStateFlow(false)
    val _isLoadingDewpoint = MutableStateFlow(false)
    val _isLoadingWMO = MutableStateFlow(false)
    val _isLoadingDailyTemperature = MutableStateFlow(false)

    // Un StateFlow combiné pour l'état global de chargement
    val isLoading: StateFlow<Boolean> = combine(
        _isLoadingTemperature,
        _isLoadingPrecipitation,
        _isLoadingApparentTemperature,
        _isLoadingPrecipitationProbability,
        _isLoadingCloudcover,
        _isLoadingWindspeed,
        _isLoadingPressure,
        _isLoadingHumidity,
        _isLoadingDewpoint,
        _isLoadingWMO,
        _isLoadingDailyTemperature
    ) { loadingStatesArray -> // Le paramètre du lambda est un Array<Boolean>
        loadingStatesArray.any { it } // 'it' fait référence à chaque Boolean dans le tableau
    }.stateIn( // Use stateIn instead of asStateFlow
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5), // Define when to start/stop collecting
        initialValue = false // Provide an initial value for the StateFlow
    )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- StateFlow pour la localisation de l'utilisateur ---
    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    // --- Client de localisation (déplacé du Composable) ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // Exemple d'utilisation pour lire les paramètres
    val userSettings = combine(
        userSettingsRepository.model,
        userSettingsRepository.roundToInt
    ) { model, round ->
        UserSettings(model, round)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettings(null, null) // <-- Valeur initiale "non chargée"
    )

    // --- LOGIQUE D'INITIALISATION ---
    init {
        // Observer à la fois le lieu et les paramètres.
        // L'action ne se déclenche que lorsque les deux sont prêts.
        viewModelScope.launch {
            combine(selectedLocation, userSettings) { location, settings ->
                // Créer une paire pour faciliter la gestion des changements
                location to settings
            }
                .distinctUntilChanged() // Ne réagit qu'aux vrais changements
                .collect { (identifier, settings) ->
                    // -- Point de contrôle crucial --
                    // Ne rien faire tant que les paramètres ne sont pas chargés depuis DataStore.
                    if (settings.model == null || settings.roundToInt == null) {
                        Log.d("WeatherViewModel", "En attente des paramètres utilisateur...")
                        return@collect // Sort de cette exécution du collect
                    }
                }
        }

        // Observer la requête de recherche pour lancer le géocodage
        viewModelScope.launch {
            _searchQuery
                .debounce(300) // Attendre 300ms après la dernière frappe
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

    // --- FONCTIONS POUR INTERAGIR AVEC LES LIEUX ---

    fun selectLocation(identifier: LocationIdentifier) {
        _selectedLocation.value = identifier
    }

    fun addLocation(location: SavedLocation) {
        viewModelScope.launch {
            userLocationsRepository.addLocation(location)
        }
    }

    fun removeLocation(location: SavedLocation) {
        viewModelScope.launch {
            userLocationsRepository.removeLocation(location)
            // Si le lieu supprimé était celui sélectionné, revenir à la position actuelle
            if (_selectedLocation.value == LocationIdentifier.Saved(location)) {
                _selectedLocation.value = LocationIdentifier.CurrentUserLocation
            }
        }
    }

    fun searchCity(query: String) {
        _searchQuery.value = query
    }

    fun loadDailyForecast(latitude: Double, longitude: Double, days: Int) {
        if (_isLoadingDailyTemperature.value){ // Ne pas relancer si déjà en cours
            Log.e("WeatherViewModel", "Le chargement des prévisions journalières de température est déjà en cours.")
            return
        }
        _errorMessage.value = null // Réinitialiser l'erreur

        viewModelScope.launch {
            _isLoadingDailyTemperature.value = true
            val result = weatherService.getDailyForecast(latitude, longitude, days, userSettings.value.model ?: "best_match")
            if (result != null) {
                _dailyTemperatureForecast.value = result
            } else {
                _errorMessage.value = "Impossible de récupérer les prévisions journalières de température."
            }
            _isLoadingDailyTemperature.value = false
        }
    }

    /**
     * Initialise le FusedLocationProviderClient et le callback.
     * Doit être appelée une seule fois depuis l'UI (par exemple, dans le onCreate de l'Activity).
     */
    fun initializeLocationClient(context: Context) {
        if (::fusedLocationClient.isInitialized) return // Déjà initialisé

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (_selectedLocation.value is LocationIdentifier.CurrentUserLocation) {
                        Log.d("WeatherViewModel", "Location update received: ${location.latitude}, ${location.longitude}")
                        _userLocation.value = location
                        // Arrête les mises à jour après avoir obtenu une localisation
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }
        }
    }

    /**
     * Fonction centrale pour charger TOUTES les données météo pour une latitude/longitude donnée.
     * C'est cette fonction qui est appelée lorsque le lieu change.
     */
    fun load24hForecast(latitude: Double, longitude: Double, startTime: LocalDateTime?) {
        if (_isLoadingTemperature.value) { // Ne pas relancer si déjà en cours
            Log.e("WeatherViewModel", "Le chargement des prévisions horaire est déjà en cours.")
            return
        }
        _errorMessage.value = null // Réinitialiser l'erreur

        if (userSettings.value.model == null)
            Log.e("WeatherViewModel", "Le modèle n'est pas défini. Chargement impossible.")

        viewModelScope.launch {
            // Log pour savoir quel lieu est en cours de chargement
            Log.d("WeatherViewModel", "Chargement des prévisions pour lat: $latitude, lon: $longitude")
            // Log pour savoir quel model est utilisé
            Log.d("WeatherViewModel", "Model :${userSettings.value.model}")

            viewModelScope.launch {
                _isLoadingTemperature.value = true
                _isLoadingPrecipitation.value = true
                _isLoadingApparentTemperature.value = true
                _isLoadingPrecipitationProbability.value = true
                _isLoadingCloudcover.value = true
                _isLoadingWindspeed.value = true
                _isLoadingPressure.value = true
                _isLoadingHumidity.value = true
                _isLoadingDewpoint.value = true
                _isLoadingWMO.value = true

                val result = if (startTime != null)
                    weatherService.get24hAllVariablesForecast(latitude, longitude, userSettings.value.model ?: "best_match", startTime)
                else
                    weatherService.get24hAllVariablesForecast(latitude, longitude, userSettings.value.model ?: "best_match")
                if (result != null) {
                    _temperatureForecast.value = result.map { it.temperatureReading }
                    if (result.first().apparentTemperatureReading != null)
                        _apparentTemperatureForecast.value = result.map { it.apparentTemperatureReading as ApparentTemperatureReading }
                    else
                        _apparentTemperatureForecast.value = null
                    _precipitationForecast.value = result.map { it.precipitationReading }
                    if (result.first().precipitationProbabilityReading != null)
                        _precipitationProbabilityForecast.value = result.map { it.precipitationProbabilityReading as PrecipitationProbabilityReading }
                    else
                        _precipitationProbabilityForecast.value = null
                    _skyInfoForecast.value = result.map { it.skyInfoReading }
                    _windspeedForecast.value = result.map { it.windspeedReading }
                    _windDirectionForecast.value = result.map { it.winddirectionReading }
                    _pressureForecast.value = result.map { it.pressureReading }
                    _humidityForecast.value = result.map { it.humidityReading }
                    _dewpointForecast.value = result.map { it.dewpointReading }
                    _wmoForecast.value = result.map { it.wmoReading }
                } else {
                    _errorMessage.value = "Impossible de récupérer les prévisions météo."
                }

                _isLoadingTemperature.value = false
                _isLoadingPrecipitation.value = false
                _isLoadingApparentTemperature.value = false
                _isLoadingPrecipitationProbability.value = false
                _isLoadingCloudcover.value = false
                _isLoadingWindspeed.value = false
                _isLoadingPressure.value = false
                _isLoadingHumidity.value = false
                _isLoadingDewpoint.value = false
                _isLoadingWMO.value = false
            }
        }
    }

    /**
     * Gère la demande de localisation GPS lorsque "Position Actuelle" est sélectionné.
     * Cette fonction ne charge pas directement les prévisions, elle demande une mise à jour
     * de la localisation. Le `locationCallback` se chargera de déclencher le chargement.
     */
    fun getLocationAndLoad24hForecastPlusDailyForecast(context: Context, startTime: LocalDateTime?) {
        // Coordonnées par défaut (Paris, France)
        val defaultLatitude = 48.85
        val defaultLongitude = 2.35

        _errorMessage.value = null // Réinitialiser l'erreur

        // 1. Vérifier si le client a été initialisé
        if (!::fusedLocationClient.isInitialized) {
            Log.e("WeatherViewModel", "FusedLocationClient non initialisé. Appelez initializeLocationClient en premier.")
            // On charge les données pour le lieu par défaut si le client GPS n'est pas prêt
            load24hForecast(defaultLatitude, defaultLongitude, LocalDateTime.now())
            return
        }

        // 2. Vérifier si les permissions sont accordées
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _errorMessage.value = "Permission de localisation non accordée. Veuillez sélectionner un lieu manuellement ou autoriser la localisation."
            // Charger pour le lieu par défaut si aucune permission n'est accordée
            load24hForecast(defaultLatitude, defaultLongitude, LocalDateTime.now())
            loadDailyForecast(defaultLatitude, defaultLongitude, weatherModelPredictionTime[userSettings.value.model] ?: 10)
            return
        }

        // 3. Demander la localisation (try-catch pour la sécurité)
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    // Nous avons une position connue, on l'utilise immédiatement
                    Log.d("WeatherViewModel", "Dernière position connue trouvée : ${location.latitude}")
                    _userLocation.value = location
                    load24hForecast(location.latitude, location.longitude, startTime)
                    loadDailyForecast(location.latitude, location.longitude, weatherModelPredictionTime[userSettings.value.model] ?: 10)
                } else {
                    // Aucune position connue, on demande une nouvelle mise à jour
                    Log.d("WeatherViewModel", "Dernière position nulle, demande de mise à jour...")
                    val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                        .setWaitForAccurateLocation(false)
                        .setMinUpdateIntervalMillis(5000)
                        .setMaxUpdateDelayMillis(15000)
                        .build()
                    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                }
            }.addOnFailureListener { e ->
                Log.e("WeatherViewModel", "Erreur lors de la récupération de la dernière position", e)
                _errorMessage.value = "Impossible de récupérer la position GPS."
                load24hForecast(defaultLatitude, defaultLongitude, LocalDateTime.now())
                loadDailyForecast(defaultLatitude, defaultLongitude, weatherModelPredictionTime[userSettings.value.model] ?: 10)
            }
        } catch (e: SecurityException) {
            Log.e("WeatherViewModel", "Exception de sécurité lors de la demande de localisation", e)
            _errorMessage.value = "Erreur de sécurité de la localisation."
            load24hForecast(defaultLatitude, defaultLongitude, LocalDateTime.now())
            loadDailyForecast(defaultLatitude, defaultLongitude, weatherModelPredictionTime[userSettings.value.model] ?: 10)
        }
    }

    // --- FONCTIONS POUR METTRE À JOUR LES PARAMÈTRES ---

    /**
     * Met à jour le modèle météo via le repository.
     */
    fun updateModel(newModel: String) {
        viewModelScope.launch {
            userSettingsRepository.updateModel(newModel)
        }
    }

    /**
     * Met à jour le paramètre d'arrondi via le repository.
     */
    fun updateRoundToInt(shouldRound: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.updateRoundToInt(shouldRound)
        }
    }

    override fun onCleared() {
        super.onCleared()
        weatherService.close()
    }
}