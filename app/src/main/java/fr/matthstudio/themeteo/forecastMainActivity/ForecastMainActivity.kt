package fr.matthstudio.themeteo.forecastMainActivity

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Nature
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SevereCold
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.core.net.toUri
import coil.compose.AsyncImage
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.dayChoserActivity.DayChooserActivity
import fr.matthstudio.themeteo.dayGraphsActivity.DayGraphsActivity
import fr.matthstudio.themeteo.dayGraphsActivity.GraphType
import fr.matthstudio.themeteo.dayGraphsActivity.WeatherIconGraph
import fr.matthstudio.themeteo.satImgs.MapActivity
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.utilClasses.AirQualityUI
import fr.matthstudio.themeteo.utilClasses.PollenUI
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.VigilanceInfos
import fr.matthstudio.themeteo.utilClasses.toSmartString
import fr.matthstudio.themeteo.utilsActivities.SettingsActivity
import java.time.Duration
import java.time.LocalDate
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
        SimpleWeatherWord.HAZE -> Icons.Rounded.Dehaze // Brume
        SimpleWeatherWord.FOGGY -> Icons.Rounded.Visibility // Brouillard
        SimpleWeatherWord.CLOUDY -> Icons.Rounded.Cloud
        SimpleWeatherWord.SUNNY_CLOUDY -> Icons.Rounded.WbCloudy
        SimpleWeatherWord.SUNNY -> Icons.Rounded.WbSunny
    }
}

fun getWeatherIconPath(word: SimpleWeatherWord, isNight: Boolean = false): String {
    val folder = "file:///android_asset/icons/weather/"
    return folder + when (word) {
        SimpleWeatherWord.SUNNY -> if (isNight) "clear-night.svg" else "clear-day.svg"
        SimpleWeatherWord.SUNNY_CLOUDY -> if (isNight) "cloudy-3-night.svg" else "cloudy-3-day.svg"
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

fun getLottieIconPath(word: SimpleWeatherWord, isNight: Boolean = false): String {
    val baseFolder = "icons/weather/"
    return baseFolder + when (word) {
        SimpleWeatherWord.SUNNY -> if (isNight) "clear-night.json" else "clear-day.json"
        SimpleWeatherWord.SUNNY_CLOUDY -> if (isNight) "partly-cloudy-night.json" else "partly-cloudy-day.json"
        SimpleWeatherWord.CLOUDY -> "cloudy.json"
        SimpleWeatherWord.FOGGY -> if (isNight) "fog-night.json" else "fog-day.json"
        SimpleWeatherWord.HAZE -> if (isNight) "haze-night.json" else "haze-day.json"
        SimpleWeatherWord.DUST -> if (isNight) "dust-night.json" else "dust-day.json"
        SimpleWeatherWord.DRIZZLY -> "drizzle.json"
        SimpleWeatherWord.RAINY1 -> "rain.json"
        SimpleWeatherWord.RAINY2 -> "extreme-rain.json"
        SimpleWeatherWord.HAIL -> "hail.json"
        SimpleWeatherWord.SNOWY1 -> "overcast-snow.json"
        SimpleWeatherWord.SNOWY2 -> "extreme-snow.json"
        SimpleWeatherWord.SNOWY3 -> "snow.json"
        SimpleWeatherWord.SNOWY_MIX -> "extreme-sleet.json"
        SimpleWeatherWord.STORMY -> if (isNight) "thunderstorms-night.json" else "thunderstorms-day.json"
    }
}

@Composable
fun LottieWeatherIcon(
    iconPath: String,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(iconPath))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = animate,
        iterations = LottieConstants.IterateForever
    )

    LottieAnimation(
        composition = composition,
        progress = { if (animate) progress else 0f },
        modifier = modifier
    )
}

// Helper function to convert ISO8601 string (e.g., "2024-05-30T05:58:00+02:00") to LocalDateTime
fun String.toEventLocalDateTime(appContext: Context): LocalDateTime? {
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
                (appContext as TheMeteo).container.telemetryManager.logException(e3)
                null
            }
        }
    }
}


class ForecastMainActivity : ComponentActivity() {
    private lateinit var weatherViewModel: WeatherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isLauncherActivity = intent.getBooleanExtra("LAUNCHER", false)

        // Instancier le viewModel
        val app = (this.application as TheMeteo)
        weatherViewModel = WeatherViewModel(app.weatherCache, app.container.telemetryManager)

