/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.dayGraphsActivity

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.forecastMainActivity.LottieWeatherIcon
import fr.matthstudio.themeteo.forecastMainActivity.ResponsiveText
import fr.matthstudio.themeteo.forecastMainActivity.getLottieIconPath
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DayGraphsActivity : ComponentActivity() {

    private lateinit var weatherViewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var startDateTime: LocalDateTime? = try {
            val dateStr = intent.getStringExtra("START_DATE_TIME")
            if (dateStr != null) LocalDateTime.parse(dateStr) else null
        } catch (e: Exception) {
            null
        }

        if (startDateTime == null) {
            startDateTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("START_DATE_TIME", LocalDateTime::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("START_DATE_TIME") as? LocalDateTime
            }
        }

        val fullPeriod = intent.getBooleanExtra("FULL_PERIOD", false)

        if (startDateTime == null) {
            Log.e("DayGraphsActivity", "start date time is null. Defaulting to now.")
            startDateTime = LocalDateTime.now()
        }

        val app = (this.application as TheMeteo)
        weatherViewModel = WeatherViewModel(app.container.weatherParser, startDateTime, fullPeriod, app.container.telemetryManager)

        enableEdgeToEdge()
        setContent {
            val userSettings by weatherViewModel.userSettings.collectAsState()
            val isNight by weatherViewModel.isNight.collectAsState()
            val currentWmo by weatherViewModel.currentWmo.collectAsState()

            TheMeteoTheme(
                themeMode = userSettings?.themeMode ?: ThemeMode.FIXED,
                currentWmoCode = currentWmo,
                isNight = isNight
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        GraphsScreen(weatherViewModel, fullPeriod)
                    }
                }
            }
        }
    }
}

