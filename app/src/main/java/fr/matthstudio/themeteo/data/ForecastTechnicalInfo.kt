package fr.matthstudio.themeteo.data

import java.time.LocalDateTime

data class ForecastTechnicalInfo(
    val requestedLat: Double,
    val requestedLon: Double,
    val gridLat: Double,
    val gridLon: Double,
    val distanceKm: Double,
    val shortTermModel: String,
    val extendedModel: String,
    val shortTermRunHour: LocalDateTime?,
    val extendedRunHour: LocalDateTime?
)
