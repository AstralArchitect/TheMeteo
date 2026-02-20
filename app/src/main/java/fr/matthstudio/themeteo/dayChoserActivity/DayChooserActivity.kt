package fr.matthstudio.themeteo.dayChoserActivity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import fr.matthstudio.themeteo.DailyReading
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.dayGraphsActivity.DayGraphsActivity
import fr.matthstudio.themeteo.forecastMainActivity.AddLocationDialog
import fr.matthstudio.themeteo.forecastMainActivity.ForecastMainActivity
import fr.matthstudio.themeteo.forecastMainActivity.ForecastMainActivityScreen
import fr.matthstudio.themeteo.forecastMainActivity.LocationManagementSheet
import fr.matthstudio.themeteo.forecastMainActivity.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.utilsActivities.SettingsActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

fun getWeatherIconPath(word: SimpleWeatherWord): String {
    val folder = "file:///android_asset/icons/weather/"
    return folder + when (word) {
        SimpleWeatherWord.SUNNY -> "clear-day.svg"
        SimpleWeatherWord.SUNNY_CLOUDY -> "cloudy-1-day.svg"
        SimpleWeatherWord.CLOUDY -> "cloudy.svg"
        SimpleWeatherWord.FOGGY -> "fog.svg"
        SimpleWeatherWord.HAZE -> "haze.svg"
        SimpleWeatherWord.DUST -> "dust.svg"
        SimpleWeatherWord.DRIZZLY -> "rainy-1.svg"
        SimpleWeatherWord.RAINY1 -> "rainy-2.svg"
        SimpleWeatherWord.RAINY2 -> "rainy-3.svg"
        SimpleWeatherWord.HAIL -> "hail.svg"
        SimpleWeatherWord.SNOWY1 -> "snowy-1.svg"
        SimpleWeatherWord.SNOWY2 -> "snowy-2.svg"
        SimpleWeatherWord.SNOWY3 -> "snowy-3.svg"
        SimpleWeatherWord.SNOWY_MIX -> "rain-and-snow-mix.svg"
        SimpleWeatherWord.STORMY -> "thunderstorms.svg"
    }
}

@Composable
fun AnimatedSvgIcon(iconPath: String, modifier: Modifier = Modifier) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Filtre CSS pour adapter les couleurs au thème clair
    val filterStyle = if (!isDark) {
        "filter: brightness(0.9) saturate(1.1) drop-shadow(0px 0px 1px rgba(0,0,0,0.2));"
    } else ""

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                object : WebView(context) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        return false
                    }
                }.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    setBackgroundColor(0)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.loadWithOverviewMode = false
                    settings.useWideViewPort = false
                    
                    // Désactiver TOUTES les interactions pour laisser passer le clic au parent
                    isEnabled = false
                    isClickable = false
                    isLongClickable = false
                    isFocusable = false
                    isFocusableInTouchMode = false
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            * { 
                                pointer-events: none !important; 
                                -webkit-tap-highlight-color: transparent;
                                user-select: none;
                            }
                            html, body { 
                                margin: 0; padding: 0; width: 100%; height: 100%; 
                                overflow: hidden; background: transparent; 
                                display: flex; align-items: center; justify-content: center;
                            }
                            img { 
                                width: 100%; height: 100%; 
                                object-fit: contain; 
                                $filterStyle
                            }
                        </style>
                    </head>
                    <body>
                        <img src="$iconPath">
                    </body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            }
        )
        // Pas besoin d'overlay si la WebView est bien inerte
    }
}

val weatherModelPredictionTime = mapOf(
    "best_match" to 15,
    "ecmwf_ifs" to 14,
    "ecmwf_aifs025_single" to 14,
    "meteofrance_seamless" to 3,
    "gfs_seamless" to 15,
    "icon_seamless" to 6,
    "gem_seamless" to 9,
    "ukmo_seamless" to 5,
)

class DayChooserActivity : ComponentActivity() {

    private lateinit var weatherViewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isLauncherActivity = intent.getBooleanExtra("LAUNCHER", false)

        // Instancier le viewModel
        val app = (this.application as TheMeteo)
        weatherViewModel = WeatherViewModel(app.weatherCache, app.container.telemetryManager)
        enableEdgeToEdge()
        setContent {
            TheMeteoTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DayChooser(weatherViewModel = weatherViewModel, isLauncherActivity)
                }
            }
        }
    }
}

