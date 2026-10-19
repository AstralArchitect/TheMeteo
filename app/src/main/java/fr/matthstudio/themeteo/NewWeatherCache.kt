package fr.matthstudio.themeteo

import android.app.Application
import android.os.Parcelable
import android.util.Log
import fr.matthstudio.themeteo.data.ExtendedForecastUI
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.forecastMainActivity.toEventLocalDateTime
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

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
    val roundToInt: Boolean,
    val defaultLocation: LocationIdentifier,
    val defaultScreen: DefaultScreen,
    val enableAnimatedIcons: Boolean,
    val temperatureUnit: TemperatureUnit,
    val windUnit: WindUnit,
    val firebaseConsent: String,
    val gcuAccepted: Boolean,
    val lastGcuUpdate: String?,
    val lastPrivacyPolicyUpdate: String?,
    val hasOpenedAppOnce: Boolean,
    val useEurAqi: Boolean,
    val backgroundLocationAsked: Boolean,
    val themeMode: ThemeMode,
    val enableRainNotifications: Boolean,
    val enableVigilanceNotifications: Boolean,
    val enableDurationExtension: Boolean,
    val expertMode: Boolean = false
)

@Serializable
data class CachedShortTermForecast(
    val data: ShortTermForecastUI,
    val pointIdx: Long,
    @Serializable(with = LocalDateTimeSerializer::class)
    val runHourUTC: LocalDateTime,
    val fetchTimestamp: Long = 0L
)

@Serializable
data class CachedExtendedTermForecast(
    val data: ExtendedForecastUI,
    val pointIdx: Long,
    @Serializable(with = LocalDateTimeSerializer::class)
    val runHourUTC: LocalDateTime,
    val fetchTimestamp: Long = 0L
)

@Serializable
data class CacheDiskState(
    val shortTerm: Map<Long, CachedShortTermForecast> = emptyMap(),
    val extended: Map<Long, CachedExtendedTermForecast> = emptyMap()
)

