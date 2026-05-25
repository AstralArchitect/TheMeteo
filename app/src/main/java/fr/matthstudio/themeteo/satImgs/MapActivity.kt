/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.satImgs

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.GroundOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.pow

class MapActivity : ComponentActivity() {

    private var map: MapView? = null
    private var selectedIsoTime = mutableStateOf<String?>(null)
    
    // Data
    private val times = mutableListOf<String>()

    // Overlays
    private var snapshotOverlay: GroundOverlay? = null
    private var weatherTilesOverlay: TilesOverlay? = null
    private var locationMarker: Marker? = null
    
    // Coroutine Job for snapshot fetching to allow cancellation
    private var snapshotJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        val weatherCache = (application as TheMeteo).weatherCache

        enableEdgeToEdge()
        setContent {
            val userSettings by weatherCache.userSettings.collectAsState()
            val currentWmo = remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(weatherCache.selectedLocation) {
                weatherCache.get(java.time.LocalDateTime.now(), 1).collect { state ->
                    currentWmo.value = when (state) {
                        is WeatherDataState.SuccessHourly -> state.data.firstOrNull()?.wmo
                        is WeatherDataState.Error -> (state.staleData as? WeatherDataState.SuccessHourly)?.data?.firstOrNull()?.wmo
                        else -> null
                    }
                }
            }

            TheMeteoTheme(
                themeMode = userSettings.themeMode,
                currentWmoCode = currentWmo.value,
                isNight = false
            ) {
                MapScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MapScreen() {
        var currentTimeIndex by remember { mutableFloatStateOf(0f) }
        var isDayOverlay by remember { mutableStateOf(true) }
        val currentTimeStr by selectedIsoTime

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Images Satellites") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        MapView(context).apply {
                            map = this
                            setMultiTouchControls(true)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            controller.setZoom(3.0)
                            minZoomLevel = 4.0
                            maxZoomLevel = 15.0
                            setBackgroundColor(Color.BLACK)
                            setTileSource(object : OnlineTileSourceBase("Empty", 1, 20, 256, "", arrayOf()) {
                                override fun getTileURLString(pMapTileIndex: Long) = ""
                            })

                            snapshotOverlay = GroundOverlay()
                            overlays.add(snapshotOverlay)

                            locationMarker = Marker(this)
                            locationMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            locationMarker?.title = "Selected Location"
                            overlays.add(locationMarker)

                            generateTimeSteps()
                            if (times.isNotEmpty()) {
                                val initialTime = times.last()
                                selectedIsoTime.value = initialTime
                                currentTimeIndex = (times.size - 1).toFloat()
                                updateMapContent(initialTime)
                            }

                            // Observe Location
                            val weatherCache = (application as TheMeteo).weatherCache
                            lifecycleScope.launch {
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
                                        locationMarker?.position = geoPoint
                                        invalidate()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Satellite data © EUMETSAT 2026",
                        style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .width(350.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = isDayOverlay,
                                    onClick = { 
                                        isDayOverlay = true
                                        selectedIsoTime.value?.let { updateTilesOverlay(it, true) }
                                    },
                                    label = { Text("Jour") }
                                )
                                Text(
                                    text = currentTimeStr ?: "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                FilterChip(
                                    selected = !isDayOverlay,
                                    onClick = { 
                                        isDayOverlay = false
                                        selectedIsoTime.value?.let { updateTilesOverlay(it, false) }
                                    },
                                    label = { Text("Nuit") }
                                )
                            }
                            
                            Slider(
                                value = currentTimeIndex,
                                onValueChange = { 
                                    currentTimeIndex = it
                                    val index = it.toInt()
                                    if (index < times.size) {
                                        val newTime = times[index]
                                        if (selectedIsoTime.value != newTime) {
                                            selectedIsoTime.value = newTime
                                            updateMapContent(newTime)
                                        }
                                    }
                                },
                                valueRange = 0f..(if (times.isNotEmpty()) (times.size - 1).toFloat() else 0f),
                                steps = if (times.size > 1) times.size - 2 else 0
                            )
                            
                            Button(
                                onClick = { 
                                    val intent = Intent(this@MapActivity, DocActivity::class.java)
                                    startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Aide à l'interprétation")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun generateTimeSteps() {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        // Align to the last 15-minute mark
        val minutes = calendar.get(Calendar.MINUTE)
        calendar.set(Calendar.MINUTE, (minutes / 15) * 15)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        times.clear()
        for (i in 0 until 12) {
            times.add(0, sdf.format(calendar.time))
            calendar.add(Calendar.MINUTE, -15)
        }
    }

    private fun updateMapContent(isoTime: String) {
        // 1. Update Snapshot (Background)
        fetchAndSetSnapshot(isoTime)
        // 2. Update Tiles (Overlay)
        val isDay = snapshotOverlay == null || true // Placeholder for logic if needed
        // Actually, we use the toggleButton state or isDayOverlay state
        // But since this is called from slider, we should respect the current overlay type
    }

    private fun fetchAndSetSnapshot(isoTime: String) {
        snapshotJob?.cancel()
        snapshotJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val url = "https://view.eumetsat.int/geoserver/wms?service=WMS&version=1.1.0&request=GetMap&layers=msg_fes:rgb_natural&styles=&bbox=-70,-70,70,70&width=1024&height=1024&srs=EPSG:4326&format=image/jpeg&time=$isoTime"
                    val connection = URL(url).openConnection()
                    connection.connect()
                    val input = connection.getInputStream()
                    BitmapFactory.decodeStream(input)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null && snapshotOverlay != null) {
                snapshotOverlay?.setBearing(0f)
                snapshotOverlay?.setImage(bitmap)
                snapshotOverlay?.setPosition(GeoPoint(70.0, -70.0), GeoPoint(-70.0, 70.0))
                map?.invalidate()
            }
        }
    }

    private fun updateTilesOverlay(isoTime: String, isDay: Boolean) {
        if (map == null) return
        if (weatherTilesOverlay != null) {
            map?.overlays?.remove(weatherTilesOverlay)
        }

        val layerName = if (isDay) "msg_fes:rgb_natural" else "msg_fes:rgb_enhancedconvection"
        val uniqueSourceName = "EUMETSAT_${layerName}_$isoTime"

        val tileSource = object : OnlineTileSourceBase(
            uniqueSourceName, 1, 20, 256, "", 
            arrayOf("https://view.eumetsat.int/geoserver/wms?service=WMS")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex)
                val x = org.osmdroid.util.MapTileIndex.getX(pMapTileIndex)
                val y = org.osmdroid.util.MapTileIndex.getY(pMapTileIndex)
                
                val n = 2.0.pow(zoom.toDouble())
                val lonMin = x / n * 360.0 - 180.0
                val lonMax = (x + 1) / n * 360.0 - 180.0
                val latMax = atan(kotlin.math.sinh(PI * (1 - 2 * y / n))) * 180.0 / PI
                val latMin = atan(kotlin.math.sinh(PI * (1 - 2 * (y + 1) / n))) * 180.0 / PI
                
                val bbox = "$lonMin,$latMin,$lonMax,$latMax"
                return "$baseUrl&version=1.1.1&request=GetMap&layers=$layerName&format=image/png&transparent=true&srs=EPSG:4326&width=256&height=256&bbox=$bbox&time=$isoTime"
            }
        }

        weatherTilesOverlay = TilesOverlay(org.osmdroid.tileprovider.MapTileProviderBasic(this, tileSource), this)
        weatherTilesOverlay?.loadingBackgroundColor = Color.TRANSPARENT
        map?.overlays?.add(weatherTilesOverlay)
        map?.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map?.onResume()
    }

    override fun onPause() {
        super.onPause()
        map?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        map?.onDetach()
    }
}
