/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import android.os.PowerManager
import android.util.Log
import androidx.glance.appwidget.updateAll
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.LocalDateSerializer
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.data.LocationProvider
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.UserLocationsRepository
import fr.matthstudio.themeteo.data.UserSettingsRepository
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.utilClasses.AirQualityForecastResponse
import fr.matthstudio.themeteo.utilClasses.AirQualityInfo
import fr.matthstudio.themeteo.utilClasses.VigilanceInfos
import fr.matthstudio.themeteo.utilClasses.PollenResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TreeMap

@Parcelize
@Serializable
sealed class LocationIdentifier : Parcelable {
    @Serializable
    @Parcelize
    object CurrentUserLocation : LocationIdentifier()

    @Serializable
    @Parcelize
    data class Saved(val location: SavedLocation) : LocationIdentifier()
}

enum class DefaultScreen {
    FORECAST_MAIN,
    DAY_CHOSER
}

data class UserSettings(
    val model: String,
    val roundToInt: Boolean,
    val defaultLocation: LocationIdentifier,
    val defaultScreen: DefaultScreen,
    val enableModelFallback: Boolean,
    val enableAnimatedIcons: Boolean,
    val forecastType: ForecastType,
    val temperatureUnit: TemperatureUnit,
    val windUnit: WindUnit,
    val widgetTransparency: Int,
    val widgetTextSize: Int,
    val firebaseConsent: String,
    val gcuAccepted: Boolean,
    val lastGcuUpdate: String?,
    val lastPrivacyPolicyUpdate: String?,
    val hasOpenedAppOnce: Boolean,
    val useEurAqi: Boolean
)

/**
 * Serializer générique pour TreeMap afin de garantir le support de kotlinx.serialization
 * tout en conservant les fonctionnalités de NavigableMap.
 */
class TreeMapSerializer<K : Comparable<K>, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
) : KSerializer<TreeMap<K, V>> {
    private val delegate = MapSerializer(keySerializer, valueSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: TreeMap<K, V>) =
        encoder.encodeSerializableValue(delegate, value)
    override fun deserialize(decoder: Decoder): TreeMap<K, V> =
        TreeMap(decoder.decodeSerializableValue(delegate))
}

// Représente le cache pour un modèle météo spécifique à un lieu.
@Serializable
data class ModelDataCache(
    // Utilisation d'une TreeMap pour supporter subMap et les accès ordonnés.
    @Serializable(with = TreeMapSerializer::class)
    var dailyBlocks: TreeMap<
            @Serializable(with = LocalDateSerializer::class) LocalDate,
            Pair<List<AllHourlyVarsReading>, DailyReading>
            > = TreeMap(),
    @Serializable(with = LocalDateTimeSerializer::class)
    var lastFullFetch: LocalDateTime = LocalDateTime.MIN,

    @Serializable
    var currentWeatherReading: CurrentWeatherReading? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    var lastCurrentWeatherFetch: LocalDateTime = LocalDateTime.MIN,
    @Serializable
    var airQualityInfo: AirQualityInfo? = null,
    @Serializable
    var airQualityForecast: AirQualityForecastResponse? = null,
    @Serializable
    var pollenInfo: PollenResponse? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    var lastAirQualityFetch: LocalDateTime = LocalDateTime.MIN,
    @Serializable
    var vigilanceInfo: VigilanceInfos? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    var lastVigilanceFetch: LocalDateTime = LocalDateTime.MIN,

    )


/**
 * Gère un cache en mémoire pour les données de prévisions météo, structuré par jour.
 * Il centralise la logique métier, l'accès aux données et les préférences utilisateur
 * de manière réactive grâce aux StateFlows.
 */
