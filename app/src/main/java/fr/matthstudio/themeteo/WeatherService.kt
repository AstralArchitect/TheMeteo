/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo

import android.content.Context
import android.os.Parcelable
import android.util.Log
import fr.matthstudio.themeteo.data.CityNameDiskCache
import fr.matthstudio.themeteo.data.LocalDateSerializer
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.data.VigilanceDomainProvider
import fr.matthstudio.themeteo.forecastMainActivity.toEventLocalDateTime
import fr.matthstudio.themeteo.proto.ExtendedForecastPointPayload
import fr.matthstudio.themeteo.proto.ShortTermPointPayload
import fr.matthstudio.themeteo.telemetry.TelemetryManager
import fr.matthstudio.themeteo.utilClasses.AirQualityForecastResponse
import fr.matthstudio.themeteo.utilClasses.AirQualityInfo
import fr.matthstudio.themeteo.utilClasses.AirQualityLocation
import fr.matthstudio.themeteo.utilClasses.AirQualityPeriod
import fr.matthstudio.themeteo.utilClasses.AirQualityRequest
import fr.matthstudio.themeteo.utilClasses.AlertStep
import fr.matthstudio.themeteo.utilClasses.MeteoFranceRainResponse
import fr.matthstudio.themeteo.utilClasses.PhenomenonAlert
import fr.matthstudio.themeteo.utilClasses.PollenResponse
import fr.matthstudio.themeteo.utilClasses.VigilanceInfos
import fr.matthstudio.themeteo.utilClasses.VigilanceMapResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache

private const val MAPS_GATEWAY_URL = "gateway.19374629.xyz"

// --- DATA CLASSES ---
@Serializable
data class PrecipitationData(
    val precipitation: Double?, // en mm
    val precipitationProbability: Int?, // en %
    val rain: Double?, // en mm
    val snowfall: Double?, // en cm
    val snowDepth: Int?, // en m
)

@Serializable
data class SkyInfoData(
    val cloudcoverTotal: Int?, // en %
    val cloudcoverLow: Int?,
    val cloudcoverMid: Int?,
    val cloudcoverHigh: Int?,
    val shortwaveRadiation: Double?, // en W/m^2
    val directRadiation: Double?, // en W/m^2
    val diffuseRadiation: Double?, // en W/m^2
    val opacity: Int?, // en %
    val uvIndex: Int?, // index UV
    val visibility: Int?, // en km
)

@Parcelize
@Serializable
data class WindData(
    val windspeed: Double?,
    val windGusts: Double?,
    val windDirection: Double?,
) : Parcelable

@Serializable
data class AllHourlyVarsReading(
    @Serializable(with = LocalDateTimeSerializer::class)
    val time: LocalDateTime,
    val temperature: Double?,
    val apparentTemperature: Double?,
    val precipitationData: PrecipitationData,
    val skyInfo: SkyInfoData,
    val wind: WindData,
    val pressure: Int?,
    val humidity: Int?,
    val dewpoint: Double?,
    val wmo: Int?,
    val ensembleStats: Map<String, EnsembleStat>? = null,
    val wmoEnsemble: WmoEnsembleStat? = null
)

@Parcelize
@Serializable
data class DailyReading(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val maxTemperature: Double?,
    val minTemperature: Double?,
    val precipitation: Double?,
    val maxWind: WindData,
    val maxUvIndex: Int?,
    val wmo: Int?,
    val sunset: String,
    val sunrise: String,
    val wmoEnsemble: WmoEnsembleStat? = null
) : Parcelable

@Parcelize
data class MinutelyReading(
    val time: LocalDateTime,
    val snowfall: Double?,
    val rain: Double?
) : Parcelable


@Serializable
data class CurrentWeatherReading(
    val temperature: Double?,
    val wmo: Int
)

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult>? = null)

@Serializable
data class GeocodingResult(
    val id: Int = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    @SerialName("admin1") val region: String? = null
)

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: List<GeocodingResult>) : SearchState()
    data class Error(val message: String) : SearchState()
    object Empty : SearchState()
}

fun Double?.nanToNull(): Double? {
    return if (this == null || this.isNaN()) null else this
}

@Serializable
data class VersionPolicyInfo(
    @SerialName("min_build") val minBuild: Int? = null,
    @SerialName("max_build") val maxBuild: Int? = null,
    @SerialName("min_version_code") val minVersionCode: Int? = null,
    @SerialName("blocked_version_codes") val blockedVersionCodes: List<Int> = emptyList(),
    @SerialName("gcu_url") val gcuUrl: String? = null,
    @SerialName("terms_url") val termsUrl: String? = null,
    @SerialName("privacy_policy_url") val privacyPolicyUrl: String? = null,
    @SerialName("last_gcu_update") val lastGcuUpdate: String? = null,
    @SerialName("last_privacy_policy_update") val lastPrivacyPolicyUpdate: String? = null
)

data class ResolvedPolicyInfo(
    val gcuUrl: String,
    val privacyPolicyUrl: String,
    val lastGcuUpdate: String,
    val lastPrivacyPolicyUpdate: String
)

