/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.forecastMainActivity

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.BuildConfig
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.PolicyUpdateInfo
import fr.matthstudio.themeteo.ResolvedPolicyInfo
import fr.matthstudio.themeteo.SearchState
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherParser
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.BentoCardType
import fr.matthstudio.themeteo.data.ForecastTechnicalInfo
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.UserLocationsRepository
import fr.matthstudio.themeteo.data.UserSettingsRepository
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import fr.matthstudio.themeteo.utilClasses.EnvironmentalUIModel
import fr.matthstudio.themeteo.utilClasses.FullSunCalculator
import fr.matthstudio.themeteo.utilClasses.FullSunData
import fr.matthstudio.themeteo.utilClasses.MoonCalculator
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

/**
 * Ce ViewModel sert d'intermédiaire entre l'UI (WeatherScreen) et la logique de données (WeatherCache).
 * Il expose les états de l'application de manière simple et réactive pour que l'UI puisse les afficher.
 * Il gère également la logique de recherche de villes.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class) // Nécessaire pour l'opérateur debounce et flatMapLatest
class WeatherViewModel(
    private val weatherParser: WeatherParser,
    private val userLocationsRepository: UserLocationsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val appContext: Application,
    private val telemetryManager: TelemetryManager
) : ViewModel() {

    val weatherService = WeatherService(telemetryManager)

    // --- 1. ÉTATS PRINCIPAUX EXPOSÉS À L'UI VIA APPSTATEMANAGER ---

    /**
     * Expose la localisation actuellement sélectionnée depuis AppStateManager.
     */
    val selectedLocation: StateFlow<LocationIdentifier> = AppStateManager.selectedLocation

    /**
     * Un flux qui émet toutes les secondes pour les mises à jour en temps réel.
     */
    private val ticker = flow {
        while (true) {
            emit(Unit)
            kotlinx.coroutines.delay(1_000.milliseconds)
        }
    }

    /**
     * Données solaires calculées centralement dans AppStateManager.
     */
    val sunData: StateFlow<FullSunData?> = AppStateManager.sunData

    /**
     * Données lunaires calculées centralement dans AppStateManager.
     */
    val moonData: StateFlow<MoonData?> = AppStateManager.moonData

    private val defaultUserSettings = UserSettings(true, LocationIdentifier.CurrentUserLocation, DefaultScreen.FORECAST_MAIN, true, TemperatureUnit.CELSIUS, WindUnit.KPH, "PENDING", false, null, null, false, true, false, ThemeMode.FIXED, true, true, false)

    /**
     * Expose les paramètres utilisateur depuis AppStateManager.
     */
    val userSettings: StateFlow<UserSettings> = AppStateManager.userSettings.map {
        it ?: defaultUserSettings
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultUserSettings)

    /**
     * Expose les positions enregistrées par l'utilisateur depuis le WeatherCache.
     */
    val savedLocations: StateFlow<List<SavedLocation>> = AppStateManager.savedLocations

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

    /**
     * Expose l'ordre des cartes Bento.
     */
    val bentoCardsOrder: StateFlow<List<BentoCardType>> = userSettingsRepository.bentoCardsOrder.map { savedOrder: List<BentoCardType> ->
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
            userSettingsRepository.updateBentoCardsOrder(newOrder)
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

    private fun resolveCoords(location: LocationIdentifier, gpsCoords: GpsCoordinates?): GpsCoordinates? {
        val raw = when (location) {
            is LocationIdentifier.Saved -> GpsCoordinates(location.location.latitude, location.location.longitude)
            is LocationIdentifier.CurrentUserLocation -> gpsCoords
        } ?: return null

        // Arrondit les coordonnées à 3 décimales (~100m) pour filtrer le bruit des mises à jour GPS fréquentes (5s)
        val roundedLat = (raw.latitude * 1000.0).roundToInt() / 1000.0
        val roundedLon = (raw.longitude * 1000.0).roundToInt() / 1000.0
        return GpsCoordinates(roundedLat, roundedLon)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val next24hForecast: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        AppStateManager.currentGpsPosition,
        refreshCounter
    ) { _, _, _ ->
    }.flatMapLatest {
        weatherParser.getShortTerm(LocalDateTime.now().withMinute(0).withSecond(0).withNano(0), 24)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WeatherDataState.Loading
    )

    /**
     * État "Nuit" centralisé depuis AppStateManager.
     */
    val isNight: StateFlow<Boolean> = AppStateManager.isNight

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailyForecast: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        AppStateManager.currentGpsPosition,
        refreshCounter
    ) { _, _, _ ->
    }.flatMapLatest {
        weatherParser.getDaily(LocalDate.now(), 14)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        WeatherDataState.Loading
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val forecastTechnicalInfo: StateFlow<ForecastTechnicalInfo?> = combine(
        selectedLocation,
        AppStateManager.currentGpsPosition,
        refreshCounter
    ) { _, _, _ ->
    }.flatMapLatest {
        flow {
            emit(weatherParser.getTechnicalInfo())
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    /**
     * Flow de WeatherDataState pour les données de qualité de l'air à la localisation selectionnée.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val airQualityResponse: StateFlow<WeatherDataState> = combine(
        selectedLocation,
        AppStateManager.currentGpsPosition,
        refreshCounter
    ) { loc, gps, _ ->
        resolveCoords(loc, gps)
    }.distinctUntilChanged().flatMapLatest { coords ->
        flow {
            if (coords != null) {
                val airQuality = weatherService.getAirQuality(coords.latitude, coords.longitude, appContext)
                val airQualityForecast = weatherService.getAirQualityForecast(coords.latitude, coords.longitude, appContext)
                val pollen = weatherService.getPollenForecast(coords.latitude, coords.longitude, appContext)
                if (airQuality != null) {
                    emit(WeatherDataState.SuccessAirQuality(Triple(airQuality, airQualityForecast, pollen)))
                } else {
                    emit(WeatherDataState.Error("Air quality unavailable"))
                }
            } else {
                emit(WeatherDataState.Error("GPS position unavailable"))
            }
        }
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
            mapToEnvironmentalUI(state.data.first, state.data.second, state.data.third, settings?.useEurAqi ?: true)
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
        AppStateManager.currentGpsPosition,
        refreshCounter
    ) { loc, gps, _ ->
        resolveCoords(loc, gps)
    }.distinctUntilChanged().flatMapLatest { coords ->
        flow {
            if (coords != null) {
                val vigilance = weatherService.getVigilanceForLocation(coords.latitude, coords.longitude)
                if (vigilance != null) {
                    emit(WeatherDataState.SuccessVigilance(vigilance))
                } else {
                    emit(WeatherDataState.Error("Vigilance unavailable"))
                }
            } else {
                emit(WeatherDataState.Error("GPS position unavailable"))
            }
        }
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
        AppStateManager.currentGpsPosition,
        refreshCounter,
        fiveMinuteTicker
    ) { loc, gps, _, ticker ->
        Pair(resolveCoords(loc, gps), ticker)
    }.distinctUntilChanged().flatMapLatest { (coords, _) ->
        flow {
            if (coords != null) {
                val rain = weatherService.getRainWithinHour(coords.latitude, coords.longitude)
                if (rain != null) {
                    emit(WeatherDataState.SuccessRainWithinHour(rain))
                } else {
                    emit(WeatherDataState.Error("Rain info unavailable"))
                }
            } else {
                emit(WeatherDataState.Error("GPS position unavailable"))
            }
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
     * Utilise flatMapLatest pour annuler les recherches précédentes si l'utilisateur continue de taper.
     */
    val searchState: StateFlow<SearchState> = _searchQuery
        .debounce(300.milliseconds)
        .flatMapLatest { query ->
            if (query.length >= 2) {
                flow<SearchState> {
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
        val remoteInfo = weatherService.getPolicyUpdateInfo() ?: return

        if (remoteInfo.isUpdateRequired(BuildConfig.VERSION_CODE)) {
            val isAvailable = fr.matthstudio.themeteo.utilClasses.AppUpdateHelper.isPlayStoreUpdateAvailable(appContext)
            if (isAvailable) {
                AppStateManager.updateIsForcedUpdateRequired(true)
            } else {
                Log.d("WeatherViewModel", "Update is required by policy, but Play Store has no update available yet. Allowing current version.")
                AppStateManager.updateIsForcedUpdateRequired(false)
            }
        } else {
            AppStateManager.updateIsForcedUpdateRequired(false)
        }

        val resolved = remoteInfo.getPolicyForVersion(BuildConfig.VERSION_CODE) ?: return

        AppStateManager.updateResolvedPolicyInfo(resolved)
        remotePolicyUpdateInfo = remoteInfo

        val currentSettings = userSettings.filterNotNull().first()

        if (!currentSettings.hasOpenedAppOnce) {
            userSettingsRepository.updateLastGcuUpdate(resolved.lastGcuUpdate)
            userSettingsRepository.updateLastPrivacyPolicyUpdate(resolved.lastPrivacyPolicyUpdate)
            userSettingsRepository.updateHasOpenedAppOnce(true)
        } else {
            val lastGcu = currentSettings.lastGcuUpdate
            val lastPriv = currentSettings.lastPrivacyPolicyUpdate

            val gcuChanged = lastGcu == null || resolved.lastGcuUpdate > lastGcu
            val privacyChanged = lastPriv == null || resolved.lastPrivacyPolicyUpdate > lastPriv

            if (gcuChanged || privacyChanged) {
                _shouldShowPolicyUpdateDialog.value = true
            }
        }
    }

    fun acceptPolicyUpdates() {
        viewModelScope.launch {
            val resolved = remotePolicyUpdateInfo?.getPolicyForVersion(BuildConfig.VERSION_CODE) ?: return@launch
            userSettingsRepository.updateLastGcuUpdate(resolved.lastGcuUpdate)
            userSettingsRepository.updateLastPrivacyPolicyUpdate(resolved.lastPrivacyPolicyUpdate)
            _shouldShowPolicyUpdateDialog.value = false
        }
    }

    fun markBackgroundLocationAsked() {
        viewModelScope.launch {
            userSettingsRepository.updateBackgroundLocationAsked(true)
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
     * Recalcule instantanément les positions astronomiques (Soleil & Lune) à la seconde près
     * pour la localisation actuellement sélectionnée par l'utilisateur.
     */
    fun recalculateSunMoonNow() {
        val location = AppStateManager.selectedLocation.value
        val coords = when (location) {
            is LocationIdentifier.Saved -> GpsCoordinates(location.location.latitude, location.location.longitude)
            is LocationIdentifier.CurrentUserLocation -> AppStateManager.currentGpsPosition.value
        } ?: return

        val sunCalc = FullSunCalculator(coords.latitude, coords.longitude)
        val sunData = sunCalc.getCompleteSunData()
        AppStateManager.updateSunData(sunData)
        AppStateManager.updateIsNight(sunData.currentPosition.elevation <= 0.0)

        val moonCalc = MoonCalculator(coords.latitude, coords.longitude)
        val currentPos = moonCalc.getMoonPosition(LocalDateTime.now())
        val dailyEvents = moonCalc.getDailyEvents(LocalDate.now())
        val moonData = MoonData(dailyEvents = dailyEvents, currentPosition = currentPos)
        AppStateManager.updateMoonData(moonData)
    }

    /**
     * Méthode appelée par l'UI pour demander un changement de localisation.
     * Le ViewModel transmet cette demande au WeatherCache, qui est la source de vérité.
     */
    fun selectLocation(location: LocationIdentifier) {
        AppStateManager.updateSelectedLocation(location)
        _refreshCounter.value = 0
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
        // Optionnel : Sélectionner immédiatement cette nouvelle position
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

    /**
     * Retourne un Flow de WeatherDataState pour une période précise.
     * Bascule automatiquement sur le Moyen Terme si le court terme a moins de 12 pas horaires.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getForecastForRange(startDateTime: LocalDateTime, endDateTime: LocalDateTime): Flow<WeatherDataState> {
        val reqDate = startDateTime.toLocalDate()
        return combine(
            selectedLocation,
            userSettings,
            refreshCounter
        ) { _, _, _ ->
        }.flatMapLatest {
            val durationInHours = Duration.between(startDateTime, endDateTime).toHours().coerceAtLeast(1)
            weatherParser.getShortTerm(startDateTime, durationInHours.toInt()).flatMapLatest { shortState ->
                if (shortState is WeatherDataState.SuccessShortTermHourly && shortState.data.size >= 12) {
                    flowOf(shortState)
                } else {
                    weatherParser.getMediumTerm(startDateTime, endDateTime).flatMapLatest { medState ->
                        if (medState is WeatherDataState.SuccessMediumTermHourly && medState.data.size >= 8) {
                            flowOf(medState)
                        } else {
                            weatherParser.getLongTerm(reqDate, reqDate)
                        }
                    }
                }
            }
        }
    }

    /**
     * Rafraîchit uniquement la localisation et force la mise à jour des données
     * sans invalider le cache météo (contrairement à refresh()).
     */
    fun refreshLocation() {
        val app = appContext as? TheMeteo
        val granted = app?.container?.locationProvider?.checkLocationPermission()
            ?: AppStateManager.isLocationPermissionGranted.value
        AppStateManager.updateLocationPermission(granted)
        if (granted && app != null) {
            viewModelScope.launch {
                val coords = app.container.locationProvider.getCurrentLocation()
                if (coords != null) {
                    AppStateManager.updateGpsPosition(coords)
                }
            }
        }
        _refreshCounter.value++
    }

    fun consumeLocationSettingsException() {
        _locationSettingsException.value = null
    }

    fun refresh() {
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