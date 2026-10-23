/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.dayGraphsActivity

import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.EnsembleStat
import fr.matthstudio.themeteo.ShortTermStepUI
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.MediumTermStepUI
import fr.matthstudio.themeteo.data.SynopticStepUI
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.forecastMainActivity.LottieWeatherIcon
import fr.matthstudio.themeteo.forecastMainActivity.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastMainActivity.getLottieIconPath
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

enum class GraphType {
    TEMP, A_TEMP,
    DEW_POINT, HUMIDITY,
    PRECIPITATION, PRECIPITATION_PROB, RAIN, SNOWFALL, SNOW_DEPTH,
    WIND_SPEED, WIND_GUST, PRESSURE,
    CLOUD_COVER, CLOUD_COVER_LOW, CLOUD_COVER_MID, CLOUD_COVER_HIGH,
    UV_INDEX, VISIBILITY
}

fun calculateDewPoint(temperature: Float, humidity: Int?): Int {
    if (humidity == null) return temperature.toInt()
    val rh = humidity.coerceIn(1, 100).toFloat()
    val a = 17.27f
    val b = 237.7f
    val alpha = ((a * temperature) / (b + temperature)) + ln(rh / 100f)
    return ((b * alpha) / (a - alpha)).toInt()
}

@Composable
fun GenericGraph(
    viewModel: WeatherViewModel,
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    contentWidth: Dp,
    scrollState: ScrollState = rememberScrollState()
) {
    val fullForecast by viewModel.hourlyForecast.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val settings = userSettings ?: return
    var roundToInt = settings.roundToInt

    if (graphType == GraphType.PRECIPITATION || graphType == GraphType.RAIN ||
        graphType == GraphType.SNOWFALL
    )
        roundToInt = false

    GenericGraphGlobal(
        fullForecast,
        roundToInt,
        settings.temperatureUnit,
        settings.windUnit,
        graphType,
        graphColor,
        valueRange,
        scrollState,
        contentWidth = contentWidth
    )
}

