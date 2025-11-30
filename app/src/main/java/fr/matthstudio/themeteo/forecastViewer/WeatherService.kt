package fr.matthstudio.themeteo.forecastViewer

import android.os.Parcelable
import android.util.Log
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
    val daily: DailyData? = null,
    @SerialName("minutely_15_units")
    val minutely15Units: Minutely15Units? = null,
    @SerialName("minutely_15")
    val minutely15: MinutelyData? = null
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
    @SerialName("rain")
    val rain: String? = null,
    @SerialName("showers")
    val showers: String? = null,
    @SerialName("snowfall")
    val snowfall: String? = null,
    @SerialName("snow_depth")
    val snowDepth: String? = null,
    @SerialName("cloudcover")
    val cloudcover: String? = null,
    @SerialName("cloudcover_low")
    val cloudcoverLow: String? = null,
    @SerialName("cloudcover_mid")
    val cloudcoverMid: String? = null,
    @SerialName("cloudcover_high")
    val cloudcoverHigh: String? = null,
    @SerialName("shortwave_radiation")
    val shortwaveRadiation: String? = null,
    @SerialName("direct_radiation")
    val directRadiation: String? = null,
    @SerialName("diffuse_radiation")
    val diffuseRadiation: String? = null,
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
    @SerialName("rain")
    val rain: List<Double>? = null,
    @SerialName("showers")
    val showers: List<Double>? = null,
    @SerialName("snowfall")
    val snowfall: List<Double>? = null,
    @SerialName("snow_depth")
    val snowDepth: List<Double>? = null,
    @SerialName("cloudcover")
    val cloudcover: List<Int>? = null, // Souvent en Int (pourcentage)
    @SerialName("cloudcover_low")
    val cloudcoverLow: List<Int>? = null,
    @SerialName("cloudcover_mid")
    val cloudcoverMid: List<Int>? = null,
    @SerialName("cloudcover_high")
    val cloudcoverHigh: List<Int>? = null,
    @SerialName("shortwave_radiation")
    val shortwaveRadiation: List<Double>? = null, // en W/m^2
    @SerialName("direct_radiation")
    val directRadiation: List<Double>? = null, // en W/m^2
    @SerialName("diffuse_radiation")
    val diffuseRadiation: List<Double>? = null, // en W/m^2
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

// Data classes for Daily forecast
@Serializable
data class DailyUnits(
    val time: String? = null,
    @SerialName("temperature_2m_max")
    val temperature2mMax: String? = null,
    @SerialName("temperature_2m_min")
    val temperature2mMin: String? = null,
    @SerialName("weather_code")
    val weatherCode: String? = null,
    @SerialName("sunset")
    val sunset: String? = null,
    @SerialName("sunrise")
    val sunrise: String? = null
)

@Serializable
data class DailyData(
    val time: List<String>? = null,
    @SerialName("temperature_2m_max")
    val temperature2mMax: List<Double>? = null,
    @SerialName("temperature_2m_min")
    val temperature2mMin: List<Double>? = null,
    @SerialName("weather_code")
    val weatherCode: List<Int>? = null,
    @SerialName("sunset")
    val sunset: List<String>? = null,
    @SerialName("sunrise")
    val sunrise: List<String>? = null
)

@Serializable
data class Minutely15Units (
    val time: String? = null,
    @SerialName("rain")
    val rain: String? = null,
    @SerialName("snowfall")
    val snowfall: String? = null
)

@Serializable
data class MinutelyData (
    val time: List<String>? = null,
    @SerialName("rain")
    val rain: List<Double>? = null,
    @SerialName("snowfall")
    val snowfall: List<Double>? = null
)

// Classes pratiques pour combiner l'heure et la valeur

data class PrecipitationData(
    // Precipitation
    val precipitation: Double, // en mm
    val precipitationProbability: Int?, // en %
    // Rain
    val rain: Double, // en mm
    // Snowfall
    val snowfall: Double, // en cm
    // Snow depth
    val snowDepth: Double?, // en m
)