@Serializable(with = PolicyUpdateInfoSerializer::class)
data class PolicyUpdateInfo(
    val minVersionCode: Int? = null,
    val blockedVersionCodes: List<Int> = emptyList(),
    val lastGcuUpdate: String? = null,
    val lastPrivacyPolicyUpdate: String? = null,
    val gcuUrl: String? = null,
    val privacyPolicyUrl: String? = null,
    val versionList: List<VersionPolicyInfo> = emptyList(),
    val versionMap: Map<String, VersionPolicyInfo> = emptyMap()
) {
    fun isUpdateRequired(versionCode: Int): Boolean {
        // 1. Check top level requirements
        if (minVersionCode != null && versionCode < minVersionCode) return true
        if (versionCode in blockedVersionCodes) return true

        // 2. Check version map rules if key matches
        for ((key, info) in versionMap) {
            if (matchesKey(key, versionCode)) {
                if (info.minVersionCode != null && versionCode < info.minVersionCode) return true
                if (versionCode in info.blockedVersionCodes) return true
            }
        }

        // 3. Check version list rules if bounds match
        for (info in versionList) {
            val minOk = info.minBuild == null || versionCode >= info.minBuild
            val maxOk = info.maxBuild == null || versionCode <= info.maxBuild
            if (minOk && maxOk) {
                if (info.minVersionCode != null && versionCode < info.minVersionCode) return true
                if (versionCode in info.blockedVersionCodes) return true
            }
        }

        return false
    }

    fun getPolicyForVersion(versionCode: Int): ResolvedPolicyInfo? {
        // 1. Check version map if present (e.g. ">780", "<=780")
        for ((key, info) in versionMap) {
            if (matchesKey(key, versionCode)) {
                val gUrl = info.gcuUrl ?: info.termsUrl ?: gcuUrl ?: return null
                val pUrl = info.privacyPolicyUrl ?: privacyPolicyUrl ?: gUrl
                val lastGcu = info.lastGcuUpdate ?: lastGcuUpdate ?: return null
                val lastPriv = info.lastPrivacyPolicyUpdate ?: lastPrivacyPolicyUpdate ?: return null
                return ResolvedPolicyInfo(
                    gcuUrl = gUrl,
                    privacyPolicyUrl = pUrl,
                    lastGcuUpdate = lastGcu,
                    lastPrivacyPolicyUpdate = lastPriv
                )
            }
        }

        // 2. Check version list if present
        for (info in versionList) {
            val minOk = info.minBuild == null || versionCode >= info.minBuild
            val maxOk = info.maxBuild == null || versionCode <= info.maxBuild
            if (minOk && maxOk) {
                val gUrl = info.gcuUrl ?: info.termsUrl ?: gcuUrl ?: return null
                val pUrl = info.privacyPolicyUrl ?: privacyPolicyUrl ?: gUrl
                val lastGcu = info.lastGcuUpdate ?: lastGcuUpdate ?: return null
                val lastPriv = info.lastPrivacyPolicyUpdate ?: lastPrivacyPolicyUpdate ?: return null
                return ResolvedPolicyInfo(
                    gcuUrl = gUrl,
                    privacyPolicyUrl = pUrl,
                    lastGcuUpdate = lastGcu,
                    lastPrivacyPolicyUpdate = lastPriv
                )
            }
        }

        // 3. Check top level if present
        if (lastGcuUpdate != null && lastPrivacyPolicyUpdate != null) {
            val gUrl = gcuUrl ?: return null
            val pUrl = privacyPolicyUrl ?: gUrl
            return ResolvedPolicyInfo(
                gcuUrl = gUrl,
                privacyPolicyUrl = pUrl,
                lastGcuUpdate = lastGcuUpdate,
                lastPrivacyPolicyUpdate = lastPrivacyPolicyUpdate
            )
        }

        return null
    }

    private fun matchesKey(key: String, versionCode: Int): Boolean {
        return when {
            key.startsWith(">=") -> versionCode >= (key.substring(2).trim().toIntOrNull() ?: Int.MIN_VALUE)
            key.startsWith(">") -> versionCode > (key.substring(1).trim().toIntOrNull() ?: Int.MIN_VALUE)
            key.startsWith("<=") -> versionCode <= (key.substring(2).trim().toIntOrNull() ?: Int.MAX_VALUE)
            key.startsWith("<") -> versionCode < (key.substring(1).trim().toIntOrNull() ?: Int.MAX_VALUE)
            key.toIntOrNull() != null -> versionCode == key.toInt()
            else -> false
        }
    }
}

object PolicyUpdateInfoSerializer : KSerializer<PolicyUpdateInfo> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("fr.matthstudio.themeteo.PolicyUpdateInfo")

    override fun serialize(encoder: Encoder, value: PolicyUpdateInfo) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Expected JsonEncoder")
        val map = mutableMapOf<String, JsonElement>()
        value.minVersionCode?.let { map["min_version_code"] = JsonPrimitive(it) }
        if (value.blockedVersionCodes.isNotEmpty()) {
            map["blocked_version_codes"] = JsonArray(value.blockedVersionCodes.map { JsonPrimitive(it) })
        }
        value.lastGcuUpdate?.let { map["last_gcu_update"] = JsonPrimitive(it) }
        value.lastPrivacyPolicyUpdate?.let { map["last_privacy_policy_update"] = JsonPrimitive(it) }
        value.gcuUrl?.let { map["gcu_url"] = JsonPrimitive(it) }
        value.privacyPolicyUrl?.let { map["privacy_policy_url"] = JsonPrimitive(it) }
        jsonEncoder.encodeJsonElement(JsonObject(map))
    }

    override fun deserialize(decoder: Decoder): PolicyUpdateInfo {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Expected JsonDecoder")
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) return PolicyUpdateInfo()

        val json = jsonDecoder.json

        val minVersionCode = element["min_version_code"]?.jsonPrimitive?.intOrNull
            ?: element["min_version"]?.jsonPrimitive?.intOrNull
        val blockedVersionCodes = (element["blocked_version_codes"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()

        val lastGcu = element["last_gcu_update"]?.jsonPrimitive?.contentOrNull
        val lastPrivacy = element["last_privacy_policy_update"]?.jsonPrimitive?.contentOrNull
        val gcuUrl = element["gcu_url"]?.jsonPrimitive?.contentOrNull ?: element["terms_url"]?.jsonPrimitive?.contentOrNull
        val privacyUrl = element["privacy_policy_url"]?.jsonPrimitive?.contentOrNull

        var versionList = emptyList<VersionPolicyInfo>()
        var versionMap = emptyMap<String, VersionPolicyInfo>()

        element["versions"]?.let { vElement ->
            when (vElement) {
                is JsonArray -> {
                    versionList = json.decodeFromJsonElement(ListSerializer(VersionPolicyInfo.serializer()), vElement)
                }
                is JsonObject -> {
                    versionMap = json.decodeFromJsonElement(MapSerializer(String.serializer(), VersionPolicyInfo.serializer()), vElement)
                }
                else -> {}
            }
        }

        return PolicyUpdateInfo(
            minVersionCode = minVersionCode,
            blockedVersionCodes = blockedVersionCodes,
            lastGcuUpdate = lastGcu,
            lastPrivacyPolicyUpdate = lastPrivacy,
            gcuUrl = gcuUrl,
            privacyPolicyUrl = privacyUrl,
            versionList = versionList,
            versionMap = versionMap
        )
    }
}