@Composable
fun GenericGraphGlobal(
    fullForecast: WeatherDataState,
    roundToInt: Boolean,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    windUnit: WindUnit = WindUnit.KPH,
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState(),
    contentWidth: Dp = 1250.dp,
    contentHeight: Dp = 125.dp,
    compactHourFormat: Boolean = false,
    sparseMode: Boolean = false
) {
    val rawShortTermData = (fullForecast as? WeatherDataState.SuccessShortTermHourly)?.data
    val shortTermData = if (sparseMode && rawShortTermData != null) {
        rawShortTermData.filterIndexed { index, _ -> index % 2 == 0 }
    } else {
        rawShortTermData
    }
    val mediumTermData = (fullForecast as? WeatherDataState.SuccessMediumTermHourly)?.data
    if (shortTermData == null && mediumTermData == null) return

    val totalSteps = shortTermData?.size ?: mediumTermData?.size ?: 0
    if (totalSteps == 0) return

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        var forecast: List<Number>
        var maxForecast: List<Number>? = null
        val times: List<String>

        if (shortTermData != null) {
            times = shortTermData.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + if (!compactHourFormat) "h" else "" }
            forecast = when (graphType) {
                GraphType.TEMP -> shortTermData.map { UnitConverter.convertTemperature(it.temperature, temperatureUnit) }
                GraphType.A_TEMP -> shortTermData.map { UnitConverter.convertTemperature(it.apparentTemperature, temperatureUnit) }
                GraphType.DEW_POINT -> shortTermData.map { f ->
                    val dp = calculateDewPoint(f.temperature, f.humidity)
                    UnitConverter.convertTemperature(dp, temperatureUnit)
                }
                GraphType.PRECIPITATION_PROB -> shortTermData.map { it.precipitationProbability ?: 0 }
                GraphType.PRECIPITATION -> {
                    maxForecast = shortTermData.map { it.maxPossiblePrecipitation }
                    shortTermData.map { it.precipitation }
                }
                GraphType.RAIN -> shortTermData.map { (it.precipitation - it.snowfall).coerceAtLeast(0f) }
                GraphType.SNOWFALL -> shortTermData.map { it.snowfall }
                GraphType.WIND_SPEED -> shortTermData.map { UnitConverter.convertWind(it.windSpeed, windUnit) }
                GraphType.WIND_GUST -> shortTermData.map { UnitConverter.convertWind(it.windGust, windUnit) }
                GraphType.PRESSURE -> shortTermData.map { it.pressure }
                GraphType.HUMIDITY -> shortTermData.map { it.humidity ?: 0 }
                GraphType.CLOUD_COVER -> shortTermData.map { it.cloudCoverTotal ?: 0 }
                GraphType.CLOUD_COVER_LOW -> shortTermData.map { (it.cloudCoverLow ?: 0).toFloat() }
                GraphType.CLOUD_COVER_MID -> shortTermData.map { (it.cloudCoverMid ?: 0).toFloat() }
                GraphType.CLOUD_COVER_HIGH -> shortTermData.map { (it.cloudCoverHigh ?: 0).toFloat() }
                else -> shortTermData.map { it.temperature }
            }
        } else {
            val mediumData = mediumTermData!!
            times = mediumData.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + if (!compactHourFormat) "h" else "" }
            forecast = when (graphType) {
                GraphType.TEMP -> mediumData.map { UnitConverter.convertTemperature(it.tempP50, temperatureUnit) }
                GraphType.A_TEMP -> mediumData.map { UnitConverter.convertTemperature(it.apparentTempP50, temperatureUnit) }
                GraphType.DEW_POINT -> mediumData.map { f ->
                    val dp = calculateDewPoint(f.tempP50, f.humidityP50)
                    UnitConverter.convertTemperature(dp, temperatureUnit)
                }
                GraphType.PRECIPITATION_PROB -> mediumData.map { it.precipitationProbability }
                GraphType.PRECIPITATION -> mediumData.map { it.totalPrecipitation }
                GraphType.RAIN -> mediumData.map { it.totalPrecipitation }
                GraphType.SNOWFALL -> mediumData.map { 0f }
                GraphType.WIND_SPEED -> mediumData.map { UnitConverter.convertWind(it.windSpeedP50, windUnit) }
                GraphType.WIND_GUST -> mediumData.map { UnitConverter.convertWind(it.windGustP50, windUnit) }
                GraphType.PRESSURE -> mediumData.map { it.pressureP50 }
                GraphType.HUMIDITY -> mediumData.map { it.humidityP50 }
                GraphType.CLOUD_COVER -> mediumData.map { it.cloudCoverTotal }
                GraphType.CLOUD_COVER_LOW -> mediumData.map { 0f }
                GraphType.CLOUD_COVER_MID -> mediumData.map { 0f }
                GraphType.CLOUD_COVER_HIGH -> mediumData.map { 0f }
                else -> mediumData.map { it.tempP50 }
            }
        }

        if (forecast.isEmpty()) return@Box

        val hasMaxPrecip = maxForecast != null && maxForecast.indices.any { i ->
            maxForecast[i].toFloat() > forecast[i].toFloat() + 0.01f
        }

        val textColor: Int = AndroidColor.rgb(
            MaterialTheme.colorScheme.onBackground.red,
            MaterialTheme.colorScheme.onBackground.green,
            MaterialTheme.colorScheme.onBackground.blue
        )

        Canvas(
            modifier = Modifier
                .width(contentWidth)
                .height(contentHeight)
        ) {
            val xPadding = with(density) { 20.dp.toPx() }
            val yPadding = 80f
            var maxValue = forecast.maxOf { if (roundToInt) it.toDouble().roundToInt().toDouble() else it.toDouble() }
            if (hasMaxPrecip && maxForecast != null) {
                maxValue = maxOf(maxValue, maxForecast.maxOf { if (roundToInt) it.toDouble().roundToInt().toDouble() else it.toDouble() })
            }
            var minValue = forecast.minOf { if (roundToInt) it.toDouble().roundToInt().toDouble() else it.toDouble() }

            if (valueRange != null) {
                maxValue = maxValue.coerceAtLeast(valueRange.endInclusive.toDouble())
                minValue = minValue.coerceAtMost(valueRange.start.toDouble())
            }

            val canvasWidth = size.width
            val xStep = (canvasWidth - 2 * xPadding) / (totalSteps - 1).coerceAtLeast(1)
            val yScale = (size.height - 2 * yPadding) / (maxValue - minValue).coerceAtLeast(1.0)

            val linePath = Path()
            val gradientPath = Path()

            val firstX = xPadding
            val firstY = size.height - yPadding - (((if(roundToInt) forecast.first().toDouble().roundToInt().toDouble() else forecast.first().toDouble()) - minValue) * yScale)
            linePath.moveTo(firstX, firstY.toFloat())
            gradientPath.moveTo(firstX, size.height)
            gradientPath.lineTo(firstX, firstY.toFloat())

            forecast.forEachIndexed { i, point ->
                val x = xPadding + (i * xStep)
                val y = size.height - yPadding - (((if(roundToInt) point.toDouble().roundToInt().toDouble() else point.toDouble()) - minValue) * yScale)
                linePath.lineTo(x, y.toFloat())
                gradientPath.lineTo(x, y.toFloat())
            }

            val lastX = xPadding + ((forecast.size - 1) * xStep)
            gradientPath.lineTo(lastX, size.height)
            gradientPath.close()

            drawPath(
                path = gradientPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        graphColor.copy(alpha = 0.35f),
                        graphColor.copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = size.height
                )
            )

            // Courbe principale (ligne continue)
            drawPath(linePath, graphColor, style = Stroke(width = 8f))

            // Courbe max possible precipitation (ligne en pointillé)
            if (hasMaxPrecip && maxForecast != null) {
                val mf = maxForecast
                val maxLinePath = Path()
                val maxFirstX = xPadding
                val maxFirstY = size.height - yPadding - (((if (roundToInt) mf[0].toDouble().roundToInt().toDouble() else mf[0].toDouble()) - minValue) * yScale)
                maxLinePath.moveTo(maxFirstX, maxFirstY.toFloat())

                mf.forEachIndexed { i, point ->
                    val x = xPadding + (i * xStep)
                    val y = size.height - yPadding - (((if (roundToInt) point.toDouble().roundToInt().toDouble() else point.toDouble()) - minValue) * yScale)
                    maxLinePath.lineTo(x, y.toFloat())
                }

                drawPath(
                    path = maxLinePath,
                    color = graphColor.copy(alpha = 0.85f),
                    style = Stroke(
                        width = 5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }

            forecast.forEachIndexed { i, mainPoint ->
                val x = xPadding + (i * xStep)
                val mainValDouble = if (roundToInt) mainPoint.toDouble().roundToInt().toDouble() else mainPoint.toDouble()
                val yMain = size.height - yPadding - ((mainValDouble - minValue) * yScale)

                val maxPoint = maxForecast?.getOrNull(i)
                val maxValDouble = maxPoint?.let { if (roundToInt) it.toDouble().roundToInt().toDouble() else it.toDouble() }
                val isMaxHigher = hasMaxPrecip && maxValDouble != null && (maxPoint.toFloat() > mainPoint.toFloat() + 0.05f)

                if (isMaxHigher && maxValDouble != null && maxPoint != null) {
                    val yMax = size.height - yPadding - ((maxValDouble - minValue) * yScale)
                    val deltaY = (yMain - yMax).toFloat()

                    // Point sur la courbe max
                    drawCircle(graphColor.copy(alpha = 0.85f), radius = 5f, center = Offset(x, yMax.toFloat()))

                    // Texte pour la valeur max (au-dessus du point max)
                    drawContext.canvas.nativeCanvas.drawText(
                        if (roundToInt) maxPoint.toDouble().roundToInt().toString() else maxPoint.toSmartString(),
                        x,
                        yMax.toFloat() - 18f,
                        Paint().apply {
                            textAlign = Paint.Align.CENTER
                            textSize = 34f
                            color = textColor
                            alpha = (255 * 0.85f).toInt()
                            isFakeBoldText = true
                        }
                    )

                    // Point sur la courbe principale
                    drawCircle(Color.White, radius = 6f, center = Offset(x, yMain.toFloat()))

                    // Texte pour la valeur principale (en-dessous du point principal si conflit vertical, sinon au-dessus)
                    val mainTextY = if (deltaY < 50f) yMain.toFloat() + 36f else yMain.toFloat() - 20f
                    drawContext.canvas.nativeCanvas.drawText(
                        if (roundToInt) mainPoint.toDouble().roundToInt().toString() else mainPoint.toSmartString(),
                        x,
                        mainTextY,
                        Paint().apply {
                            textAlign = Paint.Align.CENTER
                            textSize = 40f
                            color = textColor
                        }
                    )
                } else {
                    // Pas de valeur max distincte : affichage standard au-dessus du point principal
                    drawCircle(Color.White, radius = 6f, center = Offset(x, yMain.toFloat()))

                    drawContext.canvas.nativeCanvas.drawText(
                        if (roundToInt) mainPoint.toDouble().roundToInt().toString() else mainPoint.toSmartString(),
                        x,
                        yMain.toFloat() - 20f,
                        Paint().apply {
                            textAlign = Paint.Align.CENTER
                            textSize = 40f
                            color = textColor
                        }
                    )
                }

                drawContext.canvas.nativeCanvas.drawText(
                    times[i],
                    x,
                    size.height - 20f,
                    Paint().apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 40f
                        color = textColor
                    }
                )
            }
        }
    }

    if (graphType == GraphType.WIND_SPEED && shortTermData != null) {
        WindVectors(shortTermData, windUnit, scrollState, contentWidth, false, totalSteps)
    }
}

