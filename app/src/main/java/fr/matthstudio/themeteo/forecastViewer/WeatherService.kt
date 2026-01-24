package fr.matthstudio.themeteo.forecastViewer

import android.os.Parcelable
import android.util.Log
import fr.matthstudio.themeteo.forecastViewer.data.LocalDateSerializer
import fr.matthstudio.themeteo.forecastViewer.data.LocalDateTimeSerializer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*

// Commenter lorsqu'on utilise pas le logging
/*import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger*/

import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.lang.Math.clamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            // DÉCOMMENTER CE BLOC POUR LE LOGGING
            /*install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("HTTP Client", message) // Affiche les logs dans Logcat
                    }
                }
                level = LogLevel.ALL // Log toutes les informations, y compris l'URL et le corps de la réponse
            }*/
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

    /**
     * Récupère l'intégralité des prévisions horaires pour une période étendue.
     * C'est la fonction que le WeatherCache utilisera.
     */
    suspend fun getCompleteHourlyForecast(
        latitude: Double,
        longitude: Double,
        model: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<AllHourlyVarsReading>? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"
        val localZoneId = ZoneId.systemDefault()

        return try {
            val response: WeatherApiResponse = client.get(apiUrl) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("models", model)
                parameter("hourly", getHourlyVariables().joinToString(","))
                parameter("timezone", localZoneId.id)
                parameter("start_date", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                parameter("end_date", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }.body()

            parseHourlyData(response)

        } catch (e: Exception) {
            Log.e("getCompleteForecast", "Erreur lors de la récupération des prévisions complètes: ${e.message}")
            null
        }
    }
    /**
     * Récupère l'intégralité des prévisions journalières pour une période étendue.
     * C'est la fonction que le WeatherCache utilisera.
     */
    suspend fun getCompleteDailyForecast(
        latitude: Double,
        longitude: Double,
        model: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyReading>? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"
        val localZoneId = ZoneId.systemDefault()

        return try {
            val response: WeatherApiResponse = client.get(apiUrl) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("models", model)
                parameter("daily", getDailyVariables().joinToString(","))
                parameter("timezone", localZoneId.id)
                parameter("start_date", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                parameter("end_date", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }.body()

            parseDailyData(response)

        } catch (e: Exception) {
            Log.e("getCompleteForecast", "Erreur lors de la récupération des prévisions complètes: ${e.message}")
            null
        }
    }

    private fun getHourlyVariables(): List<String> {
        val variables = mutableListOf(
            "temperature_2m", "apparent_temperature", "precipitation", "rain", "showers", "snowfall",
            "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high", "weather_code",
            "windspeed_10m", "wind_direction_10m", "pressure_msl", "relative_humidity_2m", "dewpoint_2m",
            "precipitation_probability", "snow_depth", "shortwave_radiation", "direct_radiation",
            "diffuse_radiation", "uv_index", "visibility"
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
            val ghi = response.getDeterministicHourlyData("shortwave_radiation")
            val dhi = response.getDeterministicHourlyData("diffuse_radiation")
            val dsi = response.getDeterministicHourlyData("direct_radiation")
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
            "temperature_2m_max", "temperature_2m_min", "weather_code", "sunset", "sunrise"
        )
        return variables
    }

    private fun parseDailyData(response: WeatherApiResponse): List<DailyReading>? {
        try {
            val times = response.getDeterministicDailyData("time") ?: return null

            // Récupération sécurisée de toutes les listes de données
            val tempMax = response.getDeterministicDailyData("temperature_2m_max")
            val tempMin = response.getDeterministicDailyData("temperature_2m_min")
            val sunset = response.getDeterministicDailyData("sunset")
            val sunrise = response.getDeterministicDailyData("sunrise")
            val wmo = response.getDeterministicDailyData("weather_code")

            return times.indices.mapNotNull { i ->
                try {
                    DailyReading(
                        date = LocalDate.parse(times[i] as String),
                        maxTemperature = tempMax?.get(i) as Double,
                        minTemperature = tempMin?.get(i) as Double,
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

    fun close() {
        client.close()
    }
}