@Composable
fun GraphsScreen(viewModel: WeatherViewModel, fullPeriod: Boolean = false) {

    val forecast by viewModel.hourlyForecast.collectAsState()
    val scrollState = rememberScrollState()
    val userSettings by viewModel.userSettings.collectAsState()
    val settings = userSettings ?: return
    val currentStartDateTime by viewModel.currentStartDateTime.collectAsState()

    val verticalScrollState = rememberScrollState()

    val hasScrolled = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val contentWidth = if (forecast is WeatherDataState.SuccessShortTermHourly) {
        (forecast as WeatherDataState.SuccessShortTermHourly).data.size * 52.dp
    } else if (forecast is WeatherDataState.SuccessMediumTermHourly) {
        (forecast as WeatherDataState.SuccessMediumTermHourly).data.size * 65.dp
    } else {
        1250.dp
    }

    LaunchedEffect(currentStartDateTime) {
        hasScrolled.value = false
    }

    LaunchedEffect(forecast) {
        if (forecast is WeatherDataState.SuccessShortTermHourly && !hasScrolled.value && !fullPeriod) {
            val data = (forecast as WeatherDataState.SuccessShortTermHourly).data
            if (data.isNotEmpty()) {
                val targetHour = currentStartDateTime.withMinute(0).withSecond(0).withNano(0)
                val targetIndex = data.indexOfFirst { it.time.withMinute(0).withSecond(0).withNano(0) == targetHour }
                val indexToScroll = if (targetIndex != -1) targetIndex else data.indexOfFirst { it.time.hour == 6 }
                if (indexToScroll > 0) {
                    val contentWidthPx = with(density) { contentWidth.toPx() }
                    val xPadding = with(density) { 20.dp.toPx() }
                    val xStep = (contentWidthPx - 2 * xPadding) / (data.size - 1).coerceAtLeast(1)
                    val scrollOffset = xPadding + indexToScroll * xStep - (xStep / 2)
                    scrollState.scrollTo(scrollOffset.toInt())

                    hasScrolled.value = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (!fullPeriod && currentStartDateTime.hour == 0) {
                DaySelector(viewModel)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ResponsiveText(
                        text = if (fullPeriod)
                            stringResource(R.string.full_period_forecast)
                        else if (currentStartDateTime.hour == 0)
                            stringResource(R.string.forecast_for_the, currentStartDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
                        else
                            stringResource(R.string.next_24h_forecast),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                val hasWmo = when (val currentForecast = forecast) {
                    is WeatherDataState.SuccessShortTermHourly -> currentForecast.data.isNotEmpty()
                    is WeatherDataState.SuccessMediumTermHourly -> currentForecast.data.isNotEmpty()
                    else -> false
                }

                if (hasWmo) {
                    WeatherIconGraph(
                        modifier = Modifier.padding(top = 8.dp),
                        viewModel = viewModel,
                        scrollState = scrollState,
                        contentWidth = contentWidth,
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    val currentForecast = forecast
                    val hasHourlyData = (currentForecast is WeatherDataState.SuccessShortTermHourly && currentForecast.data.isNotEmpty()) ||
                            (currentForecast is WeatherDataState.SuccessMediumTermHourly && currentForecast.data.isNotEmpty())
                    if (hasHourlyData) {
                        Box(modifier = Modifier
                            .matchParentSize()
                            .horizontalScroll(scrollState)
                        ) {
                            BackgroundGrid(
                                forecast = currentForecast,
                                contentWidth = contentWidth
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScrollState)
                            .padding(top = 10.dp),
                    ) {
                        val titleModifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)

                        if (forecast == WeatherDataState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.padding(vertical = 50.dp))
                        } else if (forecast is WeatherDataState.SuccessShortTermHourly && (forecast as WeatherDataState.SuccessShortTermHourly).data.isNotEmpty()) {
                            val hourlyData = (forecast as WeatherDataState.SuccessShortTermHourly).data

                            // Temperature
                            Text(
                                text = stringResource(R.string.temperature) + " (${UnitConverter.getSymbolWithDegree(settings.temperatureUnit)})",
                                modifier = titleModifier
                            )
                            GenericGraph(
                                viewModel = viewModel,
                                graphType = GraphType.TEMP,
                                graphColor = Color(0xFFFFF176),
                                scrollState = scrollState,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Apparent Temperature
                            Text(
                                text = stringResource(R.string.apparent_temperature) + " (${UnitConverter.getSymbolWithDegree(settings.temperatureUnit)})",
                                modifier = titleModifier
                            )
                            GenericGraph(
                                viewModel = viewModel,
                                graphType = GraphType.A_TEMP,
                                graphColor = Color(0xFFFFD54F),
                                scrollState = scrollState,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Precipitation Probability
                            if (hourlyData.firstOrNull()?.precipitationProbability != null) {
                                Text(text = stringResource(R.string.precipitation_prob), modifier = titleModifier)
                                GenericGraph(
                                    viewModel = viewModel,
                                    graphType = GraphType.PRECIPITATION_PROB,
                                    graphColor = Color(0xFF64B5F6),
                                    scrollState = scrollState,
                                    valueRange = 0f..100f,
                                    contentWidth = contentWidth
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Precipitation
                            if ((hourlyData.maxOfOrNull { it.precipitation } ?: 0f) > 0.05f) {
                                Text(text = stringResource(R.string.precipitation), modifier = titleModifier)
                                GenericGraph(
                                    viewModel = viewModel,
                                    graphType = GraphType.PRECIPITATION,
                                    graphColor = Color(0xFF64B5F6),
                                    scrollState = scrollState,
                                    valueRange = 0f..3f,
                                    contentWidth = contentWidth
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                // Rain
                                if ((hourlyData.maxOfOrNull { (it.precipitation - it.snowfall).coerceAtLeast(0f) } ?: 0f) != 0f && settings.expertMode) {
                                    Text(text = stringResource(R.string.rain), modifier = titleModifier)
                                    GenericGraph(
                                        viewModel = viewModel,
                                        graphType = GraphType.RAIN,
                                        graphColor = Color(0xFF64B5F6),
                                        scrollState = scrollState,
                                        valueRange = 0f..3f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // Snowfall
                                if ((hourlyData.maxOfOrNull { it.snowfall } ?: 0f) != 0f) {
                                    Text(text = stringResource(R.string.snowfall_cm_h), modifier = titleModifier)
                                    GenericGraph(
                                        viewModel = viewModel,
                                        graphType = GraphType.SNOWFALL,
                                        graphColor = Color(0xFFFFFFFF),
                                        scrollState = scrollState,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_precipitations),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Wind Speed
                            Text(
                                text = stringResource(R.string.wind_speed) + " (${if (settings.windUnit == WindUnit.KPH) "km/h" else "mph"})",
                                modifier = titleModifier
                            )
                            GenericGraph(
                                viewModel = viewModel,
                                graphType = GraphType.WIND_SPEED,
                                graphColor = Color(0xFFAED581),
                                scrollState = scrollState,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // Humidity & Dew Point
                            if (hourlyData.firstOrNull()?.humidity != null) {
                                Text(text = stringResource(R.string.humidity), modifier = titleModifier)
                                GenericGraph(
                                    viewModel = viewModel,
                                    graphType = GraphType.HUMIDITY,
                                    graphColor = Color(0xFF4DD0E1),
                                    scrollState = scrollState,
                                    valueRange = 0f..100f,
                                    contentWidth = contentWidth
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = stringResource(R.string.dew_point) + " (${UnitConverter.getSymbolWithDegree(settings.temperatureUnit)})",
                                    modifier = titleModifier
                                )
                                GenericGraph(
                                    viewModel = viewModel,
                                    graphType = GraphType.DEW_POINT,
                                    graphColor = Color(0xFFFF8A65),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Cloud Cover
                            if (settings.expertMode && !(hourlyData.any { it.cloudCoverHigh == -1 } || hourlyData.any { it.cloudCoverMid == -1 } || hourlyData.any { it.cloudCoverLow == -1 })) {
                                if (hourlyData.firstOrNull()?.cloudCoverHigh != null) {
                                    Text(text = "Nuages hauts (%)", modifier = titleModifier)
                                    GenericGraph(
                                        viewModel = viewModel,
                                        graphType = GraphType.CLOUD_COVER_HIGH,
                                        graphColor = Color(0xFFB0C4DE),
                                        scrollState = scrollState,
                                        valueRange = 0f..100f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (hourlyData.firstOrNull()?.cloudCoverMid != null) {
                                    Text(text = "Nuages moyens (%)", modifier = titleModifier)
                                    GenericGraph(
                                        viewModel = viewModel,
                                        graphType = GraphType.CLOUD_COVER_MID,
                                        graphColor = Color(0xFF7B92A8),
                                        scrollState = scrollState,
                                        valueRange = 0f..100f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (hourlyData.firstOrNull()?.cloudCoverLow != null) {
                                    Text(text = "Nuages bas (%)", modifier = titleModifier)
                                    GenericGraph(
                                        viewModel = viewModel,
                                        graphType = GraphType.CLOUD_COVER_LOW,
                                        graphColor = Color(0xFF52687D),
                                        scrollState = scrollState,
                                        valueRange = 0f..100f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            } else {
                                if (hourlyData.firstOrNull()?.cloudCoverTotal != null) {
                                    Text(text = stringResource(R.string.cloud_cover), modifier = titleModifier)
                                    GenericGraph(
                                        viewModel = viewModel,
                                        graphType = GraphType.CLOUD_COVER,
                                        graphColor = Color(0xFF8E9AAF),
                                        scrollState = scrollState,
                                        valueRange = 0f..100f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            // Pressure
                            Text(text = stringResource(R.string.pressure), modifier = titleModifier)
                            GenericGraph(
                                viewModel = viewModel,
                                graphType = GraphType.PRESSURE,
                                graphColor = Color(0xFF9575CD),
                                scrollState = scrollState,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        } else if (forecast is WeatherDataState.SuccessMediumTermHourly && (forecast as WeatherDataState.SuccessMediumTermHourly).data.isNotEmpty()) {
                            val mediumData = (forecast as WeatherDataState.SuccessMediumTermHourly).data

                            // 1. Temperature
                            Text(
                                text = stringResource(R.string.temperature) + " (${UnitConverter.getSymbolWithDegree(settings.temperatureUnit)})",
                                modifier = titleModifier
                            )
                            if (settings.expertMode) {
                                MediumTermTripleLineGraph(
                                    steps = mediumData,
                                    valueExtractorP10 = { UnitConverter.convertTemperature(it.tempP10, settings.temperatureUnit) },
                                    valueExtractorP50 = { UnitConverter.convertTemperature(it.tempP50, settings.temperatureUnit) },
                                    valueExtractorP90 = { UnitConverter.convertTemperature(it.tempP90, settings.temperatureUnit) },
                                    mainColor = Color(0xFFFFF176),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            } else {
                                GenericGraphGlobal(
                                    fullForecast = forecast,
                                    roundToInt = settings.roundToInt,
                                    graphType = GraphType.TEMP,
                                    graphColor = Color(0xFFFFF176),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // 2. Apparent Temperature
                            Text(
                                text = stringResource(R.string.apparent_temperature) + " (${UnitConverter.getSymbolWithDegree(settings.temperatureUnit)})",
                                modifier = titleModifier
                            )
                            if (settings.expertMode) {
                                MediumTermTripleLineGraph(
                                    steps = mediumData,
                                    valueExtractorP10 = { UnitConverter.convertTemperature(it.apparentTempP10, settings.temperatureUnit) },
                                    valueExtractorP50 = { UnitConverter.convertTemperature(it.apparentTempP50, settings.temperatureUnit) },
                                    valueExtractorP90 = { UnitConverter.convertTemperature(it.apparentTempP90, settings.temperatureUnit) },
                                    mainColor = Color(0xFFFFD54F),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            } else {
                                GenericGraphGlobal(
                                    fullForecast = forecast,
                                    roundToInt = settings.roundToInt,
                                    graphType = GraphType.A_TEMP,
                                    graphColor = Color(0xFFFFD54F),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // 3. Precipitation Probability (%)
                            Text(text = stringResource(R.string.precipitation_prob), modifier = titleModifier)
                            GenericGraphGlobal(
                                fullForecast = forecast,
                                roundToInt = settings.roundToInt,
                                graphType = GraphType.PRECIPITATION_PROB,
                                graphColor = Color(0xFF64B5F6),
                                valueRange = 0f..100f,
                                scrollState = scrollState,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // 4. Total Precipitation (mm)
                            if (mediumData.any { it.totalPrecipitation > 0.05f }) {
                                Text(text = stringResource(R.string.precipitation), modifier = titleModifier)
                                GenericGraphGlobal(
                                    fullForecast = forecast,
                                    roundToInt = false,
                                    graphType = GraphType.PRECIPITATION,
                                    graphColor = Color(0xFF29B6F6),
                                    valueRange = 0f..3f,
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_precipitations),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // 5. Wind Speed
                            Text(
                                text = stringResource(R.string.wind_speed) + " (${if (settings.windUnit == WindUnit.KPH) "km/h" else "mph"})",
                                modifier = titleModifier
                            )
                            if (settings.expertMode) {
                                MediumTermTripleLineGraph(
                                    steps = mediumData,
                                    valueExtractorP10 = { UnitConverter.convertWind(it.windSpeedP10, settings.windUnit) },
                                    valueExtractorP50 = { UnitConverter.convertWind(it.windSpeedP50, settings.windUnit) },
                                    valueExtractorP90 = { UnitConverter.convertWind(it.windSpeedP90, settings.windUnit) },
                                    mainColor = Color(0xFFAED581),
                                    valueRange = 0f..10f,
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            } else {
                                GenericGraphGlobal(
                                    fullForecast = forecast,
                                    roundToInt = settings.roundToInt,
                                    graphType = GraphType.WIND_SPEED,
                                    graphColor = Color(0xFFAED581),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // 6. Wind Gusts
                            Text(
                                text = "Rafales de vent (${if (settings.windUnit == WindUnit.KPH) "km/h" else "mph"})",
                                modifier = titleModifier
                            )
                            if (settings.expertMode) {
                                MediumTermTripleLineGraph(
                                    steps = mediumData,
                                    valueExtractorP10 = { UnitConverter.convertWind(it.windGustP10, settings.windUnit) },
                                    valueExtractorP50 = { UnitConverter.convertWind(it.windGustP50, settings.windUnit) },
                                    valueExtractorP90 = { UnitConverter.convertWind(it.windGustP90, settings.windUnit) },
                                    mainColor = Color(0xFF7CB342),
                                    valueRange = 0f..10f,
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            } else {
                                GenericGraphGlobal(
                                    fullForecast = forecast,
                                    roundToInt = settings.roundToInt,
                                    graphType = GraphType.WIND_GUST,
                                    graphColor = Color(0xFF7CB342),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // 7. Humidity
                            Text(text = stringResource(R.string.humidity), modifier = titleModifier)
                            if (settings.expertMode) {
                                MediumTermTripleLineGraph(
                                    steps = mediumData,
                                    valueExtractorP10 = { it.humidityP10.toFloat() },
                                    valueExtractorP50 = { it.humidityP50.toFloat() },
                                    valueExtractorP90 = { it.humidityP90.toFloat() },
                                    mainColor = Color(0xFF4DD0E1),
                                    valueRange = 0f..100f,
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            } else {
                                GenericGraphGlobal(
                                    fullForecast = forecast,
                                    roundToInt = settings.roundToInt,
                                    graphType = GraphType.HUMIDITY,
                                    graphColor = Color(0xFF4DD0E1),
                                    valueRange = 0f..100f,
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // 8. Cloud Cover (%)
                            Text(text = stringResource(R.string.cloud_cover), modifier = titleModifier)
                            if (settings.expertMode) {
                                MediumTermTripleLineGraph(
                                    steps = mediumData,
                                    valueExtractorP10 = { it.cloudCoverP10.toFloat() },
                                    valueExtractorP50 = { it.cloudCoverTotal.toFloat() },
                                    valueExtractorP90 = { it.cloudCoverP90.toFloat() },
                                    mainColor = Color(0xFF8E9AAF),
                                    valueRange = 0f..100f,
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            } else {
                                GenericGraphGlobal(
                                    fullForecast = forecast,
                                    roundToInt = settings.roundToInt,
                                    graphType = GraphType.CLOUD_COVER,
                                    graphColor = Color(0xFF8E9AAF),
                                    valueRange = 0f..100f,
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // 9. Pressure
                            Text(text = stringResource(R.string.pressure), modifier = titleModifier)
                            if (settings.expertMode) {
                                MediumTermTripleLineGraph(
                                    steps = mediumData,
                                    valueExtractorP10 = { it.pressureP10 },
                                    valueExtractorP50 = { it.pressureP50 },
                                    valueExtractorP90 = { it.pressureP90 },
                                    mainColor = Color(0xFF9575CD),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            } else {
                                GenericGraphGlobal(
                                    fullForecast = forecast,
                                    roundToInt = settings.roundToInt,
                                    graphType = GraphType.PRESSURE,
                                    graphColor = Color(0xFF9575CD),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        } else if (forecast is WeatherDataState.SuccessLongTermDaily && (forecast as WeatherDataState.SuccessLongTermDaily).data.isNotEmpty()) {
                            val currentDay = (forecast as WeatherDataState.SuccessLongTermDaily).data.firstOrNull()
                            if (currentDay != null) {
                                LongTermDayPreview(dayForecast = currentDay)
                            }
                        } else {
                            Text(stringResource(R.string.no_data_available_for_day))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DaySelector(viewModel: WeatherViewModel) {
    val availableDaysState by viewModel.availableDays.collectAsState()
    val currentStartDate by viewModel.currentStartDateTime.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val settings = userSettings ?: return
    val isBatterySaverActive by AppStateManager.isBatterySaverActive.collectAsState()

    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val approxItemWidthPx = with(density) { 72.dp.toPx() }

    if (availableDaysState is WeatherDataState.SuccessDaily) {
        val days = (availableDaysState as WeatherDataState.SuccessDaily).data

        LaunchedEffect(currentStartDate, days) {
            val selectedIndex = days.indexOfFirst { it.date == currentStartDate.toLocalDate() }
            if (selectedIndex >= 0) {
                val visibleItem = listState.layoutInfo.visibleItemsInfo.find { it.index == selectedIndex }
                val itemSize = visibleItem?.size ?: approxItemWidthPx.toInt()
                val viewportWidth = if (listState.layoutInfo.viewportSize.width > 0)
                    listState.layoutInfo.viewportSize.width
                else
                    screenWidthPx.toInt()

                val offset = -(viewportWidth / 2 - itemSize / 2)

                listState.animateScrollToItem(
                    index = selectedIndex,
                    scrollOffset = offset
                )
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(days) { dailyReading ->
                val isSelected = dailyReading.date == currentStartDate.toLocalDate()
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (isSelected) 0.dp else 2.dp,
                    modifier = Modifier.clickable {
                        viewModel.updateStartDate(dailyReading.date.atStartOfDay())
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dailyReading.date.format(DateTimeFormatter.ofPattern("EEE")) + " " + dailyReading.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )

                        val weatherWord = weatherCodeToSimpleWord(dailyReading.wmoCode)
                        if (weatherWord != null) {
                            LottieWeatherIcon(
                                iconPath = getLottieIconPath(weatherWord, false, isSystemInDarkTheme()),
                                animate = settings.enableAnimatedIcons && !isBatterySaverActive,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