data class SkyInfoData(
    // Cloud Cover
    val cloudcoverTotal: Int, // en %
    val cloudcoverLow: Int,
    val cloudcoverMid: Int,
    val cloudcoverHigh: Int,
    // Sun radiation
    val shortwaveRadiation: Double?, // en W/m^2
    val directRadiation: Double?, // en W/m^2
    val diffuseRadiation: Double?, // en W/m^2
    // sky opacity (calculated)
    val opacity: Int?, // en %
)

data class AllHourlyVarsReading(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double?,
    val precipitationData: PrecipitationData,
    val skyInfo: SkyInfoData,
    val windspeed: Double,
    val windDirection: Double,
    val pressure: Double,
    val humidity: Int,
    val dewpoint: Double,
    val wmo: Int
)

// Data class for daily temperature readings
@Parcelize
data class DailyReading(
    val date: LocalDate,
    val maxTemperature: Double,
    val minTemperature: Double,
    val wmo: Int,
    val sunset: String?,
    val sunrise: String?
) : Parcelable

// Data class for 15-minutely weather readings
@Parcelize
data class MinutelyReading(
    val time: LocalDateTime,
    val snowfall: Double,
    val rain: Double
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
    var snowDepth: Boolean,
    var solarRadiation: Boolean
)

val availableVariables = mapOf(
    "best_match" to AvailableVariables(precipitationProbability = true, snowDepth = true, solarRadiation = true),
    "ecmwf_ifs" to AvailableVariables(precipitationProbability = true, snowDepth = true, solarRadiation = true),
    "ecmwf_aifs025_single" to AvailableVariables(precipitationProbability = false, snowDepth = false, solarRadiation = true),
    "meteofrance_seamless" to AvailableVariables(precipitationProbability = true, snowDepth = false, solarRadiation = true),
    "gfs_seamless" to AvailableVariables(precipitationProbability = true, snowDepth = true, solarRadiation = true),
    "icon_seamless" to AvailableVariables(precipitationProbability = true, snowDepth = true, solarRadiation = true),
    "gem_seamless" to AvailableVariables(precipitationProbability = true, snowDepth = true, solarRadiation = true),
    "ukmo_seamless" to AvailableVariables(precipitationProbability = false, snowDepth = false, solarRadiation = false),
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
            Log.e("Geocoder","Erreur de géocodage : ${e.message}")
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
            Log.e("getHourlyData", "Erreur lors de la récupération des données horaires (${hourlyFields.joinToString(",")}) : ${e.message}")
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
        val endLocalDate = now.plusDays(if (LocalDateTime.now().hour > 6) days.toLong() else days.toLong() - 1)

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
                        wmo = minTempVMO.second,
                        sunset = null,
                        sunrise = null
                    )
                }
            } else {
                Log.e("getDailyForecast", "Erreur: Données journalières de température incomplètes ou manquantes.")
                null
            }
        } catch (e: Exception) {
            Log.e("getDailyForecast", "Erreur lors de la récupération des données journalières de température : ${e.message}")
            null
        }
    }

    // --- Fonction pour télécharger les données 15-minutely sur 1 heure ---
    suspend fun get15MinutelyForecast(
        latitude: Double, longitude: Double, hours: Long
    ): List<MinutelyReading>? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"

        return try {
            val response: WeatherApiResponse = client.get(apiUrl) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                //parameter("models", model)
                parameter("minutely_15", "rain,snowfall")
                parameter("forecast_minutely_15", hours * 4)
            }.body()

            val minutelyTimes = response.minutely15?.time
            val minutelyRain = response.minutely15?.rain
            val minutelySnowfall = response.minutely15?.snowfall

            if (minutelyTimes != null && minutelyRain != null && minutelySnowfall != null)
            {
                minutelyTimes.zip(minutelyRain.zip(minutelySnowfall)).map { (timeStr, rainSnowfall) ->
                    MinutelyReading(
                        time = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        rain = rainSnowfall.first,
                        snowfall = rainSnowfall.second
                    )
                }
            }
            else {
                Log.e("get15MinutelyForecast", "Erreur: Erreur lors de la récupération des données '15 minutely'.")
                null
            }
        } catch (e: Exception) {
            Log.e("get15MinutelyForecast", "Erreur lors de la récupération des données '15 minutely' : ${e.message}")
            null
        }
    }

    suspend fun get15MinutelyAndHourlyData(
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
                parameter("minutely_15", "rain,snowfall")
                parameter("forecast_minutely_15", 12 * 4)
                parameter("forecast_days", 2)
                parameter("daily", "sunset,sunrise")
            }.body()
        } catch (e: Exception) {
            Log.e("getHourlyData", "Erreur lors de la récupération des données '15 minutely' + horaires (${hourlyFields.joinToString(",")}) : ${e.message}")
            null
        }
    }

    // --- Fonction pour télécharger les hourly data sur 24H ---
    suspend fun get24hAllVariablesForecast(
        latitude: Double, longitude: Double,
        model: String,
        startDateTime: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())
    ): List<AllHourlyVarsReading>? {
        val startDateTime = startDateTime.withMinute(0).withSecond(0)
        val variables = mutableListOf("temperature_2m", "precipitation", "rain", "snowfall", "showers",
            "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high", "apparent_temperature",
             "windspeed_10m", "wind_direction_10m", "pressure_msl", "relative_humidity_2m", "dewpoint_2m", "weather_code")
        if (availableVariables[model]?.precipitationProbability == true)
            variables.add("precipitation_probability")
        if (availableVariables[model]?.snowDepth == true)
            variables.add("snow_depth")
        if (availableVariables[model]?.solarRadiation == true)
            variables.addAll(listOf("shortwave_radiation", "direct_radiation", "diffuse_radiation"))

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
            val rain = hourly.rain
            val showers = hourly.showers
            val snowfall = hourly.snowfall
            val snowDepth = hourly.snowDepth
            val totalCover = hourly.cloudcover
            val lowCover = hourly.cloudcoverLow
            val midCover = hourly.cloudcoverMid
            val highCover = hourly.cloudcoverHigh
            val ghi = hourly.shortwaveRadiation
            val dsi = hourly.directRadiation
            val dhi = hourly.diffuseRadiation
            val windspeed = hourly.windspeed10m
            val windDirection = hourly.windDirection10m
            val pressure = hourly.pressureMsl
            val humidity = hourly.relativeHumidity2m
            val dewpoint = hourly.dewpoint2m
            val wmo = hourly.weatherCode

            // 2. Vérifier que toutes les listes de données sont présentes et ont la même taille
            if (temperature != null && precipitation != null  && rain != null && snowfall != null &&
                showers != null && totalCover != null && lowCover != null && midCover != null &&
                highCover != null && windspeed != null &&
                windDirection != null && pressure != null && humidity != null && dewpoint != null &&
                wmo != null && apparentTemperature != null &&
                times.size == temperature.size && times.size == precipitation.size &&
                times.size == apparentTemperature.size && times.size == showers.size &&
                times.size == snowfall.size && times.size == rain.size &&
                times.size == totalCover.size && times.size == lowCover.size &&
                times.size == midCover.size && times.size == highCover.size && times.size == (dsi?.size ?: times.size) &&
                times.size == (ghi?.size ?: times.size) && times.size == (dhi?.size ?: times.size) && times.size == windspeed.size &&
                times.size == pressure.size && times.size == humidity.size &&
                times.size == dewpoint.size && times.size == wmo.size && times.size == windDirection.size
            ) {

                // 3. Itérer sur les indices de la liste de temps
                times.indices.map { index ->
                    val dateTime =
                        LocalDateTime.parse(times[index], DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                    val o: Int? = if (ghi != null && dhi != null)
                            (clamp((dhi[index]) / max(ghi[index], 1.0), 0.0, 1.0) * 100.0).roundToInt()
                    else null

                    // 4. Créer l'objet SkyInfoReading avec les données de chaque liste à l'indice courant
                    AllHourlyVarsReading(
                        time = dateTime,
                        temperature = temperature[index],
                        apparentTemperature = apparentTemperature[index],
                        precipitationData = PrecipitationData(
                            precipitation = precipitation[index],
                            precipitationProbability = precipitationProbability?.get(index),
                            rain = rain[index] + showers[index],
                            snowfall = snowfall[index],
                            snowDepth = snowDepth?.get(index)
                        ),
                        skyInfo = SkyInfoData(
                            cloudcoverTotal = totalCover[index],
                            cloudcoverLow = lowCover[index],
                            cloudcoverMid = midCover[index],
                            cloudcoverHigh = highCover[index],
                            shortwaveRadiation = ghi?.get(index),
                            directRadiation = dsi?.get(index),
                            diffuseRadiation = dhi?.get(index),
                            opacity = o
                        ),
                        windspeed = windspeed[index],
                        windDirection = windDirection[index],
                        pressure = pressure[index],
                        humidity = humidity[index],
                        dewpoint = dewpoint[index],
                        wmo = wmo[index]
                    )
                }
                    .filter {
                        it.time.isAfter(startDateTime.minusMinutes(1)) && it.time.isBefore(
                            endDateTime.plusMinutes(1)
                        )
                    }
                    .take(24)

            } else {
                Log.e("get24hAllVariablesForecast", "Erreur: Erreur lors de la récupération des données horaires.")
                null
            }
        }
    }

    // --- Fonction pour télécharger les hourly data sur 24H et les 15-minutely sur 12H ---
    suspend fun get24hAllVariablesForecastPlus15Minutely(
        latitude: Double, longitude: Double,
        model: String,
        startDateTime: LocalDateTime = LocalDateTime.now(ZoneId.systemDefault())
    ): Triple<List<AllHourlyVarsReading>?, List<MinutelyReading>?, List<DailyReading>?>? {
        val startDateTime = startDateTime.withMinute(0).withSecond(0)
        val variables = mutableListOf("temperature_2m", "precipitation", "rain", "snowfall", "showers",
            "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high", "apparent_temperature",
            "windspeed_10m", "wind_direction_10m", "pressure_msl", "relative_humidity_2m", "dewpoint_2m", "weather_code")
        if (availableVariables[model]?.precipitationProbability == true)
            variables.add("precipitation_probability")
        if (availableVariables[model]?.snowDepth == true)
            variables.add("snow_depth")
        if (availableVariables[model]?.solarRadiation == true)
            variables.addAll(listOf("shortwave_radiation", "direct_radiation", "diffuse_radiation"))

        val response = get15MinutelyAndHourlyData(latitude, longitude, model, startDateTime,
            23, *variables.toTypedArray()) ?: return null

        val endDateTime = startDateTime.plusHours(23)

        // Utiliser let pour s'assurer que toutes les listes de données requises ne sont pas nulles
        try {
            val hourlyData = response.hourly?.let { hourly ->
                val times = hourly.time
                val temperature = hourly.temperature2m
                val apparentTemperature = hourly.apparentTemperature
                val precipitation = hourly.precipitation
                val precipitationProbability = hourly.precipitationProbability
                val rain = hourly.rain
                val showers = hourly.showers
                val snowfall = hourly.snowfall
                val snowDepth = hourly.snowDepth
                val totalCover = hourly.cloudcover
                val lowCover = hourly.cloudcoverLow
                val midCover = hourly.cloudcoverMid
                val highCover = hourly.cloudcoverHigh
                val ghi = hourly.shortwaveRadiation
                val dsi = hourly.directRadiation
                val dhi = hourly.diffuseRadiation
                val windspeed = hourly.windspeed10m
                val windDirection = hourly.windDirection10m
                val pressure = hourly.pressureMsl
                val humidity = hourly.relativeHumidity2m
                val dewpoint = hourly.dewpoint2m
                val wmo = hourly.weatherCode

                // 2. Vérifier que toutes les listes de données sont présentes et ont la même taille
                if (temperature != null && precipitation != null && rain != null && snowfall != null &&
                    showers != null && totalCover != null && lowCover != null && midCover != null &&
                    highCover != null && windspeed != null &&
                    windDirection != null && pressure != null && humidity != null && dewpoint != null &&
                    wmo != null && apparentTemperature != null &&
                    times.size == temperature.size && times.size == precipitation.size &&
                    times.size == apparentTemperature.size && times.size == showers.size &&
                    times.size == snowfall.size && times.size == rain.size &&
                    times.size == totalCover.size && times.size == lowCover.size &&
                    times.size == midCover.size && times.size == highCover.size && times.size == (dsi?.size
                        ?: times.size) &&
                    times.size == (ghi?.size ?: times.size) && times.size == (dhi?.size
                        ?: times.size) && times.size == windspeed.size &&
                    times.size == pressure.size && times.size == humidity.size &&
                    times.size == dewpoint.size && times.size == wmo.size && times.size == windDirection.size
                ) {
                    times.indices.map { index ->
                        val dateTime =
                            LocalDateTime.parse(times[index], DateTimeFormatter.ISO_LOCAL_DATE_TIME)

                        val o: Int? = if (ghi != null && dhi != null)
                            (clamp(
                                (dhi[index]) / max(ghi[index], 1.0),
                                0.0,
                                1.0
                            ) * 100.0).roundToInt()
                        else null

                        // 4. Créer l'objet SkyInfoReading avec les données de chaque liste à l'indice courant
                        AllHourlyVarsReading(
                            time = dateTime,
                            temperature = temperature[index],
                            apparentTemperature = apparentTemperature[index],
                            precipitationData = PrecipitationData(
                                precipitation = precipitation[index],
                                precipitationProbability = precipitationProbability?.get(index),
                                rain = rain[index] + showers[index],
                                snowfall = snowfall[index],
                                snowDepth = snowDepth?.get(index)
                            ),
                            skyInfo = SkyInfoData(
                                cloudcoverTotal = totalCover[index],
                                cloudcoverLow = lowCover[index],
                                cloudcoverMid = midCover[index],
                                cloudcoverHigh = highCover[index],
                                shortwaveRadiation = ghi?.get(index),
                                directRadiation = dsi?.get(index),
                                diffuseRadiation = dhi?.get(index),
                                opacity = o
                            ),
                            windspeed = windspeed[index],
                            windDirection = windDirection[index],
                            pressure = pressure[index],
                            humidity = humidity[index],
                            dewpoint = dewpoint[index],
                            wmo = wmo[index]
                        )
                    }
                        .filter {
                            it.time.isAfter(startDateTime.minusMinutes(1)) && it.time.isBefore(
                                endDateTime.plusMinutes(1)
                            )
                        }
                        .take(24)

                } else {
                    Log.e(
                        "get24hAllVariablesForecastPlus15Minutely",
                        "Erreur: Erreur lors de la récupération des données horaires."
                    )
                    return null
                }
            }

            var minutely15: List<MinutelyReading>?

            val minutelyTimes = response.minutely15?.time
            val minutelyRain = response.minutely15?.rain
            val minutelySnowfall = response.minutely15?.snowfall

            if (minutelyTimes != null && minutelyRain != null && minutelySnowfall != null) {
                minutely15 = minutelyTimes.zip(minutelyRain.zip(minutelySnowfall))
                    .map { (timeStr, rainSnowfall) ->
                        MinutelyReading(
                            time = LocalDateTime.parse(
                                timeStr,
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME
                            ),
                            rain = rainSnowfall.first,
                            snowfall = rainSnowfall.second
                        )
                    }
            } else {
                Log.e(
                    "get24hAllVariablesForecastPlus15Minutely",
                    "Erreur: Erreur lors de la récupération des données '15 minutely'."
                )
                return null
            }

            var daily: List<DailyReading?>

            val dailyTimes = response.daily?.time
            val dailySunset = response.daily?.sunset
            val dailySunrise = response.daily?.sunrise

            if (dailyTimes != null && dailySunset != null && dailySunrise != null) {
                daily = dailyTimes.zip(dailySunset.zip(dailySunrise))
                    .map { (timeStr, sunsetSunrise) ->
                        DailyReading(
                            date = LocalDate.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE),
                            maxTemperature = .0,
                            minTemperature = .0,
                            wmo = -1,
                            sunset = sunsetSunrise.first,
                            sunrise = sunsetSunrise.second
                        )
                    }
            } else {
                Log.e(
                    "get24hAllVariablesForecastPlus15Minutely",
                    "Erreur: Erreur lors de la récupération des données '15 minutely'."
                )
                return null
            }

            return Triple(hourlyData, minutely15, daily)
        } catch (e: Exception) {
            Log.e(
                "get24hAllVariablesForecast",
                "Erreur lors de la récupération des données : ${e.message}"
            )
            return null
        }
    }

    fun close() {
        client.close()
    }
}