@Composable
fun BackgroundGrid(
    forecast: WeatherDataState,
    modifier: Modifier = Modifier,
    contentWidth: Dp
) {
    val times: List<LocalDateTime>? = when (forecast) {
        is WeatherDataState.SuccessShortTermHourly -> forecast.data.map { it.time }
        is WeatherDataState.SuccessMediumTermHourly -> forecast.data.map { it.time }
        is WeatherDataState.SuccessLongTermDaily -> forecast.data.firstOrNull()?.scenarios?.firstOrNull()?.steps?.map { it.time }
        else -> null
    }
    if (times.isNullOrEmpty()) return

    val itemCount = times.size
    val density = LocalDensity.current
    val xPadding = with(density) { 20.dp.toPx() }
    val textColor = MaterialTheme.colorScheme.onBackground

    Canvas(
        modifier = modifier
            .width(contentWidth)
            .fillMaxHeight()
    ) {
        val xStep = (size.width - 2 * xPadding) / (itemCount - 1).coerceAtLeast(1)
        val gridColor = Color.Gray.copy(alpha = 0.3f)
        val daySeparatorColor = Color.Gray.copy(alpha = 0.7f)

        times.forEachIndexed { i, time ->
            val drawX = xPadding + (i * xStep)

            if (i < itemCount - 1) {
                val midX = drawX + xStep / 2
                drawLine(
                    color = gridColor,
                    start = Offset(midX, 0f),
                    end = Offset(midX, size.height),
                    strokeWidth = 2f
                )
            }

            if (time.hour == 0 && i > 0) {
                val separatorX = drawX - xStep / 2
                drawLine(
                    color = daySeparatorColor,
                    start = Offset(separatorX, 0f),
                    end = Offset(separatorX, size.height),
                    strokeWidth = 4f
                )

                val dayLabel = time.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                drawContext.canvas.nativeCanvas.drawText(
                    dayLabel,
                    drawX + 10f,
                    40f,
                    Paint().apply {
                        textAlign = Paint.Align.LEFT
                        textSize = 35f
                        color = AndroidColor.rgb(
                            (textColor.red * 255).toInt(),
                            (textColor.green * 255).toInt(),
                            (textColor.blue * 255).toInt()
                        )
                        isFakeBoldText = true
                    }
                )
            }
        }
    }
}

