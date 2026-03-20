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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import fr.matthstudio.themeteo.WeatherDataState
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

        if (startDateTime == null)
        {
            Log.e("DayGraphsActivity", "start date time is null. Defaulting to now.")
            // On prend l'heure actuelle, puis on met les minutes, secondes et nanosecondes à 0.
            startDateTime = LocalDateTime.now()
        }

        // Instancier le viewModel
        val app = (this.application as TheMeteo)
        weatherViewModel = WeatherViewModel(app.weatherCache, startDateTime, app.container.telemetryManager)

        enableEdgeToEdge()
        setContent {
            TheMeteoTheme {
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
                        GraphsScreen(weatherViewModel, startDateTime)
                    }
                }
            }
        }
    }
}

@Composable
fun GraphsScreen(viewModel: WeatherViewModel, startDateTime: LocalDateTime) {

    val forecast by viewModel.hourlyForecast.collectAsState()
    val scrollState = rememberScrollState()
    val backgroundColor = MaterialTheme.colorScheme.background
    val userSettings by viewModel.userSettings.collectAsState()

    val verticalScrollState = rememberScrollState()
    val showTemperatureDetailsGraphs = remember { mutableStateOf(false) }
    val showPrecipitationDetailsGraphs = remember { mutableStateOf(false) }
    val showUvDetailsGraphs = remember { mutableStateOf(false) }

    // Auto-scroll to 6 AM if starting at 00h
    val hasScrolled = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    LaunchedEffect(forecast) {
        if (forecast is WeatherDataState.SuccessHourly && !hasScrolled.value) {
            val data = (forecast as WeatherDataState.SuccessHourly).data
            if (data.isNotEmpty() && data.first().time.hour == 0) {
                val index6h = data.indexOfFirst { it.time.hour == 6 }
                if (index6h != -1) {
                    val contentWidthPx = with(density) { 1000.dp.toPx() }
                    val xPadding = 40f // Matching GenericGraphGlobal's xPadding
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
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Header with transparent background
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (startDateTime.hour == 0)
                        stringResource(R.string.forecast_for_the, startDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
                    else
                        stringResource(R.string.next_24h_forecast),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.Center)
                )
            }

            if ((forecast as? WeatherDataState.SuccessHourly)?.data?.isNotEmpty() ?: false) {
                // Icons graph with transparent background
                if ((forecast as? WeatherDataState.SuccessHourly)?.data?.first()?.wmo != null) {
                    WeatherIconGraph(viewModel, scrollState = scrollState)
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
                            itemCount = (forecast as WeatherDataState.SuccessHourly).data.size
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState), // Pour que ça puisse défiler si le contenu est grand
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
                            scrollState = scrollState
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
                                        scrollState = scrollState
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
                                        valueRange = 0f..100f
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
                                scrollState = scrollState
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
                                valueRange = 0f..3f
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
                                        valueRange = 0f..100f
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
                                        valueRange = 0f..3f
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
                                        scrollState = scrollState
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
                                    scrollState = scrollState
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
                            scrollState = scrollState
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- VISIBILITY ---
                        if (hourlyData.first().skyInfo.visibility != null) {
                            Text(stringResource(R.string.visibility), modifier = titleModifier)
                            GenericGraph(
                                viewModel,
                                GraphType.VISIBILITY,
                                Color(0xFF98FFEB),
                                scrollState = scrollState
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
                                valueRange = 0f..100f
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
                                valueRange = 0f..11f
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
                                        valueRange = 0f..100f
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
                                scrollState = scrollState
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

enum class GraphType {
    TEMP, A_TEMP,
    DEW_POINT, HUMIDITY,
    PRECIPITATION, PRECIPITATION_PROB, RAIN, SNOWFALL, SNOW_DEPTH,
    WIND_SPEED, PRESSURE,
    CLOUD_COVER, OPACITY,
    UV_INDEX, VISIBILITY,
}

@Composable
fun BackgroundGrid(
    itemCount: Int,
    contentWidth: Dp = 1000.dp
) {
    val xPadding = 40f
    
    Canvas(
        modifier = Modifier
            .width(contentWidth)
            .fillMaxHeight()
    ) {
        val xStep = (size.width - 2 * xPadding) / (itemCount - 1)
        val gridColor = Color.Gray.copy(alpha = 0.3f)

        (0 until itemCount - 1).forEach { i ->
            val drawX = xPadding + (i * xStep) + xStep / 2
            drawLine(
                color = gridColor,
                start = Offset(drawX, 0f),
                end = Offset(drawX, size.height),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
fun GenericGraph(
    viewModel: WeatherViewModel,
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
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
        scrollState
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
    contentWidth: Dp = 1000.dp,
    contentHeight: Dp = 125.dp,
    compactHourFormat: Boolean = false,
    sparseMode: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(1000.dp)
            .horizontalScroll(scrollState),
    ) {
        var forecast: List<Number>
        val times: List<String> =
            (fullForecast as WeatherDataState.SuccessHourly).data
                .map { it.time.format(DateTimeFormatter.ofPattern("HH")) + if (!compactHourFormat) "h" else "" }
        
        val isTemperatureGraph = graphType == GraphType.TEMP || graphType == GraphType.A_TEMP || graphType == GraphType.DEW_POINT

        when (graphType) {
            GraphType.TEMP -> {
                forecast = fullForecast.data.map { f -> 
                    val v = f.temperature ?: throw IllegalStateException("Graph data cannot be null")
                    UnitConverter.convertTemperature(v, temperatureUnit)
                }
            }

            GraphType.A_TEMP -> {
                forecast = fullForecast.data
                    .map { f -> 
                        val v = f.apparentTemperature ?: throw IllegalStateException("Graph data cannot be null")
                        UnitConverter.convertTemperature(v, temperatureUnit)
                    }
            }

            GraphType.DEW_POINT -> {
                forecast = fullForecast.data.map { f -> 
                    val v = f.dewpoint ?: throw IllegalStateException("Graph data cannot be null")
                    UnitConverter.convertTemperature(v, temperatureUnit)
                }
            }

            GraphType.PRECIPITATION_PROB -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.precipitationProbability ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.PRECIPITATION -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.precipitation ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.RAIN -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.rain ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.SNOWFALL -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.snowfall ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.SNOW_DEPTH -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.snowDepth ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.WIND_SPEED -> {
                forecast = fullForecast.data
                    .map { f -> 
                        val v = f.wind.windspeed ?: throw IllegalStateException("Graph data cannot be null")
                        UnitConverter.convertWind(v, windUnit)
                    }
            }

            GraphType.PRESSURE -> {
                forecast = fullForecast.data
                    .map { f -> f.pressure ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.HUMIDITY -> {
                forecast = fullForecast.data
                    .map { f -> f.humidity ?: throw IllegalStateException("Graph data cannot be null")}
            }

            GraphType.CLOUD_COVER -> {
                forecast = fullForecast.data
                    .map { f -> f.skyInfo.cloudcoverTotal ?: throw IllegalStateException("Graph data cannot be null")}
            }

            GraphType.OPACITY -> {
                forecast = fullForecast.data
                    .map { f -> f.skyInfo.opacity ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.UV_INDEX -> {
                forecast = fullForecast.data
                    .map { f -> f.skyInfo.uvIndex ?: throw IllegalStateException("Graph data cannot be null") }
            }

            GraphType.VISIBILITY -> {
                forecast = fullForecast.data
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
        val ensembleStatsRaw = if (ensembleKey != null) fullForecast.data.map { it.ensembleStats?.get(ensembleKey) } else null
        
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
            val xPadding = 40f // Unifié pour laisser de la place au texte sans padding externe
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
            val xStep = (canvasWidth - 2 * xPadding) / (forecast.size - 1)
            val yScale = (size.height - 2 * yPadding) / (maxValue - minValue).coerceAtLeast(1.0)

            // --- 4. Dessiner le ruban d'incertitude (Ensemble) ---
            if (ensembleStats != null && ensembleStats.any { it != null }) {
                val uncertaintyPath = Path()
                var firstEnsemble = true
                
                // Partie supérieure du ruban (Max)
                ensembleStats.forEachIndexed { i, stat ->
                    val x = xPadding + (i * xStep)
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
                    val x = xPadding + (i * xStep)
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
                        val x = xPadding + (i * xStep)
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
                val x = xPadding + (i * xStep)
                val y = size.height - yPadding - (((if(roundToInt) point.toDouble().roundToInt().toDouble() else point.toDouble()) - minValue) * yScale)
                linePath.lineTo(x, y.toFloat())
                gradientPath.lineTo(x, y.toFloat())
            }

            // 4. Correction de la fermeture du chemin du dégradé
            val lastX = xPadding + ((forecast.size - 1) * xStep)
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
                val x = xPadding + (i * xStep)
                val y = size.height - yPadding - (((if(roundToInt) point.toDouble().roundToInt().toDouble() else point.toDouble()) - minValue) * yScale)

                // Value, Point and Hour label logic with sparseMode
                val shouldDraw = !sparseMode || (i % 2 == 0)

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
        WindVectors(fullForecast, windUnit, scrollState)
    }
}

@Composable
fun WindVectors(forecast: WeatherDataState, windUnit: WindUnit = WindUnit.KPH, scrollState: ScrollState = rememberScrollState()) {
    // Draw the icon
    Box(
        modifier = Modifier
            .width(1000.dp)
            .horizontalScroll(scrollState), // ScrollState partagé
    ) {
        if ((forecast as WeatherDataState.SuccessHourly).data.isNotEmpty())
        {
            Row(
                modifier = Modifier.fillMaxWidth(), // Make the Row fill the 1000.dp width
                // This will space your items evenly across the width of the graph
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                forecast.data.forEach { allVarsReading ->
                    Column (
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val windGusts = allVarsReading.wind.windGusts
                        Text (
                            text = if (windGusts != null) UnitConverter.formatValue(UnitConverter.convertWind(windGusts, windUnit)) else "--",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 1.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                // Use a fixed width that matches the spacing of your graph points.
                                // 41.5.dp seems about right (1000dp / 24 hours ≈ 41.6dp)
                                .width(41.5.dp)
                                .rotate(
                                    allVarsReading.wind.windDirection?.toFloat()?.minus(180) ?: 0f
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
    contentWidth: Dp,
    showPairsOnly: Boolean
) {
    val animated = userSettings.enableAnimatedIcons && !isBatterySaverActive
    val hourlyData = (forecast as? WeatherDataState.SuccessHourly)?.data

    var numIcons = hourlyData?.size ?: 0
    if (showPairsOnly)
        numIcons /= 2

    val iconsSize = 40.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val xPaddingPx = 40f
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Box(modifier = Modifier.width(contentWidth)) {
            if (hourlyData == null) return@Box
            
            val canvasWidthPx = with(density) { contentWidth.toPx() }
            val xStepPx = (canvasWidthPx - 2 * xPaddingPx) / (hourlyData.size - 1)
            
            for (i in 0..<hourlyData.size) {
                if (showPairsOnly && i % 2 != 0) continue
                
                val xPosPx = xPaddingPx + (i * xStepPx)
                val xPosDp = with(density) { xPosPx.toDp() }
                
                val weatherWord = getSimpleWeather(hourlyData[i]).word
                val radiation = hourlyData[i].skyInfo.shortwaveRadiation
                val isDay = if (radiation != null) radiation >= 1.0 else null

                Box(
                    modifier = Modifier
                        .offset(x = xPosDp - (iconsSize / 2))
                        .size(iconsSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (hourlyData[i].wmoEnsemble != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LottieWeatherIcon(
                                iconPath = getLottieIconPath(
                                    weatherCodeToSimpleWord(hourlyData[i].wmoEnsemble?.best)!!,
                                    (isDay == false)
                                ),
                                animate = animated,
                                modifier = Modifier.size(20.dp)
                            )
                            LottieWeatherIcon(
                                iconPath = getLottieIconPath(
                                    weatherCodeToSimpleWord(hourlyData[i].wmoEnsemble?.worst)!!,
                                    (isDay == false)
                                ),
                                animate = animated,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (weatherWord != null) {
                        LottieWeatherIcon(
                            iconPath = getLottieIconPath(weatherWord, (isDay == false)),
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
    viewModel: WeatherViewModel,
    scrollState: ScrollState = rememberScrollState(),
    contentWidth: Dp = 1000.dp
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
        false
    )
}
