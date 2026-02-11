package fr.matthstudio.themeteo

import android.os.Parcelable
import android.util.Log
import android.content.Context
import android.location.Geocoder
import fr.matthstudio.themeteo.data.LocalDateSerializer
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.data.SavedLocation
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*

// Commenter lorsqu'on utilise pas le logging
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger

import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.lang.Math.clamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections.emptyList
import kotlin.math.max
import kotlin.math.roundToInt

// --- DATA CLASSES (inchangées) ---
@Serializable

data class PrecipitationData(
    val precipitation: Double, // en mm
    val precipitationProbability: Int?, // en %
    val rain: Double, // en mm
    val snowfall: Double, // en cm
    val snowDepth: Int?, // en m
)

@Serializable
data class SkyInfoData(
    val cloudcoverTotal: Int, // en %
    val cloudcoverLow: Int,
    val cloudcoverMid: Int,
    val cloudcoverHigh: Int,
    val shortwaveRadiation: Double?, // en W/m^2
    val directRadiation: Double?, // en W/m^2
    val diffuseRadiation: Double?, // en W/m^2
    val opacity: Int?, // en %
    val uvIndex: Int?, // index UV
    val visibility: Int?, // en km
)

@Serializable
data class AllHourlyVarsReading(
    @Serializable(with = LocalDateTimeSerializer::class)
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double?,
    val precipitationData: PrecipitationData,
    val skyInfo: SkyInfoData,
    val windspeed: Double,
    val windDirection: Double,
    val pressure: Int,
    val humidity: Int,
    val dewpoint: Double,
    val wmo: Int
)

@Parcelize
@Serializable
data class DailyReading(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val maxTemperature: Double,
    val minTemperature: Double,
    val precipitation: Double,
    val max_uvIndex: Int?,
    val wmo: Int,
    val sunset: String,
    val sunrise: String
) : Parcelable

@Parcelize
data class MinutelyReading(
    val time: LocalDateTime,
    val snowfall: Double,
    val rain: Double
) : Parcelable

@Parcelize
data class CurrentWeatherReading(
    val temperature: Double,
    val wmo: Int
) : Parcelable

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult>? = null)

@Serializable
data class GeocodingResult(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("country_code") val countryCode: String,
    @SerialName("admin1") val region: String
)

