package fr.matthstudio.themeteo.forecastViewer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
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
import java.io.RandomAccessFile
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import fr.matthstudio.themeteo.data.UserLocationsRepository
import fr.matthstudio.themeteo.data.SavedLocation
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * Représente le lieu dont on veut afficher la météo.
 * Soit la position GPS actuelle, soit un lieu sauvegardé.
 */
sealed class LocationIdentifier {
    object CurrentUserLocation : LocationIdentifier()
    data class Saved(val location: SavedLocation) : LocationIdentifier()
}

@OptIn(FlowPreview::class) // Nécessaire pour debounce
class WeatherViewModel(private val userLocationsRepository: UserLocationsRepository) : ViewModel() {

    private val weatherService = WeatherService()

    // --- StateFlow pour toutes les données ---

    private val _temperatureForecast = MutableStateFlow<List<TemperatureReading>>(emptyList())
    val temperatureForecast: StateFlow<List<TemperatureReading>> = _temperatureForecast.asStateFlow()

    private val _apparentTemperatureForecast = MutableStateFlow<List<ApparentTemperatureReading>>(emptyList()) 
    val apparentTemperatureForecast: StateFlow<List<ApparentTemperatureReading>> = _apparentTemperatureForecast.asStateFlow()

    private val _precipitationForecast = MutableStateFlow<List<PrecipitationReading>>(emptyList())
    val precipitationForecast: StateFlow<List<PrecipitationReading>> = _precipitationForecast.asStateFlow()

    private val _precipitationProbabilityForecast = MutableStateFlow<List<PrecipitationProbabilityReading>>(emptyList()) 
    val precipitationProbabilityForecast: StateFlow<List<PrecipitationProbabilityReading>> = _precipitationProbabilityForecast.asStateFlow()

    private val _skyInfoForecast = MutableStateFlow<List<SkyInfoReading>>(emptyList())
    val skyInfoForecast: StateFlow<List<SkyInfoReading>> = _skyInfoForecast.asStateFlow()

    private val _windspeedForecast = MutableStateFlow<List<WindspeedReading>>(emptyList()) 
    val windspeedForecast: StateFlow<List<WindspeedReading>> = _windspeedForecast.asStateFlow()

    private val _pressureForecast = MutableStateFlow<List<PressureReading>>(emptyList()) 
    val pressureForecast: StateFlow<List<PressureReading>> = _pressureForecast.asStateFlow()

    private val _humidityForecast = MutableStateFlow<List<HumidityReading>>(emptyList()) 
    val humidityForecast: StateFlow<List<HumidityReading>> = _humidityForecast.asStateFlow()

    private val _dewpointForecast = MutableStateFlow<List<DewpointReading>>(emptyList()) 
    val dewpointForecast: StateFlow<List<DewpointReading>> = _dewpointForecast.asStateFlow()

    // StateFlow for daily temperature forecast
    private val _dailyTemperatureForecast = MutableStateFlow<List<DailyTemperatureReading>>(emptyList())
    val dailyTemperatureForecast: StateFlow<List<DailyTemperatureReading>> = _dailyTemperatureForecast.asStateFlow()

    private val _isDaytime = MutableStateFlow(true) // Valeur par défaut : jour
    val isDaytime: StateFlow<Boolean> = _isDaytime.asStateFlow()

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
    val _isLoadingDailyTemperature = MutableStateFlow(false)
    val _isLoadingIsDay = MutableStateFlow(false)

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
        _isLoadingDailyTemperature,
        _isLoadingIsDay
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

    // --- LOGIQUE D'INITIALISATION ---
    init {
        // Observer le lieu sélectionné pour recharger les données météo
        viewModelScope.launch {
            selectedLocation.collect { identifier ->
                val startDateTime = LocalDateTime.now() // Ou celui pertinent pour la vue
                when (identifier) {
                    is LocationIdentifier.CurrentUserLocation -> {
                        // Si on a déjà la localisation GPS, on l'utilise
                        _userLocation.value?.let { loc ->
                            load24hForecast(loc.latitude, loc.longitude, startDateTime)
                        }
                        // Sinon, la méthode `getLocationAndLoad24hForecast` sera appelée par la vue
                    }
                    is LocationIdentifier.Saved -> {
                        // Charger la météo pour le lieu sauvegardé
                        load24hForecast(
                            identifier.location.latitude,
                            identifier.location.longitude,
                            startDateTime
                        )
                    }
                }
            }
        }

        // Observer la requête de recherche pour lancer le géocodage (avec un délai pour ne pas spammer l'API)
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

    // --- Fonctions de chargement spécifiques ---
    fun loadIsDay(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _isLoadingIsDay.value = true
            val result = weatherService.getIsDay(latitude, longitude)
            if (result != null) {
                _isDaytime.value = result
            } else {
                // En cas d'erreur, on peut laisser la valeur par défaut (jour) ou gérer autrement
                _errorMessage.value = "Impossible de déterminer si c'est le jour ou la nuit."
            }
            _isLoadingIsDay.value = false
        }
    }

    fun loadDailyTemperatureForecast(latitude: Double, longitude: Double, days: Long = 10) {
        viewModelScope.launch {
            _isLoadingDailyTemperature.value = true
            val result = weatherService.getDailyTemperatureForecast(latitude, longitude, days)
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
                        // Charge les prévisions avec la nouvelle localisation
                        load24hForecast(location.latitude, location.longitude, LocalDateTime.now())
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
        viewModelScope.launch {
            // Log pour savoir quel lieu est en cours de chargement
            Log.d("WeatherViewModel", "Chargement des prévisions pour lat: $latitude, lon: $longitude")

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

                val result = if (startTime != null)
                    weatherService.get24hAllVariablesForecast(latitude, longitude, startTime)
                else
                    weatherService.get24hAllVariablesForecast(latitude, longitude)
                if (result != null) {
                    _temperatureForecast.value = result.map { it.temperatureReading }
                    _apparentTemperatureForecast.value = result.map { it.apparentTemperatureReading }
                    _precipitationForecast.value = result.map { it.precipitationReading }
                    _precipitationProbabilityForecast.value = result.map { it.precipitationProbabilityReading }
                    _skyInfoForecast.value = result.map { it.skyInfoReading }
                    _windspeedForecast.value = result.map { it.windspeedReading }
                    _pressureForecast.value = result.map { it.pressureReading }
                    _humidityForecast.value = result.map { it.humidityReading }
                    _dewpointForecast.value = result.map { it.dewpointReading }
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
            }

            // `isDay` ne dépend pas de l'heure de début
            launch { loadIsDay(latitude, longitude) }
        }
    }

    /**
     * Gère la demande de localisation GPS lorsque "Position Actuelle" est sélectionné.
     * Cette fonction ne charge pas directement les prévisions, elle demande une mise à jour     * de la localisation. Le `locationCallback` se chargera de déclencher le chargement.
     */
    fun getLocationAndLoad24hForecast(context: Context, startTime: LocalDateTime?) {
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
            }
        } catch (e: SecurityException) {
            Log.e("WeatherViewModel", "Exception de sécurité lors de la demande de localisation", e)
            _errorMessage.value = "Erreur de sécurité de la localisation."
            load24hForecast(defaultLatitude, defaultLongitude, LocalDateTime.now())
        }
    }


    override fun onCleared() {
        super.onCleared()
        weatherService.close()
    }
}