@Composable
fun WindVectors(
    hourlyData: List<ShortTermStepUI>,
    windUnit: WindUnit = WindUnit.KPH,
    scrollState: ScrollState = rememberScrollState(),
    contentWidth: Dp = 1250.dp,
    sparseMode: Boolean = false,
    originalSize: Int = 24
) {
    val density = LocalDensity.current
    val xPaddingDp = 20.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Box(modifier = Modifier.width(contentWidth)) {
            if (hourlyData.isNotEmpty()) {
                val canvasWidthPx = with(density) { contentWidth.toPx() }
                val xPaddingPx = with(density) { xPaddingDp.toPx() }
                val xStepPx = (canvasWidthPx - 2 * xPaddingPx) / (originalSize - 1)

                hourlyData.forEachIndexed { i, allVarsReading ->
                    val xPosPx = xPaddingPx + (i * (if(sparseMode) 2 else 1) * xStepPx)
                    val xPosDp = with(density) { xPosPx.toDp() }

                    Column(
                        modifier = Modifier
                            .offset(x = xPosDp - 20.dp)
                            .width(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val windGusts = allVarsReading.windGust
                        Text(
                            text = UnitConverter.formatValue(
                                UnitConverter.convertWind(
                                    windGusts,
                                    windUnit
                                )
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(
                                    allVarsReading.windDirection + 90f
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherIconGraphGlobal(
    forecast: WeatherDataState,
    scrollState: ScrollState = rememberScrollState(),
    userSettings: UserSettings,
    isBatterySaverActive: Boolean,
    contentWidth: Dp = 1250.dp,
    showPairsOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animated = userSettings.enableAnimatedIcons && !isBatterySaverActive
    val rawHourlyList: List<Pair<LocalDateTime, Int>>? = when (forecast) {
        is WeatherDataState.SuccessShortTermHourly -> forecast.data.map { Pair(it.time, it.wmoCode) }
        is WeatherDataState.SuccessMediumTermHourly -> forecast.data.map { Pair(it.time, it.wmoCode) }
        else -> null
    }
    val hourlyList = if (showPairsOnly && rawHourlyList != null) {
        rawHourlyList.filterIndexed { index, _ -> index % 2 == 0 }
    } else {
        rawHourlyList
    }
    val sunData by AppStateManager.sunData.collectAsState()

    val density = LocalDensity.current
    val iconsSize: Dp = if (!showPairsOnly) 63.6.dp else 57.82.dp
    val xPaddingPx = with(density) { 20.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Box(modifier = Modifier.width(contentWidth)) {
            if (hourlyList.isNullOrEmpty()) return@Box

            val canvasWidthPx = with(density) { contentWidth.toPx() }
            val xStepPx = (canvasWidthPx - 2 * xPaddingPx) / (hourlyList.size - 1).coerceAtLeast(1)

            for ((i, step) in hourlyList.withIndex()) {
                val stepTime = step.first
                val wmoCode = step.second
                val xPosPx = xPaddingPx + (i * xStepPx)
                val xPosDp = with(density) { xPosPx.toDp() }

                val weatherWord = weatherCodeToSimpleWord(wmoCode)

                val dailySun = sunData?.dailyData?.find { it.date == stepTime.toLocalDate() }
                val isNightStep = if (dailySun != null) {
                    stepTime.isBefore(dailySun.sunrise) || stepTime.isAfter(dailySun.sunset)
                } else {
                    val hour = stepTime.hour
                    hour < 7 || hour >= 21
                }

                Box(
                    modifier = Modifier
                        .offset(x = xPosDp - (iconsSize / 2))
                        .size(iconsSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (weatherWord != null) {
                        LottieWeatherIcon(
                            iconPath = getLottieIconPath(weatherWord, isNightStep, isSystemInDarkTheme()),
                            animate = animated,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            imageVector = Icons.Default.NotInterested,
                            contentDescription = "Icône météo actuelle",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherIconGraph(
    modifier: Modifier = Modifier,
    viewModel: WeatherViewModel,
    scrollState: ScrollState = rememberScrollState(),
    contentWidth: Dp = 1250.dp
) {
    val forecast by viewModel.hourlyForecast.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val settings = userSettings ?: return
    val isBatterySaverActive by AppStateManager.isBatterySaverActive.collectAsState()

    WeatherIconGraphGlobal(
        forecast,
        scrollState,
        settings,
        isBatterySaverActive,
        contentWidth,
        false,
        modifier = modifier
    )
}

@Composable
fun MediumTermTripleLineGraph(
    steps: List<MediumTermStepUI>,
    valueExtractorP10: (MediumTermStepUI) -> Float,
    valueExtractorP50: (MediumTermStepUI) -> Float,
    valueExtractorP90: (MediumTermStepUI) -> Float,
    mainColor: Color = Color(0xFFFFCA28),
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState(),
    contentWidth: Dp = 1250.dp,
    contentHeight: Dp = 140.dp
) {
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val userSettingsState by AppStateManager.userSettings.collectAsState()
    val isExpertMode = userSettingsState?.expertMode ?: false

    val textColor: Int = AndroidColor.rgb(
        MaterialTheme.colorScheme.onBackground.red,
        MaterialTheme.colorScheme.onBackground.green,
        MaterialTheme.colorScheme.onBackground.blue
    )

    val p10s = steps.map { valueExtractorP10(it) }
    val p50s = steps.map { valueExtractorP50(it) }
    val p90s = steps.map { valueExtractorP90(it) }

    val max = (if (isExpertMode) p90s.maxOrNull() else p50s.maxOrNull()) ?: 0f

    val minVal = valueRange?.start ?: (if (isExpertMode) p10s.minOrNull() else p50s.minOrNull()) ?: 0f
    val maxVal = max(valueRange?.endInclusive ?: 0f, max)
    val range = (maxVal - minVal).coerceAtLeast(1.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        Canvas(
            modifier = Modifier
                .width(contentWidth)
                .height(contentHeight)
        ) {
            val xPadding = with(density) { 20.dp.toPx() }
            val yPadding = 80f
            val xStep = (size.width - 2 * xPadding) / (steps.size - 1).coerceAtLeast(1)
            val yScale = (size.height - 2 * yPadding) / range

            fun getY(value: Float): Float {
                return size.height - yPadding - ((value - minVal) * yScale)
            }

            val p90Path = Path()
            val p50Path = Path()
            val p10Path = Path()

            steps.forEachIndexed { i, _ ->
                val x = xPadding + i * xStep
                val y10 = getY(p10s[i])
                val y50 = getY(p50s[i])
                val y90 = getY(p90s[i])

                if (i == 0) {
                    p90Path.moveTo(x, y90)
                    p50Path.moveTo(x, y50)
                    p10Path.moveTo(x, y10)
                } else {
                    p90Path.lineTo(x, y90)
                    p50Path.lineTo(x, y50)
                    p10Path.lineTo(x, y10)
                }
            }

            if (isExpertMode) {
                val bandPath = Path().apply {
                    addPath(p90Path)
                    for (i in steps.indices.reversed()) {
                        val x = xPadding + i * xStep
                        val y10 = getY(p10s[i])
                        lineTo(x, y10)
                    }
                    close()
                }

                drawPath(
                    path = bandPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(mainColor.copy(alpha = if (isDark) 0.30f else 0.20f), mainColor.copy(alpha = 0.03f))
                    )
                )
            }

            val p50Color = mainColor
            val p90Color = mainColor.copy(alpha = 0.85f)
            val p10Color = mainColor.copy(alpha = 0.65f)

            if (isExpertMode) {
                drawPath(
                    path = p90Path,
                    color = p90Color,
                    style = Stroke(
                        width = 3.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }

            drawPath(
                path = p50Path,
                color = p50Color,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )

            if (isExpertMode) {
                drawPath(
                    path = p10Path,
                    color = p10Color,
                    style = Stroke(
                        width = 3.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }

            drawIntoCanvas { canvas ->
                val p50Paint = Paint().apply {
                    textAlign = Paint.Align.CENTER
                    textSize = 40f
                    color = textColor
                }

                val p90Paint = Paint().apply {
                    textSize = 28f
                    color = textColor
                    textAlign = Paint.Align.CENTER
                }

                val p10Paint = Paint().apply {
                    textSize = 28f
                    color = textColor
                    textAlign = Paint.Align.CENTER
                }

                val datePaint = Paint().apply {
                    textSize = 35f
                    color = textColor
                    textAlign = Paint.Align.CENTER
                }

                steps.forEachIndexed { i, step ->
                    val x = xPadding + i * xStep
                    val y10 = getY(p10s[i])
                    val y50 = getY(p50s[i])
                    val y90 = getY(p90s[i])

                    if (isExpertMode) {
                        drawCircle(p90Color, radius = 4f, center = Offset(x, y90))
                    }
                    drawCircle(if (isDark) Color.White else p50Color, radius = 6f, center = Offset(x, y50))
                    if (isExpertMode) {
                        drawCircle(p10Color, radius = 4f, center = Offset(x, y10))
                    }

                    val minYGap = 24f
                    val y90Text = y90 - 10f
                    var y50Text = y50 - 14f
                    if (y50Text > y90Text - minYGap) {
                        y50Text = y90Text - minYGap
                    }
                    var y10Text = y10 + 26f
                    if (y10Text < y50Text + minYGap) {
                        y10Text = y50Text + minYGap
                    }

                    val roundToInt = userSettingsState?.roundToInt ?: true
                    if (isExpertMode) {
                        canvas.nativeCanvas.drawText(if (roundToInt) p90s[i].roundToInt().toString() else p90s[i].toSmartString(), x, y90Text, p90Paint)
                    }
                    canvas.nativeCanvas.drawText(if (roundToInt) p50s[i].roundToInt().toString() else p50s[i].toSmartString(), x, y50Text, p50Paint)
                    if (isExpertMode) {
                        canvas.nativeCanvas.drawText(if (roundToInt) p10s[i].roundToInt().toString() else p10s[i].toSmartString(), x, y10Text, p10Paint)
                    }

                    val timeLabel = step.time.format(DateTimeFormatter.ofPattern("HH'h'"))
                    canvas.nativeCanvas.drawText(
                        timeLabel,
                        x,
                        size.height - 20f,
                        datePaint
                    )
                }
            }
        }
    }
}


