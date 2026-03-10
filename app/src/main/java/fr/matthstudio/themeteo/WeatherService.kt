package fr.matthstudio.themeteo

import android.os.Parcelable
import android.util.Log
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.traceEventEnd
import fr.matthstudio.themeteo.data.LocalDateSerializer
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.utilClasses.AirQualityLocation
import fr.matthstudio.themeteo.utilClasses.AirQualityRequest
import fr.matthstudio.themeteo.utilClasses.AirQualityInfo
import fr.matthstudio.themeteo.utilClasses.AlertStep
import fr.matthstudio.themeteo.utilClasses.GovernmentInvertedGeocodingAPIResponse
import fr.matthstudio.themeteo.utilClasses.PhenomenonAlert
import fr.matthstudio.themeteo.utilClasses.PollenResponse
import fr.matthstudio.themeteo.utilClasses.VigilanceInfos
import fr.matthstudio.themeteo.utilClasses.VigilanceMapResponse
import fr.matthstudio.themeteo.telemetry.TelemetryManager
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
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import java.lang.Math.clamp
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections.emptyList
import java.util.Locale as JavaLocale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.text.format

// --- DATA CLASSES (inchangées) ---
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
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("country_code") val countryCode: String,
    @SerialName("admin1") val region: String
)

fun Double?.nanToNull(): Double? {
    return if (this == null || this.isNaN()) null else this
}

