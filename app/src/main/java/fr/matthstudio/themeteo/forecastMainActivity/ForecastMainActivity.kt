package fr.matthstudio.themeteo.forecastMainActivity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.BentoCardType
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilsActivities.SettingsActivity
import kotlinx.coroutines.launch
import java.time.LocalDateTime

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
    onShowSunMoonDetails: () -> Unit,
    onShowDetails: () -> Unit,
    onShowAirQuality: () -> Unit,
    onShowVigilance: () -> Unit
) {
    when (cardType) {
        BentoCardType.VIGILANCE -> VigilanceCard(viewModel, onCardClick = onShowVigilance)
        BentoCardType.HOURLY_FORECAST -> HourlyForecastCard(hourlyForecast, context = context, viewModel = viewModel)
        BentoCardType.SUN_DETAILS -> SunAndDetails(viewModel, context, onShowSunMoonDetails = onShowSunMoonDetails, onShowDetails = onShowDetails)
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
    var showSunMoonDialog by remember { mutableStateOf(false) }

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
                    if (showDetailsDialog || showAirQualityDialog || showVigilanceDialog || showSunMoonDialog)
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
                                { showSunMoonDialog = true },
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

                if (showSunMoonDialog) {
                    SunMoonDetailsDialog(viewModel, onDismiss = { showSunMoonDialog = false })
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