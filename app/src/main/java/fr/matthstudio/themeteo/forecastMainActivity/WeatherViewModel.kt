/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.forecastMainActivity

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.PolicyUpdateInfo
import fr.matthstudio.themeteo.SearchState
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherCache
import fr.matthstudio.themeteo.BuildConfig
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.BentoCardType
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.getHourlyData
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import fr.matthstudio.themeteo.utilClasses.EnvironmentalUIModel
import fr.matthstudio.themeteo.utilClasses.FullSunData
import fr.matthstudio.themeteo.utilClasses.MoonData
import fr.matthstudio.themeteo.utilClasses.mapToEnvironmentalUI
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Ce ViewModel sert d'intermédiaire entre l'UI (WeatherScreen) et la logique de données (WeatherCache).
 * Il expose les états de l'application de manière simple et réactive pour que l'UI puisse les afficher.
 * Il gère également la logique de recherche de villes.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class) // Nécessaire pour l'opérateur debounce et flatMapLatest
class WeatherViewModel(
    private val weatherCache: WeatherCache,
    private val telemetryManager: TelemetryManager
) : ViewModel() {

    val weatherService = WeatherService(telemetryManager)

    // --- 1. ÉTATS PRINCIPAUX EXPOSÉS À L'UI ---

    /**
     * Expose la localisation actuellement sélectionnée depuis le WeatherCache.
     */
    val selectedLocation: StateFlow<LocationIdentifier> = weatherCache.selectedLocation

    /**
     * Un flux qui émet toutes les secondes pour les mises à jour en temps réel.
     */
    private val ticker = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(1_000)
        }
    }

    /**
     * Données solaires calculées centralement dans le cache.
     */
    val sunData: StateFlow<FullSunData?> = weatherCache.sunData

    /**
     * Données lunaires calculées centralement dans le cache.
     */
    val moonData: StateFlow<MoonData?> = weatherCache.moonData

    /**
     * Expose les paramètres utilisateur (modèle, arrondi, etc.) directement depuis le WeatherCache.
     * L'UI se mettra à jour automatiquement si les paramètres changent dans le DataStore.
     */
    val userSettings: StateFlow<UserSettings> = weatherCache.userSettings

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
     * Expose le nom de la ville actuelle récupéré via géocodage inverse.
     */
    val currentCityName: StateFlow<String?> = weatherCache.currentCityName

    /**
     * Expose si la permission de localisation est accordée.
     */
    val isLocationPermissionGranted: StateFlow<Boolean> = weatherCache.isLocationPermissionGranted

    /**
     * Expose l'ordre des cartes Bento.
     */
    val bentoCardsOrder: StateFlow<List<BentoCardType>> = weatherCache.userSettingsRepository.bentoCardsOrder.map { savedOrder: List<BentoCardType> ->
        val currentEntries = BentoCardType.entries
        val result = savedOrder.toMutableList()
        
        // Ajouter les cartes manquantes
        val missing = currentEntries.filter { it !in result }
        missing.forEach { missingCard ->
            if (missingCard == BentoCardType.RAIN_RADAR) {
                val sunIndex = result.indexOf(BentoCardType.SUN_DETAILS)
                if (sunIndex != -1) result.add(sunIndex, missingCard) else result.add(missingCard)
            } else if (missingCard == BentoCardType.RAIN_WITHIN_HOUR) {
                val vigilanceIndex = result.indexOf(BentoCardType.VIGILANCE)
                if (vigilanceIndex != -1) result.add(vigilanceIndex + 1, missingCard) else result.add(missingCard)
            } else {
                result.add(missingCard)
            }
        }

        // Supprimer le radar en mode FOSS
        if (BuildConfig.BUILD_TYPE == "foss") {
            result.remove(BentoCardType.RAIN_RADAR)
        }

        // Forcer VIGILANCE à 0
        val vIdx = result.indexOf(BentoCardType.VIGILANCE)
        if (vIdx != 0 && vIdx != -1) {
            result.removeAt(vIdx)
            result.add(0, BentoCardType.VIGILANCE)
        }

        // Forcer RAIN_WITHIN_HOUR à 1 (juste après VIGILANCE)
        val rIdx = result.indexOf(BentoCardType.RAIN_WITHIN_HOUR)
        if (rIdx != 1 && rIdx != -1) {
            result.removeAt(rIdx)
            result.add(1, BentoCardType.RAIN_WITHIN_HOUR)
        }

        // Forcer ADDITIONAL_INFOS à la fin
        val aIdx = result.indexOf(BentoCardType.ADDITIONAL_INFOS)
        if (aIdx != -1 && aIdx != result.size - 1) {
            result.removeAt(aIdx)
            result.add(BentoCardType.ADDITIONAL_INFOS)
        }

        result.toList()
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        BentoCardType.entries
    )

    /**
     * Met à jour l'ordre des cartes Bento.
     */
    fun updateBentoCardsOrder(newOrder: List<BentoCardType>) {
        viewModelScope.launch {
            weatherCache.userSettingsRepository.updateBentoCardsOrder(newOrder)
        }
    }

    /**
     * Variable servant à forcer le rafraichissement, elle est incrémentée à chaque appel de refresh()
     */
    private val _refreshCounter = MutableStateFlow(0)
    val refreshCounter: StateFlow<Int> = _refreshCounter.asStateFlow()

    private val _locationSettingsException = MutableStateFlow<Exception?>(null)
    val locationSettingsException: StateFlow<Exception?> = _locationSettingsException.asStateFlow()

    private val _shouldShowPolicyUpdateDialog = MutableStateFlow(false)
    val shouldShowPolicyUpdateDialog: StateFlow<Boolean> = _shouldShowPolicyUpdateDialog.asStateFlow()

    private var remotePolicyUpdateInfo: PolicyUpdateInfo? = null


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

    /**
     * État "Nuit" centralisé, dérivé des données de prévisions horaires.
     * Basé sur le rayonnement solaire (shortwave radiation) < 1.0.
     */
    val isNight: StateFlow<Boolean> = combine(hourlyForecast) { state ->
        val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val reading = state[0].getHourlyData()?.find { it.time == now }
        val radiation = reading?.skyInfo?.shortwaveRadiation
        (radiation ?: 1.0) < 1.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyForecast: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        userSettings,
        refreshCounter
    ) { _, settings, _ ->
        WeatherModelRegistry.getMaxPredictionDays(
            settings.model,
            settings.forecastType == ForecastType.ENSEMBLE,
            settings.enableDurationExtension
        ).toLong()
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
     * Données environnementales formatées pour l'UI.
     */
    val environmentalData: StateFlow<EnvironmentalUIModel?> = combine(
        airQualityResponse,
        userSettings
    ) { state, settings ->
        if (state is WeatherDataState.SuccessAirQuality) {
            mapToEnvironmentalUI(state.data.first, state.data.second, state.data.third, settings.useEurAqi)
        } else {
            null
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
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

    private fun getMillisToNextFiveMinuteMark(): Long {
        val now = Calendar.getInstance()
        val minute = now.get(Calendar.MINUTE)
        val second = now.get(Calendar.SECOND)
        val millis = now.get(Calendar.MILLISECOND)

        // Nombre de minutes à ajouter pour atteindre le prochain multiple de 5.
        val minutesToNextBoundary = 5 - (minute % 5)

        // On convertit tout en millisecondes et on soustrait le temps déjà écoulé dans la minute courante
        return (minutesToNextBoundary * 60 * 1000L) - (second * 1000L) - millis
    }

    val fiveMinuteTicker: StateFlow<Long> = flow {
        // 1. Émission initiale immédiate au lancement
        emit(System.currentTimeMillis())

        while (true) {
            val delayMillis = getMillisToNextFiveMinuteMark()

            delay(delayMillis.milliseconds)

            emit(System.currentTimeMillis())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = System.currentTimeMillis()
    )

    /**
     * Flow de WeatherDataState pour la pluie dans l'heure à la localisation selectionnée.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rainWithinHour: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        refreshCounter,
        fiveMinuteTicker
    ) { _, _, _ ->
    }.flatMapLatest {
        weatherCache.getRainWithinHour()
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
     * Utilise flatMapLatest pour annuler les recherches précédentes si l'utilisateur continue de taper.
     */
    val searchState: StateFlow<SearchState> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.length >= 2) {
                kotlinx.coroutines.flow.flow<SearchState> {
                    Log.d("WeatherViewModel", "Recherche lancée pour : $query")
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
        // Vérification des mises à jour des politiques
        viewModelScope.launch {
            checkPolicyUpdates()
        }
    }

    private suspend fun checkPolicyUpdates() {
        val remote = weatherService.getPolicyUpdateInfo() ?: return
        remotePolicyUpdateInfo = remote
        
        val currentSettings = userSettings.value
        
        if (!currentSettings.hasOpenedAppOnce) {
            // Premier lancement : on enregistre les dates sans afficher le dialogue
            weatherCache.userSettingsRepository.updateLastGcuUpdate(remote.lastGcuUpdate)
            weatherCache.userSettingsRepository.updateLastPrivacyPolicyUpdate(remote.lastPrivacyPolicyUpdate)
            weatherCache.userSettingsRepository.updateHasOpenedAppOnce(true)
        } else {
            // Lancements ultérieurs : on compare
            // Si les dates locales sont nulles, on les initialise sans afficher de dialogue
            if (currentSettings.lastGcuUpdate == null || currentSettings.lastPrivacyPolicyUpdate == null) {
                weatherCache.userSettingsRepository.updateLastGcuUpdate(currentSettings.lastGcuUpdate ?: remote.lastGcuUpdate)
                weatherCache.userSettingsRepository.updateLastPrivacyPolicyUpdate(currentSettings.lastPrivacyPolicyUpdate ?: remote.lastPrivacyPolicyUpdate)
                return
            }

            val gcuChanged = remote.lastGcuUpdate > currentSettings.lastGcuUpdate
            val privacyChanged = remote.lastPrivacyPolicyUpdate > currentSettings.lastPrivacyPolicyUpdate
            
            if (gcuChanged || privacyChanged) {
                _shouldShowPolicyUpdateDialog.value = true
            }
        }
    }

    fun acceptPolicyUpdates() {
        viewModelScope.launch {
            remotePolicyUpdateInfo?.let { remote ->
                weatherCache.userSettingsRepository.updateLastGcuUpdate(remote.lastGcuUpdate)
                weatherCache.userSettingsRepository.updateLastPrivacyPolicyUpdate(remote.lastPrivacyPolicyUpdate)
                _shouldShowPolicyUpdateDialog.value = false
            }
        }
    }

    fun markBackgroundLocationAsked() {
        viewModelScope.launch {
            weatherCache.userSettingsRepository.updateBackgroundLocationAsked(true)
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

    /**
     * Méthode appelée par l'UI pour renommer un lieu.
     */
    fun renameLocation(location: SavedLocation, newName: String) {
        weatherCache.renameLocation(location, newName)
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
        weatherCache.locationProvider.checkLocationSettings { enabled, exception ->
            if (!enabled && exception != null) {
                _locationSettingsException.value = exception
            }
        }
        _refreshCounter.value++
    }

    fun consumeLocationSettingsException() {
        _locationSettingsException.value = null
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