package fr.matthstudio.themeteo.forecastViewer

import android.os.Parcelable
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.lang.Math.clamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
//import java.lang.Math.*
import kotlin.math.*

// --- 1. CLASSES DE DONNÉES (Data Classes) pour la réponse JSON ---
// Ces classes modélisent la structure de la réponse JSON d'Open-Meteo.

@Serializable
data class WeatherApiResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("hourly_units")
    val hourlyUnits: HourlyUnits? = null,
    val hourly: HourlyData? = null,
    @SerialName("daily_units")
    val dailyUnits: DailyUnits? = null,
    val daily: DailyData? = null
)


@Serializable
data class HourlyUnits(
    @SerialName("temperature_2m")
    val temperature2m: String? = null,
    @SerialName("apparent_temperature")
    val apparentTemperature: String? = null,
    @SerialName("precipitation")
    val precipitation: String? = null,
    @SerialName("precipitation_probability")
    val precipitationProbability: String? = null,
    @SerialName("cloudcover")
    val cloudcover: String? = null,
    @SerialName("cloudcover_low")
    val cloudcover_low: String? = null,
    @SerialName("cloudcover_mid")
    val cloudcover_mid: String? = null,
    @SerialName("cloudcover_high")
    val cloudcover_high: String? = null,
    @SerialName("shortwave_radiation")
    val shortwave_radiation: String? = null,
    @SerialName("direct_radiation")
    val direct_radiation: String? = null,
    @SerialName("diffuse_radiation")
    val diffuse_radiation: String? = null,
    @SerialName("windspeed_10m")
    val windspeed10m: String? = null,
    @SerialName("wind_direction_10m")
    val windDirection10m: String? = null,
    @SerialName("pressure_msl")
    val pressureMsl: String? = null,
    @SerialName("relative_humidity_2m")
    val relativeHumidity2m: String? = null,
    @SerialName("dewpoint_2m")
    val dewpoint2m: String? = null,
    @SerialName("weather_code")
    val weatherCode: String? = null
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("temperature_2m")
    val temperature2m: List<Double>? = null,
    @SerialName("apparent_temperature")
    val apparentTemperature: List<Double>? = null,
    @SerialName("precipitation")
    val precipitation: List<Double>? = null,
    @SerialName("precipitation_probability")
    val precipitationProbability: List<Int>? = null, // Souvent en Int (pourcentage)
    @SerialName("cloudcover")
    val cloudcover: List<Int>? = null, // Souvent en Int (pourcentage)
    @SerialName("cloudcover_low")
    val cloudcover_low: List<Int>? = null,
    @SerialName("cloudcover_mid")
    val cloudcover_mid: List<Int>? = null,
    @SerialName("cloudcover_high")
    val cloudcover_high: List<Int>? = null,
    @SerialName("shortwave_radiation")
    val shortwave_radiation: List<Double>? = null, // en W/m^2
    @SerialName("direct_radiation")
    val direct_radiation: List<Double>? = null, // en W/m^2
    @SerialName("diffuse_radiation")
    val diffuse_radiation: List<Double>? = null, // en W/m^2
    @SerialName("windspeed_10m")
    val windspeed10m: List<Double>? = null,
    @SerialName("wind_direction_10m")
    val windDirection10m: List<Double>? = null, // en °
    @SerialName("pressure_msl")
    val pressureMsl: List<Double>? = null,
    @SerialName("relative_humidity_2m")
    val relativeHumidity2m: List<Int>? = null, // Souvent en Int (pourcentage)
    @SerialName("dewpoint_2m")
    val dewpoint2m: List<Double>? = null,
    @SerialName("weather_code")
    val weatherCode: List<Int>? = null
)

// NEW: Data classes for Daily forecast
@Serializable
data class DailyUnits(
    val time: String? = null,
    @SerialName("temperature_2m_max")
    val temperature2mMax: String? = null,
    @SerialName("temperature_2m_min")
    val temperature2mMin: String? = null,
    @SerialName("weather_code")
    val weatherCode: String? = null
)

@Serializable
data class DailyData(
    val time: List<String>? = null,
    @SerialName("temperature_2m_max")
    val temperature2mMax: List<Double>? = null,
    @SerialName("temperature_2m_min")
    val temperature2mMin: List<Double>? = null,
    @SerialName("weather_code")
    val weatherCode: List<Int>? = null
)

// Classes pratiques pour combiner l'heure et la valeur

data class TemperatureReading(
    val time: LocalDateTime,
    val temperature: Double
)

data class ApparentTemperatureReading(
    val time: LocalDateTime,
    val apparentTemperature: Double
)

