package fr.matthstudio.themeteo.utilClasses

import kotlinx.serialization.Serializable

/**
 * Réponse principale de l'API Pollen forecast.
 */
@Serializable
data class PollenResponse(
    val dailyInfo: List<DailyPollenInfo>,
    val nextPageToken: String? = null
)

@Serializable
data class DailyPollenInfo(
    val date: DateInfo,
    val pollenTypeInfo: List<PollenTypeInfo>? = null,
    val plantInfo: List<PlantInfo>? = null
)

@Serializable
data class DateInfo(
    val year: Int,
    val month: Int,
    val day: Int
)

/**
 * Informations sur un type de pollen (ex : GRASS, TREE, WEED).
 */
@Serializable
data class PollenTypeInfo(
    val code: String, // ex: "GRASS"
    val displayName: String? = null,
    val inSeason: Boolean? = null,
    val indexInfo: IndexInfo? = null,
    val healthRecommendations: List<String>? = null
)

/**
 * Informations sur une plante spécifique (ex: OLIVE, ALDER).
 */
@Serializable
class PlantInfo(
    val code: String, // ex: "BIRCH"
    val displayName: String? = null,
    val inSeason: Boolean? = null,
    val indexInfo: IndexInfo? = null,
    val healthRecommendations: List<String>? = null,
    val plantDescription: PlantDescription? = null
)

/**
 * Détails de l'indice de pollen (UPI - Universal Pollen Index).
 */
@Serializable
data class IndexInfo(
    val code: String, // ex: "upi"
    val displayName: String? = null,
    val value: Int? = null,
    val category: String? = null,
    val indexDescription: String? = null,
    val color: PollenColor? = null
)

@Serializable
data class PlantDescription(
    val type: String? = null,
    val family: String? = null,
    val season: String? = null,
    val specialCharacteristics: String? = null,
    val crossReactivity: String? = null
)

@Serializable
data class PollenColor(
    val red: Float? = null,
    val green: Float? = null,
    val blue: Float? = null
)