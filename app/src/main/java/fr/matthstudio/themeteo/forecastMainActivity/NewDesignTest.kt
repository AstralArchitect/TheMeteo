package fr.matthstudio.themeteo.forecastMainActivity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.SettingsActivity
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.dayChoserActivity.DayChooser
import fr.matthstudio.themeteo.dayChoserActivity.DayChooserActivity
import fr.matthstudio.themeteo.dayGraphsActivity.DayGraphsActivity
import fr.matthstudio.themeteo.dayGraphsActivity.GraphType
import fr.matthstudio.themeteo.satImgs.MapActivity
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

data class WeatherDetailItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val subValue: String? = null
)

data class NextSunEvent(
    val type: String, // "Lever" or "Coucher"
    val dateTime: LocalDateTime,
    val dayLabel: String // "Aujourd'hui" or "Demain"
)

class NewDesignActivity : ComponentActivity() {
    private lateinit var weatherViewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    ForecastMainActivityScreen(weatherViewModel)
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
        SimpleWeatherWord.STORMY -> Color(0xFF1B0033) to listOf( // Violet très sombre
            Color(0xFF673AB7).copy(alpha = 0.8f), // Violet
            Color(0xFF3F51B5).copy(alpha = 0.6f), // Indigo
            Color(0xFF9C27B0).copy(alpha = 0.5f) // Magenta
        )
        SimpleWeatherWord.HAIL, SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3, SimpleWeatherWord.SNOWY_MIX -> Color(0xFF37474F) to listOf( // Bleu-Gris
            Color(0xFFCFD8DC).copy(alpha = 0.8f), // Très clair
            Color(0xFF90A4AE).copy(alpha = 0.6f), // Gris doux
            Color(0xFF607D8B).copy(alpha = 0.5f) // Bleu ardoise
        )
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2, SimpleWeatherWord.DRIZZLY -> Color(0xFF1A237E) to listOf( // Bleu marine
            Color(0xFF3949AB).copy(alpha = 0.8f),
            Color(0xFF5C6BC0).copy(alpha = 0.6f),
            Color(0xFF283593).copy(alpha = 0.5f)
        )
        SimpleWeatherWord.DUST -> Color(0xFF8D6E63) to listOf( // Brun
            Color(0xFFBCAAA4).copy(alpha = 0.8f), // Brun clair
            Color(0xFFD7CCC8).copy(alpha = 0.6f), // Blanc cassé
            Color(0xFFA1887F).copy(alpha = 0.5f) // Brun moyen
        )
        SimpleWeatherWord.FOGGY, SimpleWeatherWord.CLOUDY -> Color(0xFF455A64) to listOf( // Gris
            Color(0xFF90A4AE).copy(alpha = 0.8f),
            Color(0xFFB0BEC5).copy(alpha = 0.6f),
            Color(0xFF78909C).copy(alpha = 0.5f)
        )
        SimpleWeatherWord.SUNNY_CLOUDY, SimpleWeatherWord.SUNNY -> Color(0xFF1565C0) to listOf( // Bleu ciel
            Color(0xFFFFB74D).copy(alpha = 0.8f), // Soleil
            Color(0xFFFFD740).copy(alpha = 0.6f),
            Color(0xFF42A5F5).copy(alpha = 0.5f)
        )
    }

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
                center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.4f)
            )
            drawCircle(
                color = meshColors[2],
                radius = size.width * 0.8f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.4f, size.height * 0.8f)
            )
        }
    }
}

