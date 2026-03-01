package fr.matthstudio.themeteo

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import android.os.PowerManager
import android.util.Log
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.LocalDateSerializer
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.data.LocationProvider
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.UserLocationsRepository
import fr.matthstudio.themeteo.data.UserSettingsRepository
import fr.matthstudio.themeteo.data.WeatherModelRegistry
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
    val forecastType: ForecastType
)

private data class LocationKey(val latitude: Double, val longitude: Double)

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
    var pollenInfo: PollenResponse? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    var lastAirQualityFetch: LocalDateTime = LocalDateTime.MIN,

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
    private val _userSettings = MutableStateFlow(UserSettings("best_match", true, LocationIdentifier.CurrentUserLocation, DefaultScreen.FORECAST_MAIN, true, true, ForecastType.DETERMINISTIC))
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
                userSettingsRepository.forecastType
            ) { values ->
                val model = values[0] as String?
                val round = values[1] as Boolean
                val location = values[2] as LocationIdentifier?
                val screen = values[3] as DefaultScreen?
                val fallback = values[4] as Boolean
                val animated = values[5] as Boolean
                val type = values[6] as ForecastType?
                        
                UserSettings(
                    model ?: "best_match",
                    round,
                    location ?: LocationIdentifier.CurrentUserLocation,
                    screen ?: DefaultScreen.FORECAST_MAIN,
                    fallback,
                    animated,
                    type ?: ForecastType.DETERMINISTIC
                )
            }.collect { settings ->
                _userSettings.value = settings
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

    private suspend fun getEffectiveModel(currentSettings: UserSettings, coords: GpsCoordinates?): String {
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

        val primaryCache = cacheMutex.withLock { 
            cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                 .getOrPut(effectiveModel) { ModelDataCache() } 
        }

        // 1. Cache retrieval
        var primaryData = getHourlyFromCache(primaryCache, startTime, endTime)
        var fallbackData: List<AllHourlyVarsReading>? = null
        if (currentSettings.enableModelFallback && effectiveModel != "best_match" && !isEnsembleMode) {
            val fallbackCache = cacheMutex.withLock { 
                cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                     .getOrPut("best_match") { ModelDataCache() } 
            }
            fallbackData = getHourlyFromCache(fallbackCache, startTime, endTime)
        }

        if (!primaryData.isNullOrEmpty()) {
            emit(WeatherDataState.SuccessHourly(if (fallbackData != null) mergeHourly(primaryData, fallbackData) else primaryData))
        } else {
            emit(WeatherDataState.Loading)
        }

        // 2. Fetch necessity
        val isDataObsolete = Duration.between(primaryCache.lastFullFetch, LocalDateTime.now()).toHours() >= 1
        val isAnyHourlyMissing = checkHourlyMissing(primaryCache, startTime.toLocalDate(), endTime.toLocalDate())
        
        var isFallbackMissing = false
        if (currentSettings.enableModelFallback && effectiveModel != "best_match" && !isEnsembleMode) {
            val fallbackCache = cacheMutex.withLock { cache[currentLocationIdentifier]?.get("best_match") ?: ModelDataCache() }
            isFallbackMissing = checkHourlyMissing(fallbackCache, startTime.toLocalDate(), endTime.toLocalDate())
        }

        val firstDayLoaded = primaryCache.dailyBlocks.firstKeyOrNull()
        val isFirstDayStale = firstDayLoaded != null && firstDayLoaded != LocalDate.now()
        val needsFetch = isDataObsolete || isAnyHourlyMissing || isFallbackMissing || isFirstDayStale

        if (needsFetch) {
            if (coords == null) {
                emit(WeatherDataState.Error("GPS position not available. Please ensure location permissions are granted."))
                return@flow
            }

            if (isEnsembleMode) {
                val freshData = weatherService.getEnsembleForecast(coords.latitude, coords.longitude, effectiveModel, startTime.toLocalDate(), endTime.toLocalDate())
                if (freshData != null) {
                    updateCache(currentLocationIdentifier, effectiveModel, freshData, isDataObsolete || isFirstDayStale)
                    primaryData = getHourlyFromCache(primaryCache, startTime, endTime)
                    if (primaryData != null) emit(WeatherDataState.SuccessHourly(primaryData))
                } else {
                    emit(WeatherDataState.Error("Failed to fetch ensemble forecast from $effectiveModel"))
                }
            } else {
                val modelsToFetch = mutableListOf(effectiveModel)
                if (currentSettings.enableModelFallback && effectiveModel != "best_match") modelsToFetch.add("best_match")

                val freshDataMap = weatherService.getForecast(coords.latitude, coords.longitude, modelsToFetch, startTime.toLocalDate(), endTime.toLocalDate())
                if (freshDataMap != null) {
                    freshDataMap.forEach { (modelName, freshData) ->
                        updateCache(currentLocationIdentifier, modelName, freshData, isDataObsolete || isFirstDayStale)
                    }
                    primaryData = getHourlyFromCache(primaryCache, startTime, endTime)
                    if (currentSettings.enableModelFallback && effectiveModel != "best_match") {
                        val fbCache = cacheMutex.withLock { cache[currentLocationIdentifier]?.get("best_match") ?: ModelDataCache() }
                        fallbackData = getHourlyFromCache(fbCache, startTime, endTime)
                    }
                    if (!primaryData.isNullOrEmpty()) {
                        emit(WeatherDataState.SuccessHourly(if (fallbackData != null) mergeHourly(primaryData, fallbackData) else primaryData))
                    } else {
                        emit(WeatherDataState.Error("Weather data unavailable after fetch."))
                    }
                } else {
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

        val primaryCache = cacheMutex.withLock { 
            cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                 .getOrPut(effectiveModel) { ModelDataCache() } 
        }

        var primaryData = getDailyFromCache(primaryCache, date, endDate)
        var fallbackData: List<DailyReading>? = null
        if (currentSettings.enableModelFallback && effectiveModel != "best_match" && !isEnsembleMode) {
            val fallbackCache = cacheMutex.withLock { 
                cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
                     .getOrPut("best_match") { ModelDataCache() } 
            }
            fallbackData = getDailyFromCache(fallbackCache, date, endDate)
        }

        if (!primaryData.isNullOrEmpty()) {
            emit(WeatherDataState.SuccessDaily(if (fallbackData != null) mergeDaily(primaryData, fallbackData) else primaryData))
        } else {
            emit(WeatherDataState.Loading)
        }

        val firstDayLoaded = primaryCache.dailyBlocks.firstKeyOrNull()
        val isFirstDayStale = firstDayLoaded != null && firstDayLoaded != LocalDate.now()
        val isDataObsolete = Duration.between(primaryCache.lastFullFetch, LocalDateTime.now()).toHours() >= 1
        val isAnyDailyMissing = checkDailyMissing(primaryCache, date, endDate)
        
        var isFallbackMissing = false
        if (currentSettings.enableModelFallback && effectiveModel != "best_match" && !isEnsembleMode) {
            val fallbackCache = cacheMutex.withLock { cache[currentLocationIdentifier]?.get("best_match") ?: ModelDataCache() }
            isFallbackMissing = checkDailyMissing(fallbackCache, date, endDate)
        }

        val needsFetch = isDataObsolete || isAnyDailyMissing || isFallbackMissing || isFirstDayStale

        if (needsFetch) {
            if (coords == null) {
                emit(WeatherDataState.Error("GPS position not available."))
                return@flow
            }

            if (isEnsembleMode) {
                val freshData = weatherService.getEnsembleForecast(coords.latitude, coords.longitude, effectiveModel, date, endDate)
                if (freshData != null) {
                    updateCache(currentLocationIdentifier, effectiveModel, freshData, isDataObsolete || isFirstDayStale)
                    primaryData = getDailyFromCache(primaryCache, date, endDate)
                    if (primaryData != null) emit(WeatherDataState.SuccessDaily(primaryData))
                } else {
                    emit(WeatherDataState.Error("Failed to fetch daily ensemble forecast."))
                }
            } else {
                val modelsToFetch = mutableListOf(effectiveModel)
                if (currentSettings.enableModelFallback && effectiveModel != "best_match") modelsToFetch.add("best_match")

                val freshDataMap = weatherService.getForecast(coords.latitude, coords.longitude, modelsToFetch, date, endDate)
                if (freshDataMap != null) {
                    freshDataMap.forEach { (modelName, freshData) ->
                        updateCache(currentLocationIdentifier, modelName, freshData, isDataObsolete || isFirstDayStale)
                    }
                    primaryData = getDailyFromCache(primaryCache, date, endDate)
                    if (currentSettings.enableModelFallback && effectiveModel != "best_match") {
                        val fbCache = cacheMutex.withLock { cache[currentLocationIdentifier]?.get("best_match") ?: ModelDataCache() }
                        fallbackData = getDailyFromCache(fbCache, date, endDate)
                    }
                    if (!primaryData.isNullOrEmpty()) {
                        emit(WeatherDataState.SuccessDaily(if (fallbackData != null) mergeDaily(primaryData, fallbackData) else primaryData))
                    } else {
                        emit(WeatherDataState.Error("Daily weather data unavailable after fetch."))
                    }
                } else {
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
                rain = (if (p.precipitationData.rain.nanToNull() == null || (p.precipitationData.rain == 0.0 && f.precipitationData.rain != 0.0 && f.precipitationData.rain.nanToNull() != null)) f.precipitationData.rain else p.precipitationData.rain).nanToNull(),
                snowfall = (if (p.precipitationData.snowfall.nanToNull() == null || (p.precipitationData.snowfall == 0.0 && f.precipitationData.snowfall != 0.0 && f.precipitationData.snowfall.nanToNull() != null)) f.precipitationData.snowfall else p.precipitationData.snowfall).nanToNull(),
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
                cloudcoverTotal = if ((p.skyInfo.cloudcoverTotal == 0) && f.skyInfo.cloudcoverTotal != 0) f.skyInfo.cloudcoverTotal else p.skyInfo.cloudcoverTotal,
                cloudcoverLow = if ((p.skyInfo.cloudcoverLow == 0) && f.skyInfo.cloudcoverLow != 0) f.skyInfo.cloudcoverLow else p.skyInfo.cloudcoverLow,
                cloudcoverMid = if ((p.skyInfo.cloudcoverMid == 0) && f.skyInfo.cloudcoverMid != 0) f.skyInfo.cloudcoverMid else p.skyInfo.cloudcoverMid,
                cloudcoverHigh = if ((p.skyInfo.cloudcoverHigh == 0) && f.skyInfo.cloudcoverHigh != 0) f.skyInfo.cloudcoverHigh else p.skyInfo.cloudcoverHigh,
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
                pressure = if (p.pressure == 0) f.pressure else p.pressure,
                humidity = if (p.humidity == 0) f.humidity else p.humidity,
                wmo = if (p.wmo == 0 && f.wmo != 0) f.wmo else p.wmo
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
                wmo = if (p.wmo == 0 && f.wmo != 0) f.wmo else p.wmo,
                sunset = p.sunset.ifEmpty { f.sunset },
                sunrise = p.sunrise.ifEmpty { f.sunrise }
            )
        }
    }

    private fun getHourlyFromCache(modelCache: ModelDataCache, startTime: LocalDateTime, endTime: LocalDateTime): List<AllHourlyVarsReading>? {
        if (modelCache.dailyBlocks.isEmpty()) return null
        val requiredDays = modelCache.dailyBlocks.subMap(startTime.toLocalDate(), true, endTime.toLocalDate(), true)
        if (requiredDays.isEmpty() || requiredDays.firstKey() > startTime.toLocalDate() || requiredDays.lastKey() < endTime.toLocalDate()) return null
        return requiredDays.values.flatMap { it.first }.filter { !it.time.isBefore(startTime) && it.time.isBefore(endTime) }
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

        if (modelCache.airQualityInfo != null && Duration.between(modelCache.lastAirQualityFetch, now).toMinutes() < 30) {
            emit(WeatherDataState.SuccessAirQuality(Pair(modelCache.airQualityInfo!!, modelCache.pollenInfo)))
            return@flow
        }

        emit(WeatherDataState.Loading)
        val coords = resolveCoordinates(currentLocationIdentifier)
        if (coords == null) {
            emit(WeatherDataState.Error("GPS position unavailable for air quality check."))
            return@flow
        }

        coroutineScope {
            val airQualityDeferred = async { weatherService.getAirQuality(coords.latitude, coords.longitude, applicationContext) }
            val pollenDeferred = async { weatherService.getPollenForecast(coords.latitude, coords.longitude, applicationContext) }

            val airQualityResponse = airQualityDeferred.await()
            val pollenResponse = pollenDeferred.await()

            if (airQualityResponse != null) {
                cacheMutex.withLock {
                    modelCache.airQualityInfo = airQualityResponse
                    modelCache.pollenInfo = pollenResponse
                    modelCache.lastAirQualityFetch = now
                }
                emit(WeatherDataState.SuccessAirQuality(Pair(airQualityResponse, pollenResponse)))
            } else if (modelCache.airQualityInfo != null) {
                emit(WeatherDataState.SuccessAirQuality(Pair(modelCache.airQualityInfo!!, modelCache.pollenInfo)))
            } else {
                emit(WeatherDataState.Error("Failed to fetch air quality data."))
            }
        }
    }

    fun getLocalVigilance(): Flow<WeatherDataState> = flow {
        emit(WeatherDataState.Loading)
        val coords = resolveCoordinates(selectedLocation.value)
        if (coords == null) {
            emit(WeatherDataState.Error("GPS position unavailable for vigilance alerts."))
            return@flow
        }

        val vigilance = weatherService.getVigilanceForLocation(coords.latitude, coords.longitude)
        if (vigilance != null) {
            emit(WeatherDataState.SuccessVigilance(vigilance))
        } else {
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
    data class SuccessAirQuality(val data: Pair<AirQualityInfo, PollenResponse?>) : WeatherDataState()
    data class SuccessVigilance(val data: VigilanceInfos) : WeatherDataState()
    data class Error(val message: String) : WeatherDataState()
}