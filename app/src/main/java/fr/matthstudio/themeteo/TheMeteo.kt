package fr.matthstudio.themeteo

import android.app.Application
import android.os.Parcelable
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import fr.matthstudio.themeteo.forecastViewer.AllHourlyVarsReading
import fr.matthstudio.themeteo.forecastViewer.DailyReading
import fr.matthstudio.themeteo.forecastViewer.WeatherService
import fr.matthstudio.themeteo.forecastViewer.data.AppContainer
import fr.matthstudio.themeteo.forecastViewer.data.AppDataContainer
import fr.matthstudio.themeteo.forecastViewer.data.GpsCoordinates
import fr.matthstudio.themeteo.forecastViewer.data.LocalDateSerializer
import fr.matthstudio.themeteo.forecastViewer.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.forecastViewer.data.LocationProvider
import fr.matthstudio.themeteo.forecastViewer.data.SavedLocation
import fr.matthstudio.themeteo.forecastViewer.data.UserLocationsRepository
import fr.matthstudio.themeteo.forecastViewer.data.UserSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TreeMap

class TheMeteo : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
    // Le cache est maintenant initialisé avec les dépendances du container.
    lateinit var weatherCache: WeatherCache

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)
        // Read from cache the data
        // ------------------------
        // check if the file exists
        val cacheFile = File(cacheDir, "weather_cache_data.json")
        if (!cacheFile.exists()) {
            weatherCache = WeatherCache(
                userLocationsRepository = container.userLocationsRepository,
                userSettingsRepository = container.userSettingsRepository,
                applicationScope = CoroutineScope(Dispatchers.Default),
                locationProvider = LocationProvider(this),
                applicationContext = this
            )
            return
        }
        // read the file's content
        try {
            val json = Json {
                allowStructuredMapKeys = true
            }
            val fileContent = cacheFile.readText()
            val value = json.decodeFromString<MutableMap<LocationIdentifier, MutableMap<String, ModelDataCache>>>(fileContent)
            weatherCache = WeatherCache(
                userLocationsRepository = container.userLocationsRepository,
                userSettingsRepository = container.userSettingsRepository,
                applicationScope = CoroutineScope(Dispatchers.Default),
                locationProvider = LocationProvider(this),
                cache = value,
                applicationContext = this
            )
        } catch (e: Exception) {
            Log.e("TheMeteo", "Error loading cache", e)
            weatherCache = WeatherCache(
                userLocationsRepository = container.userLocationsRepository,
                userSettingsRepository = container.userSettingsRepository,
                applicationScope = CoroutineScope(Dispatchers.Default),
                locationProvider = LocationProvider(this),
                applicationContext = this
            )
        }
    }

    fun saveCache() {
        try {
            Log.d("onTerminate", "Saving cache...")
            val json = Json {
                allowStructuredMapKeys = true
            }
            val serializedValue = json.encodeToString(weatherCache.getRawCache())
            File(cacheDir, "weather_cache_data.json").writeText(serializedValue)
        } catch (e: Exception) {
            Log.e("TheMeteo", "Error saving cache", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}

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

data class UserSettings(
    val model: String,
    val roundToInt: Boolean?
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
    var lastFullFetchHourly: LocalDateTime = LocalDateTime.MIN,

    @Serializable(with = LocalDateTimeSerializer::class)
    var lastFullFetchDaily: LocalDateTime = LocalDateTime.MIN
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
    private var locationCollectionJob: Job? = null

    // --- StateFlows pour les settings et la localisation sélectionnée ---
    private val _userSettings = MutableStateFlow(UserSettings("best_match", null))
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

    init {
        // Collecte les settings
        applicationScope.launch {
            userSettingsRepository.model.combine(userSettingsRepository.roundToInt) { model, round ->
                UserSettings(model ?: "best_match", round ?: false)
            }.collect { settings ->
                _userSettings.value = settings
            }
        }

        // Gère le job de collecte de la position GPS
        applicationScope.launch {
            selectedLocation.collect { identifier ->
                locationCollectionJob?.cancel() // Annule toujours le job précédent
                if (identifier is LocationIdentifier.CurrentUserLocation) {
                    // Si la localisation actuelle est sélectionnée, on lance un nouveau job de collecte.
                    locationCollectionJob = applicationScope.launch {
                        locationProvider.locationFlow.collect { newCoordinates ->
                            Log.d("WeatherCache", "New GPS coordinate: $newCoordinates")
                            _currentGpsPosition.value = newCoordinates
                        }
                    }
                } else {
                    // Si une autre localisation est sélectionnée, on s'assure que la position est nulle.
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

        val endTime = startTime.plusHours(hours.toLong())
        val modelCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(currentSettings.model) { ModelDataCache() }

        // 1. Tentative de récupération depuis le cache
        val cachedData = getHourlyFromCache(modelCache, startTime, endTime)
        if (cachedData != null && cachedData.isNotEmpty()) {
            emit(WeatherDataState.SuccessHourly(cachedData))
        } else {
            emit(WeatherDataState.Loading)
        }

        // 2. Calcul de la nécessité de fetch
        val isDataObsolete = Duration.between(modelCache.lastFullFetchHourly, LocalDateTime.now()).toHours() >= 1

        val startDay = startTime.toLocalDate()
        val endDay = endTime.toLocalDate()
        var isAnyHourlyMissing = false
        var currentD = startDay
        while (!currentD.isAfter(endDay)) {
            val block = modelCache.dailyBlocks[currentD]
            // Si le bloc n'existe pas ou que les données horaires sont vides
            if (block == null || block.first.isEmpty()) {
                isAnyHourlyMissing = true
                break
            }
            currentD = currentD.plusDays(1)
        }

        val firstDayLoaded = modelCache.dailyBlocks.firstEntry()?.key
        val needsFetch = isDataObsolete || isAnyHourlyMissing ||
                (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))

        // 3. Exécution du fetch si nécessaire
        if (needsFetch) {
            // On n'émet Loading que si on n'avait rien en cache
            if (cachedData == null || cachedData.isEmpty()) {
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
                        emit(WeatherDataState.Error)
                        return@flow
                    }
                }
                is LocationIdentifier.Saved -> LocationKey(
                    currentLocationIdentifier.location.latitude,
                    currentLocationIdentifier.location.longitude
                )
            }

            val freshData = weatherService.getCompleteHourlyForecast(
                locationKey.latitude, locationKey.longitude,
                currentSettings.model, startTime.toLocalDate(), endTime.toLocalDate()
            )

            if (freshData != null) {
                // Nettoyage si les données sont d'un autre jour
                if (isDataObsolete || (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))) {
                    modelCache.dailyBlocks.clear()
                }

                // Mise à jour de la partie "Hourly" tout en préservant le "Daily" s'il existe
                freshData.groupBy { it.time.toLocalDate() }
                    .forEach { (date, hourlyReadings) ->
                        val existingBlock = modelCache.dailyBlocks[date]
                        modelCache.dailyBlocks[date] = Pair(
                            hourlyReadings,
                            existingBlock?.second ?: DailyReading(
                                date, 0.0, 0.0, 0,
                                "", ""
                            )
                        )
                    }
                modelCache.lastFullFetchHourly = LocalDateTime.now()

                val finalData = getHourlyFromCache(modelCache, startTime, endTime)
                if (finalData != null && finalData.isNotEmpty()) {
                    emit(WeatherDataState.SuccessHourly(finalData))
                } else {
                    emit(WeatherDataState.Error)
                }
            } else if (modelCache.dailyBlocks.isEmpty()) {
                emit(WeatherDataState.Error)
            }
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

        val modelCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(currentSettings.model) { ModelDataCache() }
        val endDate = date.plusDays(days)

        // 1. Tentative de récupération depuis le cache
        val cachedData = getDailyFromCache(modelCache, date, endDate)
        if (cachedData != null && cachedData.isNotEmpty()) {
            emit(WeatherDataState.SuccessDaily(cachedData))
        } else {
            emit(WeatherDataState.Loading)
        }

        // 2. Calcul de la nécessité de fetch
        val isDataObsolete = Duration.between(modelCache.lastFullFetchDaily, LocalDateTime.now()).toHours() >= 1

        var isAnyDailyMissing = false
        var currentD = date
        while (currentD.isBefore(endDate)) {
            val block = modelCache.dailyBlocks[currentD]
            // Un bloc est incomplet pour le "Daily" si le weatherCode est vide (dummy object)
            if (block == null || block.second.sunset == "") {
                isAnyDailyMissing = true
                break
            }
            currentD = currentD.plusDays(1)
        }

        val firstDayLoaded = modelCache.dailyBlocks.firstEntry()?.key
        val needsFetch = isDataObsolete || isAnyDailyMissing ||
                (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))

        // 3. Exécution du fetch si nécessaire
        if (needsFetch) {
            if (cachedData == null || cachedData.isEmpty()) {
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
                        emit(WeatherDataState.Error)
                        return@flow
                    }
                }
                is LocationIdentifier.Saved -> LocationKey(
                    currentLocationIdentifier.location.latitude,
                    currentLocationIdentifier.location.longitude
                )
            }

            val freshData = weatherService.getCompleteDailyForecast(
                locationKey.latitude, locationKey.longitude,
                currentSettings.model, date, endDate
            )

            if (freshData != null) {
                if (isDataObsolete || (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))) {
                    modelCache.dailyBlocks.clear()
                }

                // Mise à jour de la partie "Daily" tout en préservant le "Hourly" s'il existe
                freshData.forEach { dailyReading ->
                    val existingBlock = modelCache.dailyBlocks[dailyReading.date]
                    modelCache.dailyBlocks[dailyReading.date] = Pair(
                        existingBlock?.first ?: emptyList(),
                        dailyReading
                    )
                }
                modelCache.lastFullFetchDaily = LocalDateTime.now()

                val data = getDailyFromCache(modelCache, date, endDate)
                if (data != null && data.isNotEmpty()) {
                    emit(WeatherDataState.SuccessDaily(data))
                } else {
                    emit(WeatherDataState.Error)
                }
            } else if (modelCache.dailyBlocks.isEmpty()) {
                emit(WeatherDataState.Error)
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
}

sealed class WeatherDataState {
    object Loading : WeatherDataState()
    data class SuccessHourly(val data: List<AllHourlyVarsReading>) : WeatherDataState()
    data class SuccessDaily(val data: List<DailyReading>) : WeatherDataState()
    object Error : WeatherDataState()
}
