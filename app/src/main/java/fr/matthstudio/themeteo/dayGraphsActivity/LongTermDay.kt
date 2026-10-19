/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.dayGraphsActivity

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.data.DailySynopticForecastUI
import fr.matthstudio.themeteo.data.SynopticScenarioUI
import fr.matthstudio.themeteo.data.SynopticStepUI
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.forecastMainActivity.LottieWeatherIcon
import fr.matthstudio.themeteo.forecastMainActivity.ResponsiveText
import fr.matthstudio.themeteo.forecastMainActivity.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastMainActivity.getLottieIconPath
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class PeriodInfo(
    val name: String,
    val hourLabel: String,
    val icon: ImageVector,
)

@Composable
fun getPeriodInfo(time: LocalDateTime): PeriodInfo {
    val userZone = ZoneId.systemDefault()
    val localTime = try {
        time.atZone(ZoneId.of("UTC")).withZoneSameInstant(userZone).toLocalDateTime()
    } catch (e: Exception) {
        time
    }
    val hour = localTime.hour
    val hourLabel = localTime.format(DateTimeFormatter.ofPattern("HH'h'"))
    return when (hour) {
        in 5..10 -> PeriodInfo(stringResource(R.string.period_morning), hourLabel, Icons.Rounded.WbSunny)
        in 11..16 -> PeriodInfo(stringResource(R.string.period_noon), hourLabel, Icons.Rounded.WbSunny)
        in 17..21 -> PeriodInfo(stringResource(R.string.period_evening), hourLabel, Icons.Rounded.WbTwilight)
        else -> PeriodInfo(stringResource(R.string.period_night), hourLabel, Icons.Rounded.NightsStay)
    }
}

@Composable
fun getWeatherWordDescription(word: SimpleWeatherWord): String {
    return when (word) {
        SimpleWeatherWord.STORMY -> stringResource(R.string.stormy)
        SimpleWeatherWord.STORMY_HAIL -> stringResource(R.string.thunder_with_hail)
        SimpleWeatherWord.EXTREME_STORMY -> stringResource(R.string.heavy_thunder)
        SimpleWeatherWord.EXTREME_STORMY_HAIL -> stringResource(R.string.heavy_thunder_with_hail)
        SimpleWeatherWord.HAIL -> stringResource(R.string.hail)
        SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2 -> stringResource(R.string.light_snow)
        SimpleWeatherWord.SNOWY3 -> stringResource(R.string.neige)
        SimpleWeatherWord.SNOWY_MIX -> stringResource(R.string.mixed_rain_snow)
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2, SimpleWeatherWord.RAIN_SHOWER, SimpleWeatherWord.RAIN_SHOWER1, SimpleWeatherWord.RAIN_SHOWER2 -> stringResource(R.string.pluie)
        SimpleWeatherWord.DRIZZLY -> stringResource(R.string.drizzle)
        SimpleWeatherWord.DUST -> stringResource(R.string.dust_storm)
        SimpleWeatherWord.HAZE -> stringResource(R.string.haze)
        SimpleWeatherWord.FOGGY -> stringResource(R.string.brouillard)
        SimpleWeatherWord.CLOUDY -> stringResource(R.string.nuageux)
        SimpleWeatherWord.VEILED -> stringResource(R.string.voil)
        SimpleWeatherWord.PARTLY_CLOUDY -> stringResource(R.string.sunny_spells)
        SimpleWeatherWord.MOSTLY_CLEAR -> stringResource(R.string.mostly_cloudy)
        SimpleWeatherWord.SUNNY -> "Ensoleillé"
    }
}

/**
 * Aperçu d'une journée à long terme (AIFS > J+7) basé sur un tableau synoptique par variable.
 */