@Serializable
data class CDNMeteoMetadata(
    val format: String,
    val model: String,
    val domain: String,
    @SerialName("window_hours") val windowHours: Int? = null,
    @SerialName("window_offset_utc") val windowOffsetUtc: Int? = null,
    @SerialName("point_byte_size") val pointByteSize: Int,
    @SerialName("total_file_size_bytes") val totalFileSizeBytes: Long,
    @SerialName("spatial_shape") val spatialShape: List<Int>,
    val bounds: CDNMetadataBounds,
    @SerialName("n_steps") val nSteps: Int? = null,
    @SerialName("base_timestamp_utc") val baseTimestampUtc: Long? = null,
    @SerialName("base_datetime_utc") val baseDatetimeUtc: String? = null,
    @SerialName("reference_00z_timestamp_utc") val reference00zTimestampUtc: Long? = null,
    @SerialName("reference_00z_datetime_utc") val reference00zDatetimeUtc: String? = null,
    @SerialName("latest_run_datetime_utc") val latestRunDatetimeUtc: String,
    @SerialName("n_medium_steps") val nMediumSteps: Int? = null,
    @SerialName("min_hour_offset_from_00z") val minHourOffsetFrom00z: Int? = null,
    @SerialName("max_hour_offset_from_00z") val maxHourOffsetFrom00z: Int? = null,
    @SerialName("hour_offsets_from_00z") val hourOffsetsFrom00z: List<Int>? = null,
    @SerialName("has_aifs_long_term") val hasAifsLongTerm: Boolean? = null,
    @SerialName("aifs_days") val aifsDays: List<Int>? = null,
    @SerialName("version") val version: Int = 1,
    @SerialName("format_version") val formatVersion: Int? = null,
    @SerialName("aifs_steps_per_day") val aifsStepsPerDay: Int? = null
)

@Serializable
data class CDNMetadataBounds(
    @SerialName("lat_max") val latMax: Double,
    @SerialName("lat_min") val latMin: Double,
    @SerialName("lon_min") val lonMin: Double,
    @SerialName("lon_max") val lonMax: Double,
    val resolution: Double
) {
    fun contains(lat: Double, lon: Double): Boolean {
        return lat in latMin..latMax && lon in lonMin..lonMax
    }
}