data class PrecipitationReading(
    val time: LocalDateTime,
    val precipitation: Double // en mm
)

data class PrecipitationProbabilityReading(  
    val time: LocalDateTime,
    val probability: Int // en %
)

data class SkyInfoReading(
    val time: LocalDateTime,
    // Cloud Cover
    val cloudcover_total: Int, // en %
    val cloudcover_low: Int,
    val cloudcover_mid: Int,
    val cloudcover_high: Int,
    // Sun radiation
    val shortwave_radiation: Double, // en W/m^2
    val direct_radiation: Double, // en W/m^2
    val diffuse_radiation: Double, // en W/m^2
    // sky opacity (calculated)
    val opacity: Int, // en %
)

data class WindspeedReading(  
    val time: LocalDateTime,
    val windspeed: Double // en km/h
)

data class WindDirectionReading(
    val time: LocalDateTime,
    val windDirection: Double // en °
)

data class PressureReading(  
    val time: LocalDateTime,
    val pressure: Double // en hPa
)

data class HumidityReading(  
    val time: LocalDateTime,
    val humidity: Int // en %
)

data class DewpointReading(  
    val time: LocalDateTime,
    val dewpoint: Double // en °C
)

data class WMOReading(
    val time: LocalDateTime,
    val wmo: Int
)

data class AllVarsReading(
    val time: LocalDateTime,
    val temperatureReading: TemperatureReading,
    val apparentTemperatureReading: ApparentTemperatureReading?,
    val precipitationReading: PrecipitationReading,
    val precipitationProbabilityReading: PrecipitationProbabilityReading?,
    val skyInfoReading: SkyInfoReading,
    val windspeedReading: WindspeedReading,
    val winddirectionReading: WindDirectionReading,
    val pressureReading: PressureReading,
    val humidityReading: HumidityReading,
    val dewpointReading: DewpointReading,
    val wmoReading: WMOReading
)

// Data class for daily temperature readings
@Parcelize
data class DailyReading(
    val date: LocalDate,
    val maxTemperature: Double,
    val minTemperature: Double,
    val wmo: Int
) : Parcelable

// --- Data classes pour la recherche de ville (Geocoding) ---
@Serializable
data class GeocodingResponse(val results: List<GeocodingResult>? = null)

@Serializable
data class GeocodingResult(
    val id: Int, // id unique pour la ville
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("country_code") val countryCode: String,
    @SerialName("admin1") val region: String// La région ou l'état
)

// --- Data classes pour connaître les disponibilitées des variables par modèle ---
data class AvailableVariables(
    var precipitationProbability: Boolean,
    var apparentTemperature: Boolean
)

val availableVariables = mapOf(
    "best_match" to AvailableVariables(precipitationProbability = true, apparentTemperature = true),
    "ecmwf_ifs" to AvailableVariables(precipitationProbability = true, apparentTemperature = true),
    "ecmwf_aifs025_single" to AvailableVariables(
        precipitationProbability = false,
        apparentTemperature = true
    ),
    "meteofrance_seamless" to AvailableVariables(
        precipitationProbability = true,
        apparentTemperature = true
    ),
    "gfs_seamless" to AvailableVariables(
        precipitationProbability = true,
        apparentTemperature = true
    ),
    "icon_seamless" to AvailableVariables(
        precipitationProbability = true,
        apparentTemperature = true
    ),
    "gem_seamless" to AvailableVariables(
        precipitationProbability = true,
        apparentTemperature = true
    ),
    "ukmo_seamless" to AvailableVariables(
        precipitationProbability = false,
        apparentTemperature = true
    ),
)

// --- 2. LE SERVICE MÉTÉO ---