class NewWeatherCache(
    private val applicationContext: Application
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        allowSpecialFloatingPointValues = true
    }

    private val cacheDir = File(applicationContext.cacheDir, "weather_cache").apply { if (!exists()) mkdirs() }
    private val oldCacheFile = File(applicationContext.cacheDir, "new_weather_cache.json")

    // En mémoire vive (RAM) : conservation de la ville active uniquement (1 seule entrée)
    private val shortTermCache = mutableMapOf<Long, CachedShortTermForecast>()
    private val extendedForecastCache = mutableMapOf<Long, CachedExtendedTermForecast>()

    private val lastShortFetchTimes = mutableMapOf<Long, Long>()
    private val lastExtendedFetchTimes = mutableMapOf<Long, Long>()

    init {
        // Supprime l'ancien fichier de cache monolithique s'il existe
        if (oldCacheFile.exists()) {
            oldCacheFile.delete()
        }
    }

    private fun loadShortFromDisk(index: Long): CachedShortTermForecast? {
        val file = File(cacheDir, "short_$index.json")
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            if (text.isNotBlank()) json.decodeFromString<CachedShortTermForecast>(text) else null
        } catch (e: Exception) {
            Log.e("NewWeatherCache", "Error reading short term cache for $index", e)
            file.delete()
            null
        }
    }

    private fun saveShortToDisk(index: Long, item: CachedShortTermForecast) {
        val file = File(cacheDir, "short_$index.json")
        try {
            val text = json.encodeToString(item)
            file.writeText(text)
        } catch (e: Exception) {
            Log.e("NewWeatherCache", "Error saving short term cache for $index", e)
        }
    }

    private fun loadExtendedFromDisk(index: Long): CachedExtendedTermForecast? {
        val file = File(cacheDir, "extended_$index.json")
        if (!file.exists()) return null
        return try {
            val text = file.readText()
            if (text.isNotBlank()) json.decodeFromString<CachedExtendedTermForecast>(text) else null
        } catch (e: Exception) {
            Log.e("NewWeatherCache", "Error reading extended cache for $index", e)
            file.delete()
            null
        }
    }

    private fun saveExtendedToDisk(index: Long, item: CachedExtendedTermForecast) {
        val file = File(cacheDir, "extended_$index.json")
        try {
            val text = json.encodeToString(item)
            file.writeText(text)
        } catch (e: Exception) {
            Log.e("NewWeatherCache", "Error saving extended cache for $index", e)
        }
    }

    private fun keepShortInRam(index: Long, item: CachedShortTermForecast) {
        shortTermCache.clear()
        shortTermCache[index] = item
    }

    private fun keepExtendedInRam(index: Long, item: CachedExtendedTermForecast) {
        extendedForecastCache.clear()
        extendedForecastCache[index] = item
    }

    fun saveToDisk() {
        // Obsolet avec le stockage individuel par fichier de ville
    }

    fun clearCache() {
        shortTermCache.clear()
        extendedForecastCache.clear()
        lastShortFetchTimes.clear()
        lastExtendedFetchTimes.clear()
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            if (oldCacheFile.exists()) oldCacheFile.delete()
        } catch (e: Exception) {
            Log.e("NewWeatherCache", "Error clearing cache directory", e)
        }
    }

    fun clearFetchTimes() {
        lastShortFetchTimes.clear()
        lastExtendedFetchTimes.clear()
    }

    private fun isShortTermUsable(cached: CachedShortTermForecast): Boolean {
        val now = LocalDateTime.now()
        val lastStepTime = cached.data.steps.lastOrNull()?.time ?: return false
        return lastStepTime.isAfter(now)
    }

    private fun isExtendedUsable(cached: CachedExtendedTermForecast): Boolean {
        val today = LocalDate.now()
        val lastDay = cached.data.longTermDays.lastOrNull()?.date ?: return false
        return !lastDay.isBefore(today)
    }

    suspend fun getShortFromIndex(
        index: Long,
        forceRefresh: Boolean = false,
        fetchMetadata: suspend () -> CDNMeteoMetadata? = { null }
    ): Pair<ShortTermForecastUI?, CDNMeteoMetadata?> {
        var cachedPoint = shortTermCache[index]
        if (cachedPoint == null) {
            cachedPoint = loadShortFromDisk(index)
            if (cachedPoint != null) {
                keepShortInRam(index, cachedPoint)
            }
        }

        val now = System.currentTimeMillis()
        val lastFetchTime = lastShortFetchTimes[index] ?: cachedPoint?.fetchTimestamp ?: 0L

        val isUsable = cachedPoint != null && isShortTermUsable(cachedPoint)

        if (cachedPoint != null && isUsable && !forceRefresh && (now - lastFetchTime < 10 * 60 * 1000L)) {
            return Pair(cachedPoint.data, null)
        }

        val meta = fetchMetadata()
        if (meta != null) {
            lastShortFetchTimes[index] = now
        } else {
            return Pair(if (isUsable && cachedPoint != null) cachedPoint.data else null, null)
        }

        val serverRunUTC = meta.latestRunDatetimeUtc.toEventLocalDateTime(applicationContext)
            ?: return Pair(if (isUsable && cachedPoint != null) cachedPoint.data else null, meta)

        val point = cachedPoint ?: return Pair(null, meta)

        return if (!forceRefresh && isUsable && !point.runHourUTC.isBefore(serverRunUTC)) {
            Pair(point.data, meta)
        } else {
            Pair(null, meta)
        }
    }

    fun setShortFromIndex(
        index: Long,
        runHourUTC: LocalDateTime,
        data: ShortTermForecastUI
    ) {
        val now = System.currentTimeMillis()
        lastShortFetchTimes[index] = now
        val item = CachedShortTermForecast(
            data = data,
            pointIdx = index,
            runHourUTC = runHourUTC,
            fetchTimestamp = now
        )
        keepShortInRam(index, item)
        saveShortToDisk(index, item)
    }

    suspend fun getExtendedFromIndex(
        index: Long,
        forceRefresh: Boolean = false,
        fetchMetadata: suspend () -> CDNMeteoMetadata? = { null }
    ): Pair<ExtendedForecastUI?, CDNMeteoMetadata?> {
        var cachedPoint = extendedForecastCache[index]
        if (cachedPoint == null) {
            cachedPoint = loadExtendedFromDisk(index)
            if (cachedPoint != null) {
                keepExtendedInRam(index, cachedPoint)
            }
        }

        val now = System.currentTimeMillis()
        val lastFetchTime = lastExtendedFetchTimes[index] ?: cachedPoint?.fetchTimestamp ?: 0L

        val isUsable = cachedPoint != null && isExtendedUsable(cachedPoint)

        if (cachedPoint != null && isUsable && !forceRefresh && (now - lastFetchTime < 10 * 60 * 1000L)) {
            return Pair(cachedPoint.data, null)
        }

        val meta = fetchMetadata()
        if (meta != null) {
            lastExtendedFetchTimes[index] = now
        } else {
            return Pair(if (isUsable && cachedPoint != null) cachedPoint.data else null, null)
        }

        val serverRunUTC = meta.latestRunDatetimeUtc.toEventLocalDateTime(applicationContext)
            ?: return Pair(if (isUsable && cachedPoint != null) cachedPoint.data else null, meta)

        val point = cachedPoint ?: return Pair(null, meta)

        return if (!forceRefresh && isUsable && !point.runHourUTC.isBefore(serverRunUTC)) {
            Pair(point.data, meta)
        } else {
            Pair(null, meta)
        }
    }

    fun setExtendedFromIndex(
        index: Long,
        runHourUTC: LocalDateTime,
        data: ExtendedForecastUI
    ) {
        val now = System.currentTimeMillis()
        lastExtendedFetchTimes[index] = now
        val item = CachedExtendedTermForecast(
            data = data,
            pointIdx = index,
            runHourUTC = runHourUTC,
            fetchTimestamp = now
        )
        keepExtendedInRam(index, item)
        saveExtendedToDisk(index, item)
    }
}