class WeatherService(private val telemetryManager: TelemetryManager? = null) {
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
            telemetryManager?.logException(e)
            null
        }
    }

    // Fonction pour récupérer l'empreinte SHA-1 de ton app dynamiquement
    fun getSigningSha1(context: Context): String? {    try {
        val packageName = context.packageName
        val packageManager = context.packageManager

        // PackageManager.GET_SIGNATURES est déprécié, mais nécessaire pour les API < 28
        @Suppress("DEPRECATION")
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }

        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()
                ?: packageInfo.signingInfo?.signingCertificateHistory?.firstOrNull()
        } else {
            // Pour les anciennes versions, on utilise la méthode dépréciée
            @Suppress("DEPRECATION")
            (packageInfo.signatures?.firstOrNull())
        }

        if (signature == null) {
            Log.e("SigningInfo", "Impossible de trouver la signature du certificat.")
            return null
        }

        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(signature.toByteArray())
        // On transforme le tableau de bytes en une chaîne hexadécimale SANS les ":"
        return digest.joinToString("") { "%02X".format(it) }

    } catch (e: Exception) {
        telemetryManager?.logException(e)
        Log.e("SigningInfo", "Erreur lors de la récupération du SHA-1", e)
        return null
    }
    }

    suspend fun getAirQuality(latitude: Double, longitude: Double, context: Context): AirQualityInfo? {
        val url = "https://airquality.googleapis.com/v1/currentConditions:lookup"
        val sha1 = getSigningSha1(context) ?: return null
        val packageName = context.packageName

        return try {
            val response = client.post(url) {
                parameter("key", BuildConfig.MAPS_API_KEY)
                header("X-Android-Package", packageName)
                header("X-Android-Cert", sha1) // Envoie l'empreinte automatiquement
                // On envoie la position dans le corps de la requête (POST)
                setBody(
                    AirQualityRequest(
                        location = AirQualityLocation(latitude, longitude),
                        extraComputations = listOf(
                            "HEALTH_RECOMMENDATIONS",
                            "DOMINANT_POLLUTANT_CONCENTRATION",
                            "POLLUTANT_CONCENTRATION"
                        ),
                        languageCode = java.util.Locale.getDefault().language
                    )
                )
                // Ktor s'occupe de la sérialisation en JSON grâce au plugin ContentNegotiation
                contentType(io.ktor.http.ContentType.Application.Json)
            }

            if (response.status.value == 200) {
                response.body<AirQualityInfo>()
            } else {
                val errorBody = response.body<String>()
                Log.e("AirQuality", "Erreur API Google : ${response.status} - $errorBody")
                telemetryManager?.logEvent("api_error", mapOf("api" to "google-air-quality", "status" to response.status.value, "error" to errorBody))
                null
            }
        } catch (e: Exception) {
            Log.e("AirQuality", "Exception lors de la récupération de la qualité de l'air : ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    suspend fun getCityNameFromCoords(latitude: Double, longitude: Double, context: Context): String? {
        val url = "https://maps.googleapis.com/maps/api/geocode/json"
        val sha1 = getSigningSha1(context) ?: return null
        return try {
            val response = client.get(url) {
                parameter("latlng", "$latitude,$longitude")
                parameter("key", BuildConfig.MAPS_API_KEY)
                parameter("language", java.util.Locale.getDefault().language)
                parameter("result_type", "locality|sublocality|administrative_area_level_1|administrative_area_level_2")
                header("X-Android-Package", context.packageName)
                header("X-Android-Cert", sha1)
            }
            if (response.status.value == 200) {
                val geocodingResponse = response.body<GoogleGeocodingResponse>()
                if (geocodingResponse.status == "OK" && geocodingResponse.results.isNotEmpty()) {
                    val components = geocodingResponse.results.first().addressComponents
                    components.find { "locality" in it.types }?.longName
                        ?: components.find { "sublocality" in it.types }?.longName
                        ?: components.find { "administrative_area_level_2" in it.types }?.longName
                        ?: components.find { "administrative_area_level_1" in it.types }?.longName
                } else null
            } else null
        } catch (e: Exception) {
            telemetryManager?.logException(e)
            null
        }
    }

    /**
     * Récupère les prévisions de pollen auprès de l'API Google Maps Pollen.
     */
    suspend fun getPollenForecast(latitude: Double, longitude: Double, context: Context): PollenResponse? {
        // L'endpoint pour les prévisions (forecast)
        val url = "https://pollen.googleapis.com/v1/forecast:lookup"

        // Réutilisation de la sécurité SHA-1 et package name comme pour l'Air Quality
        val sha1 = getSigningSha1(context) ?: return null
        val packageName = context.packageName

        return try {
            val response = client.get(url) {
                // Paramètres de l'API Google
                parameter("key", BuildConfig.MAPS_API_KEY)
                parameter("location.latitude", latitude)
                parameter("location.longitude", longitude)
                parameter("days", 3) // Nombre de jours de prévisions (1 à 5)
                parameter("languageCode", java.util.Locale.getDefault().language)
                parameter("plantsDescription", "true")

                // Headers de sécurité pour restreindre l'usage de la clé API à votre app
                header("X-Android-Package", packageName)
                header("X-Android-Cert", sha1)
            }

            if (response.status.value == 200) {
                response.body<PollenResponse>()
            } else {
                val errorBody = response.body<String>()
                Log.e("PollenAPI", "Erreur API Google : ${response.status} - $errorBody")
                telemetryManager?.logEvent("api_error", mapOf("api" to "google-pollen", "status" to response.status.value, "error" to errorBody))
                null
            }
        } catch (e: Exception) {
            Log.e("PollenAPI", "Exception lors de la récupération des données pollen : ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    /**
     * Récupère les textes de vigilance spécifiques au département de l'utilisateur.
     */
    suspend fun getVigilanceForLocation(lat: Double, lon: Double): VigilanceInfos? {
        val departmentCode = getDepartmentCodeFromLocation(lat, lon) ?: return null
        val url = "https://public-api.meteofrance.fr/public/DPVigilance/v1/cartevigilance/encours"
        val apiKey = BuildConfig.METEO_FRANCE_VIGILANCE_API_KEY

        return try {
            val response = client.get(url) {
                header("accept", "*/*")
                header("apikey", apiKey)
            }

            if (response.status.value == 200) {
                val fullResponse = response.body<VigilanceMapResponse>()

                // 1. On récupère les données du département pour toutes les périodes (J et J1)
                val deptInPeriods = fullResponse.product.periods.mapNotNull { period ->
                    period.timelaps.domainIds.find { it.domainId == departmentCode }
                }

                if (deptInPeriods.isEmpty()) return null

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

                if (mergedAlerts.isEmpty()) return null

                // 3. Déterminer le maxColorId global sur les alertes restantes
                val globalMaxColor = mergedAlerts.maxOf { it.maxColorId }

                VigilanceInfos(
                    departmentCode = departmentCode,
                    maxColorId = globalMaxColor,
                    alerts = mergedAlerts
                )
            } else {
                val errorBody = response.body<String>()
                Log.e("WeatherService", "Erreur API Carte Vigilance : ${response.status} - $errorBody")
                telemetryManager?.logEvent("api_error", mapOf("api" to "meteo-france-vigilance", "status" to response.status.value, "error" to errorBody))
                null
            }
        } catch (e: Exception) {
            Log.e("WeatherService", "Exception lors de la récupération de la carte vigilance", e)
            telemetryManager?.logException(e)
            null
        }
    }

    // Placeholder pour la fonction de localisation du département
    private suspend fun getDepartmentCodeFromLocation(lat: Double, lon: Double): String? {
        // On commence avec une précision élevée (ex : 6 décimales)
        // ou la précision actuelle si nécessaire.
        var precision = 6

        while (precision >= 0) {
            // Formate les coordonnées avec le nombre de décimales actuel
            val formattedLat = String.format(JavaLocale.US, "%.${precision}f", lat)
            val formattedLon = String.format(JavaLocale.US, "%.${precision}f", lon)

            val url = "https://api-adresse.data.gouv.fr/reverse/?lat=$formattedLat&lon=$formattedLon"

            try {
                val response = client.get(url)

                if (response.status.value == 200) {
                    val fullResponse = response.body<GovernmentInvertedGeocodingAPIResponse>()

                    // Vérifie si on a au moins un résultat
                    val feature = fullResponse.features.firstOrNull()
                    if (feature != null) {
                        // Succès : on renvoie les 2 premiers chiffres du code postal
                        return feature.properties.postcode.substring(0, 2)
                    }
                    // Si la liste est vide, la boucle continue et réduit la précision
                }
            } catch (e: Exception) {
                Log.e("WeatherService", "Erreur Geocoding à la précision $precision : ${e.message}")
                telemetryManager?.logException(e)
                // En cas d'erreur réseau, on peut choisir d'arrêter ou de continuer
                return null
            }

            Log.d("WeatherService", "Aucun résultat à $precision décimales, réduction de la précision...")
            precision--
        }

        // Si on arrive ici, aucune précision n'a donné de résultat
        return null
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
                val errorText = response.body<String>()
                Log.e("WeatherService", "Erreur API : $errorText")
                telemetryManager?.logEvent("api_error", mapOf("api" to "open-meteo-current", "status" to response.status.value, "error" to errorText))
                return null
            }

        } catch (e: Exception) {
            Log.e("getCurrentWeather", "Erreur lors de la récupération des prévisions complètes: ${e.message}")
            telemetryManager?.logException(e)
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
        models: List<String>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<String, Pair<List<AllHourlyVarsReading>, List<DailyReading>>>? {
        val apiUrl = "https://api.open-meteo.com/v1/forecast"
        val localZoneId = ZoneId.systemDefault()

        return try {
            val response = client.get(apiUrl) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("models", models.joinToString(","))
                parameter("hourly", getHourlyVariables().joinToString(","))
                parameter("daily", getDailyVariables().joinToString(","))
                parameter("timezone", localZoneId.id)
                parameter("start_date", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                parameter("end_date", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }

            Log.d("WeatherService", "Status: ${response.status}")

            if (response.status.value == 200) {
                val responseBody = response.body<WeatherApiResponse>()
                return models.associateWith { model ->
                    Pair(
                        parseHourlyData(responseBody, model, models.size > 1) as List<AllHourlyVarsReading>,
                        parseDailyData(responseBody, model, models.size > 1) as List<DailyReading>
                    )
                }
            } else {
                val errorText = response.body<String>()
                Log.e("WeatherService", "Erreur API : $errorText")
                telemetryManager?.logEvent("api_error", mapOf("api" to "open-meteo", "status" to response.status.value, "error" to errorText))
                return null
            }

        } catch (e: Exception) {
            Log.e("getForecast", "Erreur lors de la récupération des prévisions complètes: ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    suspend fun getEnsembleForecast(
        latitude: Double,
        longitude: Double,
        model: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Pair<List<AllHourlyVarsReading>, List<DailyReading>>? {
        val ensembleApiUrl = "https://ensemble-api.open-meteo.com/v1/ensemble"
        val localZoneId = ZoneId.systemDefault()

        val ensembleVariables = mutableListOf(
            "temperature_2m", "relative_humidity_2m", "dewpoint_2m", "apparent_temperature",
            "precipitation", "rain", "snowfall", "windspeed_10m", "wind_direction_10m", "cloudcover",
            "pressure_msl", "weather_code", "shortwave_radiation_instant", "direct_radiation_instant",
            "diffuse_radiation_instant", "wind_gusts_10m"
        )

        // Visibility is only supported by GFS and UKMO ensembles
        if (model.contains("gfs") || model.contains("ukmo")) {
            ensembleVariables.add("visibility")
        }

        // Snow Depth is only supported by GFS, IFS and GEM
        if (model.contains("gfs") || model.contains("ifs") || model.contains("gem")) {
            ensembleVariables.add("snow_depth")
        }

        return try {
            // 1. Fetch ensemble members
            val response = client.get(ensembleApiUrl) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("models", model)
                parameter("hourly", ensembleVariables.joinToString(","))
                parameter("timezone", localZoneId.id)
                parameter("start_date", startDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                parameter("end_date", endDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }

            if (response.status.value != 200) return null
            val ensembleResponseBody = response.body<WeatherApiResponse>()
            val times = ensembleResponseBody.getDeterministicHourlyData("time") ?: return null
            
            // 2. Calculate stats for each variable
            val statsMap = ensembleVariables.filter { it != "weather_code" }.associateWith { variable ->
                val matrix = ensembleResponseBody.getEnsembleData(variable)
                if (matrix != null) calculateEnsembleStats(matrix) else null
            }

            val wmoMatrix = ensembleResponseBody.getEnsembleData("weather_code")
            val wmoStats = if (wmoMatrix != null) calculateWmoEnsembleStats(wmoMatrix) else null

            // 3. Build AllHourlyVarsReading from stats
            val mergedHourly = times.indices.map { index ->
                val hourStats = mutableMapOf<String, EnsembleStat>()
                statsMap.keys.forEach { v ->
                    statsMap[v]?.getOrNull(index)?.let { hourStats[v] = it }
                }

                val temp = hourStats["temperature_2m"]?.avg
                val windSpeed = hourStats["windspeed_10m"]?.avg
                val windGusts = hourStats["wind_gusts_10m"]?.avg
                val windDir = hourStats["wind_direction_10m"]?.avg
                val shortwaveRadiation = hourStats["shortwave_radiation_instant"]?.avg
                val directRadiation = hourStats["direct_radiation_instant"]?.avg
                val diffuseRadiation = hourStats["diffuse_radiation_instant"]?.avg

                AllHourlyVarsReading(
                    time = LocalDateTime.parse(times[index] as String),
                    temperature = temp,
                    apparentTemperature = hourStats["apparent_temperature"]?.avg,
                    precipitationData = PrecipitationData(
                        precipitation = hourStats["precipitation"]?.avg,
                        rain = hourStats["rain"]?.avg,
                        snowfall = hourStats["snowfall"]?.avg,
                        precipitationProbability = null, 
                        snowDepth = (hourStats["snow_depth"]?.avg?.times(100.0))?.toInt()
                    ),
                    wind = WindData(
                        windspeed = windSpeed,
                        windGusts = windGusts,
                        windDirection = windDir
                    ),
                    skyInfo = SkyInfoData(
                        cloudcoverTotal = hourStats["cloudcover"]?.avg?.toInt(),
                        cloudcoverLow = null,
                        cloudcoverMid = null,
                        cloudcoverHigh = null,
                        shortwaveRadiation = shortwaveRadiation,
                        directRadiation = directRadiation,
                        diffuseRadiation = diffuseRadiation,
                        opacity = null,
                        uvIndex = null,
                        visibility = hourStats["visibility"]?.avg?.toInt()
                    ),
                    pressure = hourStats["pressure_msl"]?.avg?.toInt(),
                    humidity = hourStats["relative_humidity_2m"]?.avg?.toInt(),
                    dewpoint = hourStats["dewpoint_2m"]?.avg,
                    wmo = wmoStats?.getOrNull(index)?.worst,
                    ensembleStats = hourStats,
                    wmoEnsemble = wmoStats?.getOrNull(index)
                )
            }

            // 4. Derive DailyReading from hourly averages
            val dailyReadings = mergedHourly.groupBy { it.time.toLocalDate() }.map { (date, hourlyList) ->
                val dailyWmoEnsemble = if (hourlyList.any { it.wmoEnsemble != null }) {
                    WmoEnsembleStat(
                        best = hourlyList.mapNotNull { it.wmoEnsemble?.best }.minOrNull() ?: 0,
                        worst = hourlyList.mapNotNull { it.wmoEnsemble?.worst }.maxOrNull() ?: 0
                    )
                } else null

                val dailyPrecip = if (hourlyList.any { it.precipitationData.precipitation != null }) {
                    hourlyList.mapNotNull { it.precipitationData.precipitation }.sum()
                } else null

                DailyReading(
                    date = date,
                    maxTemperature = hourlyList.mapNotNull { it.temperature }.maxOrNull(),
                    minTemperature = hourlyList.mapNotNull { it.temperature }.minOrNull(),
                    precipitation = dailyPrecip,
                    maxWind = WindData(
                        windspeed = hourlyList.mapNotNull { it.wind.windspeed }.maxOrNull(),
                        windGusts = null,
                        windDirection = hourlyList.firstOrNull { it.wind.windspeed == hourlyList.mapNotNull { h -> h.wind.windspeed }.maxOrNull() }?.wind?.windDirection
                    ),
                    maxUvIndex = null,
                    wmo = dailyWmoEnsemble?.worst,
                    sunset = "",
                    sunrise = "",
                    wmoEnsemble = dailyWmoEnsemble
                )
            }

            Pair(mergedHourly, dailyReadings)

        } catch (e: Exception) {
            Log.e("getEnsembleForecast", "Error fetching ensemble forecast: ${e.message}")
            telemetryManager?.logException(e)
            null
        }
    }

    private fun getHourlyVariables(): List<String> {
        return listOf(
            "temperature_2m", "apparent_temperature", "precipitation", "rain", "showers", "snowfall",
            "cloudcover", "cloudcover_low", "cloudcover_mid", "cloudcover_high", "weather_code",
            "windspeed_10m", "wind_direction_10m", "pressure_msl", "relative_humidity_2m", "dewpoint_2m",
            "precipitation_probability", "snow_depth", "shortwave_radiation_instant", "direct_radiation_instant",
            "diffuse_radiation_instant", "uv_index", "visibility", "wind_gusts_10m"
        )
    }

    private fun getModelPrefixedName(variableName: String, model: String, isMultiModel: Boolean): String {
        return if (isMultiModel) "${variableName}_$model" else variableName
    }

    private fun Double?.safeToInt(): Int? {
        return if (this == null || this.isNaN()) null else this.toInt()
    }

    private fun parseHourlyData(response: WeatherApiResponse, model: String, isMultiModel: Boolean): List<AllHourlyVarsReading>? {
        try {
            val times = response.getDeterministicHourlyData("time") ?: return null

            val temp = response.getDeterministicHourlyData(getModelPrefixedName("temperature_2m", model, isMultiModel))
            val appTemp = response.getDeterministicHourlyData(getModelPrefixedName("apparent_temperature", model, isMultiModel))
            val precip = response.getDeterministicHourlyData(getModelPrefixedName("precipitation", model, isMultiModel))
            val precipProb = response.getDeterministicHourlyData(getModelPrefixedName("precipitation_probability", model, isMultiModel))
            val rain = response.getDeterministicHourlyData(getModelPrefixedName("rain", model, isMultiModel))
            val showers = response.getDeterministicHourlyData(getModelPrefixedName("showers", model, isMultiModel))
            val snowfall = response.getDeterministicHourlyData(getModelPrefixedName("snowfall", model, isMultiModel))
            val snowDepth = response.getDeterministicHourlyData(getModelPrefixedName("snow_depth", model, isMultiModel))
            val cloud = response.getDeterministicHourlyData(getModelPrefixedName("cloudcover", model, isMultiModel))
            val cloudLow = response.getDeterministicHourlyData(getModelPrefixedName("cloudcover_low", model, isMultiModel))
            val cloudMid = response.getDeterministicHourlyData(getModelPrefixedName("cloudcover_mid", model, isMultiModel))
            val cloudHigh = response.getDeterministicHourlyData(getModelPrefixedName("cloudcover_high", model, isMultiModel))
            val ghi = response.getDeterministicHourlyData(getModelPrefixedName("shortwave_radiation_instant", model, isMultiModel))
            val dhi = response.getDeterministicHourlyData(getModelPrefixedName("diffuse_radiation_instant", model, isMultiModel))
            val dsi = response.getDeterministicHourlyData(getModelPrefixedName("direct_radiation_instant", model, isMultiModel))
            val wui = response.getDeterministicHourlyData(getModelPrefixedName("uv_index", model, isMultiModel))
            val visibility = response.getDeterministicHourlyData(getModelPrefixedName("visibility", model, isMultiModel))
            val windspeed = response.getDeterministicHourlyData(getModelPrefixedName("windspeed_10m", model, isMultiModel))
            val windDir = response.getDeterministicHourlyData(getModelPrefixedName("wind_direction_10m", model, isMultiModel))
            val wgust = response.getDeterministicHourlyData(getModelPrefixedName("wind_gusts_10m", model, isMultiModel))
            val pressure = response.getDeterministicHourlyData(getModelPrefixedName("pressure_msl", model, isMultiModel))
            val humidity = response.getDeterministicHourlyData(getModelPrefixedName("relative_humidity_2m", model, isMultiModel))
            val dewpoint = response.getDeterministicHourlyData(getModelPrefixedName("dewpoint_2m", model, isMultiModel))
            val wmo = response.getDeterministicHourlyData(getModelPrefixedName("weather_code", model, isMultiModel))

            return times.indices.mapNotNull { i ->
                try {
                    val ghiVal = (ghi?.getOrNull(i) as? Double).nanToNull()
                    val dhiVal = (dhi?.getOrNull(i) as? Double).nanToNull()
                    val o = if (ghiVal != null && dhiVal != null)
                        (clamp(dhiVal / max(ghiVal, 1.0), 0.0, 1.0) * 100.0).roundToInt()
                    else null

                    AllHourlyVarsReading(
                        time = LocalDateTime.parse(times[i] as String),
                        temperature = (temp?.getOrNull(i) as? Double).nanToNull(),
                        apparentTemperature = (appTemp?.getOrNull(i) as? Double).nanToNull(),
                        precipitationData = PrecipitationData(
                            precipitation = (precip?.getOrNull(i) as? Double).nanToNull(),
                            precipitationProbability = (precipProb?.getOrNull(i) as? Double).safeToInt(),
                            rain = if (rain?.getOrNull(i) == null && showers?.getOrNull(i) == null) null else ((rain?.getOrNull(i) as? Double ?: 0.0) + (showers?.getOrNull(i) as? Double ?: 0.0)).nanToNull(),
                            snowfall = (snowfall?.getOrNull(i) as? Double).nanToNull(),
                            snowDepth = ((snowDepth?.getOrNull(i) as? Double)?.times(100)).safeToInt()
                        ),
                        skyInfo = SkyInfoData(
                            cloudcoverTotal = (cloud?.getOrNull(i) as? Double).safeToInt(),
                            cloudcoverLow = (cloudLow?.getOrNull(i) as? Double).safeToInt(),
                            cloudcoverMid = (cloudMid?.getOrNull(i) as? Double).safeToInt(),
                            cloudcoverHigh = (cloudHigh?.getOrNull(i) as? Double).safeToInt(),
                            shortwaveRadiation = (ghi?.getOrNull(i) as? Double).nanToNull(),
                            directRadiation = (dsi?.getOrNull(i) as? Double).nanToNull(),
                            diffuseRadiation = (dhi?.getOrNull(i) as? Double).nanToNull(),
                            opacity = o,
                            uvIndex = (wui?.getOrNull(i) as? Double).safeToInt(),
                            visibility = (visibility?.getOrNull(i) as? Double).safeToInt()
                        ),
                        wind = WindData(
                            windspeed = (windspeed?.getOrNull(i) as? Double).nanToNull(),
                            windGusts = (wgust?.getOrNull(i) as? Double).nanToNull(),
                            windDirection = (windDir?.getOrNull(i) as? Double).nanToNull()
                        ),
                        pressure = (pressure?.getOrNull(i) as? Double).safeToInt(),
                        humidity = (humidity?.getOrNull(i) as? Double).safeToInt(),
                        dewpoint = (dewpoint?.getOrNull(i) as? Double).nanToNull(),
                        wmo = (wmo?.getOrNull(i) as? Double).safeToInt()
                    )
                } catch (e: Exception) {
                    telemetryManager?.logException(e)
                    null
                }
            }
        } catch (e: Exception) {
            telemetryManager?.logException(e)
            return null
        }
    }

    private fun getDailyVariables(): List<String> {
        return listOf(
            "temperature_2m_max", "temperature_2m_min", "precipitation_sum", "wind_speed_10m_max", "wind_direction_10m_dominant",
            "wind_gusts_10m_max", "uv_index_max", "weather_code", "sunset", "sunrise"
        )
    }

    private fun parseDailyData(response: WeatherApiResponse, model: String, isMultiModel: Boolean): List<DailyReading>? {
        try {
            val times = response.getDeterministicDailyData("time") ?: return null

            val tempMax = response.getDeterministicDailyData(getModelPrefixedName("temperature_2m_max", model, isMultiModel))
            val tempMin = response.getDeterministicDailyData(getModelPrefixedName("temperature_2m_min", model, isMultiModel))
            val precip = response.getDeterministicDailyData(getModelPrefixedName("precipitation_sum", model, isMultiModel))
            val windSpeed = response.getDeterministicDailyData(getModelPrefixedName("wind_speed_10m_max", model, isMultiModel))
            val windDirection = response.getDeterministicDailyData(getModelPrefixedName("wind_direction_10m_dominant", model, isMultiModel))
            val windGusts = response.getDeterministicDailyData(getModelPrefixedName("wind_gusts_10m_max", model, isMultiModel))
            val uvIndex = response.getDeterministicDailyData(getModelPrefixedName("uv_index_max", model, isMultiModel))
            val sunset = response.getDeterministicDailyData(getModelPrefixedName("sunset", model, isMultiModel))
            val sunrise = response.getDeterministicDailyData(getModelPrefixedName("sunrise", model, isMultiModel))
            val wmo = response.getDeterministicDailyData(getModelPrefixedName("weather_code", model, isMultiModel))

            return times.indices.mapNotNull { i ->
                try {
                    DailyReading(
                        date = LocalDate.parse(times[i] as String),
                        maxTemperature = (tempMax?.getOrNull(i) as? Double).nanToNull(),
                        minTemperature = (tempMin?.getOrNull(i) as? Double).nanToNull(),
                        precipitation = (precip?.getOrNull(i) as? Double).nanToNull(),
                        maxWind = WindData(
                            windspeed = (windSpeed?.getOrNull(i) as? Double).nanToNull(),
                            windGusts = (windGusts?.getOrNull(i) as? Double).nanToNull(),
                            windDirection = (windDirection?.getOrNull(i) as? Double).nanToNull()
                        ),
                        maxUvIndex = (uvIndex?.getOrNull(i) as? Double).safeToInt(),
                        wmo = (wmo?.getOrNull(i) as? Double).safeToInt(),
                        sunset = sunset?.getOrNull(i) as? String ?: "",
                        sunrise = sunrise?.getOrNull(i) as? String ?: ""
                    )
                } catch (e: Exception) {
                    telemetryManager?.logException(e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("WeatherServiceParser", "Erreur majeure lors du parsing des données journalières", e)
            telemetryManager?.logException(e)
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
                    temperature = (temp as? Double).nanToNull(),
                    wmo = (wmo as Double).toInt()
                ))
            }
            return result
        } catch (e: Exception) {
            Log.e("WeatherServiceParser", "Erreur lors du parsing des données actuelles", e)
            telemetryManager?.logException(e)
            return null
        }
    }

    fun close() {
        client.close()
    }
}
