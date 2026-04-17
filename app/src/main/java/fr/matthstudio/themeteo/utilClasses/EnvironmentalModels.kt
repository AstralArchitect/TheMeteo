package fr.matthstudio.themeteo.utilClasses

import androidx.compose.ui.graphics.Color
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class EnvironmentalUIModel(
    val days: List<EnvironmentalDayUI>,
    val isDataAvailable: Boolean = true
)

data class EnvironmentalDayUI(
    val dateLabel: String,
    val airQuality: AirQualityUI,
    val pollen: PollenUI,
    val globalColor: Color
)

data class AirQualityUI(
    val value: Int,
    val label: String,
    val color: Color,
    val recommendation: String?,
    val pollutants: List<PollutantUI>,
    val healthAdvice: List<HealthAdviceUI>,
    val minAqi: Int = 0,
    val maxAqi: Int = 0,
    val avgAqi: Int = 0
)

data class PollutantUI(
    val name: String,
    val value: String,
    val code: String,
    val color: Color = Color.Gray
)

fun getPollutantColor(code: String, value: Double?): Color {
    if (value == null) return Color.Gray
    
    // Seuils approximatifs basés sur l'AQI européen/OMS pour simplifier
    // Vert: < 33%, Jaune: 33-66%, Rouge: > 66%
    return when (code.lowercase()) {
        "pm25" -> when {
            value < 10 -> Color(0xFF50f0e6)
            value < 20 -> Color(0xFF50ccaa)
            value < 25 -> Color(0xFFf0e641)
            value < 50 -> Color(0xFFff5050)
            value < 75 -> Color(0xFF960032)
            else -> Color(0xFF7d2181)
        }
        "pm10" -> when {
            value < 20 -> Color(0xFF50f0e6)
            value < 40 -> Color(0xFF50ccaa)
            value < 50 -> Color(0xFFf0e641)
            value < 100 -> Color(0xFFff5050)
            value < 150 -> Color(0xFF960032)
            else -> Color(0xFF7d2181)
        }
        "no2" -> when {
            value < 40 -> Color(0xFF50f0e6)
            value < 90 -> Color(0xFF50ccaa)
            value < 120 -> Color(0xFFf0e641)
            value < 230 -> Color(0xFFff5050)
            value < 340 -> Color(0xFF960032)
            else -> Color(0xFF7d2181)
        }
        "o3" -> when {
            value < 50 -> Color(0xFF50f0e6)
            value < 100 -> Color(0xFF50ccaa)
            value < 130 -> Color(0xFFf0e641)
            value < 240 -> Color(0xFFff5050)
            value < 380 -> Color(0xFF960032)
            else -> Color(0xFF7d2181)
        }
        "so2" -> when {
            value < 100 -> Color(0xFF50f0e6)
            value < 200 -> Color(0xFF50ccaa)
            value < 350 -> Color(0xFFf0e641)
            value < 500 -> Color(0xFFff5050)
            value < 750 -> Color(0xFF960032)
            else -> Color(0xFF7d2181)
        }
        "co" -> when {
            value < 2200 -> Color(0xFF50f0e6)
            value < 4400 -> Color(0xFF50ccaa)
            value < 6500 -> Color(0xFFf0e641)
            value < 8700 -> Color(0xFFff5050)
            value < 13000 -> Color(0xFF960032)
            else -> Color(0xFF7d2181)
        }
        else -> Color.Gray
    }
}

data class HealthAdviceUI(
    val title: String,
    val advice: String
)

data class PollenUI(
    val grass: PollenTypeUI?,
    val tree: PollenTypeUI?,
    val weed: PollenTypeUI?,
    val riskLevel: Int,
    val riskLabel: String,
    val color: Color,
    val plants: List<PollenPlantUI> = emptyList()
)

data class PollenPlantUI(
    val name: String,
    val level: Int,
    val description: String?,
    val color: Color,
    val type: String? // "TREE", "GRASS", "WEED"
)