class WeatherService(
    private val telemetryManager: TelemetryManager? = null,
    private val appContext: Context? = null,
    externalVigilanceDomainProvider: VigilanceDomainProvider? = null,
    private val isSharedInstance: Boolean = appContext != null
) {
    private data class TimedEntry<T>(val value: T, val timestampMs: Long)

    companion object {
        private const val VIGILANCE_TTL_MS = 15 * 60 * 1000L
        private const val AIR_QUALITY_TTL_MS = 30 * 60 * 1000L
        private const val ENV_FORECAST_TTL_MS = 60 * 60 * 1000L
        private const val RAIN_WITHIN_HOUR_TTL_MS = 4 * 60 * 1000L
        private const val METADATA_TTL_MS = 2 * 60 * 1000L
        private const val POLICY_TTL_MS = 30 * 60 * 1000L
        private const val NO_DEPT_SENTINEL = "__NONE__"
    }

    private val vigilanceDomainProvider by lazy {
        externalVigilanceDomainProvider ?: VigilanceDomainProvider(appContext)
    }

    // On définit la config une seule fois ici
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        coerceInputValues = true
    }

    private val client =
        HttpClient(OkHttp) {
            engine {
                config {
                    appContext?.let { ctx ->
                        cache(Cache(File(ctx.cacheDir, "okhttp_http_cache"), 20L * 1024L * 1024L))
                    }
                }
            }
            install(ContentNegotiation) {
                json(jsonParser)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000 // 15 secondes max
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Log.d("HTTP Client", message)
                        }
                    }
                    // LogLevel.INFO évite de sérialiser/afficher des réponses volumineuses en RAM
                    level = LogLevel.INFO
                }
            }
        }




    private val cityNameCache = ConcurrentHashMap<Pair<Double, Double>, String>()
    private val cityNameMutex = Mutex()
    private var cityNameDiskCache: CityNameDiskCache? = null

    private fun getOrCreateCityDiskCache(context: Context): CityNameDiskCache {
        return cityNameDiskCache ?: synchronized(this) {
            cityNameDiskCache ?: CityNameDiskCache(context.applicationContext).also {
                cityNameDiskCache = it
            }
        }
    }

    private val departmentCodeCache = ConcurrentHashMap<Pair<Double, Double>, String>()

    private val airQualityCache = ConcurrentHashMap<Pair<Double, Double>, TimedEntry<AirQualityInfo>>()
    private val airQualityForecastCache = ConcurrentHashMap<Pair<Double, Double>, TimedEntry<AirQualityForecastResponse>>()
    private val pollenCache = ConcurrentHashMap<Pair<Double, Double>, TimedEntry<PollenResponse>>()
    private val rainWithinHourCache = ConcurrentHashMap<Pair<Double, Double>, TimedEntry<MeteoFranceRainResponse>>()

    private val vigilanceMutex = Mutex()
    private var cachedVigilanceMap: TimedEntry<VigilanceMapResponse>? = null

    private val metadataMutex = Mutex()
    private var cachedAromeMeta: TimedEntry<CDNMeteoMetadata>? = null
    private var cachedIfsMeta: TimedEntry<CDNMeteoMetadata>? = null
    private var cachedExtendedMeta: TimedEntry<CDNMeteoMetadata>? = null

    private val policyMutex = Mutex()
    private var cachedPolicyInfo: TimedEntry<PolicyUpdateInfo>? = null

    fun clearVolatileCaches() {
        cachedAromeMeta = null
        cachedIfsMeta = null
        cachedExtendedMeta = null
        cachedVigilanceMap = null
        airQualityCache.clear()
        airQualityForecastCache.clear()
        pollenCache.clear()
        rainWithinHourCache.clear()
    }

    private fun roundCoord2(value: Double): Double = round(value * 100.0) / 100.0
    private fun roundCoord3(value: Double): Double = round(value * 1000.0) / 1000.0

    suspend fun searchCity(query: String): List<GeocodingResult>? {
        if (query.isBlank()) return emptyList()
        return try {
            val response = client.get("https://geocoding-api.open-meteo.com/v1/search") {
                parameter("name", query)
                parameter("count", 10)
                parameter("language", "fr")
                parameter("format", "json")
            }.body<GeocodingResponse>()
            response.results ?: emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("Geocoder", "Erreur de géocodage : ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }
    
    suspend fun getAirQuality(latitude: Double, longitude: Double, context: Context): AirQualityInfo? {
        val key = Pair(roundCoord2(latitude), roundCoord2(longitude))
        val now = System.currentTimeMillis()
        airQualityCache[key]?.takeIf { now - it.timestampMs < AIR_QUALITY_TTL_MS }?.let { return it.value }

        val url = "https://$MAPS_GATEWAY_URL/airquality/v1/currentConditions:lookup"



        return try {
            val response = client.post(url) {



                // On envoie la position dans le corps de la requête (POST)
                setBody(
                    AirQualityRequest(
                        location = AirQualityLocation(latitude, longitude),
                        extraComputations = listOf(
                            "LOCAL_AQI",
                            "HEALTH_RECOMMENDATIONS",
                            "DOMINANT_POLLUTANT_CONCENTRATION",
                            "POLLUTANT_CONCENTRATION"
                        ),
                        languageCode = java.util.Locale.getDefault().language
                    )
                )
                // Ktor s'occupe de la sérialisation en JSON grâce au plugin ContentNegotiation
                contentType(ContentType.Application.Json)
            }

            if (response.status.value == 200) {
                val result = response.body<AirQualityInfo>()
                airQualityCache[key] = TimedEntry(result, now)
                result
            } else {
                val errorBody = response.body<String>()
                Log.e("AirQuality", "Erreur API Google : ${response.status} - $errorBody")
                telemetryManager?.logEvent("api_error", mapOf("api" to "google-air-quality", "status" to response.status.value, "error" to errorBody))
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("AirQuality", "Exception lors de la récupération de la qualité de l'air : ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    suspend fun getAirQualityForecast(latitude: Double, longitude: Double, context: Context): AirQualityForecastResponse? {
        val key = Pair(roundCoord2(latitude), roundCoord2(longitude))
        val now = System.currentTimeMillis()
        airQualityForecastCache[key]?.takeIf { now - it.timestampMs < ENV_FORECAST_TTL_MS }?.let { return it.value }

        val url = "https://$MAPS_GATEWAY_URL/airquality/v1/forecast:lookup"
        val nowUtc = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0)
        // Google Air Quality Forecast requiert strictement que startTime soit dans le futur (prochaine heure UTC)
        val startTimeStr = nowUtc.plusHours(1).format(java.time.format.DateTimeFormatter.ISO_INSTANT)
        val endTimeStr = nowUtc.plusHours(72).format(java.time.format.DateTimeFormatter.ISO_INSTANT)



        return try {
            val response = client.post(url) {



                setBody(
                    AirQualityRequest(
                        location = AirQualityLocation(latitude, longitude),
                        extraComputations = listOf(
                            "LOCAL_AQI",
                            "HEALTH_RECOMMENDATIONS",
                            "DOMINANT_POLLUTANT_CONCENTRATION",
                            "POLLUTANT_CONCENTRATION"
                        ),
                        period = AirQualityPeriod(
                            startTime = startTimeStr,
                            endTime = endTimeStr
                        ),
                        pageSize = 72,
                        languageCode = java.util.Locale.getDefault().language
                    )
                )
                contentType(ContentType.Application.Json)
            }

            if (response.status.value == 200) {
                val result = response.body<AirQualityForecastResponse>()
                airQualityForecastCache[key] = TimedEntry(result, now)
                result
            } else {
                val errorBody = response.body<String>()
                Log.e("AirQuality", "Erreur API Google Forecast : ${response.status} - $errorBody")
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("AirQuality", "Exception lors de la récupération des prévisions d'air : ${e.message}")
            null
        }
    }

    suspend fun getCityNameFromCoords(latitude: Double, longitude: Double, context: Context): String? {
        val key = Pair(roundCoord2(latitude), roundCoord2(longitude))
        cityNameCache[key]?.let { return it }

        return cityNameMutex.withLock {
            cityNameCache[key]?.let { return@withLock it }

            val diskCache = getOrCreateCityDiskCache(context)
            val diskCached = diskCache.get(latitude, longitude)
            if (diskCached != null) {
                cityNameCache[key] = diskCached
                return@withLock diskCached
            }

            val url = "https://$MAPS_GATEWAY_URL/maps/api/geocode/json"

            try {
                val response = client.get(url) {
                    parameter("latlng", "$latitude,$longitude")
    
                    parameter("language", java.util.Locale.getDefault().language)
                    parameter("result_type", "locality|sublocality|administrative_area_level_1|administrative_area_level_2")

    
                }
                if (response.status.value == 200) {
                    val geocodingResponse = response.body<GoogleGeocodingResponse>()
                    if (geocodingResponse.status == "OK" && geocodingResponse.results.isNotEmpty()) {
                        val components = geocodingResponse.results.first().addressComponents
                        val name = components.find { "locality" in it.types }?.longName
                            ?: components.find { "sublocality" in it.types }?.longName
                            ?: components.find { "administrative_area_level_2" in it.types }?.longName
                            ?: components.find { "administrative_area_level_1" in it.types }?.longName
                        if (name != null) {
                            cityNameCache[key] = name
                            diskCache.put(latitude, longitude, name)
                        }
                        name
                    } else null
                } else null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                telemetryManager?.logException(e)
                null
            }
        }
    }

    /**
     * Récupère les prévisions de pollen auprès de l'API Google Maps Pollen.
     */
    suspend fun getPollenForecast(latitude: Double, longitude: Double, context: Context): PollenResponse? {
        val key = Pair(roundCoord2(latitude), roundCoord2(longitude))
        val now = System.currentTimeMillis()
        pollenCache[key]?.takeIf { now - it.timestampMs < ENV_FORECAST_TTL_MS }?.let { return it.value }

        // L'endpoint pour les prévisions (forecast)
        val url = "https://$MAPS_GATEWAY_URL/pollen/v1/forecast:lookup"

        // Réutilisation de la sécurité SHA-1 et package name comme pour l'Air Quality



        return try {
            val response = client.get(url) {
                // Paramètres de l'API Google

                parameter("location.latitude", latitude)
                parameter("location.longitude", longitude)
                parameter("days", 3) // Nombre de jours de prévisions (1 à 5)
                parameter("languageCode", java.util.Locale.getDefault().language)
                parameter("plantsDescription", "true")

                // Headers de sécurité pour restreindre l'usage de la clé API à votre app


            }

            if (response.status.value == 200) {
                val result = response.body<PollenResponse>()
                pollenCache[key] = TimedEntry(result, now)
                result
            } else {
                val errorBody = response.body<String>()
                Log.e("PollenAPI", "Erreur API Google : ${response.status} - $errorBody")
                telemetryManager?.logEvent("api_error", mapOf("api" to "google-pollen", "status" to response.status.value, "error" to errorBody))
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("PollenAPI", "Exception lors de la récupération des données pollen : ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    /**
     * Récupère la carte nationale de vigilance Météo-France avec mise en cache mémoire + disque (15 min),
     * afin de ne pas retélécharger le fichier national complet pour chaque ville ou réveil du Worker.
     */
    private suspend fun getNationalVigilanceMap(): VigilanceMapResponse? = vigilanceMutex.withLock {
        val now = System.currentTimeMillis()
        cachedVigilanceMap?.takeIf { now - it.timestampMs < VIGILANCE_TTL_MS }?.let {
            return@withLock it.value
        }

        val diskFile = appContext?.let { File(it.cacheDir, "vigilance_encours_cache.json") }
        if (diskFile != null && diskFile.exists() && now - diskFile.lastModified() < VIGILANCE_TTL_MS) {
            runCatching {
                val cached = jsonParser.decodeFromString<VigilanceMapResponse>(diskFile.readText())
                cachedVigilanceMap = TimedEntry(cached, diskFile.lastModified())
                return@withLock cached
            }.onFailure {
                diskFile.delete()
            }
        }

        val url = "https://public-api.meteofrance.fr/public/DPVigilance/v1/cartevigilance/encours"
        val apiKey = BuildConfig.METEO_FRANCE_VIGILANCE_API_KEY

        try {
            val response = client.get(url) {
                header("accept", "*/*")
                header("apikey", apiKey)
            }

            if (response.status.value == 200) {
                val fullResponse = response.body<VigilanceMapResponse>()
                cachedVigilanceMap = TimedEntry(fullResponse, now)
                if (diskFile != null) {
                    runCatching { diskFile.writeText(jsonParser.encodeToString(fullResponse)) }
                }
                fullResponse
            } else {
                val errorBody = response.body<String>()
                Log.e("WeatherService", "Erreur API Carte Vigilance : ${response.status} - $errorBody")
                telemetryManager?.logEvent("api_error", mapOf("api" to "meteo-france-vigilance", "status" to response.status.value, "error" to errorBody))
                cachedVigilanceMap?.value
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Exception lors de la récupération de la carte vigilance", e)
            telemetryManager?.logException(e)
            cachedVigilanceMap?.value
        }
    }

    /**
     * Récupère les textes de vigilance spécifiques au département de l'utilisateur.
     */
    suspend fun getVigilanceForLocation(lat: Double, lon: Double): VigilanceInfos? {
        val departmentCode = getDepartmentCodeFromLocation(lat, lon) ?: return null
        val fullResponse = getNationalVigilanceMap() ?: return null

        return try {
            val now = OffsetDateTime.now()

            // 1. On récupère les périodes qui contiennent notre département
            val periodsWithDept = fullResponse.product.periods.mapNotNull { period ->
                val dept = period.timelaps.domainIds.find { it.domainId == departmentCode }
                if (dept != null) period to dept else null
            }

            // 2. Fusionner les alertes par phénomène
            val mergedAlerts = periodsWithDept
                .flatMap { (period, dept) ->
                    dept.phenomenonItems.map { it to period }
                }
                .filter { (phenom, _) -> phenom.phenomenonMaxColorId > 1 }
                .groupBy { (phenom, _) -> phenom.phenomenonId }
                .mapNotNull { (id, pairs) ->
                    // On traite chaque paire (PhenomenonItem, VigilancePeriod)
                    val steps = pairs.flatMap { (phenom, period) ->
                        if (phenom.timelapsItems.isEmpty()) {
                            // Si pas de timelaps détaillés (ex : Crues), on crée un step couvrant toute la période
                            listOf(AlertStep(period.beginValidityTime, period.endValidityTime, phenom.phenomenonMaxColorId))
                        } else {
                            phenom.timelapsItems.map { AlertStep(it.beginTime, it.endTime, it.colorId) }
                        }
                    }

                    // Filtrer les steps qui sont terminés
                    val filteredSteps = steps.filter {
                        try {
                            OffsetDateTime.parse(it.endTime).isAfter(now)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            true
                        }
                    }.sortedBy { it.beginTime }

                    if (filteredSteps.isEmpty()) return@mapNotNull null

                    PhenomenonAlert(
                        phenomenonId = id,
                        maxColorId = filteredSteps.maxOf { it.colorId },
                        steps = filteredSteps
                    )
                }

            // 3. Déterminer le maxColorId global sur les alertes restantes (1 par défaut si vide)
            val globalMaxColor = mergedAlerts.maxOfOrNull { it.maxColorId } ?: 1

            VigilanceInfos(
                departmentCode = departmentCode,
                maxColorId = globalMaxColor,
                alerts = mergedAlerts
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Exception lors du traitement de la carte vigilance", e)
            telemetryManager?.logException(e)
            null
        }
    }

    /**
     * Récupère les prévisions de pluie dans l'heure de Météo-France.
     */
    suspend fun getRainWithinHour(lat: Double, lon: Double): MeteoFranceRainResponse? {
        val key = Pair(roundCoord3(lat), roundCoord3(lon))
        val now = System.currentTimeMillis()
        rainWithinHourCache[key]?.takeIf { now - it.timestampMs < RAIN_WITHIN_HOUR_TTL_MS }?.let {
            return it.value
        }

        val url = "https://webservice.meteofrance.com/v3/rain"
        val token = "__Wj7dVSTjV9YGu1guveLyDq0g7S7TfTjaHBTPTpO0kj8__"

        return try {
            val response = client.get(url) {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("token", token)
            }

            if (response.status.value == 200) {
                val result = response.body<MeteoFranceRainResponse>()
                rainWithinHourCache[key] = TimedEntry(result, now)
                result
            } else {
                val errorBody = response.body<String>()
                Log.e("WeatherService", "Erreur API Rain Within Hour : ${response.status} - $errorBody")
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Exception lors de la récupération de la pluie dans l'heure : ${e.message}")
            null
        }
    }

    /**
     * Récupère le code du département pour des coordonnées (lat, lon).
     * Utilise l'intersection spatiale avec le GeoJSON officiel de Météo-France (avec enclaves fusionnées).
     */
    private suspend fun getDepartmentCodeFromLocation(lat: Double, lon: Double): String? {
        val key = Pair(roundCoord3(lat), roundCoord3(lon))
        departmentCodeCache[key]?.let { cached ->
            return if (cached == NO_DEPT_SENTINEL) null else cached
        }

        try {
            val domainCode = vigilanceDomainProvider.getVigilanceDomain(lat, lon)
            if (!domainCode.isNullOrBlank()) {
                val formatted = formatDeptCode(domainCode)
                departmentCodeCache[key] = formatted
                Log.d("WeatherService", "Département trouvé via GeoJSON Météo-France : $formatted")
                return formatted
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Erreur lors de la recherche spatiale du domaine de vigilance", e)
        }

        // Fallback secours : API Pluie Météo-France (/v3/rain), limité à la France métropolitaine / Corse
        if (lat in 41.0..51.5 && lon in -5.5..10.0) {
            try {
                val rainData = getRainWithinHour(lat, lon)
                val deptFromRain = rainData?.properties?.frenchDepartment
                if (!deptFromRain.isNullOrBlank()) {
                    val formatted = formatDeptCode(deptFromRain)
                    departmentCodeCache[key] = formatted
                    Log.d("WeatherService", "Département trouvé via Météo-France Rain API : $formatted")
                    return formatted
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("WeatherService", "Erreur Météo-France Rain API", e)
            }
        }

        return null
    }

    private fun formatDeptCode(dept: String): String {
        val trimmed = dept.trim()
        return if (trimmed.length == 1 && trimmed.all { it.isDigit() }) {
            "0$trimmed"
        } else {
            trimmed
        }
    }

    suspend fun getCurrentWeather(positions: List<Pair<Double, Double>>): Map<Pair<Double, Double>, CurrentWeatherReading>? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"

        try {
            val latitudes = positions.joinToString(",") { it.first.toString() }
            val longitudes = positions.joinToString(",") { it.second.toString() }
            val response = client.get(apiUrl) {
                parameter("latitude", latitudes)
                parameter("longitude", longitudes)
                parameter("current", "temperature_2m,weather_code")
            }

            // 1. Log le code de statut (200, 400, 500 ?)
            Log.d("WeatherService", "Status: ${response.status}")

            if (response.status.value == 200) {
                // 1. On récupère le corps en String brute
                val bodyText = response.body<String>()
                val jsonElement = jsonParser.parseToJsonElement(bodyText)

                // 2. On transforme en liste de WeatherApiResponse
                val apiResponses: List<WeatherApiResponse> = if (jsonElement is JsonArray) {
                    // C'est déjà une liste (plusieurs positions)
                    jsonParser.decodeFromJsonElement<List<WeatherApiResponse>>(jsonElement)
                } else {
                    // C'est un objet simple (une seule position), on le met dans une liste
                    listOf(jsonParser.decodeFromJsonElement<WeatherApiResponse>(jsonElement))
                }

                val results: MutableMap<Pair<Double, Double>, CurrentWeatherReading> = mutableMapOf()
                val parsedData = parseCurrentWeatherData(apiResponses) ?: return null
                if (parsedData.size != positions.size) return null
                for (i in parsedData.indices) {
                    results[positions[i]] = parsedData[i]
                }

                return results
            } else {
                val errorText = response.body<String>()
                Log.e("WeatherService", "Erreur API : $errorText")
                telemetryManager?.logEvent("api_error", mapOf("api" to "open-meteo-current", "status" to response.status.value, "error" to errorText))
                return null
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("getCurrentWeather", "Erreur lors de la récupération des prévisions complètes: ${e.message}")
            telemetryManager?.logException(e)
            return null
        }
    }

    fun getPointIndex(lon: Double, lat: Double, forceGlobal: Boolean = false): Long {
        val AROME = !forceGlobal && shouldUseAROME(lon, lat)
        return if (AROME) {
            // Grille France 0.025° (717 x 1121)
            val rowY = ((55.4 - lat) / 0.025).roundToInt()
            val colX = ((lon + 12.0) / 0.025).roundToInt()
            rowY.toLong() * 1121L + colX.toLong()
        } else {
            // Grille Mondiale 0.25° (721 x 1440)
            val rowY = ((90.0 - lat) / 0.25).roundToInt()
            val colX = ((lon + 180.0) / 0.25).roundToInt() % 1440
            rowY.toLong() * 1440L + colX.toLong()
        }
    }

    fun getRangeForLocation(lon: Double, lat: Double, pointByteSize: Int, forceGlobal: Boolean = false): Pair<Long, Long> {
        val pointIndex = getPointIndex(lon, lat, forceGlobal)
        val byteStart = pointIndex * pointByteSize.toLong()
        val byteEnd = byteStart + pointByteSize.toLong() - 1
        return Pair(byteStart, byteEnd)
    }

    fun shouldUseAROME(lon: Double, lat: Double): Boolean {
        // 1. Boîte englobante
        if (lat !in 37.5..55.375) {
            return false
        }

        // 2. Demi-largeur conique Lambert (symétrique autour du méridien 2.0°E)
        var halfWidth = 10.175 + (13.875 - 10.175) * ((lat - 37.5) / (54.15 - 37.5))

        // 3. Correction de l'arc conique supérieur (Nord de la France)
        if (lat > 54.15) {
            val maxConic = sqrt((55.375 - lat) / 0.006363)
            halfWidth = halfWidth.coerceAtMost(maxConic)
        }

        return abs(lon - 2.0) <= halfWidth
    }

    suspend fun getAromeMetadata(): CDNMeteoMetadata? = metadataMutex.withLock {
        val now = System.currentTimeMillis()
        cachedAromeMeta?.takeIf { now - it.timestampMs < METADATA_TTL_MS }?.let { return@withLock it.value }

        val url = "https://weatherdata.19374629.xyz/arome_short_term_metadata.json"
        try {
            val response = client.get(url)
            if (response.status.value / 100 == 2) {
                val meta = response.body<CDNMeteoMetadata>()
                cachedAromeMeta = TimedEntry(meta, now)
                meta
            } else {
                Log.e("WeatherService", "Error fetching AROME metadata: ${response.status}")
                throw Exception("Error fetching AROME metadata: ${response.status}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Exception fetching AROME metadata: ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    /**
     * Vérifie si les coordonnées fournies sont situées dans le domaine de France
     * défini par les métadonnées AROME (bounds : latMin <= lat <= latMax && lonMin <= lon <= lonMax).
     */
    suspend fun isPointInFranceDomain(lat: Double, lon: Double): Boolean {
        val meta = getAromeMetadata()
        val bounds = meta?.bounds
        return if (bounds != null) {
            bounds.contains(lat, lon)
        } else {
            isPointInFranceDomainSync(lat, lon)
        }
    }

    /**
     * Vérification synchrone utilisant les métadonnées en cache ou les bornes par défaut du domaine France.
     */
    fun isPointInFranceDomainSync(lat: Double, lon: Double): Boolean {
        val bounds = cachedAromeMeta?.value?.bounds
        return if (bounds != null) {
            bounds.contains(lat, lon)
        } else {
            lat in 37.5..55.4 && lon in -12.0..16.0
        }
    }

    suspend fun getIfsMetadata(): CDNMeteoMetadata? = metadataMutex.withLock {
        val now = System.currentTimeMillis()
        cachedIfsMeta?.takeIf { now - it.timestampMs < METADATA_TTL_MS }?.let { return@withLock it.value }

        val url = "https://weatherdata.19374629.xyz/ifs_short_term_metadata.json"
        try {
            val response = client.get(url)
            if (response.status.value / 100 == 2) {
                val meta = response.body<CDNMeteoMetadata>()
                cachedIfsMeta = TimedEntry(meta, now)
                meta
            } else {
                Log.e("WeatherService", "Error fetching IFS metadata: ${response.status}")
                throw Exception("Error fetching IFS metadata: ${response.status}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Exception fetching IFS metadata: ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    suspend fun getExtendedMetadata(): CDNMeteoMetadata? = metadataMutex.withLock {
        val now = System.currentTimeMillis()
        cachedExtendedMeta?.takeIf { now - it.timestampMs < METADATA_TTL_MS }?.let { return@withLock it.value }

        val url = "https://weatherdata.19374629.xyz/global_extended_forecast_metadata.json"
        try {
            val response = client.get(url)
            if (response.status.value / 100 == 2) {
                val meta = response.body<CDNMeteoMetadata>()
                cachedExtendedMeta = TimedEntry(meta, now)
                meta
            } else {
                Log.e("WeatherService", "Error fetching Extended metadata: ${response.status}")
                throw Exception("Error fetching Extended metadata: ${response.status}")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Exception fetching Extended metadata: ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    suspend fun downloadShortTermMeta(lon: Double, lat: Double): CDNMeteoMetadata? {
        val useAROME = shouldUseAROME(lon, lat)
        return if (useAROME) getAromeMetadata() else getIfsMetadata()
    }

    suspend fun downloadExtendedMeta(): CDNMeteoMetadata? {
        return getExtendedMetadata()
    }

    /**
     * Télécharge une tranche binaire stricte (HTTP 206 Partial Content uniquement) en streaming,
     * afin d'interdire tout téléchargement accidentel du fichier complet (1,7 à 6 Go) si un proxy/CDN répond 200 OK.
     */
    private suspend fun fetchRangeBytes(url: String, range: Pair<Long, Long>): ByteArray {
        val expectedLength = (range.second - range.first + 1L)
        val maxAllowedLength = max(expectedLength * 2L, 65_536L)

        return client.prepareGet(url) {
            header(HttpHeaders.Range, "bytes=${range.first}-${range.second}")
            header(HttpHeaders.AcceptEncoding, "identity")
        }.execute { response ->
            if (response.status != HttpStatusCode.PartialContent) {
                throw IllegalStateException("Expected 206 Partial Content for $url, got ${response.status}")
            }
            val contentLength = response.contentLength()
            if (contentLength != null && (contentLength <= 0L || contentLength > maxAllowedLength)) {
                throw IllegalStateException("Refusing oversized range payload ($contentLength bytes) from $url")
            }
            val bytesToRead = (contentLength ?: expectedLength).toInt()
            response.readBytes(bytesToRead)
        }
    }

    suspend fun getShortTermForecast(
        lon: Double,
        lat: Double,
        context: Context,
        metadata: CDNMeteoMetadata? = null
    ): Pair<ShortTermPointPayload, LocalDateTime>? {
        return try {
            val useAROME = shouldUseAROME(lon, lat)
            val meta = metadata
                ?: (if (useAROME) getAromeMetadata() else getIfsMetadata())
                ?: throw Exception("Failed to get short-term metadata")
            val range = getRangeForLocation(lon, lat, meta.pointByteSize)
            val binUrl = if (useAROME) {
                "https://weatherdata.19374629.xyz/arome_short_term.bin"
            } else {
                "https://weatherdata.19374629.xyz/ifs_short_term.bin"
            }

            val bytes = fetchRangeBytes(binUrl, range)
            val response = ShortTermPointPayload.parseFrom(bytes)
            val runUTC = meta.latestRunDatetimeUtc.toEventLocalDateTime(context)
                ?: throw Exception("Unable to get LocalDateTime from the metadata")

            Pair(response, runUTC)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Error in getShortTermForecast: ${e.message}")
            null
        }
    }

    suspend fun getExtendedForecast(
        lon: Double,
        lat: Double,
        context: Context,
        metadata: CDNMeteoMetadata? = null
    ): Pair<ExtendedForecastPointPayload, LocalDateTime>? {
        return try {
            val meta = metadata ?: getExtendedMetadata() ?: throw Exception("Failed to get metadata from extended forecast")
            val range = getRangeForLocation(lon, lat, meta.pointByteSize, true)

            val bytes = fetchRangeBytes("https://weatherdata.19374629.xyz/global_extended_forecast.bin", range)
            val response = ExtendedForecastPointPayload.parseFrom(bytes)

            val runUTC = meta.latestRunDatetimeUtc.toEventLocalDateTime(context)
                ?: throw Exception("Unable to get LocalDateTime from the metadata")

            Pair(response, runUTC)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Error in getExtendedForecast: ${e.message}")
            null
        }
    }

    fun parseCurrentWeatherData(response: List<WeatherApiResponse>): List<CurrentWeatherReading>? {
        try {
            val result: MutableList<CurrentWeatherReading> = mutableListOf()
            for (currentLocationData in response) {
                val temp = currentLocationData.getCurrentWeatherData("temperature_2m")
                val wmo = currentLocationData.getCurrentWeatherData("weather_code")
                result.add(CurrentWeatherReading(
                    temperature = (temp as? Double).nanToNull(),
                    wmo = (wmo as Double).toInt()
                ))
            }
            return result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherServiceParser", "Erreur lors du parsing des données actuelles", e)
            telemetryManager?.logException(e)
            return null
        }
    }

    fun close() {
        if (!isSharedInstance) {
            client.close()
        }
    }

    suspend fun getPolicyUpdateInfo(): PolicyUpdateInfo? = policyMutex.withLock {
        val now = System.currentTimeMillis()
        cachedPolicyInfo?.takeIf { now - it.timestampMs < POLICY_TTL_MS }?.let { return@withLock it.value }

        try {
            val responseText = client.get("https://themeteodocs.19374629.xyz/last-updates.json").bodyAsText()
            val decoded = jsonParser.decodeFromString<PolicyUpdateInfo>(responseText)
            cachedPolicyInfo = TimedEntry(decoded, now)
            decoded
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Error fetching policy updates: ${e.message}")
            telemetryManager?.logException(e)
            cachedPolicyInfo?.value
        }
    }

    suspend fun getTermsOfUse(): String? {
        return try {
            client.get("https://themeteodocs.19374629.xyz/terms.html").body<String>()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("WeatherService", "Error fetching terms of use: ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }
}