@Composable
fun ForecastMainActivityScreen(viewModel: WeatherViewModel) {
    val context = LocalContext.current
    var showDetailsDialog by remember { mutableStateOf(false) }
    val hourlyForecast by viewModel.hourlyForecast.collectAsState()

    // Demander les permissions
    LocationPermissionHandler(viewModel)

    val weatherState = when (viewModel.hourlyForecast.collectAsState().value) {
        is WeatherDataState.SuccessHourly -> getSimpleWeather((viewModel.hourlyForecast.collectAsState().value as WeatherDataState.SuccessHourly).data.first())
        WeatherDataState.Loading -> SimpleWeather("Loading", SimpleWeatherWord.SUNNY)
        else -> SimpleWeather("Error", SimpleWeatherWord.SUNNY)
    }

    val stateIcon = when (weatherState.word) {
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

    val description = when(weatherState.word) {
        SimpleWeatherWord.STORMY -> "Orageux"
        SimpleWeatherWord.HAIL -> "Grêle"
        SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2 -> "Neige légère"
        SimpleWeatherWord.SNOWY3 -> "Neige forte"
        SimpleWeatherWord.SNOWY_MIX -> "Pluie et Neige mêlées"
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2 -> "Pluvieux"
        SimpleWeatherWord.DRIZZLY -> "Bruine"
        SimpleWeatherWord.DUST -> "Tempête de poussière"
        SimpleWeatherWord.FOGGY -> "Brouillard épais"
        SimpleWeatherWord.CLOUDY -> "Nuageux"
        SimpleWeatherWord.SUNNY_CLOUDY -> "Partiellement Nuageux"
        SimpleWeatherWord.SUNNY -> "Ensoleillé"
    }

    // --- GESTION DE L'ÉTAT DE L'UI ---
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    var showLocationSheet by remember { mutableStateOf(false) }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showCloudInfoDialog by remember { mutableStateOf(false) }

    // --- AFFICHAGE CONDITIONNEL DES PANNEAUX ---
    if (showLocationSheet) {
        LocationManagementSheet(
            viewModel = viewModel,
            onDismiss = { showLocationSheet = false },
            onAddLocationClick = {
                showLocationSheet = false // Ferme le premier panneau
                showAddLocationDialog = true // Ouvre le second
            }
        )
    }

    if (showAddLocationDialog) {
        AddLocationDialog(
            viewModel = viewModel,
            onDismiss = { showAddLocationDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BlurredBackground(weatherState.word)
        
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.05f)))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Settings button
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    FilledIconButton(
                        onClick = {
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
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
                                is LocationIdentifier.CurrentUserLocation -> "Position Actuelle"
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
                BentoCard(modifier = Modifier
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
                    Column (
                        modifier = Modifier.padding(start = 8.dp, end= 16.dp, top = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hourly_forecast),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(start = 12.dp, top = 20.dp)
                        )

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
                        allEvents.add(NextSunEvent("Lever", dt, "Aujourd'hui"))
                    }
                    todayReading.sunset.toEventLocalDateTime()?.let { dt ->
                        allEvents.add(NextSunEvent("Coucher", dt, "Aujourd'hui"))
                    }

                    // Tomorrow's events (Data index 1, if available)
                    if (data.size >= 2) {
                        val tomorrowReading = data[1]
                        tomorrowReading.sunrise.toEventLocalDateTime()?.let { dt ->
                            allEvents.add(NextSunEvent("Lever", dt, "Demain"))
                        }
                        tomorrowReading.sunset.toEventLocalDateTime()?.let { dt ->
                            allEvents.add(NextSunEvent("Coucher", dt, "Demain"))
                        }
                    }

                    // Filter out past events and sort by time (chronological order)
                    val futureEvents = allEvents
                        .filter { it.dateTime.isAfter(now) }
                        .sortedBy { it.dateTime }
                        .take(2)

                    // Determine which events to display
                    val displayEvent1 = futureEvents.getOrNull(0) ?: allEvents.getOrNull(0) ?: return@item
                    val displayEvent2 = futureEvents.getOrNull(1)

                    // Nouveau composant pour l'heure du soleil, using the new structure
                    SunriseSunsetCard(
                        modifier = Modifier.weight(1f),
                        event1 = displayEvent1,
                        event2 = displayEvent2
                    )

                    // La card regroupée (Détails)
                    SummaryDetailsCard(
                        modifier = Modifier.weight(1f),
                        onClick = { showDetailsDialog = true }
                    )
                }
            }

            // Daily Graph
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

                    Column {
                        Text(
                            text = stringResource(R.string.daily_forecast),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                top = 20.dp
                            )
                        )

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
                                DailyWeatherBox(dayReading, viewModel)
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
                        text = "Source : ${viewModel.userSettings.collectAsState().value.model}",
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
                        Text("Ouvrir la carte")
                    }
                }
            }
        }

        if (showDetailsDialog) {
            WeatherDetailsDialog(viewModel, onDismiss = { showDetailsDialog = false })
        }
    }
}