class WeatherService {
    // On définit la config une seule fois ici
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }
    private val client =
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(jsonParser)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15000 // 15 secondes max
                connectTimeoutMillis = 10000
            }
            // DÉCOMMENTER CE BLOC POUR LE LOGGING
            if (BuildConfig.DEBUG)
            {
                install(Logging) {
                    logger = object : Logger {
                        override fun log(message: String) {
                            Log.d("HTTP Client", message) // Affiche les logs dans Logcat
                        }
                    }
                    level = LogLevel.ALL // Log toutes les informations, y compris l'URL et le corps de la réponse
                }
            }
        }

    suspend fun searchCity(query: String): List<GeocodingResult>? {
        if (query.isBlank()) return emptyList()
        return try {
            client.get("https://geocoding-api.open-meteo.com/v1/search") {
                parameter("name", query)
                parameter("count", 10)
                parameter("language", "fr")
                parameter("format", "json")
            }.body<GeocodingResponse>().results
        } catch (e: Exception) {
            Log.e("Geocoder", "Erreur de géocodage : ${e.message}")
            null
        }
    }

    suspend fun getCurrentWeather(positions: List<Pair<Double, Double>>) : Map<Pair<Double, Double>, CurrentWeatherReading>? {
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
                // 2. Si c'est avant 8h, tu verras probablement un code 400 ici
                val errorText = response.body<String>()
                Log.e("WeatherService", "Erreur API : $errorText")
                return null
            }

        } catch (e: Exception) {
            Log.e("getCurrentWeather", "Erreur lors de la récupération des prévisions complètes: ${e.message}")
            return null
        }
    }

    /**
     * Récupère l'intégralité des prévisions pour une période étendue.
     * C'est la fonction que le WeatherCache utilisera.
     */
    suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        model: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Pair<List<AllHourlyVarsReading>, List<DailyReading>>? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"
        val localZoneId = ZoneId.systemDefault()

        return try {
            val response = client.get(apiUrl) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("models", model)
                parameter("hourly", getHourlyVariables().joinToString(","))
                parameter("daily", getDailyVariables().joinToString(","))
                parameter("timezone", localZoneId.id)
                parameter("start_date", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                parameter("end_date", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }

            // 1. Log le code de statut (200, 400, 500 ?)
            Log.d("WeatherService", "Status: ${response.status}")

            if (response.status.value == 200) {
                return Pair(parseHourlyData(response.body()) as List<AllHourlyVarsReading>,
                    parseDailyData(response.body()) as List<DailyReading>)
            } else {
                // 2. Si c'est avant 8h, tu verras probablement un code 400 ici
                val errorText = response.body<String>()
                Log.e("WeatherService", "Erreur API : $errorText")
                return null
            }

        } catch (e: Exception) {
            Log.e("getForecast", "Erreur lors de la récupération des prévisions complètes: ${e.message}")
            null
        }
    }

    private fun getHourlyVariables(): List<String> {
        val variables = mutableListOf(
            "temperature_2m", "apparent_temperature", "precipitation", "rain", "showers", "snowfall",
            "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high", "weather_code",
            "windspeed_10m", "wind_direction_10m", "pressure_msl", "relative_humidity_2m", "dewpoint_2m",
            "precipitation_probability", "snow_depth", "shortwave_radiation_instant", "direct_radiation_instant",
            "diffuse_radiation_instant", "uv_index", "visibility"
        )
        return variables
    }

    private fun parseHourlyData(response: WeatherApiResponse): List<AllHourlyVarsReading>? {
        try {
            val times = response.getDeterministicHourlyData("time") ?: return null

            // Récupération sécurisée de toutes les listes de données
            val temp = response.getDeterministicHourlyData("temperature_2m")
            val appTemp = response.getDeterministicHourlyData("apparent_temperature")
            val precip = response.getDeterministicHourlyData("precipitation")
            val precipProb = response.getDeterministicHourlyData("precipitation_probability")
            val rain = response.getDeterministicHourlyData("rain")
            val showers = response.getDeterministicHourlyData("showers")
            val snowfall = response.getDeterministicHourlyData("snowfall")
            val snowDepth = response.getDeterministicHourlyData("snow_depth")
            val cloud = response.getDeterministicHourlyData("cloudcover")
            val cloudLow = response.getDeterministicHourlyData("cloudcover_low")
            val cloudMid = response.getDeterministicHourlyData("cloudcover_mid")
            val cloudHigh = response.getDeterministicHourlyData("cloudcover_high")
            val ghi = response.getDeterministicHourlyData("shortwave_radiation_instant")
            val dhi = response.getDeterministicHourlyData("diffuse_radiation_instant")
            val dsi = response.getDeterministicHourlyData("direct_radiation_instant")
            val wui = response.getDeterministicHourlyData("uv_index")
            val visibility = response.getDeterministicHourlyData("visibility")
            val windspeed = response.getDeterministicHourlyData("windspeed_10m")
            val windDir = response.getDeterministicHourlyData("wind_direction_10m")
            val pressure = response.getDeterministicHourlyData("pressure_msl")
            val humidity = response.getDeterministicHourlyData("relative_humidity_2m")
            val dewpoint = response.getDeterministicHourlyData("dewpoint_2m")
            val wmo = response.getDeterministicHourlyData("weather_code")

            return times.indices.mapNotNull { i ->
                try {
                    val o = if (ghi != null && dhi != null)
                            (clamp(dhi[i] as Double / max(ghi[i] as Double, 1.0), 0.0, 1.0) * 100.0).roundToInt()
                    else null
                    AllHourlyVarsReading(
                        time = LocalDateTime.parse(times[i] as String),
                        temperature = temp?.get(i) as Double? ?: 0.0,
                        apparentTemperature = appTemp?.get(i) as Double?,
                        precipitationData = PrecipitationData(
                            precipitation = precip?.get(i) as Double? ?: 0.0,
                            precipitationProbability = (precipProb?.get(i) as Double?)?.toInt(),
                            rain = (rain?.get(i) as Double? ?: 0.0) + (showers?.get(i) as Double? ?: 0.0),
                            snowfall = snowfall?.get(i) as Double? ?: 0.0,
                            snowDepth = ((snowDepth?.get(i) as Double?)?.times(100))?.toInt()
                        ),
                        skyInfo = SkyInfoData(
                            cloudcoverTotal = (cloud?.get(i) as Double?)?.toInt() ?: 0,
                            cloudcoverLow = (cloudLow?.get(i) as Double?)?.toInt() ?: 0,
                            cloudcoverMid = (cloudMid?.get(i) as Double?)?.toInt() ?: 0,
                            cloudcoverHigh = (cloudHigh?.get(i) as Double?)?.toInt() ?: 0,
                            shortwaveRadiation = ghi?.get(i) as Double,
                            directRadiation = dsi?.get(i) as Double,
                            diffuseRadiation = dhi?.get(i) as Double,
                            opacity = o,
                            uvIndex = (wui?.get(i) as Double?)?.toInt(),
                            visibility = (visibility?.get(i) as Double?)?.toInt()
                        ),
                        windspeed = windspeed?.get(i) as Double? ?: 0.0,
                        windDirection = windDir?.get(i) as Double? ?: 0.0,
                        pressure = (pressure?.get(i) as Double? ?: 0.0).roundToInt(),
                        humidity = (humidity?.get(i) as Double?)?.toInt() ?: 0,
                        dewpoint = dewpoint?.get(i) as Double? ?: 0.0,
                        wmo = (wmo?.get(i) as Double?)?.toInt() ?: 0
                    )
                } catch (e: Exception) {
                    Log.w("WeatherServiceParser", "Impossible de parser l'heure à l'index $i", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherServiceParser", "Erreur majeure lors du parsing des données horaires", e)
            return null
        }
    }

    private fun getDailyVariables(): List<String> {
        val variables = mutableListOf(
            "temperature_2m_max", "temperature_2m_min", "precipitation_sum", "uv_index_max", "weather_code", "sunset", "sunrise"
        )
        return variables
    }

    private fun parseDailyData(response: WeatherApiResponse): List<DailyReading>? {
        try {
            val times = response.getDeterministicDailyData("time") ?: return null

            // Récupération sécurisée de toutes les listes de données
            val tempMax = response.getDeterministicDailyData("temperature_2m_max")
            val tempMin = response.getDeterministicDailyData("temperature_2m_min")
            val precip = response.getDeterministicDailyData("precipitation_sum")
            val uvIndex = response.getDeterministicDailyData("uv_index_max")
            val sunset = response.getDeterministicDailyData("sunset")
            val sunrise = response.getDeterministicDailyData("sunrise")
            val wmo = response.getDeterministicDailyData("weather_code")

            return times.indices.mapNotNull { i ->
                try {
                    DailyReading(
                        date = LocalDate.parse(times[i] as String),
                        maxTemperature = tempMax?.get(i) as Double,
                        minTemperature = tempMin?.get(i) as Double,
                        precipitation = precip?.get(i) as Double,
                        max_uvIndex = (uvIndex?.get(i) as Double?)?.toInt(),
                        wmo = (wmo?.get(i) as Double).toInt(),
                        sunset = sunset?.get(i) as String,
                        sunrise = sunrise?.get(i) as String
                    )
                } catch (e: Exception) {
                    Log.w("WeatherServiceParser", "Impossible de parser l'heure à l'index $i", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherServiceParser", "Erreur majeure lors du parsing des données horaires", e)
            return null
        }
    }

    fun parseCurrentWeatherData(response: List<WeatherApiResponse>): List<CurrentWeatherReading>? {
        try {
            val result: MutableList<CurrentWeatherReading> = mutableListOf()
            for (currentLocationData in response) {
                val temp = currentLocationData.getCurrentWeatherData("temperature_2m")
                val wmo = currentLocationData.getCurrentWeatherData("weather_code")
                result.add(CurrentWeatherReading(
                    temperature = temp as Double,
                    wmo = (wmo as Double).toInt()
                ))
            }
            return result
        } catch (e: Exception) {
            Log.e("WeatherServiceParser", "Erreur lors du parsing des données actuelles", e)
            return null
        }
    }

    fun close() {
        client.close()
    }
}
