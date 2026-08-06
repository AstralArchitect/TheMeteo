/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.dayGraphsActivity

import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.AllHourlyVarsReading
import fr.matthstudio.themeteo.EnsembleStat
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WmoEnsembleStat
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.forecastMainActivity.LottieWeatherIcon
import fr.matthstudio.themeteo.forecastMainActivity.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastMainActivity.getLottieIconPath
import fr.matthstudio.themeteo.forecastMainActivity.getSimpleWeather
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.times
import fr.matthstudio.themeteo.UserSettings
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

class DayGraphsActivity : ComponentActivity() {

    private lateinit var weatherViewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Récupération sécurisée du Parcelable pour toutes les versions d'Android
        // On vérifie la version du SDK pour appeler la bonne méthode
        var startDateTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Pour Android 13 (API 33) et plus, on utilise la nouvelle méthode sécurisée.
            intent.getParcelableExtra("START_DATE_TIME", LocalDateTime::class.java)
        } else {
            // Pour les versions plus anciennes, on utilise l'ancienne méthode (dépréciée).
            // L'annotation @Suppress évite l'avertissement du compilateur.
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("START_DATE_TIME") as? LocalDateTime
        }

        val fullPeriod = intent.getBooleanExtra("FULL_PERIOD", false)

        if (startDateTime == null)
        {
            Log.e("DayGraphsActivity", "start date time is null. Defaulting to now.")
            // On prend l'heure actuelle, puis on met les minutes, secondes et nanosecondes à 0.
            startDateTime = LocalDateTime.now()
        }

        // Instancier le viewModel
        val app = (this.application as TheMeteo)
        weatherViewModel = WeatherViewModel(app.weatherCache, startDateTime, fullPeriod, app.container.telemetryManager)

        enableEdgeToEdge()
        setContent {
            val userSettings by weatherViewModel.userSettings.collectAsState()
            val isNight by weatherViewModel.isNight.collectAsState()
            val currentWmo by weatherViewModel.currentWmo.collectAsState()

            TheMeteoTheme(
                themeMode = userSettings.themeMode,
                currentWmoCode = currentWmo,
                isNight = isNight
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
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
    val backgroundColor = MaterialTheme.colorScheme.background
    val userSettings by viewModel.userSettings.collectAsState()
    val currentStartDateTime by viewModel.currentStartDateTime.collectAsState()

    val verticalScrollState = rememberScrollState()
    val showTemperatureDetailsGraphs = remember { mutableStateOf(false) }
    val showPrecipitationDetailsGraphs = remember { mutableStateOf(false) }
    val showUvDetailsGraphs = remember { mutableStateOf(false) }

    // Auto-scroll to 6 AM if starting at 00h and if full-period disabled
    val hasScrolled = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val contentWidth = if (forecast is WeatherDataState.SuccessHourly) {
        (forecast as WeatherDataState.SuccessHourly).data.size * 52.dp
    } else {
        1250.dp
    }

    LaunchedEffect(currentStartDateTime) {
        hasScrolled.value = false
    }

    LaunchedEffect(forecast) {
        if (forecast is WeatherDataState.SuccessHourly && !hasScrolled.value && !fullPeriod) {
            val data = (forecast as WeatherDataState.SuccessHourly).data
            if (data.isNotEmpty() && data.first().time.hour == 0) {
                val index6h = data.indexOfFirst { it.time.hour == 6 }
                if (index6h != -1) {
                    val contentWidthPx = with(density) { contentWidth.toPx() }
                    val xPadding = with(density) { 20.dp.toPx() }
                    val xStep = (contentWidthPx - 2 * xPadding) / (data.size - 1)
                    val scrollOffset = xPadding + index6h * xStep - (xStep / 2)
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
                // Header with transparent background
                Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (fullPeriod)
                        stringResource(R.string.full_period_forecast)
                    else if (currentStartDateTime.hour == 0)
                        stringResource(R.string.forecast_for_the, currentStartDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
                    else
                        stringResource(R.string.next_24h_forecast),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }

            if ((forecast as? WeatherDataState.SuccessHourly)?.data?.isNotEmpty() ?: false) {
                // Icons graph with transparent background
                if ((forecast as? WeatherDataState.SuccessHourly)?.data?.first()?.wmo != null) {
                    WeatherIconGraph(
                        modifier = Modifier.padding(top = 8.dp),
                        viewModel,
                        scrollState = scrollState,
                        contentWidth = contentWidth,
                    )
                }
            }

            // Function to generate the fading background brush for titles
            val fadeBrush = Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.2f to backgroundColor.copy(alpha = 0.7f),
                0.8f to backgroundColor.copy(alpha = 0.7f),
                1.0f to Color.Transparent
            )
            
            // Helper modifier for titles to create the progressive fade out effect
            val titleModifier = Modifier
                .background(fadeBrush)
                .padding(vertical = 4.dp, horizontal = 8.dp)

            Box(modifier = Modifier.fillMaxSize()) {
                // Background Grid (Layer 0) - Placed inside the vertical scroll area
                if (forecast is WeatherDataState.SuccessHourly && (forecast as WeatherDataState.SuccessHourly).data.isNotEmpty()) {
                    Box(modifier = Modifier
                        .matchParentSize()
                        .horizontalScroll(scrollState)
                    ) {
                        BackgroundGrid(
                            forecast = forecast,
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
                    // On affiche un indicateur de chargement pendant la récupération des données
                    if (forecast == WeatherDataState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 50.dp))
                    } else if (forecast is WeatherDataState.SuccessHourly && (forecast as WeatherDataState.SuccessHourly).data.isNotEmpty()) {
                        val hourlyData = (forecast as WeatherDataState.SuccessHourly).data
                        
                        // --- TEMPERATURE GROUP ---
                        Row (
                            modifier = Modifier
                                .clickable {
                                    showTemperatureDetailsGraphs.value =
                                        !showTemperatureDetailsGraphs.value
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.temperature)+ " (${UnitConverter.getSymbolWithDegree(
                                viewModel.userSettings.collectAsState().value.temperatureUnit
                            )})", style = MaterialTheme.typography.titleMedium)
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.rotate(if (showTemperatureDetailsGraphs.value) 180f else 0f)
                            )
                        }
                        GenericGraph(
                            viewModel,
                            GraphType.TEMP,
                            Color(0xFFFFF176),
                            scrollState = scrollState,
                            contentWidth = contentWidth
                        )

                        if (showTemperatureDetailsGraphs.value) {
                            Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))) {
                                if (hourlyData.first().dewpoint != null) {
                                    Text(
                                        stringResource(
                                            R.string.dew_point)
                                                + " (${UnitConverter.getSymbolWithDegree(
                                                    viewModel.userSettings.collectAsState().value.temperatureUnit
                                                )})",
                                        modifier = titleModifier
                                    )
                                    GenericGraph(
                                        viewModel,
                                        GraphType.DEW_POINT,
                                        Color(0xFFFF8A65),
                                        scrollState = scrollState,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                if (hourlyData.first().humidity != null) {
                                    Text(stringResource(R.string.humidity), modifier = titleModifier)
                                    GenericGraph(
                                        viewModel,
                                        GraphType.HUMIDITY,
                                        Color(0xFF4DD0E1),
                                        scrollState = scrollState,
                                        valueRange = 0f..100f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        if (hourlyData.first().apparentTemperature != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.apparent_temperature)+ " (${UnitConverter.getSymbolWithDegree(
                                viewModel.userSettings.collectAsState().value.temperatureUnit
                            )})", modifier = titleModifier)
                            GenericGraph(
                                viewModel,
                                GraphType.A_TEMP,
                                Color(0xFFFFD54F),
                                scrollState = scrollState,
                                contentWidth = contentWidth
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- PRECIPITATION GROUP ---
                        if ((hourlyData.mapNotNull { it.precipitationData.precipitation }.maxOrNull() ?: 0.0) != 0.0) {
                            Row (
                                modifier = Modifier
                                    .clickable {
                                        showPrecipitationDetailsGraphs.value =
                                            !showPrecipitationDetailsGraphs.value
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.precipitation), style = MaterialTheme.typography.titleMedium)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (showPrecipitationDetailsGraphs.value) 180f else 0f)
                                )
                            }
                            GenericGraph(
                                viewModel,
                                GraphType.PRECIPITATION,
                                Color(0xFF64B5F6),
                                scrollState = scrollState,
                                valueRange = 0f..3f,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        } else {
                            Text(stringResource(R.string.no_precipitations), modifier = titleModifier)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        if (showPrecipitationDetailsGraphs.value) {
                            Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))) {
                                if (hourlyData.first().precipitationData.precipitationProbability != null)
                                {
                                    Text(stringResource(R.string.precipitation_prob), modifier = titleModifier)
                                    GenericGraph(
                                        viewModel,
                                        GraphType.PRECIPITATION_PROB,
                                        Color(0xFF64B5F6),
                                        scrollState = scrollState,
                                        valueRange = 0f..100f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                if ((hourlyData.mapNotNull { it.precipitationData.rain }.maxOrNull()
                                        ?: 0.0) != 0.0
                                ) {
                                    Text(stringResource(R.string.rain), modifier = titleModifier)
                                    GenericGraph(
                                        viewModel,
                                        GraphType.RAIN,
                                        Color(0xFF64B5F6),
                                        scrollState = scrollState,
                                        valueRange = 0f..3f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                                if ((hourlyData.mapNotNull { it.precipitationData.snowfall }
                                        .maxOrNull() ?: 0.0) != 0.0)
                                {
                                    Text(stringResource(R.string.snowfall_cm_h), modifier = titleModifier)
                                    GenericGraph(
                                        viewModel,
                                        GraphType.SNOWFALL,
                                        Color(0xFFFFFFFF),
                                        scrollState = scrollState,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        // --- SNOW DEPTH ---
                        if (hourlyData.first().precipitationData.snowDepth != null) {
                            if ((hourlyData.mapNotNull { it.precipitationData.snowDepth }.maxOrNull() ?: 0) != 0) {
                                Text(stringResource(R.string.snow_depth_cm), modifier = titleModifier)
                                GenericGraph(
                                    viewModel,
                                    GraphType.SNOW_DEPTH,
                                    Color(0xFFFFFFFF),
                                    scrollState = scrollState,
                                    contentWidth = contentWidth
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // --- WIND ---
                        Text(stringResource(R.string.wind_speed) + " (${if(userSettings.windUnit == WindUnit.KPH) "km/h" else "mph"})", modifier = titleModifier)
                        GenericGraph(
                            viewModel,
                            GraphType.WIND_SPEED,
                            Color(0xFFAED581),
                            scrollState = scrollState,
                            contentWidth = contentWidth
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- VISIBILITY ---
                        if (hourlyData.first().skyInfo.visibility != null) {
                            Text(stringResource(R.string.visibility), modifier = titleModifier)
                            GenericGraph(
                                viewModel,
                                GraphType.VISIBILITY,
                                Color(0xFF98FFEB),
                                scrollState = scrollState,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // --- CLOUD COVER ---
                        if (hourlyData.first().skyInfo.cloudcoverTotal != null) {
                            Text(stringResource(R.string.cloud_cover), modifier = titleModifier)
                            GenericGraph(
                                viewModel,
                                GraphType.CLOUD_COVER,
                                Color(0xFF9D9D9D),
                                scrollState = scrollState,
                                valueRange = 0f..100f,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // --- UV INDEX GROUP ---
                        if (hourlyData.first().skyInfo.uvIndex != null) {
                            Row (
                                modifier = Modifier
                                    .clickable {
                                        showUvDetailsGraphs.value = !showUvDetailsGraphs.value
                                    }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.uv_index), style = MaterialTheme.typography.titleMedium)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(if (showUvDetailsGraphs.value) 180f else 0f)
                                )
                            }
                            GenericGraph(
                                viewModel,
                                GraphType.UV_INDEX,
                                Color(0xFFFFEAB5),
                                scrollState = scrollState,
                                valueRange = 0f..11f,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            if (showUvDetailsGraphs.value && hourlyData.first().skyInfo.opacity != null) {
                                Column(modifier = Modifier.background(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))) {
                                    Text(stringResource(R.string.opacity_graph), modifier = titleModifier)
                                    GenericGraph(
                                        viewModel,
                                        GraphType.OPACITY,
                                        Color(0xFF9D9D9D),
                                        scrollState = scrollState,
                                        valueRange = 0f..100f,
                                        contentWidth = contentWidth
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }

                        // --- PRESSURE ---
                        if (hourlyData.first().pressure != null) {
                            Text(stringResource(R.string.pressure), modifier = titleModifier)
                            GenericGraph(
                                viewModel,
                                GraphType.PRESSURE,
                                Color(0xFF9575CD),
                                scrollState = scrollState,
                                contentWidth = contentWidth
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    } else {
                        // Message si aucune donnée n'est disponible après le chargement
                        Text(stringResource(R.string.no_data_available_for_day))
                    }
                }
            }
        }
    }
}
}

enum class GraphType {
    TEMP, A_TEMP,
    DEW_POINT, HUMIDITY,
    PRECIPITATION, PRECIPITATION_PROB, RAIN, SNOWFALL, SNOW_DEPTH,
    WIND_SPEED, PRESSURE,
    CLOUD_COVER, OPACITY,
    UV_INDEX, VISIBILITY,
}

@Composable
fun DaySelector(viewModel: WeatherViewModel) {
    val availableDaysState by viewModel.availableDays.collectAsState()
    val currentStartDate by viewModel.currentStartDateTime.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val isBatterySaverActive by (LocalContext.current.applicationContext as TheMeteo).weatherCache.isBatterySaverActive.collectAsState()

    if (availableDaysState is WeatherDataState.SuccessDaily) {
        val days = (availableDaysState as WeatherDataState.SuccessDaily).data
        LazyRow(
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

                        val weatherWord = weatherCodeToSimpleWord(dailyReading.wmo)
                        if (weatherWord != null) {
                            LottieWeatherIcon(
                                iconPath = getLottieIconPath(weatherWord, false, isSystemInDarkTheme()),
                                animate = userSettings.enableAnimatedIcons && !isBatterySaverActive,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundGrid(
    forecast: WeatherDataState,
    modifier: Modifier = Modifier,
    contentWidth: Dp
) {
    if (forecast !is WeatherDataState.SuccessHourly) return
    val data = forecast.data
    val itemCount = data.size
    val density = LocalDensity.current
    val xPadding = with(density) { 20.dp.toPx() }
    val textColor = MaterialTheme.colorScheme.onBackground
    
    Canvas(
        modifier = modifier
            .width(contentWidth)
            .fillMaxHeight()
    ) {
        val xStep = (size.width - 2 * xPadding) / (itemCount - 1)
        val gridColor = Color.Gray.copy(alpha = 0.3f)
        val daySeparatorColor = Color.Gray.copy(alpha = 0.7f)

        data.forEachIndexed { i, hourData ->
            val drawX = xPadding + (i * xStep)
            
            // Draw regular hour grid lines (between points)
            if (i < itemCount - 1) {
                val midX = drawX + xStep / 2
                drawLine(
                    color = gridColor,
                    start = Offset(midX, 0f),
                    end = Offset(midX, size.height),
                    strokeWidth = 2f
                )
            }

            // Draw day separators at midnight (between 23h and 00h)
            if (hourData.time.hour == 0 && i > 0) {
                val separatorX = drawX - xStep / 2
                drawLine(
                    color = daySeparatorColor,
                    start = Offset(separatorX, 0f),
                    end = Offset(separatorX, size.height),
                    strokeWidth = 4f
                )

                // day label
                val dayLabel = hourData.time.format(DateTimeFormatter.ofPattern("EEE d MMM"))
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
    var roundToInt = userSettings.roundToInt

    if (graphType == GraphType.PRECIPITATION || graphType == GraphType.RAIN ||
        graphType == GraphType.SNOWFALL
    )
        roundToInt = false

    GenericGraphGlobal(
        fullForecast,
        roundToInt,
        userSettings.temperatureUnit,
        userSettings.windUnit,
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
    val rawHourlyData = (fullForecast as WeatherDataState.SuccessHourly).data
    val hourlyData = if (sparseMode) aggregateHourlyData(rawHourlyData) else rawHourlyData
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        var forecast: List<Number>
        val times: List<String> =
            hourlyData
                .map { it.time.format(DateTimeFormatter.ofPattern("HH")) + if (!compactHourFormat) "h" else "" }
        
        val isTemperatureGraph = graphType == GraphType.TEMP || graphType == GraphType.A_TEMP || graphType == GraphType.DEW_POINT

        when (graphType) {
            GraphType.TEMP -> {
                forecast = hourlyData.map { f -> 
                    val v = f.temperature ?: throw IllegalStateException("Graph data cannot be null")
                    UnitConverter.convertTemperature(v, temperatureUnit)
                }
            }

            GraphType.A_TEMP -> {
                forecast = hourlyData
                    .map { f -> 
                        val v = f.apparentTemperature ?: throw IllegalStateException("Graph data cannot be null")
                        UnitConverter.convertTemperature(v, temperatureUnit)
                    }
            }

            GraphType.DEW_POINT -> {
                forecast = hourlyData.map { f -> 
                    val v = f.dewpoint ?: throw IllegalStateException("Graph data cannot be null")
                    UnitConverter.convertTemperature(v, temperatureUnit)
                }
            }

            GraphType.PRECIPITATION_PROB -> {
                forecast = hourlyData
                    .map { f -> f.precipitationData.precipitationProbability ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.PRECIPITATION -> {
                forecast = hourlyData
                    .map { f -> f.precipitationData.precipitation ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.RAIN -> {
                forecast = hourlyData
                    .map { f -> f.precipitationData.rain ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.SNOWFALL -> {
                forecast = hourlyData
                    .map { f -> f.precipitationData.snowfall ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.SNOW_DEPTH -> {
                forecast = hourlyData
                    .map { f -> f.precipitationData.snowDepth ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.WIND_SPEED -> {
                forecast = hourlyData
                    .map { f -> 
                        val v = f.wind.windspeed ?: throw IllegalStateException("Graph data cannot be null")
                        UnitConverter.convertWind(v, windUnit)
                    }
            }

            GraphType.PRESSURE -> {
                forecast = hourlyData
                    .map { f -> f.pressure ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.HUMIDITY -> {
                forecast = hourlyData
                    .map { f -> f.humidity ?: throw IllegalStateException("Graph data cannot be null")}
            }

            GraphType.CLOUD_COVER -> {
                forecast = hourlyData
                    .map { f -> f.skyInfo.cloudcoverTotal ?: throw IllegalStateException("Graph data cannot be null")}
            }

            GraphType.OPACITY -> {
                forecast = hourlyData
                    .map { f -> f.skyInfo.opacity ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.UV_INDEX -> {
                forecast = hourlyData
                    .map { f -> f.skyInfo.uvIndex ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.VISIBILITY -> {
                forecast = hourlyData
                    .map { f -> (f.skyInfo.visibility?.toDouble() ?: throw IllegalStateException("Graph data cannot be null")) / 1000.0 }
            }
        }

        val textColor: Int = AndroidColor.rgb(
            MaterialTheme.colorScheme.onBackground.red,
            MaterialTheme.colorScheme.onBackground.green,
            MaterialTheme.colorScheme.onBackground.blue
        )

        val ensembleKey = when (graphType) {
            GraphType.TEMP -> "temperature_2m"
            GraphType.A_TEMP -> "apparent_temperature"
            GraphType.HUMIDITY -> "relative_humidity_2m"
            GraphType.DEW_POINT -> "dewpoint_2m"
            GraphType.PRECIPITATION -> "precipitation"
            GraphType.RAIN -> "rain"
            GraphType.SNOWFALL -> "snowfall"
            GraphType.WIND_SPEED -> "windspeed_10m"
            GraphType.CLOUD_COVER -> "cloudcover"
            GraphType.VISIBILITY -> "visibility"
            GraphType.PRESSURE -> "pressure_msl"
            else -> null
        }
        val ensembleStatsRaw = if (ensembleKey != null) hourlyData.map { it.ensembleStats?.get(ensembleKey) } else null
        
        // Convert ensemble stats if necessary
        val ensembleStats = ensembleStatsRaw?.map { stat ->
            if (stat == null) null
            else if (isTemperatureGraph) {
                stat.copy(
                    min = UnitConverter.convertTemperature(stat.min ?: 0.0, temperatureUnit),
                    max = UnitConverter.convertTemperature(stat.max ?: 0.0, temperatureUnit)
                )
            } else if (graphType == GraphType.WIND_SPEED) {
                stat.copy(
                    min = UnitConverter.convertWind(stat.min ?: 0.0, windUnit),
                    max = UnitConverter.convertWind(stat.max ?: 0.0, windUnit)
                )
            } else {
                stat
            }
        }

        Canvas(
            modifier = Modifier
                .width(contentWidth)
                .height(contentHeight)
        ) {
            val xPadding = with(density) { 20.dp.toPx() }
            val yPadding = 80f
            var maxValue = forecast.maxOf { if (roundToInt) it.toDouble().roundToInt().toDouble() else it.toDouble() }
            var minValue = forecast.minOf { if (roundToInt) it.toDouble().roundToInt().toDouble() else it.toDouble() }

            if (ensembleStats != null) {
                val ensembleMax = ensembleStats.mapNotNull { it?.max }.maxOrNull()
                val ensembleMin = ensembleStats.mapNotNull { it?.min }.minOrNull()
                if (ensembleMax != null) maxValue = kotlin.math.max(maxValue, ensembleMax)
                if (ensembleMin != null) minValue = kotlin.math.min(minValue, ensembleMin)
            }

            if (valueRange != null) {
                maxValue = maxValue.coerceAtLeast(valueRange.endInclusive.toDouble())
                minValue = minValue.coerceAtMost(valueRange.start.toDouble())
            }

            // 3. Baser le calcul du pas sur la largeur totale du contenu
            val canvasWidth = size.width
            val xStep = (canvasWidth - 2 * xPadding) / (rawHourlyData.size - 1)
            val yScale = (size.height - 2 * yPadding) / (maxValue - minValue).coerceAtLeast(1.0)

            // --- 4. Dessiner le ruban d'incertitude (Ensemble) ---
            if (ensembleStats != null && ensembleStats.any { it != null }) {
                val uncertaintyPath = Path()
                var firstEnsemble = true
                
                // Partie supérieure du ruban (Max)
                ensembleStats.forEachIndexed { i, stat ->
                    val x = xPadding + (i * (if(sparseMode) 2 else 1) * xStep)
                    val value = stat?.max ?: forecast[i].toDouble()
                    val y = size.height - yPadding - ((value - minValue) * yScale)
                    if (firstEnsemble) {
                        uncertaintyPath.moveTo(x, y.toFloat())
                        firstEnsemble = false
                    } else {
                        uncertaintyPath.lineTo(x, y.toFloat())
                    }
                }
                
                // Partie inférieure du ruban (Min)
                for (i in ensembleStats.indices.reversed()) {
                    val x = xPadding + (i * (if(sparseMode) 2 else 1) * xStep)
                    val value = ensembleStats[i]?.min ?: forecast[i].toDouble()
                    val y = size.height - yPadding - ((value - minValue) * yScale)
                    uncertaintyPath.lineTo(x, y.toFloat())
                }
                uncertaintyPath.close()
                
                drawPath(
                    path = uncertaintyPath,
                    color = graphColor.copy(alpha = 0.3f)
                )

                // Labels pour les valeurs min/max de l'ensemble (optionnel, pour plus de clarté)
                ensembleStats.forEachIndexed { i, stat ->
                    if (stat?.min != null && stat.max != null) {
                        val x = xPadding + (i * (if(sparseMode) 2 else 1) * xStep)
                        val yMax = size.height - yPadding - ((stat.max - minValue) * yScale)
                        val yMin = size.height - yPadding - ((stat.min - minValue) * yScale)
                        val yAvg = size.height - yPadding - ((forecast[i].toDouble() - minValue) * yScale)

                        // On ne dessine que si c'est significativement différent de la moyenne pour éviter l'encombrement
                        if (i % 4 == 0 || i == ensembleStats.size - 1) {
                            val minDistance = 55f // Seuil en pixels pour éviter la superposition

                            val yMaxText = kotlin.math.min(yMax.toFloat() - 5f, yAvg.toFloat() - minDistance)
                            drawContext.canvas.nativeCanvas.drawText(
                                stat.max.toSmartString(),
                                x,
                                yMaxText,
                                Paint().apply {
                                    textAlign = Paint.Align.CENTER
                                    textSize = 25f
                                    color = textColor
                                }
                            )

                            // les valeurs min sont toujours en bas, elles ne superposent pas le texte d'avg qui est en haut
                            drawContext.canvas.nativeCanvas.drawText(
                                stat.min.toSmartString(),
                                x,
                                yMin.toFloat() + 25f,
                                Paint().apply {
                                    textAlign = Paint.Align.CENTER
                                    textSize = 25f
                                    color = textColor
                                }
                            )
                        }
                    }
                }
            }

            // Préparation des chemins
            val linePath = Path()
            val gradientPath = Path()

            // Premier point pour initialiser les chemins
            val firstX = xPadding
            val firstY = size.height - yPadding - (((if(roundToInt) forecast.first().toDouble().roundToInt().toDouble() else forecast.first().toDouble()) - minValue) * yScale)
            linePath.moveTo(firstX, firstY.toFloat())
            gradientPath.moveTo(firstX, size.height) // Commence en bas à gauche
            gradientPath.lineTo(firstX, firstY.toFloat()) // Monte au premier point

            // Construction des chemins pour la courbe et le dégradé
            forecast.forEachIndexed { i, point ->
                val x = xPadding + (i * (if(sparseMode) 2 else 1) * xStep)
                val y = size.height - yPadding - (((if(roundToInt) point.toDouble().roundToInt().toDouble() else point.toDouble()) - minValue) * yScale)
                linePath.lineTo(x, y.toFloat())
                gradientPath.lineTo(x, y.toFloat())
            }

            // 4. Correction de la fermeture du chemin du dégradé
            val lastX = xPadding + ((if(sparseMode) (forecast.size * 2 - 2) else (forecast.size - 1)) * xStep)
            gradientPath.lineTo(lastX, size.height)
            gradientPath.close()

            // Dégradé sous la courbe uniquement en mode déterministe
            if (ensembleStats == null || ensembleStats.any { it == null }) {
                drawPath(
                    path = gradientPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(graphColor.copy(alpha = 0.4f), Color.Transparent),
                        startY = 0f,
                        endY = size.height
                    ),
                )
            }

            // Courbe
            drawPath(linePath, graphColor, style = Stroke(width = 8f))

            // Points + labels
            forecast.forEachIndexed { i, point ->
                val x = xPadding + (i * (if(sparseMode) 2 else 1) * xStep)
                val y = size.height - yPadding - (((if(roundToInt) point.toDouble().roundToInt().toDouble() else point.toDouble()) - minValue) * yScale)

                // Value, Point and Hour label logic with sparseMode
                // With the aggregated data, we always draw every point in 'forecast'
                val shouldDraw = true

                if (shouldDraw) {
                    // Point
                    drawCircle(Color.White, radius = 6f, center = Offset(x, y.toFloat()))

                    // Value label
                    drawContext.canvas.nativeCanvas.drawText(
                        if (roundToInt) point.toDouble().roundToInt().toString() else point.toSmartString(),
                        x,
                        y.toFloat() - 20f,
                        Paint().apply {
                            textAlign = Paint.Align.CENTER
                            textSize = 40f
                            color = textColor
                        }
                    )

                    // Heure label
                    drawContext.canvas.nativeCanvas.drawText(
                        times[i],
                        x,
                        size.height - 20f, // Positionnement relatif au bas du graphique
                        Paint().apply {
                            textAlign = Paint.Align.CENTER
                            textSize = 40f // Légèrement augmenté pour la lisibilité
                            color = textColor
                        }
                    )
                }
            }
        }
    }
    // Si le graphique de vent a été choisi, alors afficher le vecteur de direction du vent
    if (graphType == GraphType.WIND_SPEED) {
        WindVectors(hourlyData, windUnit, scrollState, contentWidth, sparseMode, rawHourlyData.size)
    }
}

@Composable
fun WindVectors(
    hourlyData: List<AllHourlyVarsReading>,
    windUnit: WindUnit = WindUnit.KPH,
    scrollState: ScrollState = rememberScrollState(),
    contentWidth: Dp = 1250.dp,
    sparseMode: Boolean = false,
    originalSize: Int = 24
) {
    val density = LocalDensity.current
    val xPaddingDp = 20.dp
    
    // Draw the icon
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState), // ScrollState partagé
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
                            .offset(x = xPosDp - 20.dp) // center the column
                            .width(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val windGusts = allVarsReading.wind.windGusts
                        Text(
                            text = if (windGusts != null) UnitConverter.formatValue(
                                UnitConverter.convertWind(
                                    windGusts,
                                    windUnit
                                )
                            ) else "--",
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
                                    allVarsReading.wind.windDirection?.toFloat()?.plus(90f) ?: 0f
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
    val rawHourlyData = (forecast as? WeatherDataState.SuccessHourly)?.data
    val hourlyData = if (showPairsOnly && rawHourlyData != null) aggregateHourlyData(rawHourlyData) else rawHourlyData

    val density = LocalDensity.current
    // contentWidth (when showPairsOnly = false) = size(24 : 00h->23h) * 53.dp = 24 * 53.dp = 1272.dp
    // iconSize (when showPairsOnly = false) = 1272 / 20 = 63.6
    // contentWidth (when showPairsOnly = true) = size(12 : 00h->22h) * 53.dp = 12 * 53.dp = 636.dp
    // iconSize (when showPairsOnly = true) = 636 / 11 = 57.82
    val iconsSize: Dp = if (!showPairsOnly) 63.6.dp else 57.82.dp
    val xPaddingPx = with(density) { 20.dp.toPx() }
    val daySeparatorColor = Color.Gray.copy(alpha = 0.7f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Box(modifier = Modifier.width(contentWidth)) {
            if (hourlyData == null || rawHourlyData == null) return@Box
            
            val canvasWidthPx = with(density) { contentWidth.toPx() }
            val xStepPx = (canvasWidthPx - 2 * xPaddingPx) / (rawHourlyData.size - 1)
            
            for ((i, element) in hourlyData.withIndex()) {
                val xPosPx = xPaddingPx + (i * (if(showPairsOnly) 2 else 1) * xStepPx)
                val xPosDp = with(density) { xPosPx.toDp() }
                
                val weatherWord = getSimpleWeather(element).word
                val radiation = element.skyInfo.shortwaveRadiation
                val isDay = if (radiation != null) radiation >= 1.0 else null

                Box(
                    modifier = Modifier
                        .offset(x = xPosDp - (iconsSize / 2))
                        .size(iconsSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (element.wmoEnsemble != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LottieWeatherIcon(
                                iconPath = getLottieIconPath(
                                    weatherCodeToSimpleWord(element.wmoEnsemble?.best)!!,
                                    (isDay == false),
                                    isSystemInDarkTheme()
                                ),
                                animate = animated,
                                modifier = Modifier.size(20.dp)
                            )
                            LottieWeatherIcon(
                                iconPath = getLottieIconPath(
                                    weatherCodeToSimpleWord(element.wmoEnsemble?.worst)!!,
                                    (isDay == false),
                                    isSystemInDarkTheme()
                                ),
                                animate = animated,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (weatherWord != null) {
                        LottieWeatherIcon(
                            iconPath = getLottieIconPath(weatherWord, (isDay == false), isSystemInDarkTheme()),
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
    // Get the forecast
    val forecast by viewModel.hourlyForecast.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val isBatterySaverActive by (LocalContext.current.applicationContext as TheMeteo).weatherCache.isBatterySaverActive.collectAsState()

    WeatherIconGraphGlobal(
        forecast,
        scrollState,
        userSettings,
        isBatterySaverActive,
        contentWidth,
        false,
        modifier = modifier
    )
}

private fun aggregateHourlyData(data: List<AllHourlyVarsReading>): List<AllHourlyVarsReading> {
    return (data.indices step 2).map { i ->
        val h1 = data[i]
        val h2 = if (i + 1 < data.size) data[i + 1] else h1
        h1.copy(
            temperature = maxOfNullable(h1.temperature, h2.temperature),
            apparentTemperature = maxOfNullable(h1.apparentTemperature, h2.apparentTemperature),
            dewpoint = maxOfNullable(h1.dewpoint, h2.dewpoint),
            pressure = maxOfNullable(h1.pressure, h2.pressure),
            humidity = maxOfNullable(h1.humidity, h2.humidity),
            wmo = maxOfNullable(h1.wmo, h2.wmo),
            precipitationData = h1.precipitationData.copy(
                precipitation = maxOfNullable(h1.precipitationData.precipitation, h2.precipitationData.precipitation),
                precipitationProbability = maxOfNullable(h1.precipitationData.precipitationProbability, h2.precipitationData.precipitationProbability),
                rain = maxOfNullable(h1.precipitationData.rain, h2.precipitationData.rain),
                snowfall = maxOfNullable(h1.precipitationData.snowfall, h2.precipitationData.snowfall),
                snowDepth = maxOfNullable(h1.precipitationData.snowDepth, h2.precipitationData.snowDepth)
            ),
            skyInfo = h1.skyInfo.copy(
                cloudcoverTotal = maxOfNullable(h1.skyInfo.cloudcoverTotal, h2.skyInfo.cloudcoverTotal),
                opacity = maxOfNullable(h1.skyInfo.opacity, h2.skyInfo.opacity),
                uvIndex = maxOfNullable(h1.skyInfo.uvIndex, h2.skyInfo.uvIndex),
                visibility = maxOfNullable(h1.skyInfo.visibility, h2.skyInfo.visibility),
                shortwaveRadiation = maxOfNullable(h1.skyInfo.shortwaveRadiation, h2.skyInfo.shortwaveRadiation)
            ),
            wind = h1.wind.copy(
                windspeed = maxOfNullable(h1.wind.windspeed, h2.wind.windspeed),
                windGusts = maxOfNullable(h1.wind.windGusts, h2.wind.windGusts)
            ),
            ensembleStats = aggregateEnsembleStats(h1.ensembleStats, h2.ensembleStats),
            wmoEnsemble = if (h1.wmoEnsemble != null && h2.wmoEnsemble != null) {
                WmoEnsembleStat(
                    best = minOf(h1.wmoEnsemble.best, h2.wmoEnsemble.best),
                    worst = maxOf(h1.wmoEnsemble.worst, h2.wmoEnsemble.worst)
                )
            } else h1.wmoEnsemble ?: h2.wmoEnsemble
        )
    }
}

private fun <T : Comparable<T>> maxOfNullable(a: T?, b: T?): T? {
    return if (a == null) b else if (b == null) a else maxOf(a, b)
}

private fun <T : Comparable<T>> minOfNullable(a: T?, b: T?): T? {
    return if (a == null) b else if (b == null) a else minOf(a, b)
}

private fun aggregateEnsembleStats(s1: Map<String, EnsembleStat>?, s2: Map<String, EnsembleStat>?): Map<String, EnsembleStat>? {
    if (s1 == null) return s2
    if (s2 == null) return s1
    val result = s1.toMutableMap()
    s2.forEach { (k, v2) ->
        val v1 = result[k]
        if (v1 != null) {
            result[k] = EnsembleStat(
                avg = maxOfNullable(v1.avg, v2.avg),
                min = minOfNullable(v1.min, v2.min),
                max = maxOfNullable(v1.max, v2.max)
            )
        } else {
            result[k] = v2
        }
    }
    return result
}
