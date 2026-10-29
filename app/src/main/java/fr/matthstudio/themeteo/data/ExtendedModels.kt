/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Représente un pas de prévision moyen terme (J+2 à J+7).
 * Inclut les incertitudes via les centiles P10, P50 (médiane) et P90.
 */
@Serializable
data class MediumTermStepUI(
    @Serializable(with = LocalDateTimeSerializer::class)
    val time: LocalDateTime,
    val wmoCode: Int,
    
    // Températures (°C)
    val tempP10: Float,
    val tempP50: Float,
    val tempP90: Float,
    
    // Ressenti (°C)
    val apparentTempP10: Float,
    val apparentTempP50: Float,
    val apparentTempP90: Float,
    
    // Pression (hPa)
    val pressureP10: Float,
    val pressureP50: Float,
    val pressureP90: Float,
    
    // Vent (m/s)
    val windSpeedP10: Float,
    val windSpeedP50: Float,
    val windSpeedP90: Float,
    val windDirection: Float,
    
    // Rafales (m/s)
    val windGustP10: Float,
    val windGustP50: Float,
    val windGustP90: Float,
    
    // Précipitations (mm)
    val totalPrecipitation: Float,
    val snowPrecipitation: Float,
    
    // Probabilités et couvertures (%)
    val precipitationProbability: Int,
    val instabilityProbability: Int,
    val humidityP10: Int,
    val humidityP50: Int,
    val humidityP90: Int,
    val cloudCoverTotal: Int,
    val cloudCoverP10: Int = cloudCoverTotal,
    val cloudCoverP90: Int = cloudCoverTotal
)

/**
 * Un pas temporel (6h) au sein d'un scénario synoptique.
 * AIFS fournit des prévisions toutes les 6 heures avec son propre code WMO déduit de la physique.
 */
@Serializable
data class SynopticStepUI(
    @Serializable(with = LocalDateTimeSerializer::class)
    val time: LocalDateTime,
    val wmoCode: Int = 0, // Code WMO spécifique à ce pas de 6h (0 à 82)
    val tempP10: Float,
    val tempP50: Float,
    val tempP90: Float,
    val apparentTempP10: Float,
    val apparentTempP50: Float,
    val apparentTempP90: Float,
    val pressureP10: Float,
    val pressureP50: Float,
    val pressureP90: Float,
    val windSpeedP10: Float,
    val windSpeedP50: Float,
    val windSpeedP90: Float,
    val windDirection: Float,
    val windGustP10: Float,
    val windGustP50: Float,
    val windGustP90: Float,
    val precipitation: Float,
    val precipitationProbability: Int,
    val cloudCoverTotal: Int = 0
)

/**
 * Scénario synoptique pour le long terme (AIFS-ENS).
 * Contient le WMO représentatif / dominant du jour et la série temporelle de 7 pas de 6h.
 */
@Serializable
data class SynopticScenarioUI(
    val probability: Float,
    val wmoCode: Int, // Code WMO représentatif / dominant du scénario sur la journée
    val steps: List<SynopticStepUI>, // Série temporelle de 4 pas de 6h (chacun avec son propre wmoCode)
    val precipitationSum: Float = 0f
)

/**
 * Prévision synoptique journalière (J+7 à J+14).
 */
@Serializable
data class DailySynopticForecastUI(
    val dayNumber: Int,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val scenarios: List<SynopticScenarioUI> // Max 3 scénarios
)

/**
 * Conteneur global pour les prévisions étendues (J+2 à J+14).
 */
@Serializable
data class ExtendedForecastUI(
    val latitude: Float,
    val longitude: Float,
    @Serializable(with = LocalDateTimeSerializer::class)
    val runHour: LocalDateTime,
    val mediumTermSteps: List<MediumTermStepUI>,
    val longTermDays: List<DailySynopticForecastUI>,
    val mediumTermDailyPrecipitation: List<Float> = emptyList()
)
