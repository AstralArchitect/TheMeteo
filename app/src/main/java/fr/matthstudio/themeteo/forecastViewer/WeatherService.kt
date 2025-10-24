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
    @SerialName("pressure_msl")
    val pressureMsl: String? = null,
    @SerialName("relative_humidity_2m")
    val relativeHumidity2m: String? = null,
    @SerialName("dewpoint_2m")
    val dewpoint2m: String? = null,
    @SerialName("is_day")
    val isDay: String? = null
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
    @SerialName("pressure_msl")
    val pressureMsl: List<Double>? = null,
    @SerialName("relative_humidity_2m")
    val relativeHumidity2m: List<Int>? = null, // Souvent en Int (pourcentage)
    @SerialName("dewpoint_2m")
    val dewpoint2m: List<Double>? = null,
    @SerialName("is_day")
    val isDay: List<Int>? = null
)

// NEW: Data classes for Daily forecast
@Serializable
data class DailyUnits(
    val time: String? = null,
    @SerialName("temperature_2m_max")
    val temperature2mMax: String? = null,
    @SerialName("temperature_2m_min")
    val temperature2mMin: String? = null,
    // Add other daily fields if needed in the future
)

@Serializable
data class DailyData(
    val time: List<String>? = null,
    @SerialName("temperature_2m_max")
    val temperature2mMax: List<Double>? = null,
    @SerialName("temperature_2m_min")
    val temperature2mMin: List<Double>? = null,
    // Add other daily fields if needed
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

data class AllVarsReading(
    val time: LocalDateTime,
    val temperatureReading: TemperatureReading,
    val apparentTemperatureReading: ApparentTemperatureReading,
    val precipitationReading: PrecipitationReading,
    val precipitationProbabilityReading: PrecipitationProbabilityReading,
    val skyInfoReading: SkyInfoReading,
    val windspeedReading: WindspeedReading,
    val pressureReading: PressureReading,
    val humidityReading: HumidityReading,
    val dewpointReading: DewpointReading
)

// Data class for daily temperature readings
@Parcelize
data class DailyTemperatureReading(
    val date: LocalDate,
    val maxTemperature: Double,
    val minTemperature: Double
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
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime,
        localZoneId: ZoneId,
        vararg hourlyFields: String
    ) {
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        parameter("latitude", latitude)
        parameter("longitude", longitude)
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
    private suspend fun getHourlyData(
        latitude: Double,
        longitude: Double,
        startDateTime: LocalDateTime,
        hours: Long, // Nombre d'heures à récupérer à partir de maintenant
        vararg hourlyFields: String // Les champs spécifiques à demander (ex: "temperature_2m", "precipitation")
    ): WeatherApiResponse? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"
        val localZoneId = ZoneId.systemDefault()
        val endDateTime = startDateTime.plusHours(hours)

        return try {
            client.get(apiUrl) {
                // Utilisez la fonction d'aide
                addHourlyParameters(latitude, longitude, startDateTime, endDateTime, localZoneId, *hourlyFields)
            }.body()
        } catch (e: Exception) {
            println("Erreur lors de la récupération des données horaires (${hourlyFields.joinToString(",")}) : ${e.message}")
            null
        }
    }

    // Function to get daily temperature forecast (max/min)
    suspend fun getDailyTemperatureForecast(latitude: Double, longitude: Double, days: Long): List<DailyTemperatureReading>? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"
        val localZoneId = ZoneId.systemDefault()
        val now = LocalDate.now(localZoneId)
        // Start from tomorrow, and fetch 'days' number of days
        val startLocalDate = now.plusDays(1)
        val endLocalDate = now.plusDays(days)

        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        return try {
            val response: WeatherApiResponse = client.get(apiUrl) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("daily", "temperature_2m_max,temperature_2m_min") // Request max/min daily temps
                parameter("timezone", localZoneId.id)
                parameter("start_date", startLocalDate.format(formatter))
                parameter("end_date", endLocalDate.format(formatter))
            }.body()

            val dailyTimes = response.daily?.time
            val dailyMaxTemps = response.daily?.temperature2mMax
            val dailyMinTemps = response.daily?.temperature2mMin

            if (dailyTimes != null && dailyMaxTemps != null && dailyMinTemps != null &&
                dailyTimes.size == dailyMaxTemps.size && dailyTimes.size == dailyMinTemps.size) {
                dailyTimes.zip(dailyMaxTemps.zip(dailyMinTemps)) { timeStr, (maxTemp, minTemp) ->
                    DailyTemperatureReading(
                        date = LocalDate.parse(timeStr, formatter),
                        maxTemperature = maxTemp,
                        minTemperature = minTemp
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

    // -- Fonctions pour récupérer l'état actuel pour chaque type de donnée --
    // TODO(): Ajouter les fonctions pour chaque type de donnée

    // --- Fonctions spécifiques pour chaque type de donnée ---

    suspend fun get24hAllVariablesForecast(
        latitude: Double, longitude: Double,
        startDateTime: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())
    ): List<AllVarsReading>? {
        val response = getHourlyData(latitude, longitude, startDateTime, 24, "temperature_2m", "apparent_temperature",
                "precipitation", "precipitation_probability", "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high",
                "shortwave_radiation", "direct_radiation", "diffuse_radiation", "windspeed_10m", "pressure_msl", "relative_humidity_2m", "dewpoint_2m")
        ?: return null
        val endDateTime = startDateTime.plusHours(24)

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
            val pressure = hourly.pressureMsl
            val humidity = hourly.relativeHumidity2m
            val dewpoint = hourly.dewpoint2m

            // 2. Vérifier que toutes les listes de données sont présentes et ont la même taille
            if (temperature != null && apparentTemperature != null &&
                precipitation != null && precipitationProbability != null &&
                totalCover != null && lowCover != null && midCover != null && highCover != null &&
                dsi != null && ghi != null && dhi != null && windspeed != null &&
                pressure != null && humidity != null && dewpoint != null &&
                times.size == temperature.size && times.size == apparentTemperature.size &&
                times.size == precipitation.size && times.size == precipitationProbability.size &&
                times.size == totalCover.size && times.size == lowCover.size &&
                times.size == midCover.size && times.size == highCover.size && times.size == dsi.size &&
                times.size == ghi.size && times.size == dhi.size && times.size == windspeed.size &&
                times.size == pressure.size && times.size == humidity.size && times.size == dewpoint.size
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
                        apparentTemperatureReading = ApparentTemperatureReading(
                            time = dateTime,
                            apparentTemperature = apparentTemperature[index]
                        ),
                        precipitationReading = PrecipitationReading(
                            time = dateTime,
                            precipitation = precipitation[index]
                        ),
                        precipitationProbabilityReading = PrecipitationProbabilityReading(
                            time = dateTime,
                            probability = precipitationProbability[index]
                        ),
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
                        )
                    )
                }
                    .filter {
                        it.time.isAfter(startDateTime.minusMinutes(1)) && it.time.isBefore(
                            endDateTime.plusMinutes(1)
                        )
                    }
                    .take(25)

            } else {
                println("Erreur: Données de couverture nuageuse incomplètes ou de tailles différentes reçues.")
                null
            }
        }
    }

    suspend fun getIsDay(latitude: Double, longitude: Double): Boolean? {
        // On ne demande les données que pour 1 heure, avec seulement le champ "is_day"
        val response = getHourlyData(latitude, longitude, LocalDateTime.now(ZoneId.systemDefault()),1, "is_day") ?: return null

        // Il faut inverser la valeur donnée
        val value = response.hourly?.isDay?.firstOrNull() != 1

        // L'API retourne une liste, on prend le premier élément qui correspond à l'heure actuelle.
        // 1 signifie "jour", 0 signifie "nuit".
        // On retourne true si la valeur est 1, sinon false (ou null en cas d'erreur).
        return value
    }

    fun close() {
        client.close()
    }
}