data class PollenTypeUI(
    val level: Int,
    val color: Color,
    val label: String,
    val description: String? = null
)

fun formatPollutantUnit(unit: String?): String {
    return when (unit) {
        "PARTS_PER_BILLION" -> "ppb"
        "PART_PER_BILLION" -> "ppb"
        "PARTS_PER_MILLION" -> "ppm"
        "PART_PER_MILLION" -> "ppm"
        "MICROGRAMS_PER_CUBIC_METER" -> "µg/m³"
        null -> ""
        else -> unit.lowercase()
    }
}

fun mapToEnvironmentalUI(aqi: AirQualityInfo?, aqiForecast: AirQualityForecastResponse?, pollen: PollenResponse?, useEurAqi: Boolean = true): EnvironmentalUIModel {
    
    // Fonction utilitaire pour mapper l'AQI
    fun mapAqi(info: AirQualityInfo, min: Int = 0, max: Int = 0, avg: Int = 0): AirQualityUI {
        // Prioriser l'indice européen (eur_aqi) ou l'indice universel (uaqi)
        val mainIndex = if (useEurAqi) {
            info.indexes.find { it.code == "fra_atmo" } ?: info.indexes.firstOrNull()
        } else {
            info.indexes.firstOrNull()
        }
            
        val pollutants = info.pollutants?.map { 
            PollutantUI(
                name = it.displayName, 
                value = "${it.concentration?.value} ${formatPollutantUnit(it.concentration?.units)}", 
                code = it.code,
                color = getPollutantColor(it.code, it.concentration?.value)
            )
        } ?: emptyList()
        
        val healthAdvice = mutableListOf<HealthAdviceUI>()
        info.healthRecommendations?.let { hr ->
            hr.generalPopulation?.let { healthAdvice.add(HealthAdviceUI("Général", it)) }
            hr.elderly?.let { healthAdvice.add(HealthAdviceUI("Personnes fragiles", it)) }
            hr.children?.let { healthAdvice.add(HealthAdviceUI("Enfants", it)) }
            hr.athletes?.let { healthAdvice.add(HealthAdviceUI("Sportifs", it)) }
        }

        return AirQualityUI(
            value = mainIndex?.aqi ?: 0,
            label = mainIndex?.category ?: "N/A",
            color = mainIndex?.color?.let { Color(it.red ?: 0.5f, it.green ?: 0.5f, it.blue ?: 0.5f) } ?: Color.Gray,
            recommendation = info.healthRecommendations?.generalPopulation,
            pollutants = pollutants,
            healthAdvice = healthAdvice,
            minAqi = min,
            maxAqi = max,
            avgAqi = avg
        )
    }

    // Fonction utilitaire pour mapper le Pollen d'un jour donné
    fun mapPollenDay(day: DailyPollenInfo): PollenUI {
        fun getPollenType(code: String): PollenTypeUI? {
            val info = day.pollenTypeInfo?.find { it.code == code } ?: return null
            return PollenTypeUI(
                level = info.indexInfo?.value ?: 0,
                color = info.indexInfo?.color?.let { Color(it.red ?: 0.5f, it.green ?: 0.5f, it.blue ?: 0.5f) } ?: Color.Gray,
                label = info.displayName ?: code,
                description = info.healthRecommendations?.joinToString(". ")
            )
        }
        
        val plants = day.plantInfo?.filter { it.inSeason == true }?.map { 
            val plantLabel = buildString {
                append(it.displayName ?: it.code)
                it.plantDescription?.family?.let { fam -> append(" - $fam") }
            }
            PollenPlantUI(
                name = plantLabel,
                level = it.indexInfo?.value ?: 0,
                description = it.healthRecommendations?.joinToString(". "),
                color = it.indexInfo?.color?.let { c -> Color(c.red ?: 0.5f, c.green ?: 0.5f, c.blue ?: 0.5f) } ?: Color.Gray,
                type = it.plantDescription?.type // ex: "TREE", "GRASS", "WEED"
            )
        } ?: emptyList()

        val maxType = day.pollenTypeInfo?.maxByOrNull { it.indexInfo?.value ?: 0 }
        return PollenUI(
            grass = getPollenType("GRASS"),
            tree = getPollenType("TREE"),
            weed = getPollenType("WEED"),
            riskLevel = maxType?.indexInfo?.value ?: 0,
            riskLabel = maxType?.indexInfo?.category ?: "Aucun",
            color = maxType?.indexInfo?.color?.let { Color(it.red ?: 0.5f, it.green ?: 0.5f, it.blue ?: 0.5f) } ?: Color.Gray,
            plants = plants
        )
    }

    // Grouper les prévisions d'AQI par jour
    val aqiByDay = mutableMapOf<String, List<AirQualityInfo>>()
    aqiForecast?.hourlyForecasts?.forEach { hourly ->
        try {
            // L'API renvoie du ISO_INSTANT (ex: 2024-03-22T14:00:00Z)
            // Instant.parse est le plus robuste pour ce format
            val instant = java.time.Instant.parse(hourly.dateTime)
            val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val key = "${date.dayOfMonth}/${date.monthValue}"
            aqiByDay[key] = (aqiByDay[key] ?: emptyList()) + hourly
        } catch (e: Exception) {
            // Log d'erreur silencieux pour ne pas bloquer le reste
        }
    }

    val days = pollen?.dailyInfo?.mapIndexed { index, dailyPollen ->
        val dateLabel = dailyPollen.date.let { "${it.day}/${it.month}" }
        
        // Trouver la "pire" qualité de l'air pour ce jour
        val dayAqiInfo = if (index == 0 && aqi != null) {
            aqi
        } else {
            // On prend la valeur moyenne ou maximale pour le résumé du jour
            aqiByDay[dateLabel]?.maxByOrNull { it.indexes.firstOrNull()?.aqi ?: 0 }
        }

        val dayAqiValues = aqiByDay[dateLabel]?.map { info ->
             if (useEurAqi) {
                info.indexes.find { it.code == "eur_aqi" }?.aqi ?: info.indexes.firstOrNull()?.aqi ?: 0
            } else {
                info.indexes.firstOrNull()?.aqi ?: 0
            }
        } ?: emptyList()

        val min = dayAqiValues.minOrNull() ?: 0
        val max = dayAqiValues.maxOrNull() ?: 0
        val avg = if (dayAqiValues.isNotEmpty()) dayAqiValues.average().toInt() else 0

        val currentDayAqi = if (dayAqiInfo != null) {
            if (useEurAqi) {
                dayAqiInfo.indexes.find { it.code == "eur_aqi" }?.aqi ?: dayAqiInfo.indexes.firstOrNull()?.aqi ?: 0
            } else {
                dayAqiInfo.indexes.firstOrNull()?.aqi ?: 0
            }
        } else 0

        EnvironmentalDayUI(
            dateLabel = dateLabel,
            airQuality = if (dayAqiInfo != null) mapAqi(dayAqiInfo, min, max, avg) else AirQualityUI(
                value = 0,
                label = "N/A",
                color = Color.Gray,
                recommendation = null,
                pollutants = emptyList(),
                healthAdvice = emptyList(),
                minAqi = min,
                maxAqi = max,
                avgAqi = avg
            ),
            pollen = mapPollenDay(dailyPollen),
            globalColor = if (dayAqiInfo != null && currentDayAqi > (dailyPollen.pollenTypeInfo?.maxOfOrNull { it.indexInfo?.value ?: 0 } ?: 0) * 20) {
                mapAqi(dayAqiInfo).color
            } else {
                mapPollenDay(dailyPollen).color
            }
        )
    }?.filter { it.airQuality.value != 0 } ?: emptyList() // On ne garde que les jours complets

    return EnvironmentalUIModel(days, aqi != null || pollen != null)
}
