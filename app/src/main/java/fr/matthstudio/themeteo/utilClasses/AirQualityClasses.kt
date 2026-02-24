package fr.matthstudio.themeteo.utilClasses

import kotlinx.serialization.Serializable

@Serializable
data class AirQualityRequest(
    val location: AirQualityLocation,
    val extraComputations: List<String> = listOf(
        "HEALTH_RECOMMENDATIONS",
        "DOMINANT_POLLUTANT_CONCENTRATION",
        "POLLUTANT_ADDITIONAL_INFO",
        "POLLUTANT_CONCENTRATION"
    ),
    val languageCode: String
)

@Serializable
data class AirQualityLocation(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class AirQualityInfo(
    val dateTime: String,
    val regionCode: String? = null,
    val indexes: List<AQIIndex>,
    val pollutants: List<Pollutant>? = null,
    val healthRecommendations: HealthRecommendations? = null
)

@Serializable
data class AQIIndex(
    val code: String? = null,
    val displayName: String? = null,
    val aqi: Int? = null,
    val aqiDisplay: String? = null,
    val color: AirQualityColor? = null,
    val category: String? = null,
    val dominantPollutant: String? = null
)

@Serializable
data class Pollutant(
    val code: String,
    val displayName: String,
    val fullName: String? = null,
    val concentration: PollutantConcentration? = null
)

@Serializable
data class PollutantConcentration(
    val value: Double,
    val units: String
)

@Serializable
data class HealthRecommendations(
    val generalPopulation: String? = null,
    val elderly: String? = null,
    val children: String? = null,
    val athletes: String? = null
)

@Serializable
data class AirQualityColor(
    val red: Float?,
    val green: Float?,
    val blue: Float?
)