/**
 * A Composable function that displays a list of Cards, each showing the date,
 * day of the week, and max/min temperature for the next days.
 *
 * @param weatherViewModel The ViewModel to observe for daily forecast data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayChooser(weatherViewModel: WeatherViewModel, isLauncherActivity: Boolean) {
    val context = LocalContext.current
    // Collect the daily forecast and loading state from the ViewModel
    val dailyForecast by weatherViewModel.forecast.collectAsState()

    // --- GESTION DE L'ÉTAT DE L'UI ---
    val selectedLocation by weatherViewModel.selectedLocation.collectAsState()
    var showLocationSheet by remember { mutableStateOf(false) }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // --- AFFICHAGE CONDITIONNEL DES PANNEAUX ---
    if (showLocationSheet) {
        val savedLocations by weatherViewModel.savedLocations.collectAsState()
        val selectedLocation by weatherViewModel.selectedLocation.collectAsState()
        val currentWeathers by weatherViewModel.currentWeather.collectAsState()
        val defaultLocation = weatherViewModel.defaultLocation

        LocationManagementSheet(
            savedLocations = savedLocations,
            selectedLocation = selectedLocation,
            currentWeathers = currentWeathers,
            defaultLocation = defaultLocation,
            onSelectLocation = { weatherViewModel.selectLocation(it) },
            onRemoveLocation = { weatherViewModel.removeLocation(it) },
            onSetDefaultLocation = { weatherViewModel.setDefaultLocation(it) },
            onDismiss = { showLocationSheet = false },
            onAddLocationClick = {
                showLocationSheet = false // Ferme le premier panneau
                showAddLocationDialog = true // Ouvre le second
            },
            sheetState = sheetState
        )
    }

    if (showAddLocationDialog) {
        val searchResults by weatherViewModel.geocodingResults.collectAsState()
        val userLocation by weatherViewModel.userLocation.collectAsState()

        AddLocationDialog(
            searchResults = searchResults,
            userLocation = userLocation,
            onSearch = { weatherViewModel.searchCity(it) },
            onLocationSelected = { weatherViewModel.selectLocation(it) },
            onAddLocation = { weatherViewModel.addLocation(it) },
            onMapLocationAdded = { gpsCoordinates, name ->
                weatherViewModel.addLocationFromMap(gpsCoordinates, name)
            },
            onDismiss = { showAddLocationDialog = false },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight.toFloat()
        val dynamicBlur by remember {
            derivedStateOf {
                if (showLocationSheet) {
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
                } else 0.dp
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = 16.dp)
                .blur(dynamicBlur),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLauncherActivity) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
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

                Row(
                    modifier = Modifier
                        .clickable { showLocationSheet = true }, // OUVRE LE PANNEAU
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Current location icon",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    // Affiche le nom du lieu actuellement sélectionné
                    Text(
                        text = when (val loc = selectedLocation) {
                            is LocationIdentifier.CurrentUserLocation -> stringResource(R.string.current_location)
                            is LocationIdentifier.Saved -> loc.location.name
                        },
                        style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Change location icon",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.next_days_temperature_forecast,
                    (dailyForecast as? WeatherDataState.SuccessDaily)?.data?.size ?: 0
                ),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            if (dailyForecast == WeatherDataState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (dailyForecast is WeatherDataState.Error || (dailyForecast as? WeatherDataState.SuccessDaily)?.data?.isEmpty() == true) {
                Text("Données non disponibles", modifier = Modifier.padding(16.dp))
            } else {
                val data = (dailyForecast as WeatherDataState.SuccessDaily).data

                // Calculate absolute min and max temperatures for the entire forecast period
                val minOfAll = data.minOfOrNull { it.minTemperature } ?: 0.0
                val maxOfAll = data.maxOfOrNull { it.maxTemperature } ?: 0.0

                val chunkedData = data.chunked(2)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLauncherActivity) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Column(
                                    modifier = Modifier
                                        .clickable {
                                            val intent = Intent(
                                                context,
                                                ForecastMainActivity::class.java
                                            ).apply {
                                                putExtra("LAUNCHER", false)
                                            }
                                            context.startActivity(intent)
                                        }
                                        .padding(8.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // Icon first and bigger as requested
                                    Icon(
                                        imageVector = Icons.Rounded.AccessTime,
                                        contentDescription = "Icône météo",
                                        modifier = Modifier
                                            .size(56.dp)
                                            .padding(4.dp),
                                        tint = Color.White
                                    )

                                    val dateFormatter = DateTimeFormatter.ofPattern("dd MMM")
                                    Text(
                                        text = stringResource(R.string.currently),
                                        style = MaterialTheme.typography.labelLarge
                                    )

                                    Text(
                                        text = stringResource(R.string.goes_to_current_weather_page),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    items(chunkedData) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { dayReading ->
                                Box(modifier = Modifier.weight(1f)) {
                                    SingleDailyForecastCard(
                                        dayReading = dayReading,
                                        viewModel = weatherViewModel,
                                        minOfAll = minOfAll,
                                        maxOfAll = maxOfAll
                                    )
                                }
                            }
                            // Add an empty space if the row has only one item to maintain alignment
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A Composable function to display a single daily forecast in a Card.
 */
