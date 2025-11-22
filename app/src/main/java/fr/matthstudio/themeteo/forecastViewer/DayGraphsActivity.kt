package fr.matthstudio.themeteo.forecastViewer

import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastViewer.data.WeatherViewModelFactory
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

class DayGraphsActivity : ComponentActivity() {

    // On récupère le ViewModel via la factory pour avoir l'instance partagée
    private val weatherViewModel: WeatherViewModel by viewModels { WeatherViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // initialiser la factory
        WeatherViewModelFactory.initialize(this)

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

        val selectedLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("SELECTED_LOCATION", LocationIdentifier::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("SELECTED_LOCATION") as? LocationIdentifier
        }

        if (selectedLocation == null) {
            Log.e("DayGraphsActivity", "selectedLocation is null. You must pass a location to start this activity")
            finish()
            return
        }

        weatherViewModel.selectLocation(selectedLocation)

        if (startDateTime == null)
        {
            Log.e("DayGraphsActivity", "dayReading is null. Defaulting to now.")
            startDateTime = LocalDateTime.now()
        }

        enableEdgeToEdge()
        setContent {
            TheMeteoTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GraphsScreen(weatherViewModel, startDateTime)
                }
            }
        }
    }
}

@Composable
fun GraphsScreen(viewModel: WeatherViewModel, startDateTime: LocalDateTime) {

    // On ajoute un LaunchedEffect qui réagit au lieu ET à la date
    val selectedLocation by viewModel.selectedLocation.collectAsState()

    // Ce bloc s'exécutera si la date (startDateTime) ou le lieu (selectedLocation) change.
    LaunchedEffect(selectedLocation, startDateTime) {
        val (latitude, longitude) = when (val locationIdentifier = selectedLocation) {
            // Cas 1 : "Position Actuelle"
            is LocationIdentifier.CurrentUserLocation -> {
                // On prend les coordonnées GPS du ViewModel si elles existent, sinon Paris par défaut.
                viewModel.userLocation.value?.let { Pair(it.latitude, it.longitude) } ?: Pair(48.85, 2.35)
            }
            // Cas 2 : Un lieu sauvegardé
            is LocationIdentifier.Saved -> {
                Pair(locationIdentifier.location.latitude, locationIdentifier.location.longitude)
            }
        }

        // On lance la fonction de chargement centrale du ViewModel avec les bonnes coordonnées et la bonne date.
        viewModel.load24hForecast(latitude, longitude, startDateTime)
    }

    // On collecte les états ici pour les passer au composable du graphique
    val isLoading by viewModel.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = if (startDateTime.hour == 0)
                stringResource(R.string.forecast_for_the) +
                        "${startDateTime.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))}"
            else
                stringResource(R.string.next_24h_forecast),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val scrollState = rememberScrollState()

        if (
            viewModel.precipitationForecast.collectAsState().value.isNotEmpty() &&
            viewModel.skyInfoForecast.collectAsState().value.isNotEmpty()) {

            WeatherIconGraph(viewModel, scrollState = scrollState)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // Pour que ça puisse défiler si le contenu est grand
        ) {

            // On affiche un indicateur de chargement pendant la récupération des données
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 50.dp))
            } else if (
                viewModel.temperatureForecast.collectAsState().value.isNotEmpty() &&
                viewModel.dewpointForecast.collectAsState().value.isNotEmpty() &&
                viewModel.precipitationForecast.collectAsState().value.isNotEmpty() &&
                viewModel.windspeedForecast.collectAsState().value.isNotEmpty() &&
                viewModel.pressureForecast.collectAsState().value.isNotEmpty() &&
                viewModel.humidityForecast.collectAsState().value.isNotEmpty()
            ) {
                // Le graphique ne s'affiche QUE si les données sont prêtes
                Text(stringResource(R.string.temperature))
                GenericGraph(
                    viewModel,
                    GraphType.TEMP,
                    Color(0xFFFFD54F),
                    scrollState = scrollState
                )
                if (viewModel.apparentTemperatureForecast.collectAsState().value != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.apparent_temperature))
                    GenericGraph(
                        viewModel,
                        GraphType.A_TEMP,
                        Color(0xFFFFF176),
                        scrollState = scrollState
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.dew_point))
                GenericGraph(
                    viewModel,
                    GraphType.DEW_POINT,
                    Color(0xFFFF8A65),
                    scrollState = scrollState
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.humidity))
                GenericGraph(
                    viewModel,
                    GraphType.HUMIDITY,
                    Color(0xFF4DD0E1),
                    scrollState = scrollState,
                    valueRange = 0f..100f
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.rain))
                GenericGraph(
                    viewModel,
                    GraphType.RAIN_RATE,
                    Color(0xFF64B5F6),
                    scrollState = scrollState,
                    valueRange = 0f..3f
                )
                if (viewModel.precipitationProbabilityForecast.collectAsState().value != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.rain_prob))
                    GenericGraph(
                        viewModel,
                        GraphType.RAIN_PROB,
                        Color(0xFF64B5F6),
                        scrollState = scrollState,
                        valueRange = 0f..100f
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.wind_speed))
                GenericGraph(
                    viewModel,
                    GraphType.WIND_SPEED,
                    Color(0xFFAED581),
                    scrollState = scrollState
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.pressure))
                GenericGraph(
                    viewModel,
                    GraphType.PRESSURE,
                    Color(0xFF9575CD),
                    scrollState = scrollState
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.cloud_cover))
                GenericGraph(
                    viewModel,
                    GraphType.CLOUD_COVER,
                    Color(0xFF9D9D9D),
                    scrollState = scrollState,
                    valueRange = 0f..100f
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Opacity")
                GenericGraph(
                    viewModel,
                    GraphType.OPACITY,
                    Color(0xFF9D9D9D),
                    scrollState = scrollState,
                    valueRange = 0f..100f
                )
            } else {
                // Message si aucune donnée n'est disponible après le chargement
                Text("Données non disponibles pour ce jour.")
            }
        }
    }
}

