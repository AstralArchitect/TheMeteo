package fr.matthstudio.themeteo

import android.app.Application
import android.location.Location
import android.util.Log
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.DailyForecastUI
import fr.matthstudio.themeteo.data.DailyScenario
import fr.matthstudio.themeteo.data.DailySynopticForecastUI
import fr.matthstudio.themeteo.data.ExtendedForecastUI
import fr.matthstudio.themeteo.data.ForecastTechnicalInfo
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.MediumTermStepUI
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import fr.matthstudio.themeteo.utilClasses.AirQualityForecastResponse
import fr.matthstudio.themeteo.utilClasses.AirQualityInfo
import fr.matthstudio.themeteo.utilClasses.FullSunCalculator
import fr.matthstudio.themeteo.utilClasses.MeteoFranceRainResponse
import fr.matthstudio.themeteo.utilClasses.MoonCalculator
import fr.matthstudio.themeteo.utilClasses.MoonData
import fr.matthstudio.themeteo.utilClasses.PollenResponse
import fr.matthstudio.themeteo.utilClasses.VigilanceInfos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds

class WeatherParser(
    private val appContext: Application,
    private val scope: CoroutineScope
) {
    private val weatherService = (appContext as TheMeteo).container.weatherService
    private val weatherCache = NewWeatherCache(appContext as TheMeteo)

    init {
        scope.launch {
            AppStateManager.selectedLocation.collect {
                updateCurrentWmoAndNight()
            }
        }
        startHourlyTicker()
    }

    fun clearCacheTimes() {
        weatherCache.clearFetchTimes()
        weatherService.clearVolatileCaches()
    }

    private fun startHourlyTicker() {
        scope.launch {
            while (isActive) {
                updateCurrentWmoAndNight()
                val delayMs = getMillisToNextHour()
                delay(delayMs)
            }
        }
    }

    private fun getMillisToNextHour(): Long {
        val now = LocalDateTime.now()
        val nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0)
        return Duration.between(now, nextHour).toMillis().coerceAtLeast(1000L) + 50L
    }

    private suspend fun updateCurrentWmoAndNight() {
        val location = AppStateManager.selectedLocation.value
        val coords = resolveCoordinates(
            identifier = location,
            allowHardwareLookup = false,
            updateRemoteCityName = false
        ) ?: return

        val sunCalc = FullSunCalculator(coords.latitude, coords.longitude)
        val sunData = sunCalc.getCompleteSunData()
        AppStateManager.updateSunData(sunData)
        AppStateManager.updateIsNight(sunData.currentPosition.elevation <= 0.0)

        val moonCalc = MoonCalculator(coords.latitude, coords.longitude)
        val currentPos = moonCalc.getMoonPosition(LocalDateTime.now())
        val dailyEvents = moonCalc.getDailyEvents(LocalDate.now())
        val moonData = MoonData(dailyEvents = dailyEvents, currentPosition = currentPos)
        AppStateManager.updateMoonData(moonData)

        val pointIndex = weatherService.getPointIndex(coords.longitude, coords.latitude)
        val (cachedPoint, _) = weatherCache.getShortFromIndex(pointIndex) { null }
        if (cachedPoint != null) {
            val zoneId = ZoneId.systemDefault()
            val now = LocalDateTime.now(zoneId).withMinute(0).withSecond(0).withNano(0)

            val currentStep = cachedPoint.steps.find { step ->
                val stepLocal = step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDateTime()
                stepLocal == now
            } ?: cachedPoint.steps.minByOrNull { step ->
                val stepLocal = step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDateTime()
                Math.abs(Duration.between(stepLocal, now).toMinutes())
            }

            currentStep?.let { step ->
                AppStateManager.updateCurrentWmoCode(step.wmoCode)
            }
        }
    }

    suspend fun getCityName(lat: Double, lon: Double): String? {
        return weatherService.getCityNameFromCoords(lat, lon, appContext)
    }

    suspend fun getCachedOrFetchShortTerm(location: LocationIdentifier): ShortTermForecastUI? {
        return fetchShortTermForecast(location = location, forceRefresh = false)
    }

    private suspend fun resolveCoordinates(
        identifier: LocationIdentifier,
        allowHardwareLookup: Boolean = true,
        updateRemoteCityName: Boolean = true
    ): GpsCoordinates? {
        val coords = when (identifier) {
            is LocationIdentifier.CurrentUserLocation -> {
                val current = AppStateManager.currentGpsPosition.value
                if (current != null) {
                    current
                } else if (!allowHardwareLookup) {
                    null
                } else {
                    val app = appContext as? TheMeteo
                    val directCoords = app?.container?.locationProvider?.getCurrentLocation()
                    if (directCoords != null) {
                        Log.d("WeatherParser", "resolveCoordinates: Obtained direct GPS coords $directCoords")
                        AppStateManager.updateGpsPosition(directCoords)
                        directCoords
                    } else {
                        Log.d("WeatherParser", "resolveCoordinates: Waiting for AppStateManager GPS coords...")
                        withTimeoutOrNull(2000.milliseconds) {
                            AppStateManager.currentGpsPosition.filterNotNull().first()
                        }
                    }
                }
            }
            is LocationIdentifier.Saved -> GpsCoordinates(identifier.location.latitude, identifier.location.longitude)
        }

        if (updateRemoteCityName && identifier is LocationIdentifier.CurrentUserLocation && coords != null) {
            scope.launch {
                try {
                    val cityName = getCityName(coords.latitude, coords.longitude)
                    if (cityName != null) {
                        AppStateManager.updateCityName(cityName)
                    }
                } catch (e: Exception) {
                    Log.e("WeatherParser", "Error updating city name for GPS", e)
                }
            }
        }

        return coords
    }

    fun getShortTerm(
        startHour: LocalDateTime,
        numHours: Int,
        location: LocationIdentifier = AppStateManager.selectedLocation.value
    ): Flow<WeatherDataState> = flow {
        emit(WeatherDataState.Loading)

        val endHour = startHour.plusHours(numHours.toLong())
        val zoneId = ZoneId.systemDefault()

        val forecast = fetchShortTermForecast(location)
        if (forecast == null) {
            emit(WeatherDataState.Error("Unable to get short term forecast"))
            return@flow
        }

        val finalValue = forecast.steps.mapNotNull { step ->
            val localTime = step.time.atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(zoneId)
                .toLocalDateTime()

            if (!localTime.isBefore(startHour) && localTime.isBefore(endHour)) {
                step.copy(time = localTime)
            } else {
                null
            }
        }

        if (location == AppStateManager.selectedLocation.value) {
            val now = LocalDateTime.now(zoneId).withMinute(0).withSecond(0).withNano(0)
            val currentStep = forecast.steps.find { step ->
                val stepLocal = step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDateTime()
                stepLocal == now
            } ?: forecast.steps.minByOrNull { step ->
                val stepLocal = step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDateTime()
                Math.abs(Duration.between(stepLocal, now).toMinutes())
            }
            currentStep?.let { step ->
                AppStateManager.updateCurrentWmoCode(step.wmoCode)
            }
        }

        emit(WeatherDataState.SuccessShortTermHourly(finalValue))
    }

    fun getMediumTerm(
        startHour: LocalDateTime,
        endHour: LocalDateTime,
        location: LocationIdentifier = AppStateManager.selectedLocation.value
    ): Flow<WeatherDataState> = flow {
        emit(WeatherDataState.Loading)

        val zoneId = ZoneId.systemDefault()
        val extendedForecast = fetchExtendedTermForecast(location)
        if (extendedForecast == null) {
            emit(WeatherDataState.Error("Unable to get extended forecast"))
            return@flow
        }

        val mediumTermSteps = extendedForecast.mediumTermSteps
        val finalValue = mediumTermSteps.mapNotNull { step ->
            val localTime = step.time.atZone(ZoneId.of("UTC"))
                .withZoneSameInstant(zoneId)
                .toLocalDateTime()

            if (!localTime.isBefore(startHour) && localTime.isBefore(endHour)) {
                step.copy(time = localTime)
            } else {
                null
            }
        }

        if (finalValue.isNotEmpty()) {
            emit(WeatherDataState.SuccessMediumTermHourly(finalValue))
        } else {
            emit(WeatherDataState.Error("No medium term steps available for this period"))
        }
    }

    fun getLongTerm(
        startDate: LocalDate,
        endDate: LocalDate,
        location: LocationIdentifier = AppStateManager.selectedLocation.value
    ): Flow<WeatherDataState> = flow {
        emit(WeatherDataState.Loading)

        val zoneId = ZoneId.systemDefault()
        val extendedForecast = fetchExtendedTermForecast(location)
        if (extendedForecast == null) {
            emit(WeatherDataState.Error("Unable to get extended forecast"))
            return@flow
        }

        val resultList = mutableListOf<DailySynopticForecastUI>()
        val today = LocalDate.now()

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val targetDate = currentDate
            val daysDiff = ChronoUnit.DAYS.between(today, targetDate).toInt()

            // Find matching long term day or scenarios by date or day number offset
            val matchingDay = extendedForecast.longTermDays.find { day ->
                day.date == targetDate || day.dayNumber == daysDiff || day.scenarios.any { scenario ->
                    scenario.steps.any { step ->
                        step.time.toLocalDate() == targetDate
                    }
                }
            }

            if (matchingDay != null) {
                val convertedScenarios = matchingDay.scenarios.mapNotNull { scenario ->
                    val daySteps = scenario.steps.ifEmpty { emptyList() }

                    if (daySteps.isEmpty()) null
                    else {
                        val scenarioDayWmo = computeDailyWmoCode(daySteps.map { Pair(it.time, it.wmoCode) }, zoneId)
                        scenario.copy(
                            wmoCode = scenarioDayWmo,
                            steps = daySteps
                        )
                    }
                }

                if (convertedScenarios.isNotEmpty()) {
                    resultList.add(
                        DailySynopticForecastUI(
                            dayNumber = matchingDay.dayNumber,
                            date = targetDate,
                            scenarios = convertedScenarios
                        )
                    )
                }
            }

            currentDate = currentDate.plusDays(1)
        }

        if (resultList.isEmpty()) {
            emit(WeatherDataState.Error("No long term forecast available for $startDate"))
        } else {
            emit(WeatherDataState.SuccessLongTermDaily(resultList))
        }
    }

    private fun computeDailyWmoCode(
        steps: List<Pair<LocalDateTime, Int>>,
        zoneId: ZoneId
    ): Int {
        if (steps.isEmpty()) return 0

        val maxWmo = steps.maxOf { it.second }

        // Si le WMO max est supérieur à 4 (pluie, neige, brouillard, orage, etc.), on prend le max
        if (maxWmo > 4) {
            return maxWmo
        }

        // Sinon (maxWmo dans 0..4), on prend le WMO le plus fréquent entre 6h et 20h heure locale
        val daytimeSteps = steps.filter { (time, _) ->
            val localHour = time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).hour
            localHour in 6..20
        }

        val targetSteps = if (daytimeSteps.isNotEmpty()) daytimeSteps else steps

        return targetSteps
            .groupingBy { it.second }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: maxWmo
    }

    fun getDaily(
        date: LocalDate,
        nDays: Long,
        location: LocationIdentifier = AppStateManager.selectedLocation.value
    ): Flow<WeatherDataState> = getDaily(date, date.plusDays(nDays), location)

    fun getDaily(
        startDate: LocalDate,
        endDate: LocalDate,
        location: LocationIdentifier = AppStateManager.selectedLocation.value
    ): Flow<WeatherDataState> = flow {
        emit(WeatherDataState.Loading)

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now()
        val requiresExtended = ChronoUnit.DAYS.between(today, endDate) > 0

        val time = LocalDateTime.now()
        val (shortForecast, extendedForecast) = coroutineScope {
            if (requiresExtended) {
                val shortDeferred = async { fetchShortTermForecast(location) }
                val extendedDeferred = async { fetchExtendedTermForecast(location) }
                Pair(shortDeferred.await(), extendedDeferred.await())
            } else {
                val shortOnly = fetchShortTermForecast(location)
                val hasShortForStart = shortOnly?.steps?.any { step ->
                    step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDate() == startDate
                } == true
                val extFallback = if (hasShortForStart) null else fetchExtendedTermForecast(location)
                Pair(shortOnly, extFallback)
            }
        }
        val duration = Duration.between(time, LocalDateTime.now())

        Log.d("WeatherParser", "Got short and extended forecast in ${duration.toMillis()} ms")

        val resultList = mutableListOf<DailyForecastUI>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val daysDiff = ChronoUnit.DAYS.between(today, currentDate)

            val shortSteps = shortForecast?.steps?.filter { step ->
                step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDate() == currentDate
            }

            val mediumSteps = extendedForecast?.mediumTermSteps?.filter { step ->
                step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDate() == currentDate
            }

            val synopticDay = extendedForecast?.longTermDays?.find { day ->
                day.date == currentDate || day.dayNumber == daysDiff.toInt() || day.scenarios.any { scenario ->
                    scenario.steps.any { step ->
                        step.time.toLocalDate() == currentDate
                    }
                }
            }

            val dailyUI: DailyForecastUI? = when {
                daysDiff < 7 && !shortSteps.isNullOrEmpty() -> {
                    val minTemp = shortSteps.minOf { it.temperature }
                    val maxTemp = shortSteps.maxOf { it.temperature }
                    val precipSum = shortSteps.sumOf { it.precipitation.toDouble() }.toFloat()
                    val precipProb = shortSteps.mapNotNull { it.precipitationProbability }.maxOrNull() ?: 0
                    val maxWind = shortSteps.maxOf { it.windSpeed }
                    val maxGust = shortSteps.maxOfOrNull { it.windGust } ?: maxWind
                    val windDir = shortSteps.firstOrNull()?.windDirection ?: 0f
                    val wmo = computeDailyWmoCode(shortSteps.map { Pair(it.time, it.wmoCode) }, zoneId)

                    DailyForecastUI(
                        date = currentDate,
                        wmoCode = wmo,
                        minTemperature = minTemp,
                        maxTemperature = maxTemp,
                        precipitationSum = precipSum,
                        precipitationProbability = precipProb,
                        maxWindSpeed = maxWind,
                        maxWindGust = maxGust,
                        dominantWindDirection = windDir,
                        sunrise = "",
                        sunset = ""
                    )
                }

                daysDiff < 7 && !mediumSteps.isNullOrEmpty() -> {
                    val minTemp = mediumSteps.minOf { it.tempP50.toDouble() }.toFloat()
                    val maxTemp = mediumSteps.maxOf { it.tempP50.toDouble() }.toFloat()
                    val precipSum = 0f // Moyen terme : somme masquée sur la journée, l'UI affiche l'icône WMO et le PoP %
                    val precipProb = mediumSteps.maxOf { it.precipitationProbability }
                    val maxWind = mediumSteps.maxOf { it.windSpeedP50 }
                    val maxGust = mediumSteps.maxOfOrNull { it.windGustP50 } ?: maxWind
                    val windDir = mediumSteps.firstOrNull()?.windDirection ?: 0f
                    val wmo = computeDailyWmoCode(mediumSteps.map { Pair(it.time, it.wmoCode) }, zoneId)

                    DailyForecastUI(
                        date = currentDate,
                        wmoCode = wmo,
                        minTemperature = minTemp,
                        maxTemperature = maxTemp,
                        precipitationSum = precipSum,
                        precipitationProbability = precipProb,
                        maxWindSpeed = maxWind,
                        maxWindGust = maxGust,
                        dominantWindDirection = windDir,
                        sunrise = "",
                        sunset = ""
                    )
                }

                synopticDay != null && synopticDay.scenarios.isNotEmpty() -> {
                    val allScenarios = synopticDay.scenarios.map { scenarioItem ->
                        val steps = scenarioItem.steps
                        val minTemp = if (steps.isNotEmpty()) steps.minOf { it.tempP50.toDouble() }.toFloat() else 0f
                        val maxTemp = if (steps.isNotEmpty()) steps.maxOf { it.tempP50.toDouble() }.toFloat() else 0f
                        val precipProb = if (steps.isNotEmpty()) steps.maxOf { it.precipitationProbability } else 0
                        val maxWind = if (steps.isNotEmpty()) steps.maxOf { it.windSpeedP50 } else 0f
                        val windDir = if (steps.isNotEmpty()) steps.firstOrNull()?.windDirection ?: 0f else 0f

                        val scenarioWmo = if (steps.isNotEmpty()) {
                            computeDailyWmoCode(steps.map { Pair(it.time, it.wmoCode) }, zoneId)
                        } else {
                            scenarioItem.wmoCode
                        }

                        DailyScenario(
                            percentage = scenarioItem.probability.toInt(),
                            wmo = scenarioWmo,
                            minTemperature = minTemp,
                            maxTemperature = maxTemp,
                            precipitationProbability = precipProb,
                            maxWindSpeed = maxWind,
                            dominantWindDirection = windDir
                        )
                    }

                    val mainScenario = allScenarios.first()
                    val mainSteps = synopticDay.scenarios.first().steps
                    val maxGust = mainSteps.maxOfOrNull { it.windGustP50 } ?: mainScenario.maxWindSpeed
                    val precipSumMain = 0f

                    DailyForecastUI(
                        date = currentDate,
                        wmoCode = mainScenario.wmo,
                        minTemperature = mainScenario.minTemperature,
                        maxTemperature = mainScenario.maxTemperature,
                        precipitationSum = precipSumMain,
                        precipitationProbability = mainScenario.precipitationProbability,
                        maxWindSpeed = mainScenario.maxWindSpeed,
                        maxWindGust = maxGust,
                        dominantWindDirection = mainScenario.dominantWindDirection,
                        sunrise = "",
                        sunset = "",
                        scenario = mainScenario,
                        scenarios = allScenarios
                    )
                }

                !mediumSteps.isNullOrEmpty() -> {
                    val minTemp = mediumSteps.minOf { it.tempP50.toDouble() }.toFloat()
                    val maxTemp = mediumSteps.maxOf { it.tempP50.toDouble() }.toFloat()
                    val precipSum = 0f
                    val precipProb = mediumSteps.maxOf { it.precipitationProbability }
                    val maxWind = mediumSteps.maxOf { it.windSpeedP50 }
                    val maxGust = mediumSteps.maxOfOrNull { it.windGustP50 } ?: maxWind
                    val windDir = mediumSteps.firstOrNull()?.windDirection ?: 0f
                    val wmo = computeDailyWmoCode(mediumSteps.map { Pair(it.time, it.wmoCode) }, zoneId)

                    DailyForecastUI(
                        date = currentDate,
                        wmoCode = wmo,
                        minTemperature = minTemp,
                        maxTemperature = maxTemp,
                        precipitationSum = precipSum,
                        precipitationProbability = precipProb,
                        maxWindSpeed = maxWind,
                        maxWindGust = maxGust,
                        dominantWindDirection = windDir,
                        sunrise = "",
                        sunset = ""
                    )
                }

                !shortSteps.isNullOrEmpty() -> {
                    val minTemp = shortSteps.minOf { it.temperature }
                    val maxTemp = shortSteps.maxOf { it.temperature }
                    val precipSum = shortSteps.sumOf { it.precipitation.toDouble() }.toFloat()
                    val precipProb = shortSteps.mapNotNull { it.precipitationProbability }.maxOrNull() ?: 0
                    val maxWind = shortSteps.maxOf { it.windSpeed }
                    val maxGust = shortSteps.maxOfOrNull { it.windGust } ?: maxWind
                    val windDir = shortSteps.firstOrNull()?.windDirection ?: 0f
                    val wmo = computeDailyWmoCode(shortSteps.map { Pair(it.time, it.wmoCode) }, zoneId)

                    DailyForecastUI(
                        date = currentDate,
                        wmoCode = wmo,
                        minTemperature = minTemp,
                        maxTemperature = maxTemp,
                        precipitationSum = precipSum,
                        precipitationProbability = precipProb,
                        maxWindSpeed = maxWind,
                        maxWindGust = maxGust,
                        dominantWindDirection = windDir,
                        sunrise = "",
                        sunset = ""
                    )
                }

                else -> null
            }

            if (dailyUI != null) {
                resultList.add(dailyUI)
            }

            currentDate = currentDate.plusDays(1)
        }

        if (resultList.isEmpty()) {
            emit(WeatherDataState.Error("No forecast data available"))
        } else {
            emit(WeatherDataState.SuccessDaily(resultList))
        }
    }

    private val shortTermMutex = Mutex()
    private val extendedTermMutex = Mutex()

    private suspend fun fetchShortTermForecast(
        location: LocationIdentifier = AppStateManager.selectedLocation.value,
        forceRefresh: Boolean = false
    ): ShortTermForecastUI? = shortTermMutex.withLock {
        val coords = resolveCoordinates(location) ?: return@withLock null

        val lon = coords.longitude
        val lat = coords.latitude
        val pointIndex = weatherService.getPointIndex(lon, lat)

        val (cachedPoint, meta) = weatherCache.getShortFromIndex(pointIndex, forceRefresh) {
            weatherService.downloadShortTermMeta(lon, lat)
        }

        if (cachedPoint != null) {
            if (location == AppStateManager.selectedLocation.value) {
                updateCurrentWmoAndNight()
            }
            return@withLock cachedPoint
        }

        val rawData = weatherService.getShortTermForecast(lon, lat, appContext, meta) ?: return@withLock null
        val forecast = BinaryDecoder.parseFullShortTerm(rawData.first, rawData.second)

        weatherCache.setShortFromIndex(pointIndex, forecast.runHour, forecast)
        if (location == AppStateManager.selectedLocation.value) {
            updateCurrentWmoAndNight()
        }
        return@withLock forecast
    }

    private suspend fun fetchExtendedTermForecast(
        location: LocationIdentifier = AppStateManager.selectedLocation.value,
        forceRefresh: Boolean = false
    ): ExtendedForecastUI? = extendedTermMutex.withLock {
        val coords = resolveCoordinates(location) ?: return@withLock null

        val lon = coords.longitude
        val lat = coords.latitude
        val pointIndex = weatherService.getPointIndex(lon, lat, true)

        val (cachedPoint, meta) = weatherCache.getExtendedFromIndex(pointIndex, forceRefresh) {
            weatherService.getExtendedMetadata()
        }

        if (cachedPoint != null) {
            return@withLock cachedPoint
        }

        val rawData = weatherService.getExtendedForecast(lon, lat, appContext, meta) ?: return@withLock null
        val forecast = BinaryDecoder.parseFullExtendedTerm(rawData.first, rawData.second)

        weatherCache.setExtendedFromIndex(pointIndex, forecast.runHour, forecast)
        return@withLock forecast
    }

    suspend fun getTechnicalInfo(): ForecastTechnicalInfo? {
        val currentLocationIdentifier = AppStateManager.selectedLocation.value
        val coords = resolveCoordinates(currentLocationIdentifier) ?: return null

        val (shortTerm, extendedTerm) = coroutineScope {
            val shortDeferred = async { fetchShortTermForecast() }
            val extendedDeferred = async { fetchExtendedTermForecast() }
            Pair(shortDeferred.await(), extendedDeferred.await())
        }
        if (shortTerm == null) return null

        val requestedLat = coords.latitude
        val requestedLon = coords.longitude
        val gridLat = shortTerm.latitude.toDouble()
        val gridLon = shortTerm.longitude.toDouble()

        val results = FloatArray(1)
        Location.distanceBetween(requestedLat, requestedLon, gridLat, gridLon, results)
        val distanceKm = results[0] / 1000.0

        val useArome = weatherService.shouldUseAROME(requestedLon, requestedLat)
        val shortTermModel = if (useArome) "AROME" else "IFS"
        val extendedModel = if (shortTermModel[0] == 'I') "AIFS" else "IFS + AIFS"

        return ForecastTechnicalInfo(
            requestedLat = requestedLat,
            requestedLon = requestedLon,
            gridLat = gridLat,
            gridLon = gridLon,
            distanceKm = distanceKm,
            shortTermModel = shortTermModel,
            extendedModel = extendedModel,
            shortTermRunHour = shortTerm.runHour,
            extendedRunHour = extendedTerm?.runHour
        )
    }
}

sealed class WeatherDataState {
    object Loading : WeatherDataState()
    data class SuccessShortTermHourly(val data: List<ShortTermStepUI>): WeatherDataState()
    data class SuccessMediumTermHourly(val data: List<MediumTermStepUI>): WeatherDataState()
    data class SuccessLongTermDaily(val data: List<DailySynopticForecastUI>): WeatherDataState()
    data class SuccessDaily(val data: List<DailyForecastUI>): WeatherDataState()
    data class SuccessCurrent(val data: Map<Pair<Double, Double>, CurrentWeatherReading>) : WeatherDataState()
    data class SuccessAirQuality(val data: Triple<AirQualityInfo, AirQualityForecastResponse?, PollenResponse?>) : WeatherDataState()
    data class SuccessVigilance(val data: VigilanceInfos) : WeatherDataState()
    data class SuccessRainWithinHour(val data: MeteoFranceRainResponse) : WeatherDataState()
    data class Error(val message: String, val staleData: WeatherDataState? = null) : WeatherDataState()
}