class WeatherCache(
    val userLocationsRepository: UserLocationsRepository,
    val userSettingsRepository: UserSettingsRepository,
    private val applicationScope: CoroutineScope,
    private val locationProvider: LocationProvider,
    private val cache: MutableMap<LocationIdentifier, MutableMap<String, ModelDataCache>> = mutableMapOf(),
    private val applicationContext: Application
) {
    private val weatherService = WeatherService((applicationContext as TheMeteo).container.telemetryManager)
    private val cacheMutex = Mutex()

    // --- StateFlows pour les settings et la localisation sélectionnée ---
    private val _userSettings = MutableStateFlow(UserSettings("best_match", true, LocationIdentifier.CurrentUserLocation, DefaultScreen.FORECAST_MAIN, true, true, ForecastType.DETERMINISTIC, TemperatureUnit.CELSIUS, WindUnit.KPH, 50, 1, "PENDING", false, null, null, false, true))
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _selectedLocation = MutableStateFlow<LocationIdentifier>(LocationIdentifier.CurrentUserLocation)
    val selectedLocation: StateFlow<LocationIdentifier> = _selectedLocation.asStateFlow()

    val savedLocations: StateFlow<List<SavedLocation>> = userLocationsRepository.savedLocations
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly, // Reste actif tant que l'app tourne
            initialValue = emptyList()
        )

    // --- StateFlow pour la position GPS réelle ---
    private val _currentGpsPosition = MutableStateFlow<GpsCoordinates?>(null)
    val currentGpsPosition: StateFlow<GpsCoordinates?> = _currentGpsPosition.asStateFlow()
    private val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val _isBatterySaverActive = MutableStateFlow(powerManager.isPowerSaveMode)
    val isBatterySaverActive: StateFlow<Boolean> = _isBatterySaverActive.asStateFlow()

    private val _isLocationPermissionGranted = MutableStateFlow(locationProvider.checkLocationPermission())
    val isLocationPermissionGranted: StateFlow<Boolean> = _isLocationPermissionGranted.asStateFlow()
    
    init {
        // ... (Battery Saver monitor)
        
        // Initialiser la localisation sélectionnée avec la valeur par défaut sauvegardée
        applicationScope.launch {
            userSettingsRepository.defaultLocation.collect { defaultLoc ->
                // On ne met à jour que si c'est l'initialisation ou si on est déjà sur une position par défaut
                if (_selectedLocation.value == LocationIdentifier.CurrentUserLocation || _selectedLocation.value == defaultLoc) {
                    _selectedLocation.value = defaultLoc ?: LocationIdentifier.CurrentUserLocation
                }
            }
        }

        // Monitor Battery Saver
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                _isBatterySaverActive.value = powerManager.isPowerSaveMode
            }
        }
        applicationContext.registerReceiver(receiver, filter)
        
        // Collecte les settings
        applicationScope.launch {
            combine(
                userSettingsRepository.model,
                userSettingsRepository.roundToInt,
                userSettingsRepository.defaultLocation,
                userSettingsRepository.defaultScreen,
                userSettingsRepository.enableModelFallback,
                userSettingsRepository.enableAnimatedIcons,
                userSettingsRepository.forecastType,
                userSettingsRepository.temperatureUnit,
                userSettingsRepository.windUnit,
                userSettingsRepository.widgetTransparency,
                userSettingsRepository.widgetTextSize,
                userSettingsRepository.firebaseConsent,
                userSettingsRepository.gcuAccepted,
                userSettingsRepository.lastGcuUpdate,
                userSettingsRepository.lastPrivacyPolicyUpdate,
                userSettingsRepository.hasOpenedAppOnce,
                userSettingsRepository.useEurAqi
            ) { values ->
                val model = values[0] as String?
                val round = values[1] as Boolean
                val location = values[2] as LocationIdentifier?
                val screen = values[3] as DefaultScreen?
                val fallback = values[4] as Boolean
                val animated = values[5] as Boolean
                val type = values[6] as ForecastType?
                val unit = values[7] as TemperatureUnit?
                val wUnit = values[8] as WindUnit?
                val transparency = values[9] as Int
                val textSize = values[10] as Int
                val consent = values[11] as String
                val gcu = values[12] as Boolean
                val lastGcu = values[13] as String?
                val lastPrivacy = values[14] as String?
                val hasOpened = values[15] as Boolean
                val useEurAqi = values[16] as Boolean
                        
                UserSettings(
                    model ?: "best_match",
                    round,
                    location ?: LocationIdentifier.CurrentUserLocation,
                    screen ?: DefaultScreen.FORECAST_MAIN,
                    fallback,
                    animated,
                    type ?: ForecastType.DETERMINISTIC,
                    unit ?: TemperatureUnit.CELSIUS,
                    wUnit ?: WindUnit.KPH,
                    transparency,
                    textSize,
                    consent,
                    gcu,
                    lastGcu,
                    lastPrivacy,
                    hasOpened,
                    useEurAqi
                )
            }.collect { settings ->
                _userSettings.value = settings
                // Update telemetry consent
                (applicationContext as? TheMeteo)?.container?.telemetryManager?.setConsentGranted(settings.firebaseConsent == "GRANTED")
                // Trigger widget update on any setting change
                applicationScope.launch {
                    fr.matthstudio.themeteo.widget.WeatherWidget().updateAll(applicationContext)
                    fr.matthstudio.themeteo.widget.DailyWeatherWidget().updateAll(applicationContext)
                }
            }
        }

        // Gère la mise à jour unique de la position GPS
        applicationScope.launch {
            selectedLocation.collect { identifier ->
                if (identifier is LocationIdentifier.CurrentUserLocation) {
                    refreshCurrentLocation()
                } else {
                    _currentGpsPosition.value = null
                }
            }
        }
    }


    /**
     * Met à jour la localisation active pour la récupération des données météo.
     * Cette fonction est maintenant le seul moyen de changer la localisation de l'extérieur.
     */
    fun setCurrentLocation(location: LocationIdentifier) {
        // Met à jour la valeur du StateFlow, ce qui notifiera tous les observateurs.
        _selectedLocation.value = location
    }

    /**
     * Supprime une localisation de la liste des favorites.
     */
    fun removeLocation(location: SavedLocation) {
        CoroutineScope(Dispatchers.IO).launch {
            userLocationsRepository.removeLocation(location)
        }
    }

    /**
     * Ajoute une nouvelle localisation aux favorites.
     */
    fun addLocation(location: SavedLocation) {
        CoroutineScope(Dispatchers.IO).launch {
            userLocationsRepository.addLocation(location)
        }
    }

    /**
     * Met à jour l'ordre des localisations favorites.
     */
    fun reorderLocations(newList: List<SavedLocation>) {
        CoroutineScope(Dispatchers.IO).launch {
            userLocationsRepository.reorderLocations(newList)
        }
    }

    /**
     * Renomme une localisation existante.
     */
    fun renameLocation(location: SavedLocation, newName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            userLocationsRepository.renameLocation(location, newName)
        }
    }

    /**
     * Définition de la position par défaut
     */
    fun setDefaultLocation(location: LocationIdentifier) {
        CoroutineScope(Dispatchers.IO).launch {
            userSettingsRepository.updateDefaultLocation(location)
        }
    }

    private suspend fun resolveCoordinates(identifier: LocationIdentifier): GpsCoordinates? {
        return when (identifier) {
            is LocationIdentifier.CurrentUserLocation -> _currentGpsPosition.value ?: run {
                withTimeoutOrNull(15000) { currentGpsPosition.filterNotNull().first() }
            }
            is LocationIdentifier.Saved -> GpsCoordinates(identifier.location.latitude, identifier.location.longitude)
        }
    }

    private fun getEffectiveModel(currentSettings: UserSettings, coords: GpsCoordinates?): String {
        var effectiveModel = currentSettings.model
        val isEnsembleMode = currentSettings.forecastType == ForecastType.ENSEMBLE
        if (coords != null) {
            val modelInfo = WeatherModelRegistry.getModel(effectiveModel, isEnsembleMode)
            if (!modelInfo.isAvailableAt(coords.latitude, coords.longitude)) {
                effectiveModel = "best_match"
                applicationScope.launch(Dispatchers.IO) {
                    userSettingsRepository.updateModel("best_match")
                    userSettingsRepository.updateForecastType(ForecastType.DETERMINISTIC)
                }
            }
        }
        return effectiveModel
    }

    /**
     * Récupère les données météo horaires.
     */
    fun get(startTime: LocalDateTime, hours: Int): Flow<WeatherDataState> = flow {
        val currentSettings = userSettings.value
        val currentLocationIdentifier = selectedLocation.value
        val coords = resolveCoordinates(currentLocationIdentifier)
        val isEnsembleMode = currentSettings.forecastType == ForecastType.ENSEMBLE
        val effectiveModel = getEffectiveModel(currentSettings, coords)
        
        val maxAllowedDate = LocalDateTime.now(ZoneId.of("UTC"))
            .plusDays(WeatherModelRegistry.getModel(effectiveModel, isEnsembleMode).predictionDays.toLong())
            .toLocalDate()
        var endTime = startTime.plusHours(hours.toLong())
        if (endTime.toLocalDate().isAfter(maxAllowedDate)) {
            endTime = maxAllowedDate.atTime(23, 59)
        }

        // --- 1. Construction de la chaîne de modèles ---
        val modelChain = mutableListOf<String>()
        modelChain.add(effectiveModel)
        
        if (currentSettings.enableModelFallback && !isEnsembleMode) {
            var currentM = WeatherModelRegistry.getModel(effectiveModel, false)
            while (currentM.secondaryModelApiName != null && !modelChain.contains(currentM.secondaryModelApiName)) {
                modelChain.add(currentM.secondaryModelApiName!!)
                currentM = WeatherModelRegistry.getModel(currentM.secondaryModelApiName!!, false)
            }
            // Sécurité : s'assurer que best_match est à la fin si pas déjà présent
            if (!modelChain.contains("best_match")) {
                modelChain.add("best_match")
            }
            if (modelChain.size > 3) Log.w("WeatherCache", "Model chain is too long: $modelChain")
        }

        // --- 2. Récupération depuis le cache et fusion ---
        val cachedDataByModel = mutableMapOf<String, List<AllHourlyVarsReading>>()
        for (model in modelChain) {
            val modelCache = cacheMutex.withLock { 
                cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                     .getOrPut(model) { ModelDataCache() } 
            }
            val data = getHourlyFromCache(modelCache, startTime, endTime)
            if (data != null) cachedDataByModel[model] = data
        }

        var mergedData: List<AllHourlyVarsReading>? = null
        if (cachedDataByModel.containsKey(effectiveModel)) {
            mergedData = cachedDataByModel[effectiveModel]
            // Fusionner avec les modèles secondaires dans l'ordre de la chaîne
            for (i in 1 until modelChain.size) {
                val secondaryModel = modelChain[i]
                val secondaryData = cachedDataByModel[secondaryModel]
                if (mergedData != null && secondaryData != null) {
                    mergedData = mergeHourly(mergedData, secondaryData)
                }
            }
        }

        if (!mergedData.isNullOrEmpty()) {
            emit(WeatherDataState.SuccessHourly(mergedData))
        } else {
            emit(WeatherDataState.Loading)
        }

        // --- 3. Vérification du besoin de mise à jour (Fetch) ---
        var needsFetch = false
        val primaryCache = cacheMutex.withLock { 
            cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                 .getOrPut(effectiveModel) { ModelDataCache() } 
        }
        
        val isDataObsolete = Duration.between(primaryCache.lastFullFetch, LocalDateTime.now()).toHours() >= 1
        val firstDayLoaded = primaryCache.dailyBlocks.firstKeyOrNull()
        val isFirstDayStale = firstDayLoaded != null && firstDayLoaded != LocalDate.now()
        
        if (isDataObsolete || isFirstDayStale) {
            needsFetch = true
        } else {
            // Vérifier si des données sont manquantes dans l'un des modèles de la chaîne
            for (model in modelChain) {
                val modelCache = cacheMutex.withLock { 
                    cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                         .getOrPut(model) { ModelDataCache() } 
                }
                if (checkHourlyMissing(modelCache, startTime.toLocalDate(), endTime.toLocalDate())) {
                    needsFetch = true
                    break
                }
            }
        }

        if (needsFetch) {
            if (coords == null) {
                if (mergedData.isNullOrEmpty()) {
                    emit(WeatherDataState.Error("GPS position not available. Please ensure location permissions are granted."))
                }
                return@flow
            }

            if (isEnsembleMode) {
                val freshData = weatherService.getEnsembleForecast(coords.latitude, coords.longitude, effectiveModel, startTime.toLocalDate(), endTime.toLocalDate())
                if (freshData != null) {
                    updateCache(currentLocationIdentifier, effectiveModel, freshData, isDataObsolete || isFirstDayStale)
                    val primaryData = getHourlyFromCache(primaryCache, startTime, endTime)
                    if (primaryData != null) emit(WeatherDataState.SuccessHourly(primaryData))
                } else if (mergedData.isNullOrEmpty()) {
                    emit(WeatherDataState.Error("Failed to fetch ensemble forecast from $effectiveModel"))
                }
            } else {
                val freshDataMap = weatherService.getForecast(coords.latitude, coords.longitude, modelChain, startTime.toLocalDate(), endDate = endTime.toLocalDate())
                if (freshDataMap != null) {
                    freshDataMap.forEach { (modelName, freshData) ->
                        updateCache(currentLocationIdentifier, modelName, freshData, isDataObsolete || isFirstDayStale)
                    }
                    
                    // Re-récupérer et fusionner après le fetch
                    val finalDataByModel = mutableMapOf<String, List<AllHourlyVarsReading>>()
                    for (model in modelChain) {
                        val mCache = cacheMutex.withLock { 
                            cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                                 .getOrPut(model) { ModelDataCache() } 
                        }
                        getHourlyFromCache(mCache, startTime, endTime)?.let { finalDataByModel[model] = it }
                    }
                    
                    var finalMerged = finalDataByModel[effectiveModel]
                    for (i in 1 until modelChain.size) {
                        val secondaryModel = modelChain[i]
                        val secondaryData = finalDataByModel[secondaryModel]
                        if (finalMerged != null && secondaryData != null) {
                            finalMerged = mergeHourly(finalMerged, secondaryData)
                        }
                    }

                    if (!finalMerged.isNullOrEmpty()) {
                        emit(WeatherDataState.SuccessHourly(finalMerged))
                    } else {
                        emit(WeatherDataState.Error("Weather data unavailable after fetch."))
                    }
                } else if (mergedData.isNullOrEmpty()) {
                    emit(WeatherDataState.Error("Network error: Unable to reach weather service."))
                }
            }
        }
    }

    private fun <K, V> TreeMap<K, V>.firstKeyOrNull(): K? = try { firstKey() } catch (e: Exception) { null }

    private fun checkHourlyMissing(modelCache: ModelDataCache, startDay: LocalDate, endDay: LocalDate): Boolean {
        var currentD = startDay
        while (!currentD.isAfter(endDay)) {
            val block = modelCache.dailyBlocks[currentD]
            if (block == null || block.first.isEmpty()) return true
            currentD = currentD.plusDays(1)
        }
        return false
    }

    private fun checkDailyMissing(modelCache: ModelDataCache, startDay: LocalDate, endDay: LocalDate): Boolean {
        var currentD = startDay
        while (!currentD.isAfter(endDay)) {
            val block = modelCache.dailyBlocks[currentD]
            if (block == null || block.second.sunset == "") return true
            currentD = currentD.plusDays(1)
        }
        return false
    }

    private suspend fun updateCache(identifier: LocationIdentifier, modelName: String, data: Pair<List<AllHourlyVarsReading>, List<DailyReading>>, clearOld: Boolean) {
        cacheMutex.withLock {
            val modelCache = cache.getOrPut(identifier) { mutableMapOf() }.getOrPut(modelName) { ModelDataCache() }
            if (clearOld) modelCache.dailyBlocks.clear()
            
            val hourlyByDate = data.first.groupBy { it.time.toLocalDate() }
            val dailyByDate = data.second.associateBy { it.date }
            (hourlyByDate.keys + dailyByDate.keys).forEach { date ->
                val h = hourlyByDate[date]
                val d = dailyByDate[date]
                if (h != null && d != null) modelCache.dailyBlocks[date] = Pair(h, d)
            }
            modelCache.lastFullFetch = LocalDateTime.now()
        }
    }

    /**
     * Récupère les données météo journalières.
     */
    fun get(date: LocalDate, days: Long): Flow<WeatherDataState> = flow {
        val currentSettings = userSettings.value
        val currentLocationIdentifier = selectedLocation.value
        val coords = resolveCoordinates(currentLocationIdentifier)
        val isEnsembleMode = currentSettings.forecastType == ForecastType.ENSEMBLE
        val effectiveModel = getEffectiveModel(currentSettings, coords)

        val maxAllowedDate = LocalDateTime.now(ZoneId.of("UTC"))
            .plusDays(WeatherModelRegistry.getModel(effectiveModel, isEnsembleMode).predictionDays.toLong())
            .toLocalDate()
        var endDate = date.plusDays(days)
        if (endDate.isAfter(maxAllowedDate)) endDate = maxAllowedDate

        // --- 1. Construction de la chaîne de modèles ---
        val modelChain = mutableListOf<String>()
        modelChain.add(effectiveModel)
        
        if (currentSettings.enableModelFallback && !isEnsembleMode) {
            var currentM = WeatherModelRegistry.getModel(effectiveModel, false)
            while (currentM.secondaryModelApiName != null && !modelChain.contains(currentM.secondaryModelApiName)) {
                modelChain.add(currentM.secondaryModelApiName!!)
                currentM = WeatherModelRegistry.getModel(currentM.secondaryModelApiName!!, false)
            }
            if (!modelChain.contains("best_match")) {
                modelChain.add("best_match")
            }
        }

        // --- 2. Récupération depuis le cache et fusion ---
        val cachedDataByModel = mutableMapOf<String, List<DailyReading>>()
        for (model in modelChain) {
            val modelCache = cacheMutex.withLock { 
                cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                     .getOrPut(model) { ModelDataCache() } 
            }
            val data = getDailyFromCache(modelCache, date, endDate)
            if (data != null) cachedDataByModel[model] = data
        }

        var mergedData: List<DailyReading>? = null
        if (cachedDataByModel.containsKey(effectiveModel)) {
            mergedData = cachedDataByModel[effectiveModel]
            for (i in 1 until modelChain.size) {
                val secondaryModel = modelChain[i]
                val secondaryData = cachedDataByModel[secondaryModel]
                if (mergedData != null && secondaryData != null) {
                    mergedData = mergeDaily(mergedData, secondaryData)
                }
            }
        }

        if (!mergedData.isNullOrEmpty()) {
            emit(WeatherDataState.SuccessDaily(mergedData))
        } else {
            emit(WeatherDataState.Loading)
        }

        // --- 3. Vérification du besoin de mise à jour (Fetch) ---
        var needsFetch = false
        val primaryCache = cacheMutex.withLock { 
            cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                 .getOrPut(effectiveModel) { ModelDataCache() } 
        }
        
        val firstDayLoaded = primaryCache.dailyBlocks.firstKeyOrNull()
        val isFirstDayStale = firstDayLoaded != null && firstDayLoaded != LocalDate.now()
        val isDataObsolete = Duration.between(primaryCache.lastFullFetch, LocalDateTime.now()).toHours() >= 1
        
        if (isDataObsolete || isFirstDayStale) {
            needsFetch = true
        } else {
            for (model in modelChain) {
                val modelCache = cacheMutex.withLock { 
                    cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                         .getOrPut(model) { ModelDataCache() } 
                }
                if (checkDailyMissing(modelCache, date, endDate)) {
                    needsFetch = true
                    break
                }
            }
        }

        if (needsFetch) {
            if (coords == null) {
                if (mergedData.isNullOrEmpty()) {
                    emit(WeatherDataState.Error("GPS position not available."))
                }
                return@flow
            }

            if (isEnsembleMode) {
                val freshData = weatherService.getEnsembleForecast(coords.latitude, coords.longitude, effectiveModel, date, endDate)
                if (freshData != null) {
                    updateCache(currentLocationIdentifier, effectiveModel, freshData, isDataObsolete || isFirstDayStale)
                    val primaryData = getDailyFromCache(primaryCache, date, endDate)
                    if (primaryData != null) emit(WeatherDataState.SuccessDaily(primaryData))
                } else if (mergedData.isNullOrEmpty()) {
                    emit(WeatherDataState.Error("Failed to fetch daily ensemble forecast."))
                }
            } else {
                val freshDataMap = weatherService.getForecast(coords.latitude, coords.longitude, modelChain, date, endDate)
                if (freshDataMap != null) {
                    freshDataMap.forEach { (modelName, freshData) ->
                        updateCache(currentLocationIdentifier, modelName, freshData, isDataObsolete || isFirstDayStale)
                    }
                    
                    val finalDataByModel = mutableMapOf<String, List<DailyReading>>()
                    for (model in modelChain) {
                        val mCache = cacheMutex.withLock { 
                            cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                                 .getOrPut(model) { ModelDataCache() } 
                        }
                        getDailyFromCache(mCache, date, endDate)?.let { finalDataByModel[model] = it }
                    }
                    
                    var finalMerged = finalDataByModel[effectiveModel]
                    for (i in 1 until modelChain.size) {
                        val secondaryModel = modelChain[i]
                        val secondaryData = finalDataByModel[secondaryModel]
                        if (finalMerged != null && secondaryData != null) {
                            finalMerged = mergeDaily(finalMerged, secondaryData)
                        }
                    }

                    if (!finalMerged.isNullOrEmpty()) {
                        emit(WeatherDataState.SuccessDaily(finalMerged))
                    } else {
                        emit(WeatherDataState.Error("Daily weather data unavailable after fetch."))
                    }
                } else if (mergedData.isNullOrEmpty()) {
                    emit(WeatherDataState.Error("Network error during daily forecast fetch."))
                }
            }
        }
    }

    private fun mergeHourly(primary: List<AllHourlyVarsReading>, fallback: List<AllHourlyVarsReading>): List<AllHourlyVarsReading> {
        val fallbackMap = fallback.associateBy { it.time }
        return primary.map { p ->
            val f = fallbackMap[p.time] ?: return@map p
            
            val mergedPrecipitationData = p.precipitationData.copy(
                precipitation = p.precipitationData.precipitation.nanToNull() ?: f.precipitationData.precipitation.nanToNull(),
                precipitationProbability = p.precipitationData.precipitationProbability ?: f.precipitationData.precipitationProbability,
                rain = p.precipitationData.rain.nanToNull() ?: f.precipitationData.rain.nanToNull(),
                snowfall = p.precipitationData.snowfall.nanToNull() ?: f.precipitationData.snowfall.nanToNull(),
                snowDepth = p.precipitationData.snowDepth ?: f.precipitationData.snowDepth
            )

            val mergedWindData = p.wind.copy(
                windDirection = p.wind.windDirection.nanToNull() ?: f.wind.windDirection.nanToNull(),
                windspeed = p.wind.windspeed.nanToNull() ?: f.wind.windspeed.nanToNull(),
                windGusts = p.wind.windGusts.nanToNull() ?: f.wind.windGusts.nanToNull()
            )

            val mergedGhi = p.skyInfo.shortwaveRadiation.nanToNull() ?: f.skyInfo.shortwaveRadiation.nanToNull()
            val mergedDhi = p.skyInfo.diffuseRadiation.nanToNull() ?: f.skyInfo.diffuseRadiation.nanToNull()
            
            val mergedOpacity = if (mergedGhi != null && mergedDhi != null && mergedGhi != 0.0) {
                ((mergedDhi / mergedGhi).coerceIn(0.0, 1.0) * 100.0).toInt()
            } else {
                p.skyInfo.opacity ?: f.skyInfo.opacity
            }

            val mergedSkyInfo = p.skyInfo.copy(
                cloudcoverTotal = p.skyInfo.cloudcoverTotal ?: f.skyInfo.cloudcoverTotal,
                cloudcoverLow = p.skyInfo.cloudcoverLow ?: f.skyInfo.cloudcoverLow,
                cloudcoverMid = p.skyInfo.cloudcoverMid ?: f.skyInfo.cloudcoverMid,
                cloudcoverHigh = p.skyInfo.cloudcoverHigh ?: f.skyInfo.cloudcoverHigh,
                shortwaveRadiation = mergedGhi,
                directRadiation = p.skyInfo.directRadiation.nanToNull() ?: f.skyInfo.directRadiation.nanToNull(),
                diffuseRadiation = mergedDhi,
                opacity = mergedOpacity,
                uvIndex = p.skyInfo.uvIndex ?: f.skyInfo.uvIndex,
                visibility = p.skyInfo.visibility ?: f.skyInfo.visibility
            )

            p.copy(
                temperature = p.temperature.nanToNull() ?: f.temperature.nanToNull(),
                apparentTemperature = p.apparentTemperature.nanToNull() ?: f.apparentTemperature.nanToNull(),
                precipitationData = mergedPrecipitationData,
                skyInfo = mergedSkyInfo,
                wind = mergedWindData,
                dewpoint = p.dewpoint.nanToNull() ?: f.dewpoint.nanToNull(),
                pressure = p.pressure ?: f.pressure,
                humidity = p.humidity ?: f.humidity,
                wmo = p.wmo ?: f.wmo
            )
        }
    }

    private fun mergeDaily(primary: List<DailyReading>, fallback: List<DailyReading>): List<DailyReading> {
        val fallbackMap = fallback.associateBy { it.date }
        return primary.map { p ->
            val f = fallbackMap[p.date] ?: return@map p
            p.copy(
                maxTemperature = p.maxTemperature.nanToNull() ?: f.maxTemperature.nanToNull(),
                minTemperature = p.minTemperature.nanToNull() ?: f.minTemperature.nanToNull(),
                precipitation = p.precipitation.nanToNull() ?: f.precipitation.nanToNull(),
                maxUvIndex = p.maxUvIndex ?: f.maxUvIndex,
                wmo = p.wmo ?: f.wmo,
                sunset = p.sunset.ifEmpty { f.sunset },
                sunrise = p.sunrise.ifEmpty { f.sunrise }
            )
        }
    }

    private fun getHourlyFromCache(modelCache: ModelDataCache, startTime: LocalDateTime, endTime: LocalDateTime): List<AllHourlyVarsReading>? {
        if (modelCache.dailyBlocks.isEmpty()) return null
        // Ensure all time are hours
        val startTime = startTime.withNano(0).withSecond(0).withMinute(0)
        val endTime = endTime.withNano(0).withSecond(0).withMinute(0)
        val requiredDays = modelCache.dailyBlocks.subMap(startTime.toLocalDate(), true, endTime.toLocalDate(), true)
        if (requiredDays.isEmpty() || requiredDays.firstKey() > startTime.toLocalDate() || requiredDays.lastKey() < endTime.toLocalDate()) return null
        val returnValue = requiredDays.values.flatMap { it.first }.filter { !it.time.isBefore(startTime) && it.time.isBefore(endTime) }
        return returnValue
    }

    private fun getDailyFromCache(modelCache: ModelDataCache, startDate: LocalDate, endDate: LocalDate): List<DailyReading>? {
        if (modelCache.dailyBlocks.isEmpty()) return null
        val requiredDays = modelCache.dailyBlocks.subMap(startDate, true, endDate, true)
        if (requiredDays.isEmpty() || requiredDays.firstKey() > startDate || requiredDays.lastKey() < endDate) return null
        return requiredDays.values.map { it.second }.filter { !it.date.isBefore(startDate) && it.date.isBefore(endDate) }
    }

    fun getCurrentWeatherForSavedLocations(): Flow<WeatherDataState> = flow {
        emit(WeatherDataState.Loading)
        val locations = userLocationsRepository.savedLocations.first()
        val currentModel = userSettings.value.model
        val now = LocalDateTime.now()

        val cachedResults = mutableMapOf<Pair<Double, Double>, CurrentWeatherReading>()
        var isCacheIncompleteOrObsolete = false

        for (loc in locations) {
            val identifier = LocationIdentifier.Saved(loc)
            val modelCache = cacheMutex.withLock { cache[identifier]?.get(currentModel) }
            val isExpired = modelCache?.lastFullFetch == null || Duration.between(modelCache.lastFullFetch, now).toMinutes() >= 15
            val cachedReading = modelCache?.currentWeatherReading

            if (cachedReading != null && !isExpired) {
                cachedResults[Pair(loc.latitude, loc.longitude)] = cachedReading
            } else {
                isCacheIncompleteOrObsolete = true
            }
        }

        if (cachedResults.size == locations.size) emit(WeatherDataState.SuccessCurrent(cachedResults))

        if (isCacheIncompleteOrObsolete || cachedResults.size < locations.size) {
            val freshData = weatherService.getCurrentWeather(locations.map { Pair(it.latitude, it.longitude) })
            if (freshData != null) {
                freshData.forEach { (coords, reading) ->
                    val matchingLoc = locations.find { it.latitude == coords.first && it.longitude == coords.second }
                    if (matchingLoc != null) {
                        cacheMutex.withLock {
                            val modelCache = cache.getOrPut(LocationIdentifier.Saved(matchingLoc)) { mutableMapOf() }.getOrPut(currentModel) { ModelDataCache() }
                            modelCache.currentWeatherReading = reading
                            modelCache.lastFullFetch = now
                        }
                    }
                }
                emit(WeatherDataState.SuccessCurrent(freshData))
            } else if (cachedResults.isEmpty()) {
                emit(WeatherDataState.Error("Unable to fetch current weather for saved locations."))
            }
        }
    }

    fun getAirQuality(): Flow<WeatherDataState> = flow {
        val currentLocationIdentifier = selectedLocation.value
        val currentModel = userSettings.value.model
        val now = LocalDateTime.now()

        val modelCache = cacheMutex.withLock { 
            cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(currentModel) { ModelDataCache() } 
        }

        // Emit cache immediately if available, regardless of expiration
        if (modelCache.airQualityInfo != null) {
            emit(WeatherDataState.SuccessAirQuality(Triple(modelCache.airQualityInfo!!, modelCache.airQualityForecast, modelCache.pollenInfo)))
            
            // If it's fresh enough (< 30 minutes), we stop here
            if (Duration.between(modelCache.lastAirQualityFetch, now).toMinutes() < 30) {
                return@flow
            }
        } else {
            // No cache at all, show loading
            emit(WeatherDataState.Loading)
        }

        val coords = resolveCoordinates(currentLocationIdentifier)
        if (coords == null) {
            // If we already emitted cache, don't override it with an error
            if (modelCache.airQualityInfo == null) {
                emit(WeatherDataState.Error("GPS position unavailable for air quality check."))
            }
            return@flow
        }

        coroutineScope {
            val airQualityDeferred = async { weatherService.getAirQuality(coords.latitude, coords.longitude, applicationContext) }
            val airQualityForecastDeferred = async { weatherService.getAirQualityForecast(coords.latitude, coords.longitude, applicationContext) }
            val pollenDeferred = async { weatherService.getPollenForecast(coords.latitude, coords.longitude, applicationContext) }

            val airQualityResponse = airQualityDeferred.await()
            val airQualityForecastResponse = airQualityForecastDeferred.await()
            val pollenResponse = pollenDeferred.await()

            if (airQualityResponse != null || airQualityForecastResponse != null || pollenResponse != null) {
                cacheMutex.withLock {
                    if (airQualityResponse != null) modelCache.airQualityInfo = airQualityResponse
                    if (airQualityForecastResponse != null) modelCache.airQualityForecast = airQualityForecastResponse
                    if (pollenResponse != null) modelCache.pollenInfo = pollenResponse
                    
                    // On ne met à jour la date de rafraîchissement globale que si l'AQI principal est récupéré
                    if (airQualityResponse != null) {
                        modelCache.lastAirQualityFetch = now
                    }
                }
                
                // Return what we have (even if it's a mix of fresh and old data)
                val finalAir = airQualityResponse ?: modelCache.airQualityInfo
                if (finalAir != null) {
                    emit(WeatherDataState.SuccessAirQuality(Triple(finalAir, airQualityForecastResponse ?: modelCache.airQualityForecast, pollenResponse ?: modelCache.pollenInfo)))
                } else if (modelCache.airQualityInfo == null) {
                    emit(WeatherDataState.Error("Failed to fetch primary air quality data."))
                }
            } else if (modelCache.airQualityInfo == null) {
                emit(WeatherDataState.Error("Failed to fetch air quality data."))
            }
        }
    }

    fun getLocalVigilance(): Flow<WeatherDataState> = flow {
        val currentLocationIdentifier = selectedLocation.value
        val currentModel = userSettings.value.model
        val now = LocalDateTime.now()

        val modelCache = cacheMutex.withLock {
            cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(currentModel) { ModelDataCache() }
        }

        // Emit cache immediately if available
        if (modelCache.vigilanceInfo != null) {
            emit(WeatherDataState.SuccessVigilance(modelCache.vigilanceInfo!!))
            
            // If it's fresh enough (< 1 hour), we stop here
            if (Duration.between(modelCache.lastVigilanceFetch, now).toHours() < 1) {
                return@flow
            }
        } else {
            emit(WeatherDataState.Loading)
        }

        val coords = resolveCoordinates(currentLocationIdentifier)
        if (coords == null) {
            if (modelCache.vigilanceInfo == null) {
                emit(WeatherDataState.Error("GPS position unavailable for vigilance alerts."))
            }
            return@flow
        }

        val freshVigilance = weatherService.getVigilanceForLocation(coords.latitude, coords.longitude)
        if (freshVigilance != null) {
            cacheMutex.withLock {
                modelCache.vigilanceInfo = freshVigilance
                modelCache.lastVigilanceFetch = now
            }
            emit(WeatherDataState.SuccessVigilance(freshVigilance))
        } else if (modelCache.vigilanceInfo == null) {
            emit(WeatherDataState.Error("Météo-France vigilance data unavailable."))
        }
    }

    fun getRawCache(): MutableMap<LocationIdentifier, MutableMap<String, ModelDataCache>> = cache

    fun invalidateCache() {
        applicationScope.launch(Dispatchers.IO) {
            cacheMutex.withLock { cache.clear() }
            File(applicationContext.cacheDir, "weather_cache_data.json").delete()
            _selectedLocation.value = _selectedLocation.value
        }
    }

    fun refreshCurrentLocation() {
        _isLocationPermissionGranted.value = locationProvider.checkLocationPermission()
        if (selectedLocation.value is LocationIdentifier.CurrentUserLocation) {
            applicationScope.launch {
                val newCoordinates = locationProvider.getCurrentLocation()
                if (newCoordinates != null) _currentGpsPosition.value = newCoordinates
            }
        }
    }
}

sealed class WeatherDataState {
    object Loading : WeatherDataState()
    data class SuccessHourly(val data: List<AllHourlyVarsReading>) : WeatherDataState()
    data class SuccessDaily(val data: List<DailyReading>) : WeatherDataState()
    data class SuccessCurrent(val data: Map<Pair<Double, Double>, CurrentWeatherReading>) : WeatherDataState()
    data class SuccessAirQuality(val data: Triple<AirQualityInfo, AirQualityForecastResponse?, PollenResponse?>) : WeatherDataState()
    data class SuccessVigilance(val data: VigilanceInfos) : WeatherDataState()
    data class Error(val message: String) : WeatherDataState()
}