@Composable
fun SingleDailyForecastCard(
    dayReading: DailyReading,
    viewModel: WeatherViewModel,
    minOfAll: Double,
    maxOfAll: Double
) {
    val context = LocalContext.current

    // Charger les icônes
    val iconWeatherFolder = "file:///android_asset/icons/weather/"
    val sunnyDayIconPath: String = iconWeatherFolder + "clear-day.svg"
    val sunnyCloudyDayIconPath: String = iconWeatherFolder + "cloudy-3-day.svg"
    val cloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val foggyIconPath: String = iconWeatherFolder + "fog.svg"
    val hazeIconPath: String = iconWeatherFolder + "haze.svg"
    val dustIconPath: String = iconWeatherFolder + "dust.svg"
    val drizzleIconPath: String = iconWeatherFolder + "rainy-1.svg"
    val rainy1IconPath: String = iconWeatherFolder + "rainy-2.svg"
    val rainy2IconPath: String = iconWeatherFolder + "rainy-3.svg"
    val hailIconPath: String = iconWeatherFolder + "hail.svg"
    val snowy1IconPath: String = iconWeatherFolder + "snowy-1.svg"
    val snowy2IconPath: String = iconWeatherFolder + "snowy-2.svg"
    val snowy3IconPath: String = iconWeatherFolder + "snowy-3.svg"
    val snowyMixIconPath: String = iconWeatherFolder + "rain-and-snow-mix.svg"
    val stormyIconPath: String = iconWeatherFolder + "thunderstorms.svg"

    val weatherWord = weatherCodeToSimpleWord(dayReading.wmo)
    val fileName = when (weatherWord) {
        SimpleWeatherWord.SUNNY -> sunnyDayIconPath
        SimpleWeatherWord.SUNNY_CLOUDY -> sunnyCloudyDayIconPath
        SimpleWeatherWord.CLOUDY -> cloudyIconPath
        SimpleWeatherWord.FOGGY -> foggyIconPath
        SimpleWeatherWord.HAZE -> hazeIconPath
        SimpleWeatherWord.DUST -> dustIconPath
        SimpleWeatherWord.DRIZZLY -> drizzleIconPath
        SimpleWeatherWord.RAINY1 -> rainy1IconPath
        SimpleWeatherWord.RAINY2 -> rainy2IconPath
        SimpleWeatherWord.HAIL -> hailIconPath
        SimpleWeatherWord.SNOWY1 -> snowy1IconPath
        SimpleWeatherWord.SNOWY2 -> snowy2IconPath
        SimpleWeatherWord.SNOWY3 -> snowy3IconPath
        SimpleWeatherWord.SNOWY_MIX -> snowyMixIconPath
        SimpleWeatherWord.STORMY -> stormyIconPath
    }

    val maxTemp = dayReading.maxTemperature.roundToInt()
    val minTemp = dayReading.minTemperature.roundToInt()
    val totalPrecipitation = dayReading.precipitation

    // Build annotated string for bold and colored temperatures and precipitation
    val tempText = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                fontWeight = FontWeight.Bold,
                color = if (dayReading.maxTemperature == maxOfAll) Color.Red else Color.Unspecified
            )
        ) {
            append("$maxTemp°")
        }
        append(" / ")
        withStyle(
            style = SpanStyle(
                fontWeight = FontWeight.Bold,
                color = if (dayReading.minTemperature == minOfAll) Color(0xFF2196F3) else Color.Unspecified
            )
        ) {
            append("$minTemp°")
        }
    }
    val precipitationText = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                fontWeight = FontWeight.Bold
            )
        ) {
            append("$totalPrecipitation mm")
        }
    }
    val windText = buildAnnotatedString {
        withStyle(
            style = SpanStyle(
                fontWeight = FontWeight.Bold
            )
        ) {
            append("${dayReading.maxWind.windGusts} kph")
        }
    }


    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val weatherIconFilter = remember(isDark) {
            if (!isDark) {
                ColorFilter.colorMatrix(ColorMatrix().apply {
                    setToScale(0.9f, 0.9f, 0.9f, 1f)
                })
            } else null
        }

        Column(
            modifier = Modifier
                .clickable {
                    val intent = Intent(context, DayGraphsActivity::class.java).apply {
                        putExtra("START_DATE_TIME", dayReading.date.atTime(0, 0))
                        putExtra("SELECTED_LOCATION", viewModel.selectedLocation.value)
                    }
                    context.startActivity(intent)
                }
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // First, display the full date
            val dateFormatter = DateTimeFormatter.ofPattern("dd MMM")
            Text(
                text = dayReading.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + dayReading.date.format(dateFormatter),
                style = MaterialTheme.typography.labelLarge
            )

            // The image, the temperatures and the precipitation are displayed in a row
            Row (
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val userSettings by viewModel.userSettings.collectAsState()
                val isBatterySaverActive by (LocalContext.current.applicationContext as TheMeteo).weatherCache.isBatterySaverActive.collectAsState()
                
                if (userSettings.enableAnimatedIcons && !isBatterySaverActive) {
                    AnimatedSvgIcon(
                        iconPath = fileName,
                        modifier = Modifier
                            .size(85.dp)
                            .padding(bottom = 4.dp)
                    )
                } else {
                    AsyncImage(
                        model = fileName,
                        contentDescription = "Icône météo",
                        modifier = Modifier
                            .size(85.dp)
                            .padding(bottom = 4.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = weatherIconFilter
                    )
                }

                Column (
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = tempText,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = precipitationText,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = windText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}