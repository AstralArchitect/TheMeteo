package fr.matthstudio.themeteo.forecastMainActivity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.utilsActivities.SettingsActivity
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.dayChoserActivity.DayChooserActivity
import fr.matthstudio.themeteo.dayGraphsActivity.DayGraphsActivity
import fr.matthstudio.themeteo.dayGraphsActivity.GraphType
import fr.matthstudio.themeteo.satImgs.MapActivity
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherDetailItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val subValue: String? = null
)

data class NextSunEvent(
    val type: String,
    val dateTime: LocalDateTime,
    val dayLabel: String
)

fun getStateIconFromWord(word: SimpleWeatherWord): ImageVector {
    return when (word) {
        SimpleWeatherWord.STORMY -> Icons.Rounded.Thunderstorm
        SimpleWeatherWord.HAIL, SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3 -> Icons.Rounded.AcUnit // Flocon
        SimpleWeatherWord.SNOWY_MIX -> Icons.Rounded.Grain // Pluie + Neige (approximation)
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2 -> Icons.Rounded.Umbrella
        SimpleWeatherWord.DRIZZLY -> Icons.Rounded.WaterDrop // Bruine
        SimpleWeatherWord.DUST -> Icons.Rounded.Public // Vent (approximation poussière)
        SimpleWeatherWord.FOGGY -> Icons.Rounded.Dehaze // Brouillard/Haze
        SimpleWeatherWord.CLOUDY -> Icons.Rounded.Cloud
        SimpleWeatherWord.SUNNY_CLOUDY -> Icons.Rounded.WbCloudy
        SimpleWeatherWord.SUNNY -> Icons.Rounded.WbSunny
    }
}


class ForecastMainActivity : ComponentActivity() {
    private lateinit var weatherViewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isLauncherActivity = intent.getBooleanExtra("LAUNCHER", false)

        // Instancier le viewModel
        weatherViewModel = WeatherViewModel((this.application as TheMeteo).weatherCache)

        // C'est ici que vous appelez votre fonction Composable principale
        enableEdgeToEdge()
        setContent {
            TheMeteoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ForecastMainActivityScreen(weatherViewModel, isLauncherActivity)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        (this.application as TheMeteo).saveCache()
    }
}