@Composable
fun SunriseSunsetCard(modifier: Modifier, event1: NextSunEvent, event2: NextSunEvent?) {
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
                Text("Soleil", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Next event (Event 1)
            Column {
                val time1 = event1.dateTime.formatTime()
                // If the event is today, only show the type (Lever/Coucher). If it's tomorrow, add the day label.
                val label1 = if (event1.dayLabel == "Aujourd'hui") event1.type else "${event1.type} (${event1.dayLabel})"

                Text(
                    "$label1: $time1",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                // Second next event (Event 2)
                event2?.let { event ->
                    val time2 = event.dateTime.formatTime()
                    val label2 = if (event.dayLabel == "Aujourd'hui") event.type else "${event.type} (${event.dayLabel})"

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
    val actualReading = (viewModel.hourlyForecast.collectAsState().value as WeatherDataState.SuccessHourly).data.first()
    // Note: J'ai fusionné les détails "Précipitations" pour une meilleure présentation
    val precipitationSubValue = "Proba: ${actualReading.precipitationData.precipitationProbability}% | Pluie: ${actualReading.precipitationData.rain}mm" // Exemple avec pluie

    val details = listOf(
        WeatherDetailItem(Icons.Rounded.Thermostat, "Température", "${actualReading.temperature}°"),
        WeatherDetailItem(Icons.Rounded.DeviceThermostat, "Ressentie", "${actualReading.apparentTemperature}°"),
        if (actualReading.skyInfo.uvIndex != null)
        {
            WeatherDetailItem(Icons.Rounded.WbSunny, "Indice UV", "${actualReading.skyInfo.uvIndex}", getUVDescription(actualReading.skyInfo.uvIndex))
        } else {

        },
        WeatherDetailItem(Icons.Rounded.Water, "Point de rosée", "${actualReading.dewpoint}°"),
        WeatherDetailItem(Icons.Rounded.WaterDrop, "Humidité", "${actualReading.humidity}%"),
        WeatherDetailItem(Icons.Rounded.Umbrella, "Précipitations", "${actualReading.precipitationData.precipitation} mm", precipitationSubValue),
        WeatherDetailItem(Icons.Rounded.Air, "Vent", "${actualReading.windspeed} km/h", "Direction : ${actualReading.windDirection}°"),
        WeatherDetailItem(Icons.Rounded.Compress, "Pression", "${actualReading.pressure} hPa"),
        WeatherDetailItem(Icons.Rounded.Cloud, "Couverture nuageuse", "${actualReading.skyInfo.cloudcoverTotal}%", "Low : ${actualReading.skyInfo.cloudcoverLow} | Mid : ${actualReading.skyInfo.cloudcoverMid} | High : ${actualReading.skyInfo.cloudcoverHigh}"),
        WeatherDetailItem(Icons.Rounded.LightMode, "Opacité (Radiation)", "${actualReading.skyInfo.opacity}", "Diffuse/Totale")
    )

    Dialog(onDismissRequest = onDismiss) {
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
                    "Détails Météo",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(details) { detail ->
                        DetailRow(detail as WeatherDetailItem)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                
                TextButton(
                    onClick = onDismiss,
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