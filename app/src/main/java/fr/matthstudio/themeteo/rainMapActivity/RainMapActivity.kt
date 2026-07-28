/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import android.graphics.Bitmap
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
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.GroundOverlay
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

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
            val userSettings by viewModel.userSettings.collectAsState()
            val currentWmo by viewModel.currentWmo.collectAsState()

            TheMeteoTheme(
                themeMode = userSettings.themeMode,
                currentWmoCode = currentWmo,
                isNight = false
            ) {
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
                    state.frames,
                    initialLat,
                    initialLon
                )
            }
        }
    }
}

@Composable
fun RainMapContent(
    frames: List<TimeFrame>,
    initialLat: Double,
    initialLon: Double
) {
    var currentIndex by remember { mutableIntStateOf(frames.lastIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var mapView: MapView? by remember { mutableStateOf(null) }
    
    // User Location
    var userGeoPoint by remember { mutableStateOf<GeoPoint?>(GeoPoint(initialLat, initialLon)) }

    // Rendu Radar unique : On recycle une seule Bitmap pour économiser la RAM
    // Désactivation explicite de l'interpolation linéaire (isFilterBitmap/isAntiAlias = false) pour un rendu de pixels nets
    val radarOverlay = remember {
        object : GroundOverlay() {
            // Safely access the private mPaint field via reflection
            private val paint: android.graphics.Paint? = try {
                GroundOverlay::class.java.getDeclaredField("mPaint").let { field ->
                    field.isAccessible = true
                    field.get(this) as android.graphics.Paint
                }
            } catch (e: Exception) {
                null
            }

            init {
                paint?.let {
                    it.isFilterBitmap = false
                    it.isAntiAlias = false
                    it.isDither = false
                }
            }

            override fun draw(c: android.graphics.Canvas, p: MapView, shadow: Boolean) {
                // Ensure properties are set (though init should be enough)
                paint?.let {
                    it.isFilterBitmap = false
                    it.isAntiAlias = false
                }
                super.draw(c, p, shadow)
            }
        }
    }
    val displayBitmap = remember(frames.firstOrNull()?.width, frames.firstOrNull()?.height) {
        val f = frames.firstOrNull()
        if (f != null && f.width > 0 && f.height > 0) {
            Bitmap.createBitmap(f.width, f.height, Bitmap.Config.ARGB_8888)
        } else null
    }

    // Animation Loop
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                delay(1000.milliseconds)
                currentIndex = (currentIndex + 1) % frames.size
            }
        }
    }

    // Mise à jour de l'image sur la carte
    LaunchedEffect(currentIndex, mapView, displayBitmap) {
        val map = mapView ?: return@LaunchedEffect
        val bitmap = displayBitmap ?: return@LaunchedEffect
        val frame = frames.getOrNull(currentIndex) ?: return@LaunchedEffect
        val buffer = frame.buffer ?: return@LaunchedEffect

        // 1. Update Bitmap depuis le ByteBuffer natif (ultra-rapide)
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        // 2. Configurer le GroundOverlay
        if (!map.overlays.contains(radarOverlay)) {
            // Bounding box géographique normalisée de la mosaïque radar sur la métropole (synchronisée avec le serveur)
            val north = 51.50
            val south = 41.30
            val west = -5.50
            val east = 9.80
            radarOverlay.setPosition(GeoPoint(north, west), GeoPoint(south, east))
            radarOverlay.setTransparency(0.2f) // Un peu de transparence pour voir la carte dessous
            map.overlays.add(0, radarOverlay) // Placer sous le marqueur
        }

        // 3. Appliquer la nouvelle image et rafraîchir
        radarOverlay.setImage(bitmap)
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
            }
        )

        // Legend
        RainMapLegend(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .padding(top = 32.dp) // Avoid status bar if not edge-to-edge handled
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
            val date = Date(frames.getOrNull(currentIndex)?.time?.let { it * 1000 } ?: System.currentTimeMillis())
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateStr = formatter.format(date)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Radar : $dateStr",
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
            
            val attributionString = "Données Radar : MétéoFrance | © OpenStreetMap contributors, © CARTO"

            Text(
                text = attributionString,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
