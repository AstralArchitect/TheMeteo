package fr.matthstudio.themeteo

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import android.os.PowerManager
import android.util.Log
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.LocalDateSerializer
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.data.LocationProvider
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.UserLocationsRepository
import fr.matthstudio.themeteo.data.UserSettingsRepository
import fr.matthstudio.themeteo.dayChoserActivity.weatherModelPredictionTime
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
    val enableAnimatedIcons: Boolean
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
    private val weatherService = WeatherService()

    // --- StateFlows pour les settings et la localisation sélectionnée ---
    private val _userSettings = MutableStateFlow(UserSettings("best_match", true, LocationIdentifier.CurrentUserLocation, DefaultScreen.FORECAST_MAIN, true, true))
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
                userSettingsRepository.enableAnimatedIcons
            ) { values ->
                val model = values[0] as String?
                val round = values[1] as Boolean
                val location = values[2] as LocationIdentifier?
                val screen = values[3] as DefaultScreen?
                val fallback = values[4] as Boolean
                val animated = values[5] as Boolean
                        
                UserSettings(
                    model ?: "best_match",
                    round,
                    location ?: LocationIdentifier.CurrentUserLocation,
                    screen ?: DefaultScreen.FORECAST_MAIN,
                    fallback,
                    animated
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
     * Définition de la position par défaut
     */
    fun setDefaultLocation(location: LocationIdentifier) {
        CoroutineScope(Dispatchers.IO).launch {
            userSettingsRepository.updateDefaultLocation(location)
        }
    }

    /**
     * Récupère les données météo. La fonction est asynchrone et retourne un Flow d'états.
     * @param startTime La date et l'heure de début de la période.
     * @param hours Le nombre d'heures à récupérer.
     */
    fun get(
        startTime: LocalDateTime,
        hours: Int
    ): Flow<WeatherDataState> = flow {
        val currentSettings = userSettings.value
        val currentLocationIdentifier = selectedLocation.value

        val maxAllowedDate = LocalDate.now().plusDays((weatherModelPredictionTime[userSettings.value.model]?.toLong() ?: 3) - 1)
        var endTime = startTime.plusHours(hours.toLong())

        // Si l'heure de fin dépasse la date max autorisée par l'API
        if (endTime.toLocalDate().isAfter(maxAllowedDate)) {
            endTime = maxAllowedDate.atTime(23, 59)
        }

        val primaryCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(currentSettings.model) { ModelDataCache() }

        // 1. Tentative de récupération depuis le cache (Modèle primaire)
        var primaryData = getHourlyFromCache(primaryCache, startTime, endTime)

        // 2. Gestion du fallback (Meilleur Modèle)
        var fallbackData: List<AllHourlyVarsReading>? = null
        if (currentSettings.enableModelFallback && currentSettings.model != "best_match") {
            val fallbackCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut("best_match") { ModelDataCache() }
            fallbackData = getHourlyFromCache(fallbackCache, startTime, endTime)
        }

        // Émission initiale si on a des données (éventuellement fusionnées)
        if (!primaryData.isNullOrEmpty()) {
            if (fallbackData != null) {
                emit(WeatherDataState.SuccessHourly(mergeHourly(primaryData, fallbackData)))
            } else {
                emit(WeatherDataState.SuccessHourly(primaryData))
            }
        } else {
            emit(WeatherDataState.Loading)
        }

        // 3. Calcul de la nécessité de fetch
        val isDataObsolete = Duration.between(primaryCache.lastFullFetch, LocalDateTime.now()).toHours() >= 1

        val startDay = startTime.toLocalDate()
        val endDay = endTime.toLocalDate()
        var isAnyHourlyMissing = false
        var currentD = startDay
        while (!currentD.isAfter(endDay)) {
            val block = primaryCache.dailyBlocks[currentD]
            if (block == null || block.first.isEmpty()) {
                isAnyHourlyMissing = true
                break
            }
            currentD = currentD.plusDays(1)
        }

        // Si fallback activé, on vérifie aussi s'il manque des données au fallback
        var isFallbackMissing = false
        if (currentSettings.enableModelFallback && currentSettings.model != "best_match") {
            val fallbackCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut("best_match") { ModelDataCache() }
            var d = startDay
            while (!d.isAfter(endDay)) {
                val block = fallbackCache.dailyBlocks[d]
                if (block == null || block.first.isEmpty()) {
                    isFallbackMissing = true
                    break
                }
                d = d.plusDays(1)
            }
        }

        val firstDayLoaded = primaryCache.dailyBlocks.firstEntry()?.key
        val needsFetch = isDataObsolete || isAnyHourlyMissing || isFallbackMissing ||
                (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))

        // 4. Exécution du fetch si nécessaire
        if (needsFetch) {
            if (primaryData.isNullOrEmpty()) {
                emit(WeatherDataState.Loading)
            }

            val locationKey = when (currentLocationIdentifier) {
                is LocationIdentifier.CurrentUserLocation -> {
                    val gpsPos = _currentGpsPosition.value ?: run {
                        emit(WeatherDataState.Loading)
                        withTimeoutOrNull(15000) {
                            currentGpsPosition.filterNotNull().first()
                        }
                    }

                    if (gpsPos != null) {
                        LocationKey(gpsPos.latitude, gpsPos.longitude)
                    } else {
                        emit(WeatherDataState.Error("Cannot get GPS position, please ensure that you authorized the app"))
                        return@flow
                    }
                }
                is LocationIdentifier.Saved -> LocationKey(
                    currentLocationIdentifier.location.latitude,
                    currentLocationIdentifier.location.longitude
                )
            }

            val modelsToFetch = mutableListOf(currentSettings.model)
            if (currentSettings.enableModelFallback && currentSettings.model != "best_match") {
                modelsToFetch.add("best_match")
            }

            val freshDataMap = weatherService.getForecast(
                locationKey.latitude, locationKey.longitude,
                modelsToFetch, startTime.toLocalDate(), endTime.toLocalDate()
            )

            if (freshDataMap != null) {
                // Mise à jour de chaque modèle dans le cache
                freshDataMap.forEach { (modelName, freshData) ->
                    val modelCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(modelName) { ModelDataCache() }

                    if (isDataObsolete || (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))) {
                        modelCache.dailyBlocks.clear()
                    }

                    val hourlyByDate = freshData.first.groupBy { it.time.toLocalDate() }
                    val dailyByDate = freshData.second.associateBy { it.date }
                    val allDates = hourlyByDate.keys + dailyByDate.keys

                    allDates.forEach { date ->
                        val hourly = hourlyByDate[date]
                        val daily = dailyByDate[date]
                        if (hourly == null || daily == null){
                            emit(WeatherDataState.Error(""))
                            return@flow
                        }
                        modelCache.dailyBlocks[date] = Pair(hourly, daily)
                    }
                    modelCache.lastFullFetch = LocalDateTime.now()
                }

                // Ré-extraction et fusion finale
                primaryData = getHourlyFromCache(primaryCache, startTime, endTime)
                if (currentSettings.enableModelFallback && currentSettings.model != "best_match") {
                    val fbCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut("best_match") { ModelDataCache() }
                    fallbackData = getHourlyFromCache(fbCache, startTime, endTime)
                }

                if (!primaryData.isNullOrEmpty()) {
                    if (fallbackData != null) {
                        emit(WeatherDataState.SuccessHourly(mergeHourly(primaryData, fallbackData)))
                    } else {
                        emit(WeatherDataState.SuccessHourly(primaryData))
                    }
                } else {
                    emit(WeatherDataState.Error("Error"))
                }
            } else if (primaryCache.dailyBlocks.isEmpty()) {
                emit(WeatherDataState.Error("Error"))
            }
        }
    }

    private fun mergeHourly(primary: List<AllHourlyVarsReading>, fallback: List<AllHourlyVarsReading>): List<AllHourlyVarsReading> {
        val fallbackMap = fallback.associateBy { it.time }
        return primary.map { p ->
            val f = fallbackMap[p.time] ?: return@map p
            
            val mergedPrecipitationData = p.precipitationData.copy(
                precipitation = if (p.precipitationData.precipitation.isNaN()) f.precipitationData.precipitation else p.precipitationData.precipitation,
                precipitationProbability = p.precipitationData.precipitationProbability ?: f.precipitationData.precipitationProbability,
                rain = if (p.precipitationData.rain == 0.0 && f.precipitationData.rain != 0.0) f.precipitationData.rain else p.precipitationData.rain,
                snowfall = if (p.precipitationData.snowfall == 0.0 && f.precipitationData.snowfall != 0.0) f.precipitationData.snowfall else p.precipitationData.snowfall,
                snowDepth = p.precipitationData.snowDepth ?: f.precipitationData.snowDepth
            )

            val mergedWindData = p.wind.copy(
                windDirection = if (p.wind.windDirection.isNaN()) f.wind.windDirection else p.wind.windDirection,
                windspeed = if (p.wind.windspeed.isNaN()) f.wind.windspeed else p.wind.windspeed,
                windGusts = if (p.wind.windGusts.isNaN()) f.wind.windGusts else p.wind.windGusts
            )

            val mergedGhi = p.skyInfo.shortwaveRadiation ?: f.skyInfo.shortwaveRadiation
            val mergedDhi = p.skyInfo.diffuseRadiation ?: f.skyInfo.diffuseRadiation
            
            // Recalculer l'opacité si on a fusionné les radiations
            val mergedOpacity = if (mergedGhi != null && mergedDhi != null) {
                (kotlin.math.max(0.0, kotlin.math.min(1.0, mergedDhi / mergedGhi)) * 100.0).toInt()
            } else {
                p.skyInfo.opacity ?: f.skyInfo.opacity
            }

            val mergedSkyInfo = p.skyInfo.copy(
                cloudcoverTotal = if ((p.skyInfo.cloudcoverTotal == 0) && f.skyInfo.cloudcoverTotal != 0) f.skyInfo.cloudcoverTotal else p.skyInfo.cloudcoverTotal,
                cloudcoverLow = if ((p.skyInfo.cloudcoverLow == 0) && f.skyInfo.cloudcoverLow != 0) f.skyInfo.cloudcoverLow else p.skyInfo.cloudcoverLow,
                cloudcoverMid = if ((p.skyInfo.cloudcoverMid == 0) && f.skyInfo.cloudcoverMid != 0) f.skyInfo.cloudcoverMid else p.skyInfo.cloudcoverMid,
                cloudcoverHigh = if ((p.skyInfo.cloudcoverHigh == 0) && f.skyInfo.cloudcoverHigh != 0) f.skyInfo.cloudcoverHigh else p.skyInfo.cloudcoverHigh,
                shortwaveRadiation = mergedGhi,
                directRadiation = p.skyInfo.directRadiation ?: f.skyInfo.directRadiation,
                diffuseRadiation = mergedDhi,
                opacity = mergedOpacity,
                uvIndex = p.skyInfo.uvIndex ?: f.skyInfo.uvIndex,
                visibility = p.skyInfo.visibility ?: f.skyInfo.visibility
            )

            p.copy(
                temperature = if (p.temperature.isNaN()) f.temperature else p.temperature,
                apparentTemperature = p.apparentTemperature ?: f.apparentTemperature,
                precipitationData = mergedPrecipitationData,
                skyInfo = mergedSkyInfo,
                wind = mergedWindData,
                dewpoint = if (p.dewpoint.isNaN()) f.dewpoint else p.dewpoint,
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
                maxTemperature = if (p.maxTemperature.isNaN()) f.maxTemperature else p.maxTemperature,
                minTemperature = if (p.minTemperature.isNaN()) f.minTemperature else p.minTemperature,
                precipitation = if (p.precipitation.isNaN()) f.precipitation else p.precipitation,
                maxUvIndex = p.maxUvIndex ?: f.maxUvIndex,
                wmo = if (p.wmo == 0 && f.wmo != 0) f.wmo else p.wmo,
                sunset = if (p.sunset.isEmpty()) f.sunset else p.sunset,
                sunrise = if (p.sunrise.isEmpty()) f.sunrise else p.sunrise
            )
        }
    }

    /**
     * Récupère les données météo. La fonction est asynchrone et retourne un Flow d'états.
     * @param date La date de début de la période.
     * @param days Le nombre de jours à récupérer.
     */
    fun get(
        date: LocalDate,
        days: Long
    ): Flow<WeatherDataState> = flow {
        val currentSettings = userSettings.value
        val currentLocationIdentifier = selectedLocation.value

        val primaryCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(currentSettings.model) { ModelDataCache() }
        // CALCUL SÉCURISÉ
        val maxAllowedDate = LocalDate.now().plusDays(15)
        var endDate = date.plusDays(days)

        if (endDate.isAfter(maxAllowedDate)) {
            endDate = maxAllowedDate.plusDays(1) // subMap est souvent exclusif sur la fin ou inclut selon votre logique,
            // ici on s'assure de ne pas demander au service au delà de maxAllowedDate
        }

        // 1. Tentative de récupération depuis le cache (Modèle primaire)
        var primaryData = getDailyFromCache(primaryCache, date, endDate)

        // 2. Gestion du fallback (Meilleur Modèle)
        var fallbackData: List<DailyReading>? = null
        if (currentSettings.enableModelFallback && currentSettings.model != "best_match") {
            val fallbackCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut("best_match") { ModelDataCache() }
            fallbackData = getDailyFromCache(fallbackCache, date, endDate)
        }

        // Émission initiale si on a des données (éventuellement fusionnées)
        if (primaryData != null && primaryData.isNotEmpty()) {
            if (fallbackData != null) {
                emit(WeatherDataState.SuccessDaily(mergeDaily(primaryData, fallbackData)))
            } else {
                emit(WeatherDataState.SuccessDaily(primaryData))
            }
        } else {
            emit(WeatherDataState.Loading)
        }

        // 3. Calcul de la nécessité de fetch
        val isDataObsolete = Duration.between(primaryCache.lastFullFetch, LocalDateTime.now()).toHours() >= 1

        var isAnyDailyMissing = false
        var currentD = date
        while (currentD.isBefore(endDate)) {
            val block = primaryCache.dailyBlocks[currentD]
            // Un bloc est incomplet pour le "Daily" si le weatherCode est vide (dummy object)
            if (block == null || block.second.sunset == "") {
                isAnyDailyMissing = true
                break
            }
            currentD = currentD.plusDays(1)
        }

        // Si fallback activé, on vérifie aussi s'il manque des données au fallback
        var isFallbackMissing = false
        if (currentSettings.enableModelFallback && currentSettings.model != "best_match") {
            val fallbackCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut("best_match") { ModelDataCache() }
            var d = date
            while (d.isBefore(endDate)) {
                val block = fallbackCache.dailyBlocks[d]
                if (block == null || block.second.sunset == "") {
                    isFallbackMissing = true
                    break
                }
                d = d.plusDays(1)
            }
        }

        val firstDayLoaded = primaryCache.dailyBlocks.firstEntry()?.key
        val needsFetch = isDataObsolete || isAnyDailyMissing || isFallbackMissing ||
                (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))

        // 4. Exécution du fetch si nécessaire
        if (needsFetch) {
            if (primaryData == null || primaryData.isEmpty()) {
                emit(WeatherDataState.Loading)
            }
            val locationKey = when (currentLocationIdentifier) {
                is LocationIdentifier.CurrentUserLocation -> {
                    val gpsPos = _currentGpsPosition.value ?: run {
                        emit(WeatherDataState.Loading)
                        withTimeoutOrNull(15000) {
                            currentGpsPosition.filterNotNull().first()
                        }
                    }

                    if (gpsPos != null) {
                        LocationKey(gpsPos.latitude, gpsPos.longitude)
                    } else {
                        emit(WeatherDataState.Error("Cannot get GPS position, please ensure that you athorized the app"))
                        return@flow
                    }
                }
                is LocationIdentifier.Saved -> LocationKey(
                    currentLocationIdentifier.location.latitude,
                    currentLocationIdentifier.location.longitude
                )
            }

            val modelsToFetch = mutableListOf(currentSettings.model)
            if (currentSettings.enableModelFallback && currentSettings.model != "best_match") {
                modelsToFetch.add("best_match")
            }

            val freshDataMap = weatherService.getForecast(
                locationKey.latitude, locationKey.longitude,
                modelsToFetch, date, endDate
            )

            if (freshDataMap != null) {
                // Mise à jour de chaque modèle dans le cache
                freshDataMap.forEach { (modelName, freshData) ->
                    val modelCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(modelName) { ModelDataCache() }

                    if (isDataObsolete || (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))) {
                        modelCache.dailyBlocks.clear()
                    }

                    // Mise à jour atomique du cache (Hourly + Daily)
                    val hourlyByDate = freshData.first.groupBy { it.time.toLocalDate() }
                    val dailyByDate = freshData.second.associateBy { it.date }

                    // On récupère toutes les dates uniques des deux listes
                    val allDates = hourlyByDate.keys + dailyByDate.keys

                    allDates.forEach { date ->
                        val hourly = hourlyByDate[date]
                        val daily = dailyByDate[date]
                        if (hourly == null || daily == null) {
                            emit(WeatherDataState.Error(""))
                            return@flow
                        }
                        modelCache.dailyBlocks[date] = Pair(hourly, daily)
                    }
                    modelCache.lastFullFetch = LocalDateTime.now()
                }

                // Ré-extraction et fusion finale
                primaryData = getDailyFromCache(primaryCache, date, endDate)
                if (currentSettings.enableModelFallback && currentSettings.model != "best_match") {
                    val fbCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut("best_match") { ModelDataCache() }
                    fallbackData = getDailyFromCache(fbCache, date, endDate)
                }

                if (primaryData != null && primaryData.isNotEmpty()) {
                    if (fallbackData != null) {
                        emit(WeatherDataState.SuccessDaily(mergeDaily(primaryData, fallbackData)))
                    } else {
                        emit(WeatherDataState.SuccessDaily(primaryData))
                    }
                } else {
                    emit(WeatherDataState.Error("Erreur"))
                }
            } else if (primaryCache.dailyBlocks.isEmpty()) {
                emit(WeatherDataState.Error("Erreur"))
            }
        }
    }

    /**
     * Fonctions helper privées pour extraire les données du cache et vérifier leur complétude.
     * Retourne null si les données sont incomplètes pour la période demandée.
     */
    private fun getHourlyFromCache(
        modelCache: ModelDataCache,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<AllHourlyVarsReading>? {
        if (modelCache.dailyBlocks.isEmpty()) return null

        val requiredDays = modelCache.dailyBlocks.subMap(startTime.toLocalDate(), true, endTime.toLocalDate(), true)

        // Vérification de la couverture des jours
        val startDay = startTime.toLocalDate()
        val endDay = endTime.toLocalDate()
        if (requiredDays.isEmpty() || requiredDays.firstKey() > startDay || requiredDays.lastKey() < endDay) {
            return null // Données manquantes
        }

        val consolidatedData = requiredDays.values.flatMap { it.first }
        val filteredData = consolidatedData.filter {
            !it.time.isBefore(startTime) && it.time.isBefore(endTime)
        }

        // On retourne les données seulement si on a le nombre exact d'heures demandées
        return filteredData
    }

    private fun getDailyFromCache(
        modelCache: ModelDataCache,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyReading>? {
        if (modelCache.dailyBlocks.isEmpty()) return null

        val requiredDays = modelCache.dailyBlocks.subMap(startDate, true, endDate, true)

        // Vérification de la couverture des jours
        val startDay = startDate
        if (requiredDays.isEmpty() || requiredDays.firstKey() > startDay || requiredDays.lastKey() < endDate) {
            return null // Données manquantes
        }

        val consolidatedData = requiredDays.values.map { it.second }
        val filteredData = consolidatedData.filter {
            !it.date.isBefore(startDate) && it.date.isBefore(endDate)
        }

        return filteredData
    }

    fun getCurrentWeatherForSavedLocations(): Flow<WeatherDataState> = flow {
        emit(WeatherDataState.Loading)

        val locations = userLocationsRepository.savedLocations.first()
        val currentModel = userSettings.value.model
        val now = LocalDateTime.now()

        // 1. Préparation des données du cache
        val cachedResults = mutableMapOf<Pair<Double, Double>, CurrentWeatherReading>()
        var isCacheIncompleteOrObsolete = false

        for (loc in locations) {
            val identifier = LocationIdentifier.Saved(loc)
            val modelCache = cache[identifier]?.get(currentModel)
            val lastFetch = modelCache?.lastFullFetch // Ou utilisez un champ spécifique pour current si existant

            val isExpired = lastFetch == null || Duration.between(lastFetch, now).toMinutes() >= 15
            val cachedReading = modelCache?.currentWeatherReading

            if (cachedReading != null && !isExpired) {
                cachedResults[Pair(loc.latitude, loc.longitude)] = cachedReading
            } else {
                isCacheIncompleteOrObsolete = true
            }
        }

        // 2. Émission du cache si on a des données (même partielles, ou seulement si complet selon votre choix)
        // Ici, on émet si le cache contient au moins toutes les locations demandées
        if (cachedResults.size == locations.size) {
            emit(WeatherDataState.SuccessCurrent(cachedResults))
        }

        // 3. Recharge si nécessaire
        if (isCacheIncompleteOrObsolete || cachedResults.size < locations.size) {
            val positions = locations.map { Pair(it.latitude, it.longitude) }
            val freshData = weatherService.getCurrentWeather(positions)

            if (freshData != null) {
                // Mise à jour du cache interne pour chaque location reçue
                freshData.forEach { (coords, reading) ->
                    // On retrouve la location correspondante pour mettre à jour le bon ModelDataCache
                    val matchingLoc = locations.find { it.latitude == coords.first && it.longitude == coords.second }
                    if (matchingLoc != null) {
                        val identifier = LocationIdentifier.Saved(matchingLoc)
                        val modelMap = cache.getOrPut(identifier) { mutableMapOf() }
                        val modelCache = modelMap.getOrPut(currentModel) { ModelDataCache() }

                        modelCache.currentWeatherReading = reading
                        modelCache.lastFullFetch = now // On marque le temps du rafraîchissement
                    }
                }
                emit(WeatherDataState.SuccessCurrent(freshData))
            } else {
                // Si on n'a rien du tout (pas de cache et échec réseau).
                if (cachedResults.isEmpty()) {
                    emit(WeatherDataState.Error("Error: Unable to fetch current weather"))
                }
            }
        }
    }

    fun getAirQuality(): Flow<WeatherDataState> = flow {
        val currentLocationIdentifier = selectedLocation.value
        val currentModel = userSettings.value.model
        val now = LocalDateTime.now()

        // 1. Récupération du cache
        val modelCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }
            .getOrPut(currentModel) { ModelDataCache() }

        val cachedAirQuality = modelCache.airQualityInfo
        val cachedPollen = modelCache.pollenInfo
        val lastFetch = modelCache.lastAirQualityFetch

        // On considère le cache valide s'il a moins de 30 minutes (le pollen change peu)
        val isCacheValid = cachedAirQuality != null &&
                Duration.between(lastFetch, now).toMinutes() < 30

        // 2. Émettre le cache immédiatement s'il est valide
        if (isCacheValid) {
            emit(WeatherDataState.SuccessAirQuality(Pair(cachedAirQuality!!, cachedPollen)))
            return@flow
        }

        emit(WeatherDataState.Loading)

        // 3. Détermination des coordonnées
        val locationKey = when (currentLocationIdentifier) {
            is LocationIdentifier.CurrentUserLocation -> {
                val gpsPos = _currentGpsPosition.value ?: run {
                    withTimeoutOrNull(15000) { currentGpsPosition.filterNotNull().first() }
                }
                if (gpsPos != null) LocationKey(gpsPos.latitude, gpsPos.longitude) else null
            }
            is LocationIdentifier.Saved -> LocationKey(
                currentLocationIdentifier.location.latitude,
                currentLocationIdentifier.location.longitude
            )
        }

        if (locationKey == null) {
            emit(WeatherDataState.Error("Position GPS non disponible"))
            return@flow
        }

        // 4. Appels réseau en parallèle
        coroutineScope {
            val airQualityDeferred = async {
                weatherService.getAirQuality(locationKey.latitude, locationKey.longitude, applicationContext)
            }
            val pollenDeferred = async {
                weatherService.getPollenForecast(locationKey.latitude, locationKey.longitude, applicationContext)
            }

            val airQualityResponse = airQualityDeferred.await()
            val pollenResponse = pollenDeferred.await()

            if (airQualityResponse != null) {
                // Mise à jour du cache
                modelCache.airQualityInfo = airQualityResponse
                modelCache.pollenInfo = pollenResponse
                modelCache.lastAirQualityFetch = now

                emit(WeatherDataState.SuccessAirQuality(Pair(airQualityResponse, pollenResponse)))
            } else {
                if (cachedAirQuality != null) {
                    // Si l'appel échoue mais qu'on a un cache, on l'émet quand même
                    emit(WeatherDataState.SuccessAirQuality(Pair(cachedAirQuality, cachedPollen)))
                } else {
                    emit(WeatherDataState.Error("Impossible de récupérer les données environnementales"))
                }
            }
        }
    }

    fun getLocalVigilance(): Flow<WeatherDataState> = flow {
        emit(WeatherDataState.Loading)

        val locationKey = when (val currentLoc = selectedLocation.value) {
            is LocationIdentifier.CurrentUserLocation -> {
                val gpsPos = _currentGpsPosition.value ?: run {
                    // On n'émet pas Loading ici car déjà fait au début du flow
                    withTimeoutOrNull(15000) {
                        currentGpsPosition.filterNotNull().first()
                    }
                }

                if (gpsPos != null) {
                    LocationKey(gpsPos.latitude, gpsPos.longitude)
                } else {
                    emit(WeatherDataState.Error("Cannot get GPS position, please ensure that you authorized the app"))
                    return@flow
                }
            }
            is LocationIdentifier.Saved -> LocationKey(
                currentLoc.location.latitude,
                currentLoc.location.longitude
            )
        }

        // Appel de la nouvelle fonction basée sur la carte (J et J+1)
        val vigilance = weatherService.getVigilanceForLocation(locationKey.latitude, locationKey.longitude)

        if (vigilance != null) {
            emit(WeatherDataState.SuccessVigilance(vigilance))
        } else {
            // Optionnel : Vous pouvez mettre un message plus précis
            emit(WeatherDataState.Error("Impossible de récupérer les données de vigilance"))
        }
    }

    fun getRawCache(): MutableMap<LocationIdentifier, MutableMap<String, ModelDataCache>> {
        return cache
    }

    fun invalidateCache() {
        cache.clear()
        File(applicationContext.cacheDir, "weather_cache_data.json").delete()

        // Forcer le rafraîchissement des flows en ré-émettant la localisation
        val current = _selectedLocation.value
        _selectedLocation.value = current
    }

    fun refreshCurrentLocation() {
        val identifier = _selectedLocation.value
        if (identifier is LocationIdentifier.CurrentUserLocation) {
            applicationScope.launch {
                val newCoordinates = locationProvider.getCurrentLocation()
                if (newCoordinates != null) {
                    Log.d("WeatherCache", "One-time GPS update: $newCoordinates")
                    _currentGpsPosition.value = newCoordinates
                }
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