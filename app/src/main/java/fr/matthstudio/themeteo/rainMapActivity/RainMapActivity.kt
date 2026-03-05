package fr.matthstudio.themeteo.rainMapActivity

import android.graphics.Color
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.roundToInt

class RainMapActivity : ComponentActivity() {
    private lateinit var viewModel: RainMapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = this.application
        viewModel = RainMapViewModel(app)

        val initialLat = intent.getDoubleExtra("LAT", 48.8566)
        val initialLon = intent.getDoubleExtra("LON", 2.3522)

        enableEdgeToEdge()
        setContent {
            TheMeteoTheme {
                RainMapScreen(viewModel, initialLat, initialLon)
            }
        }
    }
}

@Composable
fun RainMapScreen(viewModel: RainMapViewModel, initialLat: Double, initialLon: Double) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState) {
            is RainMapUiState.Loading -> {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is RainMapUiState.Error -> {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is RainMapUiState.Success -> {
                RainMapContent(
                    state.host,
                    state.frames,
                    state.lastPastIndex,
                    initialLat,
                    initialLon
                )
            }
        }
    }
}

@Composable
fun RainMapContent(
    host: String,
    frames: List<TimeFrame>,
    lastPastIndex: Int,
    initialLat: Double,
    initialLon: Double
) {
    var currentIndex by remember(lastPastIndex) { mutableIntStateOf(if (lastPastIndex != -1) lastPastIndex else frames.lastIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var mapView: MapView? by remember { mutableStateOf(null) }
    
    // User Location for Radius Check
    var userGeoPoint by remember { mutableStateOf<GeoPoint?>(GeoPoint(initialLat, initialLon)) }

    // Store overlays and providers
    val overlays = remember { mutableMapOf<Int, TilesOverlay>() }
    val providers = remember { mutableMapOf<Int, MapTileProviderBasic>() }

    // Use current value reference for MapListener
    val latestIndex = rememberUpdatedState(currentIndex)

    // Trigger downloads for zoom 4 tiles when map is overzoomed
    fun triggerDownloads(map: MapView) {
        if (map.zoomLevelDouble <= 4.0) return
        val currentIdx = latestIndex.value
        val provider = providers[currentIdx] ?: return
        val zoom = 4
        val bbox = map.boundingBox

        // Formulas for Slippy Map Tilenames
        val xMin = Math.floor((bbox.lonWest + 180.0) / 360.0 * Math.pow(2.0, zoom.toDouble())).toInt()
        val xMax = Math.floor((bbox.lonEast + 180.0) / 360.0 * Math.pow(2.0, zoom.toDouble())).toInt()
        val yMin = Math.floor((1.0 - Math.log(Math.tan(Math.toRadians(bbox.latNorth)) + 1.0 / Math.cos(Math.toRadians(bbox.latNorth))) / Math.PI) / 2.0 * Math.pow(2.0, zoom.toDouble())).toInt()
        val yMax = Math.floor((1.0 - Math.log(Math.tan(Math.toRadians(bbox.latSouth)) + 1.0 / Math.cos(Math.toRadians(bbox.latSouth))) / Math.PI) / 2.0 * Math.pow(2.0, zoom.toDouble())).toInt()

        val yStart = Math.min(yMin, yMax)
        val yEnd = Math.max(yMin, yMax)
        val xStart = Math.min(xMin, xMax)
        val xEnd = Math.max(xMin, xMax)

        // Sanity check to avoid huge downloads if bbox is weird
        val count = (xEnd - xStart + 1) * (yEnd - yStart + 1)
        if (count > 100 || count <= 0) return

        for (x in xStart..xEnd) {
            for (y in yStart..yEnd) {
                val tileIndex = MapTileIndex.getTileIndex(zoom, x, y)
                // Calling getMapTile triggers the download pipeline if missing from cache
                provider.getMapTile(tileIndex)
            }
        }
    }

    // Animation Loop
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                delay(1000)
                currentIndex = (currentIndex + 1) % frames.size
            }
        }
    }

    // Effect to update map visibility and initialize overlays
    LaunchedEffect(currentIndex, mapView, userGeoPoint) {
        val map = mapView ?: return@LaunchedEffect
        val userPos = userGeoPoint ?: return@LaunchedEffect
        
        // 1. Initialize ALL overlays if not done yet
        if (overlays.isEmpty()) {
            frames.forEachIndexed { index, frame ->
                val tileSource = object : OnlineTileSourceBase(
                    "RainViewer_Limited_${frame.time}",
                    3, 4, 256, ".png", // DECLARE 256px to ensure standard scaling behavior
                    arrayOf("$host${frame.path}/512/")
                ) {
                    override fun getTileURLString(pMapTileIndex: Long): String {
                        val zoom = MapTileIndex.getZoom(pMapTileIndex)
                        val x = MapTileIndex.getX(pMapTileIndex)
                        val y = MapTileIndex.getY(pMapTileIndex)

                        // Calculate tile center
                        val lon = tile2lon(x, zoom) + (tile2lon(x + 1, zoom) - tile2lon(x, zoom)) / 2
                        val lat = tile2lat(y, zoom) + (tile2lat(y + 1, zoom) - tile2lat(y, zoom)) / 2

                        // RADIUS CHECK: 2000km from user
                        val results = FloatArray(1)
                        Location.distanceBetween(userPos.latitude, userPos.longitude, lat, lon, results)
                        val distanceInKm = results[0] / 1000

                        return if (distanceInKm <= 2000) {
                            "$baseUrl$zoom/$x/$y/2/1_1.png"
                        } else {
                            "" // Forbid download outside 2000km
                        }
                    }
                }

                val provider = MapTileProviderBasic(map.context, tileSource)
                provider.setTileRequestCompleteHandler(map.tileRequestCompleteHandler)
                
                val overlay = TilesOverlay(provider, map.context)
                overlay.loadingBackgroundColor = Color.TRANSPARENT
                overlay.loadingLineColor = Color.TRANSPARENT
                overlay.isEnabled = false
                
                providers[index] = provider
                overlays[index] = overlay
                map.overlays.add(0, overlay) // Add at bottom
            }
        }

        // 2. Toggle Visibility
        overlays.forEach { (index, overlay) ->
            if (index == currentIndex) {
                if (!overlay.isEnabled) {
                    // Clear memory cache to force retry of failed/partial tiles
                    providers[index]?.clearTileCache()
                    overlay.isEnabled = true
                }
            } else {
                if (overlay.isEnabled) {
                    overlay.isEnabled = false
                }
            }
        }
        
        // Trigger downloads for zoom 4 if map is already overzoomed
        if (map.zoomLevelDouble > 4.0) {
            triggerDownloads(map)
        }
        
        map.invalidate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        // Map View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).apply {
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            triggerDownloads(this@apply)
                            return true
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            triggerDownloads(this@apply)
                            return true
                        }
                    })

                    // Base Map
                    val baseSource = object : OnlineTileSourceBase(
                        "CartoDB ${if(isDark) "Dark" else "Light"}",
                        1, 20, 256, ".png",
                        arrayOf("https://a.basemaps.cartocdn.com/${if (isDark) "dark_all" else "light_all"}/")
                    ) {
                        override fun getTileURLString(pMapTileIndex: Long): String {
                            return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                                    MapTileIndex.getX(pMapTileIndex) + "/" +
                                    MapTileIndex.getY(pMapTileIndex) + ".png"
                        }
                    }
                    setTileSource(baseSource)
                    
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(initialLat, initialLon))
                    
                    // LOCK Min Zoom to 3.0 to align with our download constraints
                    minZoomLevel = 3.0
                    
                    // Add User Marker
                    val marker = Marker(this)
                    marker.position = GeoPoint(initialLat, initialLon)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    this.overlays.add(marker)

                    mapView = this
                }
            },
            onRelease = {
                mapView = null
                overlays.clear()
                providers.clear()
            }
        )

        // Observe Global Location
        val activity = LocalActivity.current as RainMapActivity
        val weatherCache = (activity.application as TheMeteo).weatherCache
        val lifecycleOwner = LocalLifecycleOwner.current
        
        LaunchedEffect(mapView) {
            val map = mapView ?: return@LaunchedEffect
            lifecycleOwner.lifecycleScope.launch {
                combine(
                    weatherCache.selectedLocation,
                    weatherCache.currentGpsPosition
                ) { selected, gps ->
                    when (selected) {
                        is LocationIdentifier.Saved -> GeoPoint(selected.location.latitude, selected.location.longitude)
                        is LocationIdentifier.CurrentUserLocation -> gps?.let { GeoPoint(it.latitude, it.longitude) }
                    }
                }.collect { geoPoint ->
                    if (geoPoint != null) {
                        userGeoPoint = geoPoint
                        // Update marker position if it exists
                        map.overlays.filterIsInstance<Marker>().firstOrNull()?.position = geoPoint
                        map.invalidate()
                    }
                }
            }
        }

        // Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                .padding(16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val date = Date(frames[currentIndex].time * 1000)
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateStr = formatter.format(date)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (frames[currentIndex].isForecast) "Prévision : $dateStr" else "Passé : $dateStr",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                FilledIconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null
                    )
                }
            }

            Slider(
                value = currentIndex.toFloat(),
                onValueChange = { 
                    currentIndex = it.roundToInt()
                    isPlaying = false
                },
                valueRange = 0f..frames.lastIndex.toFloat(),
                steps = if (frames.size > 2) frames.size - 2 else 0,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            val uriHandler = LocalUriHandler.current
            // ZONE D'ATTRIBUTION LÉGALE
            val attributionString = buildAnnotatedString {
                append("Données Radar : ")
                pushStringAnnotation(tag = "URL", annotation = "https://www.rainviewer.com/")
                withStyle(style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
                ) {
                    append("RainViewer")
                }
                pop()
                append(" | © ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("OpenStreetMap")
                }
                append(" contributors, © CARTO")
            }

            ClickableText(
                text = attributionString,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                onClick = { offset ->
                    attributionString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item)
                        }
                }
            )
        }
    }
    
    // Lifecycle
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView?.onDetach()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

// Helper functions for tile math
private fun tile2lon(x: Int, z: Int): Double {
    return x / 2.0.pow(z.toDouble()) * 360.0 - 180
}

private fun tile2lat(y: Int, z: Int): Double {
    val n = PI - (2.0 * PI * y) / 2.0.pow(z.toDouble())
    return (180.0 / PI * atan(0.5 * (Math.exp(n) - Math.exp(-n))))
}
