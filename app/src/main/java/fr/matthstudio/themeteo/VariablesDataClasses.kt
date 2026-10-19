/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive

// --- CLASSES DE DONNÉES (Data Classes) pour la réponse JSON ---
// Ces classes modélisent la structure de la réponse JSON d'Open-Meteo.
@Serializable
data class WeatherApiResponse(
    val latitude: Double,
    val longitude: Double,
    // --- Données Actuelles  ---
    @SerialName("current_units")
    val currentUnits: Map<String, String>? = null,
    val current: Map<String, JsonElement>? = null,

    // --- Données Horaires ---
    @SerialName("hourly_units")
    val hourlyUnits: Map<String, String>? = null,
    val hourly: Map<String, JsonElement>? = null,

    // --- Données Journalières ---
    @SerialName("daily_units")
    val dailyUnits: Map<String, String>? = null,
    val daily: Map<String, JsonElement>? = null,

    // --- Données 15 minutes ---
    @SerialName("minutely_15_units")
    val minutely15Units: Map<String, String>? = null,
    @SerialName("minutely_15")
    val minutely15: Map<String, JsonElement>? = null
)

@Serializable
data class EnsembleStat(val avg: Double?, val min: Double?, val max: Double?)

@Serializable
@Parcelize
data class WmoEnsembleStat(val best: Int, val worst: Int) : Parcelable

/**
 * Récupère les données d'une variable spécifique.
 * @param variableName Le nom de la variable (ex: "temperature_2m", "rain")
 * @return Un Double (nullable pour gérer les données manquantes)
 */
fun WeatherApiResponse.getCurrentWeatherData(variableName: String): Any? {
    // On cherche directement la clé dans la map hourly
    val dataElement = current?.get(variableName) ?: return null

    return try {
        if (dataElement.jsonPrimitive.isString) dataElement.jsonPrimitive.content
        else dataElement.jsonPrimitive.double
    } catch (_: Exception) {
        // Au cas où l'élément n'est pas un tableau (sécurité)
        null
    }
}

@Serializable
data class GoogleGeocodingResponse(
    val results: List<GoogleGeocodingResult>,
    val status: String
)

@Serializable
data class GoogleGeocodingResult(
    @SerialName("address_components") val addressComponents: List<AddressComponent>,
    @SerialName("formatted_address") val formattedAddress: String
)

@Serializable
data class AddressComponent(
    @SerialName("long_name") val longName: String,
    @SerialName("short_name") val shortName: String,
    val types: List<String>
)