package fr.matthstudio.themeteo.forecastMainActivity

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SevereCold
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import fr.matthstudio.themeteo.data.BentoCardType
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import coil.compose.AsyncImage
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.VigilanceInfos
import fr.matthstudio.themeteo.utilClasses.toSmartString
import fr.matthstudio.themeteo.utilsActivities.SettingsActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
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
fun BentoCardContent(
    cardType: BentoCardType,
    viewModel: WeatherViewModel,
    hourlyForecast: WeatherDataState,
    context: android.content.Context,
    isLauncherActivity: Boolean,
    onShowDetails: () -> Unit,
    onShowAirQuality: () -> Unit,
    onShowVigilance: () -> Unit
) {
    when (cardType) {
        BentoCardType.VIGILANCE -> VigilanceCard(viewModel, onCardClick = onShowVigilance)
        BentoCardType.HOURLY_FORECAST -> HourlyForecastCard(hourlyForecast, context = context, viewModel = viewModel)
        BentoCardType.SUN_DETAILS -> SunAndDetails(viewModel, context, onShowDetails = onShowDetails)
        BentoCardType.DAILY_FORECAST -> if (isLauncherActivity) DailyForecastCard(viewModel, context)
        BentoCardType.AIR_QUALITY -> {
            val environmentalData by viewModel.environmentalData.collectAsState()
            environmentalData?.days?.firstOrNull()?.airQuality?.let { data ->
                AirQualityCard(data = data, onClick = onShowAirQuality)
            }
        }
        BentoCardType.POLLEN -> {
            val environmentalData by viewModel.environmentalData.collectAsState()
            environmentalData?.days?.firstOrNull()?.pollen?.let { data ->
                PollenCard(data = data, onClick = onShowAirQuality)
            }
        }
        BentoCardType.ADDITIONAL_INFOS -> AdditionalInfos(viewModel, context)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastMainActivityScreen(viewModel: WeatherViewModel, isLauncherActivity: Boolean) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
    val bentoCardsOrder by viewModel.bentoCardsOrder.collectAsState()
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
            Box(modifier = Modifier.fillMaxSize()) {
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                var draggedItemKey by remember { mutableStateOf<String?>(null) }
                var isActivelyDragging by remember { mutableStateOf(false) }
                var draggingOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
                val coroutineScope = rememberCoroutineScope()
                val density = androidx.compose.ui.platform.LocalDensity.current.density
                var tempOrder by remember { mutableStateOf(bentoCardsOrder) }
                val currentOrder by androidx.compose.runtime.rememberUpdatedState(tempOrder)

                LaunchedEffect(bentoCardsOrder, isActivelyDragging) {
                    if (!isActivelyDragging) {
                        tempOrder = bentoCardsOrder
                    }
                }

                // Shared swap logic to be called on drag and auto-scroll
                val checkAndSwap: () -> Unit = {
                    val key = draggedItemKey
                    if (key != null) {
                        val layoutInfo = lazyListState.layoutInfo
                        val visibleItems = layoutInfo.visibleItemsInfo
                        val draggedItemInfo = visibleItems.find { it.key == key }
                        
                        if (draggedItemInfo != null) {
                            val currentIndex = currentOrder.indexOfFirst { it.name == key }
                            
                            // Prevent double swaps and layout state de-syncs by verifying 
                            // the visible layout order of draggable items matches our state.
                            val visibleDraggableItems = visibleItems.filter { item -> currentOrder.any { it.name == item.key } }
                            val visibleDraggableKeys = visibleDraggableItems.map { it.key }
                            val currentOrderVisibleKeys = currentOrder.map { it.name }.filter { it in visibleDraggableKeys }
                            
                            if (visibleDraggableKeys == currentOrderVisibleKeys && currentIndex != -1) {

                                val draggedItemCenter = draggedItemInfo.offset + draggedItemInfo.size / 2 + draggingOffset

                                val targetItem = visibleItems.find { item ->
                                    val targetIndex = currentOrder.indexOfFirst { it.name == item.key }
                                    val isTargetDraggable = item.key != BentoCardType.VIGILANCE.name && item.key != BentoCardType.ADDITIONAL_INFOS.name
                                    item.key != key && targetIndex != -1 && isTargetDraggable &&
                                            if (targetIndex > currentIndex) draggedItemCenter > item.offset + item.size / 2
                                            else draggedItemCenter < item.offset + item.size / 2
                                }

                                if (targetItem != null) {
                                    val targetIndex = currentOrder.indexOfFirst { it.name == targetItem.key }
                                    if (targetIndex != -1) {
                                        // Compensate for layout shift immediately, synchronously
                                        draggingOffset += (draggedItemInfo.offset - targetItem.offset)
                                        
                                        tempOrder = currentOrder.toMutableList().apply {
                                            add(targetIndex, removeAt(currentIndex))
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        }
                    }
                }

                // Auto-scroll logic when dragging near edges
                LaunchedEffect(draggedItemKey, isActivelyDragging) {
                    while (draggedItemKey != null && isActivelyDragging) {
                        val layoutInfo = lazyListState.layoutInfo
                        val draggedItemInfo = layoutInfo.visibleItemsInfo.find { it.key == draggedItemKey }

                        if (draggedItemInfo != null) {
                            val viewPortTop = layoutInfo.viewportStartOffset
                            val viewPortBottom = layoutInfo.viewportEndOffset
                            val draggedItemTop = draggedItemInfo.offset + draggingOffset
                            val draggedItemBottom = draggedItemTop + draggedItemInfo.size

                            val scrollThreshold = 60f * density
                            var scrollAmount = 0f

                            if (draggedItemTop < viewPortTop + scrollThreshold) {
                                val diff = viewPortTop + scrollThreshold - draggedItemTop
                                scrollAmount = -(diff / 8f).coerceIn(1f * density, 10f * density)
                            } else if (draggedItemBottom > viewPortBottom - scrollThreshold) {
                                val diff = draggedItemBottom - (viewPortBottom - scrollThreshold)
                                scrollAmount = (diff / 8f).coerceIn(1f * density, 10f * density)
                            }

                            if (scrollAmount != 0f) {
                                val consumed = lazyListState.scrollBy(scrollAmount)
                                // CRITICAL: Keep item perfectly under finger during auto-scroll
                                draggingOffset += consumed
                                // Only update layout offsets, do NOT call checkAndSwap directly here.
                                // We wait for the next frame's layout pass to avoid state de-syncs.
                            }
                        }
                        androidx.compose.runtime.withFrameNanos { } // Loop perfectly synced with display refresh rate
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
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

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isLauncherActivity) {
                                Row(
                                    modifier = Modifier
                                        .clickable { showLocationSheet = true },
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
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
                                    iconPath = getLottieIconPath(
                                        weatherState.word ?: SimpleWeatherWord.SUNNY, isNight
                                    ),
                                    animate = userSettings.enableAnimatedIcons && !isBatterySaverActive,
                                    modifier = Modifier.size(120.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val temperature =
                                    (hourlyForecast as WeatherDataState.SuccessHourly).data.first().temperature
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
                            val userSettings by viewModel.userSettings.collectAsState()
                            val max = (dailyForecast as? WeatherDataState.SuccessDaily)?.data?.firstOrNull()?.maxTemperature
                            val min = (dailyForecast as? WeatherDataState.SuccessDaily)?.data?.firstOrNull()?.minTemperature
                            max?.let { max ->
                                Text (
                                    text = "Max : ${
                                        UnitConverter.formatTemperature(
                                            max,
                                            userSettings.temperatureUnit,
                                            userSettings.roundToInt
                                        )
                                    } - Min : ${
                                        UnitConverter.formatTemperature(
                                            min,
                                            userSettings.temperatureUnit,
                                            userSettings.roundToInt
                                        )
                                    }",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }

                    item {
                        RainAlertCard(hourlyForecast)
                    }

                    items(tempOrder, key = { it.name }) { cardType ->
                        val isDragged = draggedItemKey == cardType.name
                        val scale by animateFloatAsState(if (isDragged) 1.03f else 1f, label = "scale")
                        val elevation by animateDpAsState(if (isDragged) 12.dp else 0.dp, label = "elevation")

                        val isDraggable = cardType != BentoCardType.VIGILANCE && cardType != BentoCardType.ADDITIONAL_INFOS

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isDragged) Modifier else Modifier.animateItem())
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    shadowElevation = elevation.toPx()
                                    if (isDragged) {
                                        translationY = draggingOffset
                                    }
                                }
                                .then(
                                    if (isDraggable) {
                                        Modifier.pointerInput(Unit) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    draggingOffset = 0f
                                                    draggedItemKey = cardType.name
                                                    isActivelyDragging = true
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    draggingOffset += dragAmount.y
                                                    checkAndSwap()
                                                },
                                                onDragEnd = {
                                                    viewModel.updateBentoCardsOrder(tempOrder)
                                                    isActivelyDragging = false
                                                    val capturedKey = draggedItemKey
                                                    coroutineScope.launch {
                                                        androidx.compose.animation.core.animate(
                                                            initialValue = draggingOffset,
                                                            targetValue = 0f
                                                        ) { value, _ ->
                                                            if (draggedItemKey == capturedKey) {
                                                                draggingOffset = value
                                                            }
                                                        }
                                                        if (draggedItemKey == capturedKey) {
                                                            draggedItemKey = null
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    tempOrder = bentoCardsOrder // Revert
                                                    isActivelyDragging = false
                                                    val capturedKey = draggedItemKey
                                                    coroutineScope.launch {
                                                        androidx.compose.animation.core.animate(
                                                            initialValue = draggingOffset,
                                                            targetValue = 0f
                                                        ) { value, _ ->
                                                            if (draggedItemKey == capturedKey) {
                                                                draggingOffset = value
                                                            }
                                                        }
                                                        if (draggedItemKey == capturedKey) {
                                                            draggedItemKey = null
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            BentoCardContent(
                                cardType,
                                viewModel,
                                hourlyForecast,
                                context,
                                isLauncherActivity,
                                { showDetailsDialog = true },
                                { showAirQualityDialog = true },
                                { showVigilanceDialog = true }
                            )
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
}

@Composable
fun PolicyUpdateDialog(onAccept: () -> Unit) {
    AlertDialog(
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
                stringResource(R.string.a_temperature_unit),
                UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
            )
        },
        actualReading.apparentTemperature?.let {
            WeatherDetailItem(
                Icons.Rounded.DeviceThermostat,
                stringResource(R.string.apparent_temperature),
                UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
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
                UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
            )
        },
        actualReading.humidity?.let { hu ->
            WeatherDetailItem(Icons.Rounded.WaterDrop, stringResource(R.string.humidity), "${hu.toSmartString()}%")
        },
        actualReading.precipitationData.precipitation?.let { precip ->
            val subValue = buildString {
                actualReading.precipitationData.precipitationProbability?.let { append("Prob: $it% ") }
                actualReading.precipitationData.rain?.takeIf { it > 0 }?.let {
                    if (isNotEmpty() && !endsWith(" ")) append("\n")
                    append("Rain: ${it.toSmartString()} mm ")
                }
                actualReading.precipitationData.snowfall?.takeIf { it > 0 }?.let {
                    if (isNotEmpty() && !endsWith(" ")) append("\n")
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
                    append("${stringResource(R.string.gusts)}: ${UnitConverter.formatWind(it, userSettings.windUnit)}")
                }
            }
            WeatherDetailItem(
                Icons.Rounded.Air,
                stringResource(R.string.wind_speed),
                UnitConverter.formatWind(ws, userSettings.windUnit),
                subValue.takeIf { it.isNotEmpty() }
            )
        },
        actualReading.pressure?.let {
            WeatherDetailItem(Icons.Rounded.Compress, stringResource(R.string.pressure), "${it.toSmartString()} hPa")
        },
        actualReading.skyInfo.cloudcoverTotal?.let { cct ->
            // If low cloud cover is available, all levels are too
            val subValue = if (actualReading.skyInfo.cloudcoverLow != null) {
                "Low: ${actualReading.skyInfo.cloudcoverLow.toSmartString()}%\nMid: ${actualReading.skyInfo.cloudcoverMid?.toSmartString()}%\nHigh: ${actualReading.skyInfo.cloudcoverHigh?.toSmartString()}%"
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

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(details) { detail ->
                                WeatherDetailCard(detail)
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
fun WeatherDetailCard(item: WeatherDetailItem) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1
                    )
                }
                Text(
                    item.value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            item.subValue?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    maxLines = 3,
                    lineHeight = 14.sp
                )
            }
        }
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