enum class GraphType {
    TEMP, A_TEMP, DEW_POINT, RAIN_PROB, RAIN_RATE, WIND_SPEED, PRESSURE, HUMIDITY, CLOUD_COVER, OPACITY
}

@Composable
fun GenericGraph(
    viewModel: WeatherViewModel,
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    sublist: Pair<Int, Int>? = null,
    scrollState: ScrollState = rememberScrollState()
) {
    Box(
        modifier = Modifier
            .width(1000.dp)
            .horizontalScroll(scrollState),
    ) {
        var forecast: List<Double>
        var times: List<String>
        when (graphType) {
            GraphType.TEMP -> {
                val fullForecast = viewModel.temperatureForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast.subList(it.first, it.second).map { f -> f.temperature }
                } ?: fullForecast.map { it.temperature }
                times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
            }

            GraphType.A_TEMP -> {
                val fullForecast = viewModel.apparentTemperatureForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast?.subList(it.first, it.second)?.map { f -> f.apparentTemperature }
                } ?: (fullForecast?.map { it.apparentTemperature } ?: emptyList())
                times =
                    fullForecast?.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" } ?: emptyList()
            }

            GraphType.DEW_POINT -> {
                val fullForecast = viewModel.dewpointForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast.subList(it.first, it.second).map { f -> f.dewpoint }
                } ?: fullForecast.map { it.dewpoint }
                times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
            }

            GraphType.RAIN_PROB -> {
                val fullForecast = viewModel.precipitationProbabilityForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast?.subList(it.first, it.second)?.map { f -> f.probability.toDouble() }
                } ?: (fullForecast?.map { it.probability.toDouble() } ?: emptyList())
                times =
                    fullForecast?.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" } ?: emptyList()
            }

            GraphType.RAIN_RATE -> {
                val fullForecast = viewModel.precipitationForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast.subList(it.first, it.second).map { f -> f.precipitation }
                } ?: fullForecast.map { it.precipitation }
                times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
            }

            GraphType.WIND_SPEED -> {
                val fullForecast = viewModel.windspeedForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast.subList(it.first, it.second).map { f -> f.windspeed }
                } ?: fullForecast.map { it.windspeed }
                times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
            }

            GraphType.PRESSURE -> {
                val fullForecast = viewModel.pressureForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast.subList(it.first, it.second).map { f -> f.pressure }
                } ?: fullForecast.map { it.pressure }
                times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
            }

            GraphType.HUMIDITY -> {
                val fullForecast = viewModel.humidityForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast.subList(it.first, it.second).map { f -> f.humidity.toDouble() }
                } ?: fullForecast.map { it.humidity.toDouble() }
                times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
            }

            GraphType.CLOUD_COVER -> {
                val fullForecast = viewModel.skyInfoForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast.subList(it.first, it.second)
                        .map { f -> f.cloudcover_total.toDouble() }
                } ?: fullForecast.map { it.cloudcover_total.toDouble() }
                times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
            }

            GraphType.OPACITY -> {
                val fullForecast = viewModel.skyInfoForecast.collectAsState().value
                forecast = sublist?.let {
                    fullForecast.subList(it.first, it.second).map { f -> f.opacity.toDouble() }
                } ?: fullForecast.map { it.opacity.toDouble() }
                times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
            }
        }
        val roundToInt = viewModel.userSettings.collectAsState().value.roundToInt ?: true

        // 1. Définir une largeur totale pour le contenu du graphique, plus grande que la fenêtre
        val contentWidth = 1000.dp

        val textColor: Int = AndroidColor.rgb(
            MaterialTheme.colorScheme.onBackground.red,
            MaterialTheme.colorScheme.onBackground.green,
            MaterialTheme.colorScheme.onBackground.blue)

        Canvas(
            modifier = Modifier
                .width(contentWidth) // La largeur du contenu défilable
                .height(150.dp)
        ) {
            // 2. Ajuster le padding pour qu'il soit raisonnable
            val xPadding = 50f // Padding sur les côtés à l'intérieur du Canvas
            val yPadding = 100f
            val maxValue = valueRange?.endInclusive?.let { (if (it < forecast.max()) forecast.max() else valueRange.endInclusive) }?.toDouble() ?: forecast.max()
            val minValue = valueRange?.start?.let { (if (it > forecast.min()) forecast.min() else valueRange.start) }?.toDouble() ?: forecast.min()

            // 3. Baser le calcul du pas sur la largeur totale du contenu
            val canvasWidth = size.width
            val xStep = (canvasWidth - 2 * xPadding) / (forecast.size - 1)
            val yScale = (size.height - 2 * yPadding) / (maxValue - minValue).coerceAtLeast(1.0)

            // Préparation des chemins
            val linePath = Path()
            val gradientPath = Path()

            // Premier point pour initialiser les chemins
            val firstX = xPadding
            val firstY = size.height - yPadding - (((if (graphType == GraphType.RAIN_RATE || !roundToInt)
                forecast.first() else forecast.first().roundToInt().toDouble()) - minValue) * yScale)
            linePath.moveTo(firstX, firstY.toFloat())
            gradientPath.moveTo(firstX, size.height) // Commence en bas à gauche
            gradientPath.lineTo(firstX, firstY.toFloat())     // Monte au premier point

            // Construction des chemins pour la courbe et le dégradé
            forecast.forEachIndexed { i, point ->
                val x = xPadding + (i * xStep)
                val y = size.height - yPadding - (((if (graphType == GraphType.RAIN_RATE || !roundToInt)
                    point else point.roundToInt().toDouble()) - minValue) * yScale)
                linePath.lineTo(x, y.toFloat())
                gradientPath.lineTo(x, y.toFloat())
            }

            // 4. Correction de la fermeture du chemin du dégradé
            val lastX = xPadding + ((forecast.size - 1) * xStep)
            gradientPath.lineTo(lastX, size.height)
            gradientPath.close()

            // Dégradé sous la courbe
            val gradientColor = graphColor
            drawPath(
                path = gradientPath,
                brush = Brush.verticalGradient(
                    colors = listOf(gradientColor.copy(alpha = 0.4f), Color.Transparent),
                    startY = 0f,
                    endY = size.height
                ),
            )

            // Courbe
            drawPath(linePath, gradientColor, style = Stroke(width = 8f))

            // Points + labels
            forecast.forEachIndexed { i, point ->
                val x = xPadding + (i * xStep)
                val y = size.height - yPadding - (((if (graphType == GraphType.RAIN_RATE || !roundToInt)
                    point else point.roundToInt().toDouble()) - minValue) * yScale)

                // Point
                drawCircle(Color.White, radius = 6f, center = Offset(x, y.toFloat()))

                // Value
                drawContext.canvas.nativeCanvas.drawText(
                    (if (graphType == GraphType.RAIN_RATE || (!roundToInt && graphType != GraphType.PRESSURE))
                        point else point.roundToInt()).toString(),
                    x,
                    y.toFloat() - 20f,
                    Paint().apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 40f
                        color = textColor
                    }
                )

                // Heure
                drawContext.canvas.nativeCanvas.drawText(
                    times[i],
                    x,
                    size.height - yPadding + 70f, // Positionnement relatif au bas du graphique
                    Paint().apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 40f // Légèrement augmenté pour la lisibilité
                        color = textColor
                    }
                )
            }
        }
    }
    // Si le graphique de vent a été choisi, alors afficher le vecteur de direction du vent
    if (graphType == GraphType.WIND_SPEED) {
        WindVectors(viewModel, scrollState)
    }
}

