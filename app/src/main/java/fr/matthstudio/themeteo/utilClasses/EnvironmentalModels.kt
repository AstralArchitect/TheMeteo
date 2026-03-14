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
    val healthAdvice: List<HealthAdviceUI>
)

data class PollutantUI(
    val name: String,
    val value: String,
    val code: String
)

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
    val color: Color
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

fun mapToEnvironmentalUI(aqi: AirQualityInfo?, aqiForecast: AirQualityForecastResponse?, pollen: PollenResponse?): EnvironmentalUIModel {
    
    // Fonction utilitaire pour mapper l'AQI
    fun mapAqi(info: AirQualityInfo): AirQualityUI {
        val mainIndex = info.indexes.firstOrNull()
        val pollutants = info.pollutants?.map { 
            PollutantUI(it.displayName, "${it.concentration?.value} ${formatPollutantUnit(it.concentration?.units)}", it.code)
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
            healthAdvice = healthAdvice
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
        val maxType = day.pollenTypeInfo?.maxByOrNull { it.indexInfo?.value ?: 0 }
        return PollenUI(
            grass = getPollenType("GRASS"),
            tree = getPollenType("TREE"),
            weed = getPollenType("WEED"),
            riskLevel = maxType?.indexInfo?.value ?: 0,
            riskLabel = maxType?.indexInfo?.category ?: "Aucun",
            color = maxType?.indexInfo?.color?.let { Color(it.red ?: 0.5f, it.green ?: 0.5f, it.blue ?: 0.5f) } ?: Color.Gray
        )
    }

    // Grouper les prévisions d'AQI par jour
    val aqiByDay = mutableMapOf<String, List<AirQualityInfo>>()
    aqiForecast?.hourlyForecasts?.forEach { hourly ->
        try {
            val date = ZonedDateTime.parse(hourly.dateTime).toLocalDate()
            val key = "${date.dayOfMonth}/${date.monthValue}"
            aqiByDay[key] = (aqiByDay[key] ?: emptyList()) + hourly
        } catch (e: Exception) {}
    }

    val days = pollen?.dailyInfo?.mapIndexed { index, dailyPollen ->
        val dateLabel = dailyPollen.date.let { "${it.day}/${it.month}" }
        
        // Trouver la "pire" qualité de l'air pour ce jour
        val dayAqiInfo = if (index == 0 && aqi != null) {
            aqi
        } else {
            aqiByDay[dateLabel]?.maxByOrNull { it.indexes.firstOrNull()?.aqi ?: 0 }
        }

        EnvironmentalDayUI(
            dateLabel = dateLabel,
            airQuality = if (dayAqiInfo != null) mapAqi(dayAqiInfo) else AirQualityUI(
                value = 0,
                label = "N/A",
                color = Color.Gray,
                recommendation = null,
                pollutants = emptyList(),
                healthAdvice = emptyList(),
            ),
            pollen = mapPollenDay(dailyPollen),
            globalColor = if (dayAqiInfo != null && (dayAqiInfo.indexes.firstOrNull()?.aqi ?: 0) > (dailyPollen.pollenTypeInfo?.maxOfOrNull { it.indexInfo?.value ?: 0 } ?: 0) * 20) {
                mapAqi(dayAqiInfo).color
            } else {
                mapPollenDay(dailyPollen).color
            }
        )
    }?.filter { it.airQuality.value != 0 } ?: emptyList() // On ne garde que les jours complets

    return EnvironmentalUIModel(days, aqi != null || pollen != null)
}