        weatherViewModel.selectLocation(weatherViewModel.userSettings.value.defaultLocation)

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
fun BlurredBackground(state: SimpleWeatherWord?, isNight: Boolean = false) {
    val (baseColor, meshColors) = when (state) {
        SimpleWeatherWord.STORMY -> if (isNight) {
            Color(0xFF0D001A) to listOf(
                Color(0xFF311B92).copy(alpha = 0.7f),
                Color(0xFF1A237E).copy(alpha = 0.5f),
                Color(0xFF4A148C).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF1B0033) to listOf(
                Color(0xFF673AB7).copy(alpha = 0.8f),
                Color(0xFF3F51B5).copy(alpha = 0.6f),
                Color(0xFF9C27B0).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.HAIL, SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3, SimpleWeatherWord.SNOWY_MIX -> if (isNight) {
            Color(0xFF101416) to listOf(
                Color(0xFF37474F).copy(alpha = 0.7f),
                Color(0xFF263238).copy(alpha = 0.6f),
                Color(0xFF455A64).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF37474F) to listOf(
                Color(0xFFCFD8DC).copy(alpha = 0.8f),
                Color(0xFF90A4AE).copy(alpha = 0.6f),
                Color(0xFF607D8B).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2, SimpleWeatherWord.DRIZZLY -> if (isNight) {
            Color(0xFF090C29) to listOf(
                Color(0xFF1A237E).copy(alpha = 0.7f),
                Color(0xFF0D47A1).copy(alpha = 0.6f),
                Color(0xFF01579B).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF1A237E) to listOf(
                Color(0xFF3949AB).copy(alpha = 0.8f),
                Color(0xFF5C6BC0).copy(alpha = 0.6f),
                Color(0xFF283593).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.DUST -> if (isNight) {
            Color(0xFF2E1B15) to listOf(
                Color(0xFF5D4037).copy(alpha = 0.7f),
                Color(0xFF4E342E).copy(alpha = 0.6f),
                Color(0xFF3E2723).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF8D6E63) to listOf(
                Color(0xFFBCAAA4).copy(alpha = 0.8f),
                Color(0xFFD7CCC8).copy(alpha = 0.6f),
                Color(0xFFA1887F).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.HAZE -> if (isNight) {
            Color(0xFF1A2124) to listOf(
                Color(0xFF37474F).copy(alpha = 0.7f),
                Color(0xFF263238).copy(alpha = 0.6f),
                Color(0xFF455A64).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF546E7A) to listOf(
                Color(0xFFB0BEC5).copy(alpha = 0.8f),
                Color(0xFFCFD8DC).copy(alpha = 0.6f),
                Color(0xFF90A4AE).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.FOGGY, SimpleWeatherWord.CLOUDY -> if (isNight) {
            Color(0xFF161B1E) to listOf(
                Color(0xFF263238).copy(alpha = 0.7f),
                Color(0xFF37474F).copy(alpha = 0.6f),
                Color(0xFF212121).copy(alpha = 0.4f)
            )
        } else {
            Color(0xFF455A64) to listOf(
                Color(0xFF90A4AE).copy(alpha = 0.8f),
                Color(0xFFB0BEC5).copy(alpha = 0.6f),
                Color(0xFF78909C).copy(alpha = 0.5f)
            )
        }
        SimpleWeatherWord.SUNNY_CLOUDY, SimpleWeatherWord.SUNNY -> if (isNight) {
            Color(0xFF000814) to listOf(
                Color(0xFF001D3D).copy(alpha = 0.8f),
                Color(0xFF003566).copy(alpha = 0.6f),
                Color(0xFF1B263B).copy(alpha = 0.5f)
            )
        } else {
            Color(0xFF1565C0) to listOf(
                Color(0xFFFFB74D).copy(alpha = 0.8f),
                Color(0xFFFFD740).copy(alpha = 0.6f),
                Color(0xFF42A5F5).copy(alpha = 0.5f)
            )
        }
        null -> MaterialTheme.colorScheme.background to listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || state == null) {
        // Dégradé simple pour les versions anciennes ou lorsque state est null
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
    val isDark = isSystemInDarkTheme()
    val hourlyForecast by viewModel.hourlyForecast.collectAsState()
    val dailyForecast by viewModel.dailyForecast.collectAsState()

    val weatherIconFilter = remember(isDark) {
        if (!isDark) {
            ColorFilter.colorMatrix(ColorMatrix().apply {
                // Assombrit légèrement les icônes statiques en mode clair
                setToScale(0.8f, 0.8f, 0.8f, 1f)
            })
        } else null
    }

    // Demander les permissions
    LocationPermissionHandler(viewModel)

    val weatherState = when (viewModel.hourlyForecast.collectAsState().value) {
        is WeatherDataState.SuccessHourly -> getSimpleWeather((viewModel.hourlyForecast.collectAsState().value as WeatherDataState.SuccessHourly).data.first())
        WeatherDataState.Loading -> SimpleWeather("Loading", SimpleWeatherWord.SUNNY)
        else -> SimpleWeather("Error", SimpleWeatherWord.SUNNY)
    }

    val isNight = (dailyForecast as? WeatherDataState.SuccessDaily)?.data?.firstOrNull()?.sunset
        ?.toEventLocalDateTime(context.applicationContext)?.isBefore(LocalDateTime.now()) ?: false
            || (dailyForecast as? WeatherDataState.SuccessDaily)?.data?.firstOrNull()?.sunrise
                ?.toEventLocalDateTime(context.applicationContext)?.isAfter(LocalDateTime.now()) ?: false

    val description = when(weatherState.word) {
        SimpleWeatherWord.STORMY -> stringResource(R.string.stormy)
        SimpleWeatherWord.HAIL -> stringResource(R.string.hail)
        SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2 -> stringResource(R.string.light_snow)
        SimpleWeatherWord.SNOWY3 -> stringResource(R.string.heavy_snow)
        SimpleWeatherWord.SNOWY_MIX -> stringResource(R.string.mixed_rain_snow)
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2 -> stringResource(R.string.rainy)
        SimpleWeatherWord.DRIZZLY -> stringResource(R.string.drizzle)
        SimpleWeatherWord.DUST -> stringResource(R.string.dust_storm)
        SimpleWeatherWord.HAZE -> stringResource(R.string.haze)
        SimpleWeatherWord.FOGGY -> stringResource(R.string.foggy)
        SimpleWeatherWord.CLOUDY -> stringResource(R.string.cloudy)
        SimpleWeatherWord.SUNNY_CLOUDY -> stringResource(R.string.sunny_cloudy)
        SimpleWeatherWord.SUNNY -> stringResource(R.string.clear)
        null -> ""
    }

    // --- GESTION DE L'ÉTAT DE L'UI ---
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    var showLocationSheet by remember { mutableStateOf(false) }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showAirQualityDialog by remember { mutableStateOf(false) }
    var showVigilanceDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val isRefreshing = false


    // --- AFFICHAGE CONDITIONNEL DES PANNEAUX ---
    if (showLocationSheet) {
        val savedLocations by viewModel.savedLocations.collectAsState()
        val selectedLocation by viewModel.selectedLocation.collectAsState()
        val currentWeathers by viewModel.currentWeather.collectAsState()
        val userSettings by viewModel.userSettings.collectAsState()
        val isPermissionGranted by viewModel.isLocationPermissionGranted.collectAsState()

        LocationManagementSheet(
            savedLocations = savedLocations,
            selectedLocation = selectedLocation,
            currentWeathers = currentWeathers,
            userSettings = userSettings,
            isPermissionGranted = isPermissionGranted,
            onSelectLocation = { viewModel.selectLocation(it) },
            onRemoveLocation = { viewModel.removeLocation(it) },
            onRenameLocation = { location, newName -> viewModel.renameLocation(location, newName) },
            onReorderLocations = { viewModel.reorderLocations(it) },
            onSetDefaultLocation = { viewModel.setDefaultLocation(it) },
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
            weatherService = viewModel.weatherService,
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
                    if (showDetailsDialog || showAirQualityDialog)
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

        BlurredBackground(weatherState.word, isNight)

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

                // HERO HEADER
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
                                    contentDescription = null,
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
                                    contentDescription = null,
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
                            
                            val userSettings by viewModel.userSettings.collectAsState()
                            val isBatterySaverActive by (LocalContext.current.applicationContext as TheMeteo).weatherCache.isBatterySaverActive.collectAsState()

                            LottieWeatherIcon(
                                iconPath = getLottieIconPath(weatherState.word ?: SimpleWeatherWord.SUNNY, isNight),
                                animate = userSettings.enableAnimatedIcons && !isBatterySaverActive,
                                modifier = Modifier.size(120.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val temperature = (hourlyForecast as WeatherDataState.SuccessHourly).data.first().temperature
                            Text(
                                text = UnitConverter.formatTemperature(
                                    celsius = temperature,
                                    unit = userSettings.temperatureUnit,
                                    roundToInt = true,
                                    showUnitSymbol = false,
                                    showDegreeSymbol = true
                                ),
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

                // Vigilance
                item {
                    VigilanceCard(viewModel, onCardClick = { showVigilanceDialog = true })
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
                                Row (
                                    modifier = Modifier.padding(start = 12.dp, top = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Timer,
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = stringResource(R.string.hourly_forecast),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(end = 20.dp, top = 20.dp)
                                )
                            }

                            if ((hourlyForecast as? WeatherDataState.SuccessHourly)?.data?.isEmpty() ?: true)
                                return@BentoCard

                            val scrollState = rememberScrollState()
                            val tempUnit = viewModel.userSettings.collectAsState().value.temperatureUnit
                            val windUnit = viewModel.userSettings.collectAsState().value.windUnit
                            when (variable) {
                                ChosenVar.TEMPERATURE -> GenericGraph(
                                    viewModel,
                                    tempUnit,
                                    windUnit,
                                    GraphType.TEMP,
                                    Color(0xFFFFF176),
                                    scrollState = scrollState
                                )

                                ChosenVar.APPARENT_TEMPERATURE -> if ((hourlyForecast as WeatherDataState.SuccessHourly).data.first().apparentTemperature != null)
                                    GenericGraph(
                                        viewModel,
                                        tempUnit,
                                        windUnit,
                                        GraphType.A_TEMP,
                                        Color(0xFFFFB300),
                                        scrollState = scrollState
                                    )

                                ChosenVar.PRECIPITATION -> GenericGraph(
                                    viewModel,
                                    tempUnit,
                                    windUnit,
                                    GraphType.PRECIPITATION,
                                    Color(0xFF039BE5),
                                    valueRange = 0f..3f,
                                    scrollState = scrollState
                                )

                                ChosenVar.WIND -> GenericGraph(
                                    viewModel,
                                    tempUnit,
                                    windUnit,
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

                                    if (buttonVariable == ChosenVar.PRECIPITATION && (hourlyForecast as WeatherDataState.SuccessHourly).data.mapNotNull { it.precipitationData.precipitation }.maxOrNull() == 0.0)
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

                        // variable to determine if the sun card can be shown
                        var isSunOk = true
                        // --- Logic for calculating next two sun events ---
                        val now = LocalDateTime.now()

                        val allEvents = mutableListOf<NextSunEvent>()

                        // Today's events (Data index 0)
                        val todayReading = data[0]
                        todayReading.sunrise.toEventLocalDateTime(context.applicationContext)?.let { dt ->
                            allEvents.add(
                                NextSunEvent(
                                    stringResource(R.string.sunrise),
                                    dt,
                                    stringResource(R.string.today)
                                )
                            )
                        }
                        todayReading.sunset.toEventLocalDateTime(context.applicationContext)?.let { dt ->
                            allEvents.add(
                                NextSunEvent(
                                    stringResource(R.string.sunset),
                                    dt,
                                    stringResource(R.string.today)
                                )
                            )
                        }

                        // Tomorrow's events (Data index 1, if available)
                        val tomorrowReading = data.getOrNull(1)
                        tomorrowReading?.sunrise?.toEventLocalDateTime(context.applicationContext)?.let { dt ->
                            allEvents.add(
                                NextSunEvent(
                                    stringResource(R.string.sunrise),
                                    dt,
                                    stringResource(R.string.tomorrow)
                                )
                            )
                        }
                        tomorrowReading?.sunset?.toEventLocalDateTime(context.applicationContext)?.let { dt ->
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

                        var text: String = ""
                        if (futureEvents.isEmpty())
                            isSunOk = false
                        if (futureEvents.size < 2)
                            isSunOk = false

                        if (isSunOk) {
                            when (futureEvents[0].type) {
                                stringResource(R.string.sunrise) if tomorrowReading != null -> {
                                    val duration = Duration.between(
                                        todayReading.sunset.toEventLocalDateTime(context.applicationContext),
                                        tomorrowReading.sunrise.toEventLocalDateTime(context.applicationContext)
                                    )
                                    text =
                                        "${stringResource(R.string.night)} : ${duration.toHours()}h ${duration.toMinutes() % 60}min"
                                }

                                stringResource(R.string.sunset) -> {
                                    val duration = Duration.between(
                                        todayReading.sunrise.toEventLocalDateTime(context.applicationContext),
                                        todayReading.sunset.toEventLocalDateTime(context.applicationContext)
                                    )
                                    text =
                                        "${stringResource(R.string.day)} : ${duration.toHours()}h ${duration.toMinutes() % 60}min"
                                }

                                else -> {
                                    text = ""
                                }
                            }
                        }

                        // Determine which events to display
                        val displayEvent1 =
                            futureEvents.getOrNull(0) ?: allEvents.getOrNull(0)
                        val displayEvent2 = futureEvents.getOrNull(1)

                        if (text != "" && displayEvent1 != null) {
                            SunriseSunsetCard(
                                modifier = Modifier.weight(1f),
                                event1 = displayEvent1,
                                event2 = displayEvent2,
                                text
                            )
                        }

                        // La card regroupée (Détails)
                       SummaryDetailsCard(
                            modifier = Modifier.weight(1f),
                            onClick = { showDetailsDialog = true }
                       )
                    }
                }

                if (isLauncherActivity) {
                    // Daily Boxes
                    item {
                        val dailyForecast by viewModel.dailyForecast.collectAsState()
                        BentoCard(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            val nDays = 7
                            if (dailyForecast == WeatherDataState.Loading) {
                                Box(
                                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                                return@BentoCard
                            }

                            if (dailyForecast is WeatherDataState.Error || (dailyForecast as? WeatherDataState.SuccessDaily)?.data.isNullOrEmpty())
                                return@BentoCard

                            val dailyData = (dailyForecast as WeatherDataState.SuccessDaily).data
                            val minOverallTemp = dailyData.take(nDays).minOf { it.minTemperature ?: 0.0 }
                            val maxOverallTemp = dailyData.take(nDays).maxOf { it.maxTemperature ?: 0.0 }
                            val userSettings by viewModel.userSettings.collectAsState()
                            val isBatterySaverActive by (LocalContext.current.applicationContext as TheMeteo).weatherCache.isBatterySaverActive.collectAsState()

                            var expandedDayIndex by remember { mutableStateOf(-1) }

                            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                        .clickable {
                                            val intent = Intent(context, DayChooserActivity::class.java)
                                            context.startActivity(intent)
                                        },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text(text = stringResource(R.string.daily_forecast), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                dailyData.take(nDays).forEachIndexed { index, dayReading ->
                                    DailyForecastRow(
                                        dayReading = dayReading,
                                        isExpanded = expandedDayIndex == index,
                                        minOverallTemp = minOverallTemp,
                                        maxOverallTemp = maxOverallTemp,
                                        userSettings = userSettings,
                                        isBatterySaverActive = isBatterySaverActive,
                                        onClick = {
                                            expandedDayIndex = if (expandedDayIndex == index) -1 else index
                                        }
                                    ) {
                                        // Expanded Content: Graph and Details
                                        val hourlyForecast by viewModel.getForecastForRange(
                                            dayReading.date.atTime(0, 0),
                                            dayReading.date.plusDays(1).atTime(0, 0)
                                        ).collectAsState(initial = WeatherDataState.Loading)

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.3f
                                                    ), RoundedCornerShape(16.dp)
                                                )
                                                .padding(12.dp)
                                                .clickable {
                                                    val intent = Intent(
                                                        context,
                                                        DayGraphsActivity::class.java
                                                    ).apply {
                                                        putExtra(
                                                            "START_DATE_TIME",
                                                            dayReading.date.atTime(0, 0)
                                                        )
                                                        putExtra(
                                                            "SELECTED_LOCATION",
                                                            viewModel.selectedLocation.value
                                                        )
                                                    }
                                                    context.startActivity(intent)
                                                }
                                        ) {
                                            if (hourlyForecast is WeatherDataState.SuccessHourly) {
                                                AdvancedGraph(
                                                    hourlyForecast,
                                                    userSettings.roundToInt,
                                                    userSettings.temperatureUnit,
                                                    userSettings.windUnit,
                                                    GraphType.TEMP,
                                                    Color(0xFFFFF176),
                                                    contentWidth = 500.dp,
                                                    contentHeight = 80.dp,
                                                    compactHourFormat = false
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(12.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceAround
                                            ) {
                                                // Wind
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = if (dayReading.maxWind.windDirection != null) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Air,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .rotate(
                                                                dayReading.maxWind.windDirection?.toFloat()
                                                                    ?.minus(180) ?: 0f
                                                            ),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(UnitConverter.formatWind(dayReading.maxWind.windspeed, userSettings.windUnit), style = MaterialTheme.typography.labelSmall)
                                                }
                                                // UV
                                                if (dayReading.maxUvIndex != null) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(Icons.Rounded.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp), tint = getUVColor(dayReading.maxUvIndex))
                                                        Text("UV ${dayReading.maxUvIndex}", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                                // Sun
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                                    Text(
                                                        "${dayReading.sunrise.toEventLocalDateTime(context.applicationContext)?.hour}:${dayReading.sunrise.toEventLocalDateTime(context.applicationContext)?.minute} " +
                                                                "/ ${dayReading.sunset.toEventLocalDateTime(context.applicationContext)?.hour}:${dayReading.sunset.toEventLocalDateTime(context.applicationContext)?.minute}", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                    if (index < dailyData.size - 1 && index < 9) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val environmentalData by viewModel.environmentalData.collectAsState()
                    environmentalData?.let { data ->
                        val today = data.days.firstOrNull()
                        if (today != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AirQualityCard(
                                    data = today.airQuality,
                                    onClick = { showAirQualityDialog = true }
                                )
                                PollenCard(
                                    data = today.pollen,
                                    onClick = { showAirQualityDialog = true }
                                )
                            }
                        }
                    }
                }

                /*if (hourlyForecast is WeatherDataState.SuccessHourly) {
                    // Rain Map Preview
                    item {
                        var lat = 0.0
                        var lon = 0.0
                        when (val loc = selectedLocation) {
                            is LocationIdentifier.CurrentUserLocation ->
                                viewModel.userLocation.collectAsState().value?.let {
                                    lat = it.latitude
                                    lon = it.longitude
                                }

                            is LocationIdentifier.Saved -> {
                                lat = loc.location.latitude
                                lon = loc.location.longitude
                            }
                        }

                        RainMapPreviewCard(
                            lat = lat,
                            lon = lon,
                            onClick = {
                                val intent = Intent(context, RainMapActivity::class.java).apply {
                                    putExtra("LAT", lat)
                                    putExtra("LON", lon)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }*/

                // Other infos
                item {
                    // Informations en bas de page
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val locationText = when (val loc = selectedLocation) {
                            is LocationIdentifier.CurrentUserLocation -> stringResource(
                                R.string.location_coords_format,
                                viewModel.userLocation.collectAsState().value?.latitude ?: 0.0,
                                viewModel.userLocation.collectAsState().value?.longitude ?: 0.0
                            )
                            is LocationIdentifier.Saved -> stringResource(
                                R.string.location_coords_format,
                                loc.location.latitude,
                                loc.location.longitude
                            )
                        }

                        ResponsiveText(
                            text = locationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        ResponsiveText(
                            text = stringResource(R.string.source_format, WeatherModelRegistry.models.firstOrNull { it.apiName == viewModel.userSettings.collectAsState().value.model }?.sourceName ?: viewModel.userSettings.collectAsState().value.model),
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

            if (showAirQualityDialog) {
                AirQualityDetailsDialog(viewModel, onDismiss = { showAirQualityDialog = false })
            }

            if (showVigilanceDialog) {
                val vigilanceState by viewModel.weatherVigilanceInfo.collectAsState()
                if (vigilanceState is WeatherDataState.SuccessVigilance) {
                    VigilanceDetailsDialog(
                        vigilanceData = (vigilanceState as WeatherDataState.SuccessVigilance).data,
                        onDismiss = { showVigilanceDialog = false }
                    )
                }
            }

            val shouldShowPolicyUpdateDialog by viewModel.shouldShowPolicyUpdateDialog.collectAsState()
            if (shouldShowPolicyUpdateDialog) {
                PolicyUpdateDialog(
                    onAccept = { viewModel.acceptPolicyUpdates() }
                )
            }
        }
    }
}

@Composable
fun PolicyUpdateDialog(onAccept: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { /* Empêcher la fermeture sans acceptation si nécessaire, ou onAccept() */ },
        title = { Text(text = stringResource(R.string.policy_update_title)) },
        text = { Text(text = stringResource(R.string.policy_update_message)) },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(text = stringResource(R.string.accept))
            }
        },
        dismissButton = {
            // Optionnel : bouton pour voir les CGU
        }
    )
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

            ResponsiveText(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            // Next event (Event 1)
            Column {
                val time1 = event1.dateTime.formatTime()
                
                val label1 = if (event1.dayLabel == stringResource(R.string.today)) {
                    stringResource(R.string.sun_event_format, event1.type, time1)
                } else {
                    stringResource(R.string.sun_event_day_format, event1.type, event1.dayLabel, time1)
                }

                ResponsiveText(
                    label1,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                // Second next event (Event 2)
                event2?.let { event ->
                    val time2 = event.dateTime.formatTime()
                    val label2 = if (event.dayLabel == stringResource(R.string.today)) {
                        stringResource(R.string.sun_event_format, event.type, time2)
                    } else {
                        stringResource(R.string.sun_event_day_format, event.type, event.dayLabel, time2)
                    }

                    ResponsiveText(
                        label2,
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
fun AirQualityCard(
    data: AirQualityUI,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Groupe Gauche : Titre et Label
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.air_quality),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = data.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Barre horizontale colorée (Jauge)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f) 
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(data.color.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((data.value.toFloat() / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(data.color)
                    )
                }
            }

            // Groupe Droite : AQI en très grand
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = data.value.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    lineHeight = 40.sp
                )
                Text(
                    text = "AQI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun PollenCard(
    data: PollenUI,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Pollen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOfNotNull(
                    data.tree to Pair(stringResource(R.string.trees), Icons.Rounded.Nature),
                    data.grass to Pair(stringResource(R.string.weed), Icons.Rounded.LocalFlorist),
                    data.weed to Pair(stringResource(R.string.grasses), Icons.Rounded.Grass)
                ).forEach { (type, data) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Box {
                            EnvironmentalGauge(
                                value = (type?.level?.toFloat() ?: 0f) / 5f,
                                color = type?.color ?: MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                text = data.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.Center).offset(y = 6.dp)
                            )
                            Icon(
                                imageVector = data.second,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.Center).offset(y = (-11).dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "${type?.level ?: 0}/4",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Display a short description (ex: Low, Moderate, etc.)
                        Text(
                            text = getPollenShortDescFromLevel(type?.level ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDetailsDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    val actualReading =
        (viewModel.hourlyForecast.collectAsState().value as? WeatherDataState.SuccessHourly)?.data?.first()
            ?: return

    val userSettings by viewModel.userSettings.collectAsState()

    // Créer un état pour gérer l'animation de visibilité
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    // Fonction de fermeture qui attend la fin de l'animation
    val scope = rememberCoroutineScope()
    fun animateAndDismiss() {
        visibleState.targetState = false
        onDismiss()
    }

    val details = listOfNotNull(
        actualReading.temperature?.let {
            WeatherDetailItem(
                Icons.Rounded.Thermostat,
                stringResource(R.string.temperature),
                fr.matthstudio.themeteo.utilClasses.UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
            )
        },
        actualReading.apparentTemperature?.let {
            WeatherDetailItem(
                Icons.Rounded.DeviceThermostat,
                stringResource(R.string.apparent_temperature),
                fr.matthstudio.themeteo.utilClasses.UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
            )
        },
        actualReading.skyInfo.uvIndex?.let { uv ->
            WeatherDetailItem(
                Icons.Rounded.WbSunny,
                stringResource(R.string.uv_index),
                "$uv",
                getUVDescription(uv)
            )
        },
        actualReading.dewpoint?.let {
            WeatherDetailItem(
                Icons.Rounded.Water,
                stringResource(R.string.dew_point),
                fr.matthstudio.themeteo.utilClasses.UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
            )
        },
        actualReading.humidity?.let { hu ->
            WeatherDetailItem(Icons.Rounded.WaterDrop, stringResource(R.string.humidity), "${hu.toSmartString()}%")
        },
        actualReading.precipitationData.precipitation?.let { precip ->
            val subValue = buildString {
                actualReading.precipitationData.precipitationProbability?.let { append("Prob: $it% ") }
                actualReading.precipitationData.rain?.takeIf { it > 0 }?.let {
                    if (isNotEmpty() && !endsWith(" ")) append("| ")
                    append("Rain: ${it.toSmartString()} mm ")
                }
                actualReading.precipitationData.snowfall?.takeIf { it > 0 }?.let {
                    if (isNotEmpty() && !endsWith(" ")) append("| ")
                    append("Snow: ${it.toSmartString()} cm")
                }
            }.trim()
            WeatherDetailItem(
                Icons.Rounded.Umbrella,
                stringResource(R.string.precipitation),
                "${precip.toSmartString()} mm",
                subValue.takeIf { it.isNotEmpty() }
            )
        },
        actualReading.precipitationData.snowDepth?.takeIf { it > 0 }?.let {
            WeatherDetailItem(Icons.Rounded.SevereCold, stringResource(R.string.snow_depth), "${it.toSmartString()} cm")
        },
        actualReading.wind.windspeed?.let { ws ->
            val subValue = buildString {
                actualReading.wind.windDirection?.let { append("Direction: $it°") }
                actualReading.wind.windGusts?.let {
                    if (isNotEmpty()) append("\n")
                    append("${stringResource(R.string.gusts)}: ${fr.matthstudio.themeteo.utilClasses.UnitConverter.formatWind(it, userSettings.windUnit)}")
                }
            }
            WeatherDetailItem(
                Icons.Rounded.Air,
                stringResource(R.string.wind_speed),
                fr.matthstudio.themeteo.utilClasses.UnitConverter.formatWind(ws, userSettings.windUnit),
                subValue.takeIf { it.isNotEmpty() }
            )
        },
        actualReading.pressure?.let {
            WeatherDetailItem(Icons.Rounded.Compress, stringResource(R.string.pressure), "${it.toSmartString()} hPa")
        },
        actualReading.skyInfo.cloudcoverTotal?.let { cct ->
            // If low cloud cover is available, all levels are too
            val subValue = if (actualReading.skyInfo.cloudcoverLow != null) {
                "Low: ${actualReading.skyInfo.cloudcoverLow.toSmartString()}% | Mid: ${actualReading.skyInfo.cloudcoverMid?.toSmartString()}% | High: ${actualReading.skyInfo.cloudcoverHigh?.toSmartString()}%"
            } else null
            WeatherDetailItem(Icons.Rounded.Cloud, stringResource(R.string.cloud_cover), "${cct.toSmartString()}%", subValue)
        },
        actualReading.skyInfo.opacity?.let { op ->
            WeatherDetailItem(
                Icons.Rounded.Opacity,
                stringResource(R.string.opacity),
                "${op.toSmartString()}%",
                actualReading.skyInfo.shortwaveRadiation?.let { "Radiation: ${it.roundToInt()} W/m²" }
            )
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
fun VigilanceCard(viewModel: WeatherViewModel, onCardClick: () -> Unit) {
    val context = LocalContext.current
    val vigilanceState by viewModel.weatherVigilanceInfo.collectAsState()

    if (vigilanceState !is WeatherDataState.SuccessVigilance) return
    val vigilanceData = (vigilanceState as WeatherDataState.SuccessVigilance).data

    // Si aucune alerte (> vert), on n'affiche pas la carte
    if (vigilanceData.alerts.isEmpty()) return

    // On récupère l'alerte avec le niveau le plus élevé pour l'affichage principal
    val mainAlert = vigilanceData.alerts.maxBy { it.maxColorId }
    val isMinified = vigilanceData.maxColorId < 3

    // On récupère les heures de début et de fin (première et dernière étape > Vert)
    val activeSteps = mainAlert.steps.filter { it.colorId > 1 }
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val now = LocalDate.now()

    val startDateTime = activeSteps.firstOrNull()?.beginTime?.let {
        try { OffsetDateTime.parse(it) } catch (e: Exception) { null }
    }
    val endDateTime = activeSteps.lastOrNull()?.endTime?.let {
        try { OffsetDateTime.parse(it) } catch (e: Exception) { null }
    }

    val startTimeStr = startDateTime?.format(formatter)
    val endTimeStr = endDateTime?.format(formatter)

    val showDate = startDateTime?.toLocalDate() != LocalDate.now() || endDateTime?.toLocalDate() != LocalDate.now()

    val startLabel = if (showDate && startDateTime != null) {
        val label = if (startDateTime.toLocalDate() == now) stringResource(R.string.today_lower) else stringResource(
            R.string.tomorrow_lower
        )
        " ($label)"
    } else ""

    val endLabel = if (showDate && endDateTime != null) {
        val label = if (endDateTime.toLocalDate() == now) stringResource(R.string.today_lower) else stringResource(R.string.tomorrow_lower)
        " ($label)"
    } else ""

    // Formatage du préfixe d'alerte

    val alertPrefix : String = when (vigilanceData.maxColorId) {
        2 -> stringResource(R.string.weather_alert)
        3 -> stringResource(R.string.weather_warning)
        4 -> stringResource(R.string.severe_weather_warning)
        else -> ""
    }

    val isDark = isSystemInDarkTheme()
    // Mapping des couleurs et des noms Météo-France vers Compose
    val alertColor = when (vigilanceData.maxColorId) {
        1 -> Color(0xFF4CAF50) // Vert
        2 -> Color(0xFFFFEB3B) // Jaune
        3 -> Color(0xFFFF9800) // Orange
        4 -> Color(0xFFF44336) // Rouge
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val alertValue = when (vigilanceData.maxColorId) {
        1 -> "Vert"
        2 -> "Jaune"
        3 -> "Orange"
        4 -> "Rouge"
        else -> null
    }

    val contentColor = if (!isDark) when (vigilanceData.maxColorId) {
        2 -> Color(0xFF422B00) // Jaune (Marron foncé)
        3 -> Color(0xFFE65100) // Orange foncé
        4 -> Color(0xFFB71C1C) // Rouge foncé
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    } else alertColor

    BentoCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isMinified) 64.dp else 130.dp)
            .padding(vertical = 4.dp)
            .clickable { onCardClick() }
    ) {
        if (isMinified) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(alertColor.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getPhenomenonIcon(mainAlert.phenomenonId),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "$alertPrefix : ${stringResource(mapPhenomenonIdToName(mainAlert.phenomenonId)).uppercase()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(alertColor.copy(alpha = 0.2f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = getPhenomenonIcon(mainAlert.phenomenonId),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "$alertPrefix : ${stringResource(mapPhenomenonIdToName(mainAlert.phenomenonId)).uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (startTimeStr != null && endTimeStr != null) {
                            stringResource(
                                R.string.vigilance_level_time_format,
                                mainAlert.maxColorId,
                                alertValue ?: "",
                                "$startTimeStr$startLabel",
                                "$endTimeStr$endLabel"
                            )
                        } else {
                            stringResource(R.string.vigilance_level_ongoing_format, mainAlert.maxColorId)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun VigilanceDetailsDialog(vigilanceData: VigilanceInfos, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val dayFormatter = DateTimeFormatter.ofPattern("dd/MM")

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
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        stringResource(
                            R.string.vigilance_alerts_dept_code,
                            vigilanceData.departmentCode
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(vigilanceData.alerts) { alert ->
                            val alertColor = when (alert.maxColorId) {
                                1 -> Color(0xFF4CAF50)
                                2 -> Color(0xFFFFEB3B)
                                3 -> Color(0xFFFF9800)
                                4 -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.outline
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        alertColor.copy(alpha = 0.1f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                val isDark = isSystemInDarkTheme()
                                val itemContentColor = if (!isDark) when (alert.maxColorId) {
                                    2 -> Color(0xFF422B00) // Marron très foncé
                                    3 -> Color(0xFFE65100) // Orange foncé
                                    4 -> Color(0xFFB71C1C) // Rouge foncé
                                    else -> MaterialTheme.colorScheme.onSurface
                                } else alertColor

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = getPhenomenonIcon(alert.phenomenonId),
                                        contentDescription = null,
                                        tint = itemContentColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(mapPhenomenonIdToName(alert.phenomenonId)),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = itemContentColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                alert.steps.forEach { step ->
                                    val start = OffsetDateTime.parse(step.beginTime)
                                    val end = OffsetDateTime.parse(step.endTime)
                                    val isToday = start.toLocalDate() == LocalDate.now()
                                    
                                    val stepColor = when (step.colorId) {
                                        1 -> Color(0xFF4CAF50)
                                        2 -> Color(0xFFFFEB3B)
                                        3 -> Color(0xFFFF9800)
                                        4 -> Color(0xFFF44336)
                                        else -> Color.Gray
                                    }

                                    Row(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier
                                            .size(10.dp)
                                            .background(stepColor, CircleShape))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${start.format(formatter)} - ${end.format(formatter)} ${if (!isToday) "(${start.format(dayFormatter)})" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, "https://vigilance.meteofrance.fr/fr".toUri())
                                context.startActivity(intent)
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.official_website), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close))
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

@Composable
fun RainMapPreviewCard(
    lat: Double,
    lon: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background image (Static map tile or placeholder)
            AsyncImage(
                model = "https://basemaps.cartocdn.com/dark_all/6/31/21.png", // Generic dark tile
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Semi-transparent overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.Umbrella,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                ResponsiveText(
                    text = "Rain Radar",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                ResponsiveText(
                    text = "Click to view full screen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}