@Composable
fun BlurredBackground(state: SimpleWeatherWord) {
    val (baseColor, meshColors) = when (state) {
        SimpleWeatherWord.STORMY -> Color(0xFF1B0033) to listOf(
            Color(0xFF673AB7).copy(alpha = 0.8f),
            Color(0xFF3F51B5).copy(alpha = 0.6f),
            Color(0xFF9C27B0).copy(alpha = 0.5f)
        )
        SimpleWeatherWord.HAIL, SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3, SimpleWeatherWord.SNOWY_MIX -> Color(0xFF37474F) to listOf(
            Color(0xFFCFD8DC).copy(alpha = 0.8f),
            Color(0xFF90A4AE).copy(alpha = 0.6f),
            Color(0xFF607D8B).copy(alpha = 0.5f)
        )
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2, SimpleWeatherWord.DRIZZLY -> Color(0xFF1A237E) to listOf(
            Color(0xFF3949AB).copy(alpha = 0.8f),
            Color(0xFF5C6BC0).copy(alpha = 0.6f),
            Color(0xFF283593).copy(alpha = 0.5f)
        )
        SimpleWeatherWord.DUST -> Color(0xFF8D6E63) to listOf(
            Color(0xFFBCAAA4).copy(alpha = 0.8f),
            Color(0xFFD7CCC8).copy(alpha = 0.6f),
            Color(0xFFA1887F).copy(alpha = 0.5f)
        )
        SimpleWeatherWord.FOGGY, SimpleWeatherWord.CLOUDY -> Color(0xFF455A64) to listOf(
            Color(0xFF90A4AE).copy(alpha = 0.8f),
            Color(0xFFB0BEC5).copy(alpha = 0.6f),
            Color(0xFF78909C).copy(alpha = 0.5f)
        )
        SimpleWeatherWord.SUNNY_CLOUDY, SimpleWeatherWord.SUNNY -> Color(0xFF1565C0) to listOf(
            Color(0xFFFFB74D).copy(alpha = 0.8f),
            Color(0xFFFFD740).copy(alpha = 0.6f),
            Color(0xFF42A5F5).copy(alpha = 0.5f)
        )
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        // Dégradé simple pour les versions anciennes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = meshColors
                    )
                )
        )
    } else {
        // Effet de cercles floutés (Mesh) pour les versions récentes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp)
            ) {
                drawCircle(
                    color = meshColors[0],
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.1f, size.height * 0.2f)
                )
                drawCircle(
                    color = meshColors[1],
                    radius = size.width * 0.7f,
                    center = Offset(size.width * 0.9f, size.height * 0.4f)
                )
                drawCircle(
                    color = meshColors[2],
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.4f, size.height * 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastMainActivityScreen(viewModel: WeatherViewModel, isLauncherActivity: Boolean) {
    val context = LocalContext.current
    val hourlyForecast by viewModel.hourlyForecast.collectAsState()

    // Demander les permissions
    LocationPermissionHandler(viewModel)

    val weatherState = when (viewModel.hourlyForecast.collectAsState().value) {
        is WeatherDataState.SuccessHourly -> getSimpleWeather((viewModel.hourlyForecast.collectAsState().value as WeatherDataState.SuccessHourly).data.first())
        WeatherDataState.Loading -> SimpleWeather("Loading", SimpleWeatherWord.SUNNY)
        else -> SimpleWeather("Error", SimpleWeatherWord.SUNNY)
    }

    val stateIcon = getStateIconFromWord(weatherState.word)

    val description = when(weatherState.word) {
        SimpleWeatherWord.STORMY -> stringResource(R.string.stormy)
        SimpleWeatherWord.HAIL -> stringResource(R.string.hail)
        SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2 -> stringResource(R.string.light_snow)
        SimpleWeatherWord.SNOWY3 -> stringResource(R.string.heavy_snow)
        SimpleWeatherWord.SNOWY_MIX -> stringResource(R.string.mixed_rain_snow)
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2 -> stringResource(R.string.rainy)
        SimpleWeatherWord.DRIZZLY -> stringResource(R.string.drizzle)
        SimpleWeatherWord.DUST -> stringResource(R.string.dust_storm)
        SimpleWeatherWord.FOGGY -> stringResource(R.string.foggy)
        SimpleWeatherWord.CLOUDY -> stringResource(R.string.cloudy)
        SimpleWeatherWord.SUNNY_CLOUDY -> stringResource(R.string.sunny_cloudy)
        SimpleWeatherWord.SUNNY -> stringResource(R.string.clear)
    }

    // --- GESTION DE L'ÉTAT DE L'UI ---
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    var showLocationSheet by remember { mutableStateOf(false) }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val isRefreshing = false


    // --- AFFICHAGE CONDITIONNEL DES PANNEAUX ---
    if (showLocationSheet) {
        val savedLocations by viewModel.savedLocations.collectAsState()
        val selectedLocation by viewModel.selectedLocation.collectAsState()
        val currentWeathers by viewModel.currentWeather.collectAsState()

        LocationManagementSheet(
            savedLocations = savedLocations,
            selectedLocation = selectedLocation,
            currentWeathers = currentWeathers,
            onSelectLocation = { viewModel.selectLocation(it) },
            onRemoveLocation = { viewModel.removeLocation(it) },
            onDismiss = { showLocationSheet = false },
            onAddLocationClick = {
                showLocationSheet = false // Ferme le premier panneau
                showAddLocationDialog = true // Ouvre le second
            },
            sheetState = sheetState
        )
    }

    if (showAddLocationDialog) {
        val searchResults by viewModel.geocodingResults.collectAsState()
        val userLocation by viewModel.userLocation.collectAsState()

        AddLocationDialog(
            searchResults = searchResults,
            userLocation = userLocation,
            onSearch = { viewModel.searchCity(it) },
            onLocationSelected = { viewModel.selectLocation(it) },
            onAddLocation = { viewModel.addLocation(it) },
            onMapLocationAdded = { gpsCoordinates, name ->
                viewModel.addLocationFromMap(gpsCoordinates, name)
            },
            onDismiss = { showAddLocationDialog = false },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        // Calculer le flou dynamiquement en fonction de l'offset de la sheet
        val dynamicBlur by remember {
            derivedStateOf {
                if (!showLocationSheet) {
                    if (showDetailsDialog)
                        10.dp
                    else 0.dp
                } else {
                    // requireOffset() renvoie la position Y du haut de la sheet
                    // Si caché = screenHeightPx, Si ouvert = 0 (ou proche de 0)
                    val offset = try {
                        sheetState.requireOffset()
                    } catch (e: Exception) {
                        screenHeightPx
                    }

                    // On calcule la progression (0.0 = fermé, 1.0 = totalement ouvert)
                    val progress = ((screenHeightPx - offset) / screenHeightPx).coerceIn(0f, 1f)

                    // On multiplie par le rayon de flou max souhaité (ex: 16.dp)
                    (progress * 16).dp
                }
            }
        }

        BlurredBackground(weatherState.word)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.05f))
        )

        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            onRefresh = { viewModel.refresh() },
            isRefreshing = isRefreshing
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // On applique le flou si la sheet est ouverte
                    .blur(dynamicBlur),
                contentPadding = PaddingValues(
                    top = 40.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLauncherActivity) {
                    // Settings button
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            FilledIconButton(
                                onClick = {
                                    val intent = Intent(context, SettingsActivity::class.java)
                                    context.startActivity(intent)
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.6f
                                    ),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                }

                // HERO HEADER + Settings button
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLauncherActivity) {
                            Row(
                                modifier = Modifier
                                    .clickable { showLocationSheet = true }, // OUVRE LE PANNEAU
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Lieu actuel",
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                // Affiche le nom du lieu actuellement sélectionné
                                Text(
                                    text = when (val loc = selectedLocation) {
                                        is LocationIdentifier.CurrentUserLocation -> stringResource(
                                            R.string.current_location
                                        )

                                        is LocationIdentifier.Saved -> loc.location.name
                                    },
                                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White),
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Changer de lieu",
                                    tint = Color.White
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (hourlyForecast !is WeatherDataState.SuccessHourly)
                                return@Row
                            Icon(
                                imageVector = stateIcon,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "${(hourlyForecast as WeatherDataState.SuccessHourly).data.first().temperature.roundToInt()}°",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 104.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White
                            )
                        }
                        Text(
                            text = description,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                // Hourly forecast
                item {
                    if (hourlyForecast is WeatherDataState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                        return@item
                    }
                    if (hourlyForecast is WeatherDataState.Error) {
                        Text(
                            text = (hourlyForecast as WeatherDataState.Error).message,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        return@item
                    }
                    var variable: ChosenVar by remember { mutableStateOf(ChosenVar.TEMPERATURE) }
                    BentoCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(true, onClick = { // Make the card clickable
                                // Create an Intent to launch DayGraphsActivity
                                val intent = Intent(context, DayGraphsActivity::class.java).apply {
                                    putExtra("SELECTED_LOCATION", viewModel.selectedLocation.value)
                                    putExtra("START_DATE_TIME", LocalDateTime.now())
                                }
                                // Start the activity
                                context.startActivity(intent)
                            })
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 8.dp, top = 0.dp)
                        ) {
                            Row (
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.hourly_forecast),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(start = 12.dp, top = 20.dp)
                                )
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(end = 20.dp)
                                )
                            }

                            if ((hourlyForecast as WeatherDataState.SuccessHourly).data.isEmpty())
                                return@BentoCard

                            val scrollState = rememberScrollState()
                            when (variable) {
                                ChosenVar.TEMPERATURE -> GenericGraph(
                                    viewModel,
                                    GraphType.TEMP,
                                    Color(0xFFFFF176),
                                    scrollState = scrollState
                                )

                                ChosenVar.APPARENT_TEMPERATURE -> if ((hourlyForecast as WeatherDataState.SuccessHourly).data.first().apparentTemperature != null)
                                    GenericGraph(
                                        viewModel,
                                        GraphType.A_TEMP,
                                        Color(0xFFFFB300),
                                        scrollState = scrollState
                                    )

                                ChosenVar.PRECIPITATION -> GenericGraph(
                                    viewModel,
                                    GraphType.PRECIPITATION,
                                    Color(0xFF039BE5),
                                    valueRange = 0f..3f,
                                    scrollState = scrollState
                                )

                                ChosenVar.WIND -> GenericGraph(
                                    viewModel,
                                    GraphType.WIND_SPEED,
                                    Color(0xFF7CB342),
                                    scrollState = scrollState
                                )
                            }
                            if (variable != ChosenVar.WIND)
                                WeatherIconGraph(viewModel, scrollState = scrollState)

                            Row(
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ChosenVar.entries.forEach { buttonVariable ->
                                    if (buttonVariable == ChosenVar.APPARENT_TEMPERATURE && (hourlyForecast as WeatherDataState.SuccessHourly).data.first().apparentTemperature == null)
                                        return@forEach
                                    val isSelected = variable == buttonVariable

                                    if (buttonVariable == ChosenVar.PRECIPITATION && (hourlyForecast as WeatherDataState.SuccessHourly).data.maxOf { it.precipitationData.precipitation } == 0.0)
                                        return@forEach

                                    OutlinedButton(
                                        modifier = Modifier
                                            .padding(4.dp),
                                        enabled = !isSelected,
                                        onClick = { variable = buttonVariable }
                                    ) {
                                        Text(
                                            when (buttonVariable) {
                                                ChosenVar.TEMPERATURE -> stringResource(R.string.temperature_unit)
                                                ChosenVar.APPARENT_TEMPERATURE -> stringResource(R.string.a_temperature_unit)
                                                ChosenVar.PRECIPITATION -> stringResource(R.string.precipitation_unit)
                                                ChosenVar.WIND -> stringResource(R.string.wind_speed_unit)
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sun + Details
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val dailyForecast by viewModel.dailyForecast.collectAsState()
                        if (dailyForecast !is WeatherDataState.SuccessDaily)
                            return@item

                        val data = (dailyForecast as WeatherDataState.SuccessDaily).data
                        if (data.isEmpty())
                            return@item

                        // --- Logic for calculating next two sun events ---
                        val now = LocalDateTime.now()

                        // Helper function to convert ISO8601 string (e.g., "2024-05-30T05:58:00+02:00") to LocalDateTime
                        fun String.toEventLocalDateTime(): LocalDateTime? {
                            return try {
                                // Try parsing with timezone offset (OffsetDateTime)
                                OffsetDateTime.parse(this).toLocalDateTime()
                            } catch (e: Exception) {
                                try {
                                    // Fallback: ZonedDateTime (if zone ID is used)
                                    ZonedDateTime.parse(this).toLocalDateTime()
                                } catch (e2: Exception) {
                                    try {
                                        // Fallback: Simple LocalDateTime (if no offset/zone is included)
                                        LocalDateTime.parse(this)
                                    } catch (e3: Exception) {
                                        // Handle parsing error
                                        null
                                    }
                                }
                            }
                        }

                        val allEvents = mutableListOf<NextSunEvent>()

                        // Today's events (Data index 0)
                        val todayReading = data[0]
                        todayReading.sunrise.toEventLocalDateTime()?.let { dt ->
                            allEvents.add(
                                NextSunEvent(
                                    stringResource(R.string.sunrise),
                                    dt,
                                    stringResource(R.string.today)
                                )
                            )
                        }
                        todayReading.sunset.toEventLocalDateTime()?.let { dt ->
                            allEvents.add(
                                NextSunEvent(
                                    stringResource(R.string.sunset),
                                    dt,
                                    stringResource(R.string.today)
                                )
                            )
                        }

                        // Tomorrow's events (Data index 1, if available)
                        val tomorrowReading = data[1]
                        tomorrowReading.sunrise.toEventLocalDateTime()?.let { dt ->
                            allEvents.add(
                                NextSunEvent(
                                    stringResource(R.string.sunrise),
                                    dt,
                                    stringResource(R.string.tomorrow)
                                )
                            )
                        }
                        tomorrowReading.sunset.toEventLocalDateTime()?.let { dt ->
                            allEvents.add(
                                NextSunEvent(
                                    stringResource(R.string.sunset),
                                    dt,
                                    stringResource(R.string.tomorrow)
                                )
                            )
                        }

                        // Filter out past events and sort by time (chronological order)
                        val futureEvents = allEvents
                            .filter { it.dateTime.isAfter(now) }
                            .sortedBy { it.dateTime }
                            .take(2)

                        var text: String
                        if (futureEvents.isEmpty())
                            return@item
                        if (futureEvents.size < 2)
                            return@item

                        if (futureEvents[0].type == stringResource(R.string.sunrise)) {
                            val duration = Duration.between(
                                todayReading.sunset.toEventLocalDateTime(),
                                tomorrowReading.sunrise.toEventLocalDateTime()
                            )
                            text =
                                "${stringResource(R.string.night)} : ${duration.toHours()}h ${duration.toMinutes() % 60}min"
                        } else {
                            val duration = Duration.between(
                                todayReading.sunrise.toEventLocalDateTime(),
                                todayReading.sunset.toEventLocalDateTime()
                            )
                            text =
                                "${stringResource(R.string.day)} : ${duration.toHours()}h ${duration.toMinutes() % 60}min"
                        }

                        // Determine which events to display
                        val displayEvent1 =
                            futureEvents.getOrNull(0) ?: allEvents.getOrNull(0) ?: return@item
                        val displayEvent2 = futureEvents.getOrNull(1)

                        // Nouveau composant pour l'heure du soleil, using the new structure
                        SunriseSunsetCard(
                            modifier = Modifier.weight(1f),
                            event1 = displayEvent1,
                            event2 = displayEvent2,
                            text
                        )

                        // La card regroupée (Détails)
                        SummaryDetailsCard(
                            modifier = Modifier.weight(1f),
                            onClick = { showDetailsDialog = true }
                        )
                    }
                }

                // Daily Boxes
                item {
                    val dailyForecast by viewModel.dailyForecast.collectAsState()
                    BentoCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(true, onClick = {
                                val intent = Intent(context, DayChooserActivity::class.java)
                                context.startActivity(intent)
                            })
                    ) {
                        if (dailyForecast == WeatherDataState.Loading) {
                            Box(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                CircularProgressIndicator()
                            }
                            return@BentoCard
                        }

                        if (dailyForecast is WeatherDataState.Error)
                            return@BentoCard

                        if ((dailyForecast as WeatherDataState.SuccessDaily).data.isEmpty())
                            return@BentoCard

                        var selectedDayReading by remember { mutableStateOf(
                            (dailyForecast as WeatherDataState.SuccessDaily).data[0]
                        ) }
                        var shouldExpand by remember { mutableStateOf(false) }

                        Column {
                            Row (
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.daily_forecast),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(
                                        start = 20.dp,
                                        top = 20.dp
                                    )
                                )
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(end = 20.dp)
                                )
                            }

                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp), // Space between cards
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(
                                    (dailyForecast as? WeatherDataState.SuccessDaily)?.data
                                        ?: emptyList()
                                ) { dayReading ->
                                    DailyWeatherBox(dayReading, viewModel) {
                                        shouldExpand =
                                            if (dayReading == selectedDayReading) !shouldExpand else true
                                        selectedDayReading = dayReading
                                    }
                                }
                            }
                            if (shouldExpand) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(true, onClick = {
                                            // Create an Intent to launch DayGraphsActivity
                                            val intent = Intent(
                                                context,
                                                DayGraphsActivity::class.java
                                            ).apply {
                                                putExtra(
                                                    "START_DATE_TIME",
                                                    selectedDayReading.date.atTime(0, 0)
                                                )
                                                putExtra(
                                                    "SELECTED_LOCATION",
                                                    viewModel.selectedLocation.value
                                                )
                                            }
                                            // Start the activity
                                            context.startActivity(intent)
                                        })
                                ) {
                                    // get the hourly forecast for the selected day
                                    val hourlyForecast by viewModel.getForecastForRange(
                                            selectedDayReading.date.atTime(0, 0),
                                            selectedDayReading.date.plusDays(1).atTime(0, 0)
                                        ).collectAsState(
                                            initial = WeatherDataState.Loading
                                        )
                                    Text(
                                        text = selectedDayReading.date.format(DateTimeFormatter.ofPattern("EEEE dd MMM")),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Row (
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (hourlyForecast is WeatherDataState.SuccessHourly) {
                                            Box (
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = 16.dp)
                                            ) {
                                                Log.w("DEBUG", "${(hourlyForecast as WeatherDataState.SuccessHourly).data.size}")
                                                AdvancedGraph(
                                                    hourlyForecast,
                                                    viewModel.userSettings.collectAsState().value.roundToInt,
                                                    GraphType.TEMP,
                                                    Color(0xFFFFF176),
                                                    contentWidth = 750.dp, contentHeight = 100.dp,
                                                    compactHourFormat = true
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = getStateIconFromWord(weatherCodeToSimpleWord(selectedDayReading.wmo)),
                                            contentDescription = null,
                                            modifier = Modifier.size(100.dp),
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row (
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row {
                                            Icon(
                                                imageVector = Icons.Rounded.Thermostat,
                                                contentDescription = null,
                                            )
                                            Text(
                                                text = "${selectedDayReading.maxTemperature}° / ${selectedDayReading.minTemperature}°",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row {
                                            Icon(
                                                imageVector = Icons.Rounded.WaterDrop,
                                                contentDescription = null,
                                            )
                                            Text(
                                                text = "${selectedDayReading.precipitation} mm",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (selectedDayReading.max_uvIndex != null) {
                                            Row {
                                                Icon(
                                                    imageVector = Icons.Rounded.WbSunny,
                                                    contentDescription = null,
                                                )
                                                Spacer(modifier = Modifier.width(1.dp))
                                                Text(
                                                    text = "${selectedDayReading.max_uvIndex}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(1.dp))
                                                Icon(
                                                    imageVector = Icons.Rounded.Circle,
                                                    contentDescription = null,
                                                    tint = getUVColor(selectedDayReading.max_uvIndex!!)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Other infos
                item {
                    // Informations en bas de page            item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val locationText = when (val loc = selectedLocation) {
                            is LocationIdentifier.CurrentUserLocation -> "Position : ${viewModel.userLocation.collectAsState().value?.latitude}, ${viewModel.userLocation.collectAsState().value?.longitude}"
                            is LocationIdentifier.Saved -> "Position : ${loc.location.latitude}, ${loc.location.longitude}"
                        }

                        Text(
                            text = locationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Text(
                            text = "Source : ${getModelSourceText(viewModel.userSettings.collectAsState().value.model)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val intent = Intent(context, MapActivity::class.java)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Map, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.open_satellite_map))
                        }
                    }
                }
            }

            if (showDetailsDialog) {
                WeatherDetailsDialog(viewModel, onDismiss = { showDetailsDialog = false })
            }
        }
    }
}

@Composable
fun SunriseSunsetCard(modifier: Modifier, event1: NextSunEvent, event2: NextSunEvent?, text: String) {
    // Helper function to format the time string required (HH:mm)
    fun LocalDateTime.formatTime(): String {
        return String.format(Locale.getDefault(), "%02d:%02d", this.hour, this.minute)
    }

    BentoCard(modifier = modifier.height(140.dp)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sun), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            // Next event (Event 1)
            Column {
                val time1 = event1.dateTime.formatTime()
                // If the event is today, only show the type (Lever/Coucher). If it's tomorrow, add the day label.
                val label1 = if (event1.dayLabel == stringResource(R.string.today)) event1.type else "${event1.type} (${event1.dayLabel})"

                Text(
                    "$label1: $time1",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                // Second next event (Event 2)
                event2?.let { event ->
                    val time2 = event.dateTime.formatTime()
                    val label2 = if (event.dayLabel == stringResource(R.string.today)) event.type else "${event.type} (${event.dayLabel})"

                    Text(
                        "$label2: $time2",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryDetailsCard(modifier: Modifier, onClick: () -> Unit) {
    BentoCard(
        modifier = modifier
            .height(140.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.details), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Icons from previous cards to hint content
                Icon(Icons.Rounded.Air, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Icon(Icons.Rounded.WaterDrop, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                Icon(Icons.Rounded.Compress, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Text(stringResource(R.string.show_more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun WeatherDetailsDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    val actualReading =
        (viewModel.hourlyForecast.collectAsState().value as? WeatherDataState.SuccessHourly)?.data?.first()
            ?: return

    // Créer un état pour gérer l'animation de visibilité
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    // Fonction de fermeture qui attend la fin de l'animation
    val scope = rememberCoroutineScope()
    fun animateAndDismiss() {
        visibleState.targetState = false
        onDismiss()
    }

    val details = listOfNotNull(
        WeatherDetailItem(Icons.Rounded.Thermostat, stringResource(R.string.temperature), "${actualReading.temperature}°"),
        actualReading.apparentTemperature?.let {
            WeatherDetailItem(Icons.Rounded.DeviceThermostat, stringResource(R.string.apparent_temperature), "$it°")
        },
        actualReading.skyInfo.uvIndex?.let { uv ->
            WeatherDetailItem(
                Icons.Rounded.WbSunny,
                stringResource(R.string.uv_index),
                "$uv",
                getUVDescription(uv)
            )
        },
        WeatherDetailItem(Icons.Rounded.Water, stringResource(R.string.dew_point), "${actualReading.dewpoint}°"),
        WeatherDetailItem(Icons.Rounded.WaterDrop, stringResource(R.string.humidity), "${actualReading.humidity}%"),
        WeatherDetailItem(
            Icons.Rounded.Umbrella,
            stringResource(R.string.precipitation),
            "${actualReading.precipitationData.precipitation} mm",
            actualReading.precipitationData.precipitationProbability?.let { "Proba: $it% | ${stringResource(R.string.rain)}: ${actualReading.precipitationData.rain}mm" } ?: "${stringResource(R.string.rain)}: ${actualReading.precipitationData.rain}mm"
        ),
        WeatherDetailItem(Icons.Rounded.Air, stringResource(R.string.wind_speed), "${actualReading.windspeed} km/h", "Direction : ${actualReading.windDirection}°"),
        WeatherDetailItem(Icons.Rounded.Compress, stringResource(R.string.pressure), "${actualReading.pressure} hPa"),
        WeatherDetailItem(Icons.Rounded.Cloud, stringResource(R.string.cloud_cover), "${actualReading.skyInfo.cloudcoverTotal}%", "Low : ${actualReading.skyInfo.cloudcoverLow} | Mid : ${actualReading.skyInfo.cloudcoverMid} | High : ${actualReading.skyInfo.cloudcoverHigh}"),
        actualReading.skyInfo.opacity?.let { op ->
            WeatherDetailItem(Icons.Rounded.Opacity, stringResource(R.string.opacity), "$op%", "Radiation: ${actualReading.skyInfo.shortwaveRadiation?.roundToInt()} W/m²")
        },
        actualReading.skyInfo.visibility?.let { vis ->
            val visibility = if (vis < 1000) vis else (vis.toDouble() / 1000.0).roundToInt()
            val unit = if (vis >= 1000) "km" else "m"
            WeatherDetailItem(Icons.Rounded.Visibility, stringResource(R.string.visibility), "$visibility $unit")
        }
    )

    // 1. LE SCRIM (Voile de fond)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Color.Transparent else Color.Black.copy(
                    alpha = 0.6f
                )
            )
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // Utiliser AnimatedVisibility pour le contenu
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn() + scaleIn(initialScale = 0.8f), // Zoom progressif
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            // 2. LE CONTENU DU DIALOGUE (Animation de zoom)
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .clickable(enabled = false) { } // Empêche de fermer en cliquant sur le blanc
                    .animateEnterExit(
                        enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                        exit = scaleOut(targetScale = 0.8f) + fadeOut()
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface, // Couleur Material You pour le Dialog
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            stringResource(R.string.weather_details),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(details) { detail ->
                                DetailRow(detail)
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                        alpha = 0.5f
                                    )
                                )
                            }
                        }

                        TextButton(
                            onClick = { animateAndDismiss() },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 16.dp)
                        ) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(item: WeatherDetailItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            item.subValue?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
        Text(
            item.value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BentoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        content()
    }
}