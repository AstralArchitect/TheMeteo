/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class DailyScenario(
    val percentage: Int,
    val wmo: Int,
    val minTemperature: Float,
    val maxTemperature: Float,
    val precipitationProbability: Int,
    val maxWindSpeed: Float,
    val dominantWindDirection: Float,
)

@Serializable
data class DailyForecastUI(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val wmoCode: Int,
    val minTemperature: Float,
    val maxTemperature: Float,
    val precipitationSum: Float,
    val precipitationProbability: Int,
    val maxWindSpeed: Float,
    val maxWindGust: Float = 0f,
    val dominantWindDirection: Float,
    val sunrise: String,
    val sunset: String,
    val scenario: DailyScenario? = null,
    val scenarios: List<DailyScenario>? = null
)

@Serializable
data class DailyForecastListUI(
    val latitude: Float,
    val longitude: Float,
    val days: List<DailyForecastUI>
)
