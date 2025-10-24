package fr.matthstudio.themeteo.forecastViewer

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.GridLines
import co.yml.charts.ui.linechart.model.IntersectionPoint
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle
import co.yml.charts.ui.linechart.model.LineType
import co.yml.charts.ui.linechart.model.SelectionHighlightPoint
import co.yml.charts.ui.linechart.model.SelectionHighlightPopUp
import co.yml.charts.ui.linechart.model.ShadowUnderLine
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.data.WeatherViewModelFactory
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
            intent.getParcelableExtra("START_DATE", LocalDateTime::class.java)
        } else {
            // Pour les versions plus anciennes, on utilise l'ancienne méthode (dépréciée).
            // L'annotation @Suppress évite l'avertissement du compilateur.
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("START_DATE") as? LocalDateTime
        }

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
            .padding(start = 16.dp, end = 16.dp)
            .verticalScroll(rememberScrollState()), // Pour que ça puisse défiler si le contenu est grand
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

        // On affiche un indicateur de chargement pendant la récupération des données
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 50.dp))
        } else if (
            viewModel.temperatureForecast.collectAsState().value.isNotEmpty() &&
            viewModel.apparentTemperatureForecast.collectAsState().value.isNotEmpty() &&
            viewModel.dewpointForecast.collectAsState().value.isNotEmpty() &&
            viewModel.precipitationProbabilityForecast.collectAsState().value.isNotEmpty() &&
            viewModel.precipitationForecast.collectAsState().value.isNotEmpty() &&
            viewModel.windspeedForecast.collectAsState().value.isNotEmpty() &&
            viewModel.pressureForecast.collectAsState().value.isNotEmpty() &&
            viewModel.humidityForecast.collectAsState().value.isNotEmpty()
            ) {
            // Le graphique ne s'affiche QUE si les données sont prêtes
            Text(stringResource(R.string.temperature))
            GenericGraph(viewModel, GraphType.TEMP, Color(0xFFFFD54F))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.apparent_temperature))
            GenericGraph(viewModel, GraphType.A_TEMP, Color(0xFFFFF176))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.dew_point))
            GenericGraph(viewModel, GraphType.DEW_POINT, Color(0xFFFF8A65))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.humidity))
            GenericGraph(viewModel, GraphType.HUMIDITY, Color(0xFF4DD0E1))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.rain))
            GenericGraph(viewModel, GraphType.RAIN_RATE, Color(0xFF64B5F6))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.rain_prob))
            GenericGraph(viewModel, GraphType.RAIN_PROB, Color(0xFF64B5F6))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.wind_speed))
            GenericGraph(viewModel, GraphType.WIND_SPEED, Color(0xFFAED581))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.pressure))
            GenericGraph(viewModel, GraphType.PRESSURE, Color(0xFF9575CD))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.cloud_cover))
            GenericGraph(viewModel, GraphType.CLOUD_COVER, Color(0xFF9D9D9D))
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // Message si aucune donnée n'est disponible après le chargement
            Text("Données non disponibles pour ce jour.")
        }
    }
}

enum class GraphType {
    TEMP, A_TEMP, DEW_POINT, RAIN_PROB, RAIN_RATE, WIND_SPEED, WIND_DIRECTION, PRESSURE, HUMIDITY, CLOUD_COVER
}