@Composable
fun WindVectors(viewModel: WeatherViewModel, scrollState: ScrollState = rememberScrollState()) {
    val context = LocalContext.current
    // 1. Get the vector icon
    val iconBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, "icons/variables/wind_vector.png")) }
    // 2. Draw the icon
    Box(
        modifier = Modifier
            .width(1000.dp)
            .horizontalScroll(scrollState), // ScrollState partagé
    ) {
        val windDirectionForecast = viewModel.windDirectionForecast.value
        if (windDirectionForecast.isNotEmpty())
        {
            Row(
                modifier = Modifier.fillMaxWidth(), // Make the Row fill the 1000.dp width
                // This will space your items evenly across the width of the graph
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                windDirectionForecast.forEach { direction ->
                    Image(
                        bitmap = iconBitmap ?: ImageBitmap(1, 1),
                        contentDescription = null,
                        modifier = Modifier
                            // Use a fixed width that matches the spacing of your graph points.
                            // 41.5.dp seems about right (1000dp / 24 hours ≈ 41.6dp)
                            .width(41.5.dp)
                            .rotate(direction.windDirection.toFloat())
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherIconGraph(
    viewModel: WeatherViewModel,
    //sublist: Pair<Int, Int>? = null,
    scrollState: ScrollState = rememberScrollState()
) {
    // Charger les icônes
    val iconWeatherFolder = "file:///android_asset/icons/weather/"
    val sunnyDayIconPath: String = iconWeatherFolder + "clear-day.svg"
    val sunnyNightIconPath: String = iconWeatherFolder + "clear-night.svg"
    val sunnyCloudyDayIconPath: String = iconWeatherFolder + "cloudy-3-day.svg"
    val sunnyCloudyNightIconPath: String = iconWeatherFolder + "cloudy-3-night.svg"
    val cloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val foggyIconPath: String = iconWeatherFolder + "fog.svg"
    val dustIconPath: String = iconWeatherFolder + "dust.svg"
    val drizzleDayIconPath: String = iconWeatherFolder + "rainy-1-day.svg"
    val drizzleNightIconPath: String = iconWeatherFolder + "rainy-1-night.svg"
    val rainy1DayIconPath: String = iconWeatherFolder + "rainy-2-day.svg"
    val rainy1NightIconPath: String = iconWeatherFolder + "rainy-2-night.svg"
    val rainy2DayIconPath: String = iconWeatherFolder + "rainy-3-day.svg"
    val rainy2NightIconPath: String = iconWeatherFolder + "rainy-3-night.svg"
    val hailIconPath: String = iconWeatherFolder + "hail.svg"
    val snowyIconPath: String = iconWeatherFolder + "snowy-2.svg"
    val snowyMixIconPath: String = iconWeatherFolder + "rain-and-snow-mix.svg"
    val stormyIconPath: String = iconWeatherFolder + "thunderstorms.svg"

    val simpleWeatherList = mutableListOf<Pair<SimpleWeatherWord, Boolean>>()
    for (index in 0..23) {
        simpleWeatherList.add(Pair(getSimpleWeather(viewModel, index).word, viewModel.skyInfoForecast.collectAsState().value[index].shortwave_radiation >= 1.0))
    }

    Box(
        modifier = Modifier
            .width(1000.dp) // Largeur fixe, identique à GenericGraph
            .horizontalScroll(scrollState), // ScrollState partagé
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), // La Row prend toute la largeur du Box (1000.dp)
            horizontalArrangement = Arrangement.SpaceAround, // L'arrangement gère l'espacement
            verticalAlignment = Alignment.CenterVertically
        ) {
            simpleWeatherList.forEach { (weatherWord, isDay) ->
                val fileName = when (weatherWord) {
                    SimpleWeatherWord.SUNNY -> if (isDay) sunnyDayIconPath else sunnyNightIconPath
                    SimpleWeatherWord.SUNNY_CLOUDY -> if (isDay) sunnyCloudyDayIconPath else sunnyCloudyNightIconPath
                    SimpleWeatherWord.CLOUDY -> cloudyIconPath
                    SimpleWeatherWord.FOGGY -> foggyIconPath
                    SimpleWeatherWord.DUST -> dustIconPath
                    SimpleWeatherWord.DRIZZLY -> if (isDay) drizzleDayIconPath else drizzleNightIconPath
                    SimpleWeatherWord.RAINY1 -> if (isDay) rainy1DayIconPath else rainy1NightIconPath
                    SimpleWeatherWord.RAINY2 -> if (isDay) rainy2DayIconPath else rainy2NightIconPath
                    SimpleWeatherWord.HAIL -> hailIconPath
                    SimpleWeatherWord.SNOWY -> snowyIconPath
                    SimpleWeatherWord.SNOWY_MIX -> snowyMixIconPath
                    SimpleWeatherWord.STORMY -> stormyIconPath
                }

                AsyncImage(
                    model = fileName,
                    contentDescription = "Icône météo actuelle",
                    modifier = Modifier
                        .width(41.5.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}