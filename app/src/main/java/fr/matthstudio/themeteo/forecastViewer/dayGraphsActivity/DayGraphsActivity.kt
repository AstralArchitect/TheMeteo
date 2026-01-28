package fr.matthstudio.themeteo.forecastViewer.dayGraphsActivity

import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.forecastViewer.forecastMainActivity.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastViewer.forecastMainActivity.getSimpleWeather
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme
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
        weatherViewModel = WeatherViewModel((this.application as TheMeteo).weatherCache, startDateTime)

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

    val forecast by viewModel.hourlyForecast.collectAsState()

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

        if ((forecast as? WeatherDataState.SuccessHourly)?.data?.isNotEmpty() ?: false) {
            WeatherIconGraph(viewModel, scrollState = scrollState)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()), // Pour que ça puisse défiler si le contenu est grand
        ) {

            // On affiche un indicateur de chargement pendant la récupération des données
            if (forecast == WeatherDataState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 50.dp))
            } else if (forecast is WeatherDataState.SuccessHourly && (forecast as WeatherDataState.SuccessHourly).data.isNotEmpty()) {
                // Le graphique ne s'affiche QUE si les données sont prêtes
                Text(stringResource(R.string.temperature))
                GenericGraph(
                    viewModel,
                    GraphType.TEMP,
                    Color(0xFFFFF176),
                    scrollState = scrollState
                )
                if ((forecast as WeatherDataState.SuccessHourly).data.first().apparentTemperature != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.apparent_temperature))
                    GenericGraph(
                        viewModel,
                        GraphType.A_TEMP,
                        Color(0xFFFFD54F),
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
                if ((forecast as WeatherDataState.SuccessHourly).data.maxOf { it.precipitationData.precipitation } != 0.0) {
                    Text(stringResource(R.string.precipitation))
                    GenericGraph(
                        viewModel,
                        GraphType.PRECIPITATION,
                        Color(0xFF64B5F6),
                        scrollState = scrollState,
                        valueRange = 0f..3f
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if ((forecast as WeatherDataState.SuccessHourly).data.first().precipitationData.precipitationProbability != null)
                {
                    Text(stringResource(R.string.precipitation_prob))
                    GenericGraph(
                        viewModel,
                        GraphType.PRECIPITATION_PROB,
                        Color(0xFF64B5F6),
                        scrollState = scrollState,
                        valueRange = 0f..100f
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if ((forecast as WeatherDataState.SuccessHourly).data.maxOf { it.precipitationData.rain } != 0.0) {
                    Text(stringResource(R.string.rain))
                    GenericGraph(
                        viewModel,
                        GraphType.RAIN,
                        Color(0xFF64B5F6),
                        scrollState = scrollState,
                        valueRange = 0f..3f
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if ((forecast as WeatherDataState.SuccessHourly).data.maxOf { it.precipitationData.snowfall } != 0.0)
                {
                    Text("Snowfall (cm/h)")
                    GenericGraph(
                        viewModel,
                        GraphType.SNOWFALL,
                        Color(0xFFFFFFFF),
                        scrollState = scrollState
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if ((forecast as WeatherDataState.SuccessHourly).data.first().precipitationData.snowDepth != null) {
                    if ((forecast as WeatherDataState.SuccessHourly).data.maxOf { it.precipitationData.snowDepth!! } != 0) {
                        Text("Snow depth (cm)")
                        GenericGraph(
                            viewModel,
                            GraphType.SNOW_DEPTH,
                            Color(0xFFFFFFFF),
                            scrollState = scrollState
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
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
                if ((forecast as WeatherDataState.SuccessHourly).data.first().skyInfo.opacity != null) {
                    if ((forecast as WeatherDataState.SuccessHourly).data.isNotEmpty()) {
                        Text(stringResource(R.string.opacity_graph))
                        GenericGraph(
                            viewModel,
                            GraphType.OPACITY,
                            Color(0xFF9D9D9D),
                            scrollState = scrollState,
                            valueRange = 0f..100f
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if ((forecast as WeatherDataState.SuccessHourly).data.first().skyInfo.visibility != null) {
                    if ((forecast as WeatherDataState.SuccessHourly).data.isNotEmpty()) {
                        Text(stringResource(R.string.visibility))
                        GenericGraph(
                            viewModel,
                            GraphType.VISIBILITY,
                            Color(0xFF98FFEB),
                            scrollState = scrollState
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if ((forecast as WeatherDataState.SuccessHourly).data.first().skyInfo.uvIndex != null) {
                    if ((forecast as WeatherDataState.SuccessHourly).data.isNotEmpty()) {
                        Text(stringResource(R.string.uv_index))
                        GenericGraph(
                            viewModel,
                            GraphType.UV_INDEX,
                            Color(0xFFFFEAB5),
                            scrollState = scrollState,
                            valueRange = 0f..11f
                        )
                    }
                }
            } else {
                // Message si aucune donnée n'est disponible après le chargement
                Text("Données non disponibles pour ce jour.")
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
fun GenericGraph(
    viewModel: WeatherViewModel,
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState()
) {
    val fullForecast by viewModel.hourlyForecast.collectAsState()
    var roundToInt = viewModel.userSettings.collectAsState().value.roundToInt ?: true

    if (graphType == GraphType.PRECIPITATION || graphType == GraphType.RAIN ||
        graphType == GraphType.SNOWFALL
    )
        roundToInt = false

    GenericGraphGlobal(
        fullForecast,
        roundToInt,
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
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState()
) {
    Box(
        modifier = Modifier
            .width(1000.dp)
            .horizontalScroll(scrollState),
    ) {
        var forecast: List<Number>
        val times: List<String> =
            (fullForecast as WeatherDataState.SuccessHourly).data
                .map { it.time.format(DateTimeFormatter.ofPattern("HH")) + "h" }
        when (graphType) {
            GraphType.TEMP -> {
                forecast = fullForecast.data.map { f -> f.temperature }
            }

            GraphType.A_TEMP -> {
                forecast = fullForecast.data
                    .map { f -> f.apparentTemperature ?: return }
            }

            GraphType.DEW_POINT -> {
                forecast = fullForecast.data.map { it.dewpoint }
            }

            GraphType.PRECIPITATION_PROB -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.precipitationProbability ?: return }
            }

            GraphType.PRECIPITATION -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.precipitation }
            }

            GraphType.RAIN -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.rain }
            }

            GraphType.SNOWFALL -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.snowfall }
            }

            GraphType.SNOW_DEPTH -> {
                forecast = fullForecast.data
                    .map { f -> f.precipitationData.snowDepth ?: return }
            }

            GraphType.WIND_SPEED -> {
                forecast = fullForecast.data
                    .map { f -> f.windspeed }
            }

            GraphType.PRESSURE -> {
                forecast = fullForecast.data
                    .map { f -> f.pressure }
            }

            GraphType.HUMIDITY -> {
                forecast = fullForecast.data
                    .map { f -> f.humidity }
            }

            GraphType.CLOUD_COVER -> {
                forecast = fullForecast.data
                    .map { f -> f.skyInfo.cloudcoverTotal }
            }

            GraphType.OPACITY -> {
                forecast = fullForecast.data
                    .map { f -> f.skyInfo.opacity ?: return }
            }

            GraphType.UV_INDEX -> {
                forecast = fullForecast.data
                    .map { f -> f.skyInfo.uvIndex ?: return }
            }

            GraphType.VISIBILITY -> {
                forecast = fullForecast.data
                    .map { f -> f.skyInfo.visibility?.div(1000f) ?: return }
            }
        }

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
            var maxValue = forecast.maxOf { if (roundToInt) it.toDouble().roundToInt().toDouble() else it.toDouble() }
            var minValue = forecast.minOf { if (roundToInt) it.toDouble().roundToInt().toDouble() else it.toDouble() }

            if (valueRange != null) {
                maxValue = maxValue.coerceAtLeast(valueRange.endInclusive.toDouble())
                minValue = minValue.coerceAtMost(valueRange.start.toDouble())
            }

            // 3. Baser le calcul du pas sur la largeur totale du contenu
            val canvasWidth = size.width
            val xStep = (canvasWidth - 2 * xPadding) / (forecast.size - 1)
            val yScale = (size.height - 2 * yPadding) / (maxValue - minValue).coerceAtLeast(1.0)

            // Préparation des chemins
            val linePath = Path()
            val gradientPath = Path()

            // Premier point pour initialiser les chemins
            val firstX = xPadding
            val firstY = size.height - yPadding - (((if(roundToInt) forecast.first().toDouble().roundToInt().toDouble() else forecast.first().toDouble()) - minValue) * yScale)
            linePath.moveTo(firstX, firstY.toFloat())
            gradientPath.moveTo(firstX, size.height) // Commence en bas à gauche
            gradientPath.lineTo(firstX, firstY.toFloat())     // Monte au premier point

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
                val y = size.height - yPadding - (((if(roundToInt) point.toDouble().roundToInt().toDouble() else point.toDouble()) - minValue) * yScale)

                // Point
                drawCircle(Color.White, radius = 6f, center = Offset(x, y.toFloat()))

                // Value
                drawContext.canvas.nativeCanvas.drawText(
                    if (roundToInt) point.toDouble().roundToInt().toString() else point.toString(),
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
        WindVectors(fullForecast, scrollState)
    }
}

@Composable
fun WindVectors(forecast: WeatherDataState, scrollState: ScrollState = rememberScrollState()) {
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            // Use a fixed width that matches the spacing of your graph points.
                            // 41.5.dp seems about right (1000dp / 24 hours ≈ 41.6dp)
                            .width(41.5.dp)
                            .rotate(allVarsReading.windDirection.toFloat() - 180)
                    )
                }
            }
        }
    }
}

/*@Composable
fun BarsGraph(
    viewModel: WeatherViewModel,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState()
) {
    val forecast by viewModel.minutelyForecast15.collectAsState()

    if (forecast.isEmpty()) {
        return // Ne rien dessiner si les données ne sont pas prêtes
    }

    Box(
        modifier = Modifier
            .width(1000.dp) // Largeur fixe pour correspondre aux autres graphiques
            .horizontalScroll(scrollState),
    ) {
        val rainColor = Color(0xFF64B5F6) // Bleu pour la pluie
        val snowColor = Color.White       // Blanc pour la neige
        val textColor: Int = AndroidColor.rgb(
            MaterialTheme.colorScheme.onBackground.red,
            MaterialTheme.colorScheme.onBackground.green,
            MaterialTheme.colorScheme.onBackground.blue
        )

        Canvas(
            modifier = Modifier
                .width(1000.dp)
                .height(150.dp)
        ) {
            val xPadding = 50f
            val yPadding = 100f

            // Déterminer la valeur maximale pour l'échelle Y
            val maxPrecipitation = forecast.maxOfOrNull { it.rain + it.snowfall }?.toFloat() ?: 1f
            val maxValue = valueRange?.endInclusive?.coerceAtLeast(maxPrecipitation) ?: maxPrecipitation

            if (maxValue <= 0f) return@Canvas // Éviter la division par zéro si aucune précipitation

            val canvasWidth = size.width
            val xStep = (canvasWidth - 2 * xPadding) / (forecast.size - 1)
            val barWidth = xStep * 0.7f // Laisser un peu d'espace entre les barres
            val yScale = (size.height - 2 * yPadding) / maxValue

            forecast.forEachIndexed { index, reading ->
                val x = xPadding + (index * xStep)
                val rainHeight = (reading.rain * yScale).toFloat()
                val snowHeight = (reading.snowfall * yScale).toFloat()
                val totalHeight = rainHeight + snowHeight

                // 1. Dessiner la barre de pluie (en bas)
                if (rainHeight > 0) {
                    drawRect(
                        color = rainColor,
                        topLeft = Offset(x - barWidth / 2, size.height - yPadding - rainHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, rainHeight)
                    )
                }

                // 2. Dessiner la barre de neige (empilée sur la pluie)
                if (snowHeight > 0) {
                    drawRect(
                        color = snowColor,
                        topLeft = Offset(x - barWidth / 2, size.height - yPadding - rainHeight - snowHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, snowHeight)
                    )
                }

                // 3. Dessiner la valeur totale au-dessus de la barre (si > 0)
                val totalPrecipitationValue = reading.rain + reading.snowfall
                if (totalPrecipitationValue > 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.getDefault(), "%.1f", totalPrecipitationValue), // Formatter avec une décimale
                        x,
                        size.height - yPadding - totalHeight - 15f, // Position au-dessus de la barre
                        Paint().apply {
                            textAlign = Paint.Align.CENTER
                            textSize = 25f
                            color = textColor
                        }
                    )
                }

                // 4. Dessiner la minute en bas
                drawContext.canvas.nativeCanvas.drawText(
                    reading.time.format(DateTimeFormatter.ofPattern("mm")),
                    x,
                    size.height - yPadding + 90f,
                    Paint().apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 35f
                        color = textColor
                    }
                )
                // Dessiner l'heure au dessus de la minute si elle est égale à 0 et dessiner une barre vertical devant celle-ci
                if (reading.time.minute == 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        reading.time.format(DateTimeFormatter.ofPattern("HH")) + "h",
                        x,
                        size.height - yPadding + 60f,
                        Paint().apply {
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.CENTER
                            textSize = 35f
                            color = textColor
                        }
                    )
                    drawRect(
                        color = Color.Gray,
                        topLeft = Offset(x - xStep / 2f, 0f),
                        size = androidx.compose.ui.geometry.Size(2f, size.height)
                    )
                }
            }
        }
    }
}*/

@Composable
fun WeatherIconGraph(
    viewModel: WeatherViewModel,
    scrollState: ScrollState = rememberScrollState()
) {
    // Get the forecast
    val forecast by viewModel.hourlyForecast.collectAsState()
    // Charger les icônes
    val iconWeatherFolder = "file:///android_asset/icons/weather/"
    val sunnyDayIconPath: String = iconWeatherFolder + "clear-day.svg"
    val sunnyNightIconPath: String = iconWeatherFolder + "clear-night.svg"
    val sunnyCloudyDayIconPath: String = iconWeatherFolder + "cloudy-3-day.svg"
    val sunnyCloudyNightIconPath: String = iconWeatherFolder + "cloudy-3-night.svg"
    val sunnyCloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val cloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val foggyIconPath: String = iconWeatherFolder + "fog.svg"
    val dustIconPath: String = iconWeatherFolder + "dust.svg"
    val drizzleDayIconPath: String = iconWeatherFolder + "rainy-1-day.svg"
    val drizzleNightIconPath: String = iconWeatherFolder + "rainy-1-night.svg"
    val drizzleIconPath: String = iconWeatherFolder + "rainy-1.svg"
    val rainy1DayIconPath: String = iconWeatherFolder + "rainy-2-day.svg"
    val rainy1NightIconPath: String = iconWeatherFolder + "rainy-2-night.svg"
    val rainy1IconPath: String = iconWeatherFolder + "rainy-2.svg"
    val rainy2DayIconPath: String = iconWeatherFolder + "rainy-3-day.svg"
    val rainy2NightIconPath: String = iconWeatherFolder + "rainy-3-night.svg"
    val rainy2IconPath: String = iconWeatherFolder + "rainy-3.svg"
    val hailIconPath: String = iconWeatherFolder + "hail.svg"
    val snowy1IconPath: String = iconWeatherFolder + "snowy-1.svg"
    val snowy2IconPath: String = iconWeatherFolder + "snowy-2.svg"
    val snowy3IconPath: String = iconWeatherFolder + "snowy-3.svg"
    val snowyMixIconPath: String = iconWeatherFolder + "rain-and-snow-mix.svg"
    val stormyIconPath: String = iconWeatherFolder + "thunderstorms.svg"

    val simpleWeatherList = mutableListOf<Pair<SimpleWeatherWord, Boolean?>>()

    if ((forecast as WeatherDataState.SuccessHourly).data.first().skyInfo.shortwaveRadiation != null) {
        for (index in 0..23) {
            simpleWeatherList.add(
                Pair(
                    getSimpleWeather((forecast as WeatherDataState.SuccessHourly).data[index]).word,
                    (forecast as WeatherDataState.SuccessHourly).data[index].skyInfo.shortwaveRadiation!! >= 1.0
                )
            )
        }
    } else {
        for (index in 0..23) {
            simpleWeatherList.add(Pair(getSimpleWeather((forecast as WeatherDataState.SuccessHourly).data[index]).word, null))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState), // ScrollState partagé
    ) {
        Row(
            modifier = Modifier.width(1000.dp), // Largeur fixe, identique à GenericGraph
            horizontalArrangement = Arrangement.SpaceAround, // L'arrangement gère l'espacement
            verticalAlignment = Alignment.CenterVertically
        ) {
            simpleWeatherList.forEach { (weatherWord, isDay) ->
                val fileName = when (weatherWord) {
                    SimpleWeatherWord.SUNNY -> if (isDay != null) if (isDay) sunnyDayIconPath else sunnyNightIconPath else sunnyDayIconPath
                    SimpleWeatherWord.SUNNY_CLOUDY -> if (isDay != null) if (isDay) sunnyCloudyDayIconPath else sunnyCloudyNightIconPath else sunnyCloudyIconPath
                    SimpleWeatherWord.CLOUDY -> cloudyIconPath
                    SimpleWeatherWord.FOGGY -> foggyIconPath
                    SimpleWeatherWord.DUST -> dustIconPath
                    SimpleWeatherWord.DRIZZLY -> if (isDay != null) if (isDay) drizzleDayIconPath else drizzleNightIconPath else drizzleIconPath
                    SimpleWeatherWord.RAINY1 -> if (isDay != null) if (isDay) rainy1DayIconPath else rainy1NightIconPath else rainy1IconPath
                    SimpleWeatherWord.RAINY2 -> if (isDay != null) if (isDay) rainy2DayIconPath else rainy2NightIconPath else rainy2IconPath
                    SimpleWeatherWord.HAIL -> hailIconPath
                    SimpleWeatherWord.SNOWY1 -> snowy1IconPath
                    SimpleWeatherWord.SNOWY2 -> snowy2IconPath
                    SimpleWeatherWord.SNOWY3 -> snowy3IconPath
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