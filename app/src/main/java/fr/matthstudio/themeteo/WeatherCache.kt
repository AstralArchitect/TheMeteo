package fr.matthstudio.themeteo

import android.app.Activity
import android.app.Application
import android.os.Parcelable
import android.util.Log
import androidx.core.app.ComponentActivity
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.LocalDateSerializer
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.data.LocationProvider
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.UserLocationsRepository
import fr.matthstudio.themeteo.data.UserSettingsRepository
import fr.matthstudio.themeteo.dayChoserActivity.weatherModelPredictionTime
import fr.matthstudio.themeteo.forecastMainActivity.ForecastMainActivity
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
    val defaultScreen: DefaultScreen
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
    var lastFullFetch: LocalDateTime = LocalDateTime.MIN
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
    private val _userSettings = MutableStateFlow(UserSettings("best_match", true, LocationIdentifier.CurrentUserLocation, DefaultScreen.FORECAST_MAIN))
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
            combine(
                userSettingsRepository.model,
                userSettingsRepository.roundToInt,
                userSettingsRepository.defaultLocation,
                userSettingsRepository.defaultScreen
            ){ model, round, location, screen ->
                UserSettings(model ?: "best_match", round, location ?: LocationIdentifier.CurrentUserLocation, screen ?: DefaultScreen.FORECAST_MAIN)
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

        val maxAllowedDate = LocalDate.now().plusDays((weatherModelPredictionTime[userSettings.value.model]?.toLong() ?: 3) - 1) // Limite de 16 jours (0 à 15)
        var endTime = startTime.plusHours(hours.toLong())

        // Si l'heure de fin dépasse la date max autorisée par l'API
        if (endTime.toLocalDate().isAfter(maxAllowedDate)) {
            endTime = maxAllowedDate.atTime(23, 59)
        }

        val modelCache = cache.getOrPut(currentLocationIdentifier) { mutableMapOf() }.getOrPut(currentSettings.model) { ModelDataCache() }

        // 1. Tentative de récupération depuis le cache
        val cachedData = getHourlyFromCache(modelCache, startTime, endTime)
        if (cachedData != null && cachedData.isNotEmpty()) {
            emit(WeatherDataState.SuccessHourly(cachedData))
        } else {
            emit(WeatherDataState.Loading)
        }

        // 2. Calcul de la nécessité de fetch
        val isDataObsolete = Duration.between(modelCache.lastFullFetch, LocalDateTime.now()).toHours() >= 1

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
                        emit(WeatherDataState.Error("Cannot get GPS position, please ensure that you athorized the app"))
                        return@flow
                    }
                }
                is LocationIdentifier.Saved -> LocationKey(
                    currentLocationIdentifier.location.latitude,
                    currentLocationIdentifier.location.longitude
                )
            }

            val freshData = weatherService.getForecast(
                locationKey.latitude, locationKey.longitude,
                currentSettings.model, startTime.toLocalDate(), endTime.toLocalDate()
            )

            if (freshData != null) {
                // Nettoyage si les données sont d'un autre jour
                if (isDataObsolete || (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))) {
                    modelCache.dailyBlocks.clear()
                }

                // Mise à jour atomique du cache (Hourly + Daily)
                val hourlyByDate = freshData.first.groupBy { it.time.toLocalDate() }
                val dailyByDate = freshData.second.associateBy { it.date }

                // On récupère toutes les dates uniques des deux listes
                val allDates = hourlyByDate.keys + dailyByDate.keys

                allDates.forEach { date ->
                    val hourly = hourlyByDate[date] ?: emptyList()
                    val daily = dailyByDate[date] ?: DailyReading(
                        date, 0.0, 0.0, 0.0, 0, 0, "", ""
                    )

                    modelCache.dailyBlocks[date] = Pair(hourly, daily)
                }
                modelCache.lastFullFetch = LocalDateTime.now()

                val finalData = getHourlyFromCache(modelCache, startTime, endTime)
                if (finalData != null && finalData.isNotEmpty()) {
                    emit(WeatherDataState.SuccessHourly(finalData))
                } else {
                    emit(WeatherDataState.Error("Error"))
                }
            } else if (modelCache.dailyBlocks.isEmpty()) {
                emit(WeatherDataState.Error("Error"))
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
        // CALCUL SÉCURISÉ
        val maxAllowedDate = LocalDate.now().plusDays(15)
        var endDate = date.plusDays(days)

        if (endDate.isAfter(maxAllowedDate)) {
            endDate = maxAllowedDate.plusDays(1) // subMap est souvent exclusif sur la fin ou inclut selon votre logique,
            // ici on s'assure de ne pas demander au service au delà de maxAllowedDate
        }

        // 1. Tentative de récupération depuis le cache
        val cachedData = getDailyFromCache(modelCache, date, endDate)
        if (cachedData != null && cachedData.isNotEmpty()) {
            emit(WeatherDataState.SuccessDaily(cachedData))
        } else {
            emit(WeatherDataState.Loading)
        }

        // 2. Calcul de la nécessité de fetch
        val isDataObsolete = Duration.between(modelCache.lastFullFetch, LocalDateTime.now()).toHours() >= 1

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
                        emit(WeatherDataState.Error("Cannot get GPS position, please ensure that you athorized the app"))
                        return@flow
                    }
                }
                is LocationIdentifier.Saved -> LocationKey(
                    currentLocationIdentifier.location.latitude,
                    currentLocationIdentifier.location.longitude
                )
            }

            val freshData = weatherService.getForecast(
                locationKey.latitude, locationKey.longitude,
                currentSettings.model, date, endDate
            )

            if (freshData != null) {
                if (isDataObsolete || (firstDayLoaded != null && !firstDayLoaded.isEqual(LocalDate.now()))) {
                    modelCache.dailyBlocks.clear()
                }

                // Mise à jour atomique du cache (Hourly + Daily)
                val hourlyByDate = freshData.first.groupBy { it.time.toLocalDate() }
                val dailyByDate = freshData.second.associateBy { it.date }

                // On récupère toutes les dates uniques des deux listes
                val allDates = hourlyByDate.keys + dailyByDate.keys

                allDates.forEach { date ->
                    val hourly = hourlyByDate[date] ?: emptyList()
                    val daily = dailyByDate[date] ?: DailyReading(
                        date, 0.0, 0.0, 0.0, 0, 0, "", ""
                    )

                    modelCache.dailyBlocks[date] = Pair(hourly, daily)
                }
                modelCache.lastFullFetch = LocalDateTime.now()

                val data = getDailyFromCache(modelCache, date, endDate)
                if (data != null && data.isNotEmpty()) {
                    emit(WeatherDataState.SuccessDaily(data))
                } else {
                    emit(WeatherDataState.Error("Erreur"))
                }
            } else if (modelCache.dailyBlocks.isEmpty()) {
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

    fun getCurrentWeatherForSavedLocations(): Flow<WeatherDataState> {
        return flow {
            emit (WeatherDataState.Loading)
            val locations = userLocationsRepository.savedLocations.first()
            val positions = locations.map { Pair(it.latitude, it.longitude) }
            val data = weatherService.getCurrentWeather(positions)
            if (data != null)
                emit(WeatherDataState.SuccessCurrent(data))
            else
                emit(WeatherDataState.Error("Error"))
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
            // On annule et on relance manuellement le job de collecte GPS
            locationCollectionJob?.cancel()
            locationCollectionJob = applicationScope.launch {
                locationProvider.locationFlow.collect { newCoordinates ->
                    Log.d("WeatherCache", "New GPS coordinate: $newCoordinates")
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
    data class Error(val message: String) : WeatherDataState()
}