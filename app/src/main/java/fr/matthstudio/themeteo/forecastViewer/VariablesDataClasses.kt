package fr.matthstudio.themeteo.forecastViewer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// --- CLASSES DE DONNÉES (Data Classes) pour la réponse JSON ---
// Ces classes modélisent la structure de la réponse JSON d'Open-Meteo.
@Serializable
data class WeatherApiResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("hourly_units")
    val hourlyUnits: Map<String, String>? = null,
    val hourly: Map<String, JsonElement>? = null,
    @SerialName("daily_units")
    val dailyUnits: Map<String, String>? = null,
    val daily: Map<String, JsonElement>? = null,
    @SerialName("minutely_15_units")
    val minutely15Units: Map<String, String>? = null,
    @SerialName("minutely_15")
    val minutely15: Map<String, JsonElement>? = null,
    @SerialName("elevation")
    val elevation: Double
)

/**
 * Récupère les données de tous les membres d'ensemble pour une variable donnée.
 *
 * @param variableName Le nom de base de la variable (ex: "temperature_2m", "pressure_msl")
 * @return Une liste de listes (Matrice 2D).
 * - Dim 1 : L'index du membre (0 = member01, 1 = member02...)
 * - Dim 2 : La série temporelle des valeurs.
 */
fun WeatherApiResponse.getEnsembleData(variableName: String): List<List<Double?>>? {
    // Le préfixe à chercher, ex: "temperature_2m_member"
    val targetPrefix = "${variableName}_member"

    return hourly
        // 1. On filtre pour ne garder que les clés qui concernent notre variable ET sont des membres
        ?.filter { (key, _) -> key.startsWith(targetPrefix) }

        // 2. On trie les clés alphabétiquement pour garantir l'ordre (member01, member02...)
        ?.toSortedMap()

        // 3. On prend les valeurs (qui sont des JsonElement/JsonArray)
        ?.values
        ?.map { jsonElement ->
            // 4. On transforme le tableau JSON en liste Kotlin
            jsonElement.jsonArray.map { element ->
                // On gère le cas où la valeur est "null" dans le JSON ou n'est pas un nombre
                if (element is JsonNull) null else element.jsonPrimitive.doubleOrNull
            }
        }
}

data class EnsembleStat(val avg: Double, val min: Double, val max: Double)

/**
 * Calcule la moyenne, le minimum et le maximum pour chaque pas de temps.
 *
 * @param ensembleMatrix La matrice [Membre][Heure] obtenue via getEnsembleData
 * @return Une liste de Triple où :
 * - first = Moyenne (Average)
 * - second = Minimum
 * - third = Maximum
 */
fun calculateEnsembleStats(ensembleMatrix: List<List<Double?>>): List<EnsembleStat> {
    // Sécurité : si la matrice est vide, on retourne une liste vide
    if (ensembleMatrix.isEmpty()) return emptyList()

    // On assume que tous les membres ont la même durée (standard Open-Météo)
    // On récupère le nombre d'heures (taille de la liste du premier membre)
    val timeSeriesLength = ensembleMatrix.first().size

    // On crée une liste de la taille du nombre d'heures
    return List(timeSeriesLength) { timeIndex ->

        // Pour l'heure actuelle 'timeIndex', on récupère les valeurs de tous les membres
        // mapNotNull écarte automatiquement les membres qui auraient une valeur null à cette heure
        val valuesAtHour = ensembleMatrix.mapNotNull { memberList ->
            memberList.getOrNull(timeIndex)
        }

        if (valuesAtHour.isNotEmpty()) {
            val average = valuesAtHour.average()
            val min = valuesAtHour.minOrNull() ?: 0.0
            val max = valuesAtHour.maxOrNull() ?: 0.0

            EnsembleStat(average, min, max)
        } else {
            EnsembleStat(Double.NaN, Double.NaN, Double.NaN)
        }
    }
}

/**
 * Récupère les données d'une variable spécifique pour le modèle déterministe.
 * * @param variableName Le nom de la variable (ex: "temperature_2m", "rain")
 * @return Une liste de Double? (nullable pour gérer les données manquantes)
 */
fun WeatherApiResponse.getDeterministicHourlyData(variableName: String): List<Any>? {
    // On cherche directement la clé dans la map hourly
    val dataElement = hourly?.get(variableName) ?: return emptyList()

    return try {
        dataElement.jsonArray.map { element ->
            if (element.jsonPrimitive.isString) element.jsonPrimitive.content
            else element.jsonPrimitive.double
        }
    } catch (_: Exception) {
        // Au cas où l'élément n'est pas un tableau (sécurité)
        null
    }
}

/**
 * Récupère les données d'une variable spécifique pour le modèle déterministe.
 * * @param variableName Le nom de la variable (ex: "temperature_2m", "rain")
 * @return Une liste de Double? (nullable pour gérer les données manquantes)
 */
fun WeatherApiResponse.getDeterministicDailyData(variableName: String): List<Any>? {
    // On cherche directement la clé dans la map daily
    val dataElement = daily?.get(variableName) ?: return emptyList()

    return try {
        dataElement.jsonArray.map { element ->
            if (element.jsonPrimitive.isString) element.jsonPrimitive.content
            else element.jsonPrimitive.double
        }
    } catch (_: Exception) {
        // Au cas où l'élément n'est pas un tableau (sécurité)
        null
    }
}

/**
 * Récupère les données d'une variable spécifique pour le modèle déterministe.
 * * @param variableName Le nom de la variable (ex: "temperature_2m", "rain")
 * @return Une liste de Double? (nullable pour gérer les données manquantes)
 */
fun WeatherApiResponse.getDeterministicMinutely15Data(variableName: String): List<Any>? {
    // On cherche directement la clé dans la map daily
    val dataElement = minutely15?.get(variableName) ?: return emptyList()

    return try {
        dataElement.jsonArray.map { element ->
            if (element.jsonPrimitive.isString) element.jsonPrimitive.content
            else element.jsonPrimitive.double
        }
    } catch (_: Exception) {
        // Au cas où l'élément n'est pas un tableau (sécurité)
        null
    }
}