@Composable
fun LongTermDayPreview(
    dayForecast: DailySynopticForecastUI,
    modifier: Modifier = Modifier
) {
    val scenarios = dayForecast.scenarios
    if (scenarios.isEmpty()) return

    var activeScenarioIndex by rememberSaveable { mutableIntStateOf(0) }
    val scenario = scenarios.getOrNull(activeScenarioIndex) ?: scenarios.first()

    val userSettingsState by AppStateManager.userSettings.collectAsState()
    val tempUnit = userSettingsState?.temperatureUnit ?: TemperatureUnit.CELSIUS
    val windUnit = userSettingsState?.windUnit ?: WindUnit.KPH
    val isExpertMode = userSettingsState?.expertMode == true

    val isBatterySaverActive by AppStateManager.isBatterySaverActive.collectAsState()
    val isDark = isSystemInDarkTheme()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DashboardDaySummaryCard(
            scenario = scenario,
            allScenarios = scenarios,
            tempUnit = tempUnit,
            windUnit = windUnit,
            isExpertMode = isExpertMode
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                scenarios.forEachIndexed { index, sc ->
                    val isSelected = activeScenarioIndex == index
                    Surface(
                        onClick = { activeScenarioIndex = index },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = "Scénario ${index + 1} (${sc.probability.roundToInt()}%)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // En-tête des 4 périodes de la journée
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    scenario.steps.forEach { step ->
                        val periodInfo = getPeriodInfo(step.time)
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = periodInfo.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = periodInfo.hourLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // --- LIGNE 1 : TEMPS SENSIBLE & CONDITIONS ---
                DashboardRowSection(title = "Conditions", icon = Icons.Rounded.WbSunny) {
                    scenario.steps.forEach { step ->
                        val wmoCode = if (step.wmoCode != 0) step.wmoCode else scenario.wmoCode
                        val weatherWord = weatherCodeToSimpleWord(wmoCode) ?: SimpleWeatherWord.SUNNY
                        val isNight = step.time.hour !in 6..21

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LottieWeatherIcon(
                                    iconPath = getLottieIconPath(weatherWord, isNight, isDark),
                                    animate = !isBatterySaverActive,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // --- LIGNE 2 : TEMPÉRATURE & RESSENTI ---
                DashboardRowSection(title = "Température", icon = Icons.Rounded.Thermostat) {
                    val dayMinTemp = scenario.steps.minOfOrNull { UnitConverter.convertTemperature(it.tempP50, tempUnit).roundToInt() }
                    val dayMaxTemp = scenario.steps.maxOfOrNull { UnitConverter.convertTemperature(it.tempP50, tempUnit).roundToInt() }

                    scenario.steps.forEach { step ->
                        val tempP50 = UnitConverter.convertTemperature(step.tempP50, tempUnit).roundToInt()
                        val appP50 = UnitConverter.convertTemperature(step.apparentTempP50, tempUnit).roundToInt()
                        val tempP10 = UnitConverter.convertTemperature(step.tempP10, tempUnit).roundToInt()
                        val tempP90 = UnitConverter.convertTemperature(step.tempP90, tempUnit).roundToInt()

                        val tempColor = when (tempP50) {
                            dayMinTemp -> Color(0xFF2196F3)
                            dayMaxTemp -> Color.Red
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$tempP50°",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = tempColor
                            )
                            Text(
                                text = "${stringResource(R.string.a_temperature_unit)} $appP50°",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isExpertMode) {
                                Text(
                                    text = "$tempP10°–$tempP90°",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // --- LIGNE 3 : PRÉCIPITATIONS & PLUIE ---
                DashboardRowSection(title = "Pluie", icon = Icons.Rounded.WaterDrop) {
                    scenario.steps.forEach { step ->
                        val mm = step.precipitation

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (mm > 0.05f) String.format(Locale.US, "%.1f mm/h", mm) else "0 mm/h",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (mm > 0.05f) Color(0xFF64B5F6) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (isExpertMode) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    DashboardRowSection(title = "Nébulosité", icon = Icons.Rounded.Cloud) {
                        scenario.steps.forEach { step ->
                            val cloudCover = step.cloudCoverTotal

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFF8E9AAF)
                                )
                                Text(
                                    text = "$cloudCover%",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                // --- LIGNE 4 : VENT & RAFALES ---
                DashboardRowSection(title = "Vent", icon = Icons.Rounded.Air) {
                    scenario.steps.forEach { step ->
                        val windP50 = UnitConverter.convertWind(step.windSpeedP50, windUnit).roundToInt()
                        val windP10 = UnitConverter.convertWind(step.windSpeedP10, windUnit).roundToInt()
                        val windP90 = UnitConverter.convertWind(step.windSpeedP90, windUnit).roundToInt()
                        val gustP90 = UnitConverter.convertWind(step.windGustP90, windUnit).roundToInt()
                        val speedUnitLabel = if (windUnit == WindUnit.KPH) "km/h" else "mph"

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(12.dp)
                                        .rotate(step.windDirection + 90f),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "$windP50",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = speedUnitLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ResponsiveText(
                                text = "Raf. $gustP90$speedUnitLabel",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isExpertMode) {
                                Text(
                                    text = "$windP10–$windP90",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // --- LIGNE 5 : PRESSION ---
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                DashboardRowSection(title = "Pression", icon = Icons.Rounded.Compress) {
                    scenario.steps.forEach { step ->
                        val pressP50 = step.pressureP50.roundToInt()
                        val pressP10 = step.pressureP10.roundToInt()
                        val pressP90 = step.pressureP90.roundToInt()

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "$pressP50 hPa",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isExpertMode) {
                                Text(
                                    text = "$pressP10–$pressP90",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // --- LIGNE 6 : HUMIDITÉ ---
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                DashboardRowSection(title = "Humidité", icon = Icons.Rounded.Opacity) {
                    scenario.steps.forEach { step ->
                        val humP50 = step.humidityP50
                        val humP10 = step.humidityP10
                        val humP90 = step.humidityP90

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Opacity,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFF4DD0E1)
                                )
                                Text(
                                    text = "$humP50%",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4DD0E1)
                                )
                            }
                            if (isExpertMode) {
                                Text(
                                    text = "$humP10–$humP90%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Carte de résumé synthétique de la journée pour le tableau synoptique.
 */
@Composable
fun DashboardDaySummaryCard(
    scenario: SynopticScenarioUI,
    allScenarios: List<SynopticScenarioUI>,
    tempUnit: TemperatureUnit,
    windUnit: WindUnit,
    isExpertMode: Boolean,
    modifier: Modifier = Modifier
) {
    val minTempP50 = scenario.steps.minOfOrNull { it.tempP50 } ?: 0f
    val maxTempP50 = scenario.steps.maxOfOrNull { it.tempP50 } ?: 0f
    val convertedMin = UnitConverter.convertTemperature(minTempP50, tempUnit).roundToInt()
    val convertedMax = UnitConverter.convertTemperature(maxTempP50, tempUnit).roundToInt()

    val allMinP10 = allScenarios.flatMap { s -> s.steps.map { it.tempP10 } }.minOrNull() ?: 0f
    val allMaxP90 = allScenarios.flatMap { s -> s.steps.map { it.tempP90 } }.maxOrNull() ?: 0f
    val convertedMinP10 = UnitConverter.convertTemperature(allMinP10, tempUnit).roundToInt()
    val convertedMaxP90 = UnitConverter.convertTemperature(allMaxP90, tempUnit).roundToInt()

    val totalPrecip = scenario.steps.sumOf { it.precipitation.toDouble() }.toFloat()
    val maxWind = scenario.steps.maxOfOrNull { it.windSpeedP50 } ?: 0f
    val maxGustP90 = scenario.steps.maxOfOrNull { it.windGustP90 } ?: 0f

    val convertedWind = UnitConverter.convertWind(maxWind, windUnit).roundToInt()
    val convertedGustP90 = UnitConverter.convertWind(maxGustP90, windUnit).roundToInt()
    val speedUnitLabel = if (windUnit == WindUnit.KPH) "km/h" else "mph"

    val mainWord = weatherCodeToSimpleWord(scenario.wmoCode) ?: SimpleWeatherWord.SUNNY
    val isBatterySaverActive by AppStateManager.isBatterySaverActive.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LottieWeatherIcon(
                            iconPath = getLottieIconPath(mainWord, false, isSystemInDarkTheme()),
                            animate = !isBatterySaverActive,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = getWeatherWordDescription(mainWord),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$convertedMin°",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                    Text(
                        text = " / ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$convertedMax°",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pluie synthétique
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF64B5F6)
                    )
                    Text(
                        text = "Pluie: ${if (totalPrecip > 0.1f) String.format(Locale.US, "%.1f mm", totalPrecip) else "0 mm"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64B5F6)
                    )
                }

                // Vent synthétique
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Air,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isExpertMode)
                            "Vent max: $convertedWind $speedUnitLabel (raf. $convertedGustP90${speedUnitLabel})"
                        else
                            "Vent max: $convertedWind $speedUnitLabel",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardRowSection(
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

// =========================================================================================
// DONNÉES MOCK & PREVIEWS COMPOSE
// =========================================================================================

fun generateMockDailySynopticForecast(): DailySynopticForecastUI {
    val date = LocalDate.now().plusDays(8)

    fun mockStep(hour: Int, baseTemp: Float, precipProb: Int, precipMm: Float, wmoCode: Int = 0): SynopticStepUI {
        return SynopticStepUI(
            time = date.atTime(hour, 0),
            wmoCode = wmoCode,
            tempP10 = baseTemp - 3f,
            tempP50 = baseTemp,
            tempP90 = baseTemp + 4f,
            apparentTempP10 = baseTemp - 4f,
            apparentTempP50 = baseTemp - 1f,
            apparentTempP90 = baseTemp + 3f,
            pressureP10 = 1012f,
            pressureP50 = 1015f,
            pressureP90 = 1018f,
            windSpeedP10 = 12f,
            windSpeedP50 = 18f,
            windSpeedP90 = 28f,
            windDirection = 225f,
            windGustP10 = 22f,
            windGustP50 = 35f,
            windGustP90 = 50f,
            precipitation = precipMm,
            visibility = 10000f,
            humidityP10 = 60,
            humidityP50 = 75,
            humidityP90 = 85,
            precipitationProbability = precipProb,
            cloudCoverTotal = 65
        )
    }

    return DailySynopticForecastUI(
        dayNumber = 8,
        date = date,
        scenarios = listOf(
            SynopticScenarioUI(
                probability = 60f,
                steps = listOf(
                    mockStep(6, 13f, 30, 0.2f, 1),
                    mockStep(12, 19f, 70, 2.5f, 61),
                    mockStep(18, 15f, 25, 0.1f, 2),
                    mockStep(0, 11f, 15, 0.0f, 0)
                ),
                wmoCode = 61
            ),
            SynopticScenarioUI(
                probability = 25f,
                steps = listOf(
                    mockStep(6, 11f, 5, 0.0f, 0),
                    mockStep(12, 22f, 10, 0.0f, 0),
                    mockStep(18, 17f, 5, 0.0f, 1),
                    mockStep(0, 9f, 0, 0.0f, 0)
                ),
                wmoCode = 1
            ),
            SynopticScenarioUI(
                probability = 15f,
                steps = listOf(
                    mockStep(6, 10f, 60, 1.8f, 61),
                    mockStep(12, 14f, 80, 5.0f, 63),
                    mockStep(18, 11f, 40, 0.9f, 3),
                    mockStep(0, 8f, 30, 0.5f, 3)
                ),
                wmoCode = 63
            )
        )
    )
}

// --- COMPOSE PREVIEWS ---

@Preview(showBackground = true, name = "Tableau Synoptique - Mode Standard")
@Composable
fun LongTermDayPreviewStandardMode() {
    val mockSettings = UserSettings(
        roundToInt = true,
        defaultLocation = LocationIdentifier.CurrentUserLocation,
        defaultScreen = DefaultScreen.FORECAST_MAIN,
        enableAnimatedIcons = true,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windUnit = WindUnit.KPH,
        firebaseConsent = "GRANTED",
        gcuAccepted = true,
        lastGcuUpdate = null,
        lastPrivacyPolicyUpdate = null,
        hasOpenedAppOnce = true,
        useEurAqi = true,
        backgroundLocationAsked = true,
        themeMode = ThemeMode.FIXED,
        enableRainNotifications = true,
        enableVigilanceNotifications = true,
        enableDurationExtension = false,
        expertMode = false
    )
    AppStateManager.updateUserSettings(mockSettings)

    TheMeteoTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                LongTermDayPreview(dayForecast = remember { generateMockDailySynopticForecast() })
            }
        }
    }
}

@Preview(showBackground = true, name = "Tableau Synoptique - Mode Expert")
@Composable
fun LongTermDayPreviewExpertMode() {
    val mockSettings = UserSettings(
        roundToInt = true,
        defaultLocation = LocationIdentifier.CurrentUserLocation,
        defaultScreen = DefaultScreen.FORECAST_MAIN,
        enableAnimatedIcons = true,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windUnit = WindUnit.KPH,
        firebaseConsent = "GRANTED",
        gcuAccepted = true,
        lastGcuUpdate = null,
        lastPrivacyPolicyUpdate = null,
        hasOpenedAppOnce = true,
        useEurAqi = true,
        backgroundLocationAsked = true,
        themeMode = ThemeMode.FIXED,
        enableRainNotifications = true,
        enableVigilanceNotifications = true,
        enableDurationExtension = false,
        expertMode = true
    )
    AppStateManager.updateUserSettings(mockSettings)

    TheMeteoTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                LongTermDayPreview(dayForecast = remember { generateMockDailySynopticForecast() })
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Tableau Synoptique - Thème Sombre")
@Composable
fun LongTermDayPreviewDark() {
    val mockSettings = UserSettings(
        roundToInt = true,
        defaultLocation = LocationIdentifier.CurrentUserLocation,
        defaultScreen = DefaultScreen.FORECAST_MAIN,
        enableAnimatedIcons = true,
        temperatureUnit = TemperatureUnit.CELSIUS,
        windUnit = WindUnit.KPH,
        firebaseConsent = "GRANTED",
        gcuAccepted = true,
        lastGcuUpdate = null,
        lastPrivacyPolicyUpdate = null,
        hasOpenedAppOnce = true,
        useEurAqi = true,
        backgroundLocationAsked = true,
        themeMode = ThemeMode.FIXED,
        enableRainNotifications = true,
        enableVigilanceNotifications = true,
        enableDurationExtension = false,
        expertMode = false
    )
    AppStateManager.updateUserSettings(mockSettings)

    TheMeteoTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                LongTermDayPreview(dayForecast = remember { generateMockDailySynopticForecast() })
            }
        }
    }
}