@Composable
fun GenericGraph(
    viewModel: WeatherViewModel,
    graphType: GraphType,
    color: Color,
    is6hrGraph: Boolean = false,
    min2zero: Boolean = false,
    lineType: LineType = LineType.Straight(),
    sublist: Pair<Int, Int>? = null
) {
    var shortUnit: String
    var longUnit: String
    var forecast: List<Double>
    var times: List<String>
    when (graphType) {
        GraphType.TEMP -> {
            val fullForecast = viewModel.temperatureForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.temperature } } ?: fullForecast.map { it.temperature }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "°"
            longUnit = " °C"
        }
        GraphType.A_TEMP -> {
            val fullForecast = viewModel.apparentTemperatureForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.apparentTemperature } } ?: fullForecast.map { it.apparentTemperature }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "°"
            longUnit = " °C"
        }
        GraphType.DEW_POINT -> {
            val fullForecast = viewModel.dewpointForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.dewpoint } } ?: fullForecast.map { it.dewpoint }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "°"
            longUnit = " °C"
        }
        GraphType.RAIN_PROB -> {
            val fullForecast = viewModel.precipitationProbabilityForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.probability.toDouble() } } ?: fullForecast.map { it.probability.toDouble() }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "%"
            longUnit = "%"
        }
        GraphType.RAIN_RATE -> {
            val fullForecast = viewModel.precipitationForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.precipitation } } ?: fullForecast.map { it.precipitation }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "mm"
            longUnit = "mm"
        }
        GraphType.WIND_SPEED -> {
            val fullForecast = viewModel.windspeedForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.windspeed } } ?: fullForecast.map { it.windspeed }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "kph"
            longUnit = "km/h"
        }
        GraphType.WIND_DIRECTION -> TODO()
        GraphType.PRESSURE -> {
            val fullForecast = viewModel.pressureForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.pressure } } ?: fullForecast.map { it.pressure }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "hPa"
            longUnit = "hPa"
        }
        GraphType.HUMIDITY -> {
            val fullForecast = viewModel.humidityForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.humidity.toDouble() } } ?: fullForecast.map { it.humidity.toDouble() }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "%"
            longUnit = "%"
        }
        GraphType.CLOUD_COVER -> {
            val fullForecast = viewModel.skyInfoForecast.collectAsState().value
            forecast = sublist?.let { fullForecast.subList(it.first, it.second).map { f -> f.cloudcover_total.toDouble() } } ?: fullForecast.map { it.cloudcover_total.toDouble() }
            times = fullForecast.map { it.time.format(DateTimeFormatter.ofPattern("H")) }
            shortUnit = "%"
            longUnit = "%"
        }
    }

    val pointsData = forecast.mapIndexed { index, value ->
        Point(index.toFloat(), value.toFloat())
    }

    val axisColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    val xAxisData = AxisData.Builder()
        .axisStepSize(if (is6hrGraph) 50.dp else 25.dp)
        .backgroundColor(axisColor)
        .axisLabelColor(MaterialTheme.colorScheme.onSurface)
        .steps(pointsData.size - 1)
        .labelData { i ->
            if (i == 0 || i % 2 != 0) "" else times[i] + "h"
        }
        .labelAndAxisLinePadding(15.dp)
        .build()

    val yAxisData = AxisData.Builder()
        .steps(5)
        .backgroundColor(axisColor)
        .axisLabelColor(MaterialTheme.colorScheme.onSurface)
        .labelAndAxisLinePadding(20.dp)
        .labelData { i ->
            val minY = forecast.minOfOrNull { it }?.toFloat() ?: 0f
            //minY = if (min2zero) 0f else dataMinY
            val maxY = forecast.maxOfOrNull { it }?.toFloat() ?: 0f
            val range = maxY - minY
            val value = minY + (range / 5 * i)
            "%.1f".format(value) + shortUnit
        }.build()

    val lineChartData = LineChartData(
        linePlotData = LinePlotData(
            lines = listOf(
                Line(
                    dataPoints = pointsData,
                    lineStyle = LineStyle(
                        color = color,
                        // Rendre la ligne courbe pour un aspect plus naturel
                        lineType = lineType,
                    ),
                    // Rendre les points d'intersection plus visibles mais pas dominants
                    intersectionPoint = IntersectionPoint(
                        color = color,
                        radius = 4.dp
                    ),
                    // Rendre le point de sélection plus visible
                    selectionHighlightPoint = SelectionHighlightPoint(
                        color = MaterialTheme.colorScheme.onSurface, // Couleur contrastante
                        alpha = 0.6f,
                        radius = 6.dp
                    ),
                    // Une ombre douce pour l'aspect "rempli"
                    shadowUnderLine = ShadowUnderLine(
                        color = color.copy(alpha = 0.3f), // Couleur de la ligne avec plus de transparence
                        alpha = 0.5f // Intensité de l'ombre
                    ),
                    selectionHighlightPopUp = SelectionHighlightPopUp(
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        // Personnalisation du label :
                        popUpLabel = { x, y ->
                            // x est l'index, y est la température (float)
                            // Nous voulons juste la température avec l'unité.
                            // Y est déjà la valeur réelle de température de vos pointsData
                            "%.1f".format(y) + longUnit
                        }
                    )
                )
            ),
        ),
        xAxisData = xAxisData,
        yAxisData = yAxisData,
        // Adoucir les lignes de grille
        gridLines = GridLines(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), // Gris très clair et transparent
        ),
        backgroundColor = MaterialTheme.colorScheme.surface, // Garder le fond du thème
    )

    LineChart(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        lineChartData = lineChartData
    )
}