class WeatherService {

    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            // AJOUTER CE BLOC POUR LE LOGGING
            /*install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println("Ktor Log: $message") // Affiche les logs Ktor dans Logcat
                    }
                }
                level = LogLevel.ALL // Log toutes les informations, y compris l'URL et le corps de la réponse
            }*/
        }

    // Helper pour construire les paramètres horaires
    private fun HttpRequestBuilder.addHourlyParameters(
        latitude: Double,
        longitude: Double,
        model: String,
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime,
        localZoneId: ZoneId,
        vararg hourlyFields: String
    ) {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        parameter("latitude", latitude)
        parameter("longitude", longitude)
        parameter("models", model)
        parameter("hourly", hourlyFields.joinToString(",")) // Joindre tous les champs
        parameter("timezone", localZoneId.id)
        parameter("start_date", startDateTime.format(formatter).substringBefore("T"))
        parameter("end_date", endDateTime.format(formatter).substringBefore("T"))
        parameter("hourly_start", startDateTime.format(formatter))
        parameter("hourly_end", endDateTime.format(formatter))
    }

    // --- RECHERCHE DE VILLE ---
    /**
     * Interroge l'API de géocodage d'Open-Meteo pour trouver des villes.
     * @param query Le nom de la ville à rechercher.
     * @return Une liste de résultats de géocodage ou null en cas d'erreur.
     */
    suspend fun searchCity(query: String): List<GeocodingResult>? {
        // Ne lance pas de recherche pour une requête vide
        if (query.isBlank()) {
            return emptyList()
        }

        val apiUrl = "https://geocoding-api.open-meteo.com/v1/search"
        return try {
            client.get(apiUrl) {
                parameter("name", query)
                parameter("count", 10) // Limiter le nombre de résultats à 10
                parameter("language", "fr") // Obtenir les résultats en français
                parameter("format", "json")
            }.body<GeocodingResponse>().results
        } catch (e: Exception) {
            println("Erreur de géocodage : ${e.message}")
            null
        }
    }

    /**
     * Récupère toutes les données horaires pour une période donnée.
     * Cette fonction sera la base pour tous nos appels.
     */
    suspend fun getHourlyData(
        latitude: Double,
        longitude: Double,
        model: String,
        startDateTime: LocalDateTime,
        hours: Long, // Nombre d'heures à récupérer à partir de maintenant
        vararg hourlyFields: String, // Les champs spécifiques à demander (ex: "temperature_2m", "precipitation")
    ): WeatherApiResponse? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"
        val localZoneId = ZoneId.systemDefault()
        val endDateTime = startDateTime.plusHours(hours)

        return try {
            client.get(apiUrl) {
                // Utilisez la fonction d'aide
                addHourlyParameters(latitude, longitude, model, startDateTime, endDateTime, localZoneId, *hourlyFields)
            }.body()
        } catch (e: Exception) {
            println("Erreur lors de la récupération des données horaires (${hourlyFields.joinToString(",")}) : ${e.message}")
            null
        }
    }

    // Function to get daily temperature forecast (max/min)
    suspend fun getDailyForecast(latitude: Double, longitude: Double, days: Int, model: String): List<DailyReading>? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"
        val localZoneId = ZoneId.systemDefault()
        val now = LocalDate.now(localZoneId)
        // Start from tomorrow, and fetch 'days' number of days
        val startLocalDate = now.plusDays(1)
        val endLocalDate = now.plusDays(days.toLong())

        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        return try {
            val response: WeatherApiResponse = client.get(apiUrl) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("models", model)
                parameter("daily", "temperature_2m_max,temperature_2m_min,weather_code") // Request max/min daily temps and VMO code
                parameter("timezone", localZoneId.id)
                parameter("start_date", startLocalDate.format(formatter))
                parameter("end_date", endLocalDate.format(formatter))
            }.body()

            val dailyTimes = response.daily?.time
            val dailyMaxTemps = response.daily?.temperature2mMax
            val dailyMinTemps = response.daily?.temperature2mMin
            val dailyVMO = response.daily?.weatherCode

            if (dailyTimes != null && dailyMaxTemps != null && dailyMinTemps != null && dailyVMO != null &&
                dailyTimes.size == dailyMaxTemps.size && dailyTimes.size == dailyMinTemps.size && dailyTimes.size == dailyVMO.size
            ) {
                dailyTimes.zip(dailyMaxTemps.zip(dailyMinTemps.zip(dailyVMO))) { timeStr, (maxTemp, minTempVMO) ->
                    DailyReading(
                        date = LocalDate.parse(timeStr, formatter),
                        maxTemperature = maxTemp,
                        minTemperature = minTempVMO.first,
                        wmo = minTempVMO.second
                    )
                }
            } else {
                println("Erreur: Données journalières de température incomplètes ou manquantes.")
                null
            }
        } catch (e: Exception) {
            println("Erreur lors de la récupération des données journalières de température : ${e.message}")
            null
        }
    }

    // --- Fonctions pour télécharger les hourly data sur 24H ---
    suspend fun get24hAllVariablesForecast(
        latitude: Double, longitude: Double,
        model: String,
        startDateTime: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())
    ): List<AllVarsReading>? {
        val startDateTime = startDateTime.withMinute(0).withSecond(0)
        val variables = mutableListOf("temperature_2m", "precipitation",
            "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high",
            "shortwave_radiation", "direct_radiation", "diffuse_radiation", "windspeed_10m", "wind_direction_10m",
            "pressure_msl", "relative_humidity_2m", "dewpoint_2m", "weather_code")
        if (availableVariables[model]?.precipitationProbability == true)
            variables.add("precipitation_probability")
        if (availableVariables[model]?.apparentTemperature == true)
            variables.add("apparent_temperature")

        val response = getHourlyData(latitude, longitude, model, startDateTime, 23,
            variables.joinToString(",")
        ) ?: return null
        val endDateTime = startDateTime.plusHours(23)

        // Utiliser let pour s'assurer que toutes les listes de données requises ne sont pas nulles
        return response.hourly?.let { hourly ->
            val times = hourly.time
            val temperature = hourly.temperature2m
            val apparentTemperature = hourly.apparentTemperature
            val precipitation = hourly.precipitation
            val precipitationProbability = hourly.precipitationProbability
            val totalCover = hourly.cloudcover
            val lowCover = hourly.cloudcover_low
            val midCover = hourly.cloudcover_mid
            val highCover = hourly.cloudcover_high
            val ghi = hourly.shortwave_radiation
            val dsi = hourly.direct_radiation
            val dhi = hourly.diffuse_radiation
            val windspeed = hourly.windspeed10m
            val windDirection = hourly.windDirection10m
            val pressure = hourly.pressureMsl
            val humidity = hourly.relativeHumidity2m
            val dewpoint = hourly.dewpoint2m
            val wmo = hourly.weatherCode

            // 2. Vérifier que toutes les listes de données sont présentes et ont la même taille
            if (temperature != null && precipitation != null &&
                totalCover != null && lowCover != null && midCover != null && highCover != null &&
                dsi != null && ghi != null && dhi != null && windspeed != null && windDirection != null &&
                pressure != null && humidity != null && dewpoint != null && wmo != null &&
                times.size == temperature.size && times.size == precipitation.size &&
                times.size == totalCover.size && times.size == lowCover.size &&
                times.size == midCover.size && times.size == highCover.size && times.size == dsi.size &&
                times.size == ghi.size && times.size == dhi.size && times.size == windspeed.size &&
                times.size == pressure.size && times.size == humidity.size &&
                times.size == dewpoint.size && times.size == wmo.size && times.size == windDirection.size
            ) {

                // 3. Itérer sur les indices de la liste de temps
                times.indices.map { index ->
                    val dateTime =
                        LocalDateTime.parse(times[index], DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                    val o = (clamp((dhi[index]) / max(ghi[index], 1.0), 0.0, 1.0) * 100.0).roundToInt()

                    // 4. Créer l'objet SkyInfoReading avec les données de chaque liste à l'indice courant
                    AllVarsReading(
                        time = dateTime,
                        temperatureReading = TemperatureReading(
                            time = dateTime,
                            temperature = temperature[index]
                        ),
                        apparentTemperatureReading = if (apparentTemperature != null) ApparentTemperatureReading(
                            time = dateTime,
                            apparentTemperature = apparentTemperature[index]
                        ) else null,
                        precipitationReading = PrecipitationReading(
                            time = dateTime,
                            precipitation = precipitation[index]
                        ),
                        precipitationProbabilityReading = if (precipitationProbability != null) PrecipitationProbabilityReading(
                            time = dateTime,
                            probability = precipitationProbability[index]
                        ) else null,
                        skyInfoReading = SkyInfoReading(
                            time = dateTime,
                            cloudcover_total = totalCover[index],
                            cloudcover_low = lowCover[index],
                            cloudcover_mid = midCover[index],
                            cloudcover_high = highCover[index],
                            shortwave_radiation = ghi[index],
                            direct_radiation = dsi[index],
                            diffuse_radiation = dhi[index],
                            opacity = o
                        ),
                        windspeedReading = WindspeedReading(
                            time = dateTime,
                            windspeed = windspeed[index]
                        ),
                        winddirectionReading = WindDirectionReading(
                            time = dateTime,
                            windDirection = windDirection[index]
                        ),
                        pressureReading = PressureReading(
                            time = dateTime,
                            pressure = pressure[index]
                        ),
                        humidityReading = HumidityReading(
                            time = dateTime,
                            humidity = humidity[index]
                        ),
                        dewpointReading = DewpointReading(
                            time = dateTime,
                            dewpoint = dewpoint[index]
                        ),
                        wmoReading = WMOReading(
                            time = dateTime,
                            wmo = wmo[index]
                        )
                    )
                }
                    .filter {
                        it.time.isAfter(startDateTime.minusMinutes(1)) && it.time.isBefore(
                            endDateTime.plusMinutes(1)
                        )
                    }
                    .take(24)

            } else {
                println("Erreur: Erreur lors de la récupération des données horaires.")
                null
            }
        }
    }

    fun close() {
        client.close()
    }
}