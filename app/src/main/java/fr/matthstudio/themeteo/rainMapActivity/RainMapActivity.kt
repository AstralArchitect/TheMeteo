/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.BuildConfig
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
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.platform.LocalLocale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withSave

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
                    state.bounds,
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
    bounds: RadarBounds,
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
            } catch (_: Exception) {
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
    // Masque d'assombrissement Canvas hors-zone (Assombrit tout l'écran sauf le rectangle radar)
    val maskOverlay = remember(bounds) {
        object : org.osmdroid.views.overlay.Overlay() {
            private val maskPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(112, 3, 7, 18) // 140 * 0.8 opacity = 112 pour une teinte 100% identique
                style = android.graphics.Paint.Style.FILL
            }

            private val rectF = android.graphics.RectF()
            private val nwPoint = android.graphics.Point()
            private val sePoint = android.graphics.Point()

            override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
                if (shadow) return
                val projection = mapView.projection ?: return

                // Conversion des GeoPoints Nord-Ouest et Sud-Est en pixels d'écran
                projection.toPixels(GeoPoint(bounds.north, bounds.west), nwPoint)
                projection.toPixels(GeoPoint(bounds.south, bounds.east), sePoint)

                rectF.set(
                    nwPoint.x.toFloat(),
                    nwPoint.y.toFloat(),
                    sePoint.x.toFloat(),
                    sePoint.y.toFloat()
                )

                canvas.withSave {

                    canvas.clipOutRect(rectF)

                    canvas.drawPaint(maskPaint)
                }
            }
        }
    }

    // Contour en pointillés cyan délimitant la zone d'observation
    val boundsOutlineOverlay = remember(bounds) {
        org.osmdroid.views.overlay.Polygon().apply {
            points = listOf(
                GeoPoint(bounds.north, bounds.west),
                GeoPoint(bounds.north, bounds.east),
                GeoPoint(bounds.south, bounds.east),
                GeoPoint(bounds.south, bounds.west),
                GeoPoint(bounds.north, bounds.west)
            )
            fillPaint.color = android.graphics.Color.TRANSPARENT
            outlinePaint.color = "#38BDF8".toColorInt()
            outlinePaint.strokeWidth = 6f
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 15f), 0f)
        }
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
    LaunchedEffect(currentIndex, mapView, bounds) {
        val map = mapView ?: return@LaunchedEffect
        val frame = frames.getOrNull(currentIndex) ?: return@LaunchedEffect
        val bitmap = frame.bitmap ?: return@LaunchedEffect

        // 1. Configurer les calques radar, masque d'écran et contour
        if (!map.overlays.contains(radarOverlay)) {
            radarOverlay.setPosition(GeoPoint(bounds.north, bounds.west), GeoPoint(bounds.south, bounds.east))
            radarOverlay.setTransparency(0.2f) // Un peu de transparence pour voir la carte dessous
            map.overlays.add(0, radarOverlay)
        }
        if (!map.overlays.contains(maskOverlay)) {
            map.overlays.add(1, maskOverlay)
        }
        if (!map.overlays.contains(boundsOutlineOverlay)) {
            map.overlays.add(2, boundsOutlineOverlay)
        }

        // 2. Appliquer la nouvelle image et rafraîchir
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
                                    MapTileIndex.getY(pMapTileIndex) + ".png" +
                                    "?key=${BuildConfig.CARTODB_API_KEY}"
                        }
                    }
                    setTileSource(baseSource)
                    
                    controller.setZoom(9.0)
                    controller.setCenter(GeoPoint(initialLat, initialLon))
                    minZoomLevel = 6.5
                    
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

        var extended by remember{ mutableStateOf(false) }

        // Legend
        RainMapLegend(
            extended,
            {extended = !extended},
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
        Box (
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val date = Date(frames.getOrNull(currentIndex)?.time?.let { it * 1000 }
                    ?: System.currentTimeMillis())
                val formatter = SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale)
                val dateStr = formatter.format(date)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        val diffMin = (System.currentTimeMillis() - date.time) / 60000
                        val elapsedStr = if (diffMin >= 60) {
                            "${diffMin / 60}h${String.format(LocalLocale.current.platformLocale, "%02d", diffMin % 60)}"
                        } else {
                            "$diffMin min"
                        }

                        Text(
                            text = "Radar : $dateStr",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            modifier = Modifier.align(Alignment.Bottom),
                            text = "Il y a $elapsedStr",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

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

                val attributionString =
                    "Données Radar : MétéoFrance | © OpenStreetMap contributors, © CARTO"

                Text(
                    text = attributionString,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
    
    // Lifecycle
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
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
