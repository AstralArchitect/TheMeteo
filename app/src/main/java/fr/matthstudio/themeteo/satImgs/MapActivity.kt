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
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.text.TextPainter.paint
import androidx.lifecycle.lifecycleScope
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
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

class MapActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private var selectedIsoTime: String? = null
    private lateinit var seekBar: SeekBar
    private lateinit var dateText: TextView
    private lateinit var toggleButton: ToggleButton
    private lateinit var docButton: Button
    
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

        setContentView(R.layout.activity_map)
        seekBar = findViewById(R.id.seekBar)
        dateText = findViewById(R.id.date_text_view)
        toggleButton = findViewById(R.id.day_night_button)
        docButton = findViewById(R.id.doc_button)
        map = findViewById(R.id.map)

        // Map Setup
        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.controller.setZoom(3.0)
        map.minZoomLevel = 4.0
        map.maxZoomLevel = 15.0
        
        // Set base map to empty/black to avoid standard map interference
        map.setBackgroundColor(Color.BLACK)
        map.setTileSource(object : OnlineTileSourceBase("Empty", 1, 20, 256, "", arrayOf()) {
            override fun getTileURLString(pMapTileIndex: Long) = ""
        })

        // Initialize Overlays
        snapshotOverlay = GroundOverlay()
        // We add snapshot overlay first (bottom layer)
        map.overlays.add(snapshotOverlay)

        // Initialize Location Marker
        locationMarker = Marker(map)
        locationMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        locationMarker?.title = "Selected Location"
        // Note: Default icon is used. If you want a custom one:
        // locationMarker?.icon = ContextCompat.getDrawable(this, R.drawable.my_icon)
        map.overlays.add(locationMarker)

        // Time Generation
        generateTimeSteps()

        // Initial Load
        selectedIsoTime = times.last()
        dateText.text = selectedIsoTime
        
        // Initial map update
        updateMapContent(selectedIsoTime!!)

        // Observe Location from WeatherCache
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
                    map.controller.animateTo(geoPoint) // Optional: Center map on location
                    map.invalidate()
                }
            }
        }

        // UI Listeners
        seekBar.max = times.size - 1
        seekBar.progress = times.size - 1

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar == null) return
                if (progress >= 0 && progress < times.size) {
                    val newTime = times[progress]
                    if (selectedIsoTime != newTime) {
                        dateText.text = newTime
                        selectedIsoTime = newTime
                        // Debouncing could be added here, but user wants "never blank", 
                        // so immediate feedback via snapshot is better.
                        updateMapContent(newTime)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        toggleButton.setOnClickListener {
            selectedIsoTime?.let { updateMapContent(it) }
        }

        docButton.setOnClickListener {
            val intent = Intent(this, DocActivity::class.java)
            this.startActivity(intent)
        }
    }

    private fun generateTimeSteps() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.MINUTE, -30) // Ensure data availability

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")

        for (i in 0 until 10) {
            val minutes = calendar.get(Calendar.MINUTE)
            if (minutes < 30) {
                calendar.set(Calendar.MINUTE, 0)
            } else {
                calendar.set(Calendar.MINUTE, 30)
            }
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            times.add(dateFormat.format(calendar.time))
            calendar.add(Calendar.MINUTE, -30)
        }
        times.reverse()
    }

    private fun updateMapContent(time: String) {
        val isDay = toggleButton.isChecked
        
        // Cancel any pending snapshot fetch
        snapshotJob?.cancel()

        // 1. Fetch Full-World Snapshot in Web Mercator (EPSG:3857)
        // This prevents projection distortion (squashing) on the standard OSM map.
        snapshotJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Mercator limits (approx 85.05 degrees)
                val latLimit = 85.0
                // BBox in Meters for EPSG:3857 (Whole World)
                val mercatorBbox = "-20037508.34,-20037508.34,20037508.34,20037508.34"

                // Request a SQUARE image (1:1 aspect ratio) to match Mercator world
                // Using 512x512 for balance between speed and quality
                val width = 512
                val height = 512
                
                val layers = "backgrounds:ne_gray," +
                        (if (isDay) "mtg_fd:rgb_cloudtype"
                        else "msg_iodc:rgb_fog,msg_fes:rgb_fog") +
                        ",backgrounds:ne_boundary_lines_land,backgrounds:ne_10m_coastline"
                
                val snapshotUrl = "https://view.eumetsat.int/geoserver/wms?service=WMS" +
                        "&VERSION=1.1.1" +
                        "&REQUEST=GetMap" +
                        "&layers=$layers" +
                        "&width=$width&height=$height" +
                        "&format=image/jpeg" +
                        "&time=$time" +
                        "&crs=EPSG:3857" +
                        "&bbox=$mercatorBbox"

                // Log.d("MapActivity", "Fetching snapshot: $snapshotUrl")
                
                val connection = URL(snapshotUrl).openConnection() as java.net.HttpURLConnection
                connection.connect()
                
                if (connection.responseCode == 200) {
                    val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                    
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            snapshotOverlay?.image = bitmap
                            // Set position to the Mercator limits
                            snapshotOverlay?.setPosition(
                                GeoPoint(latLimit, -180.0), // Top-Left
                                GeoPoint(-latLimit, 180.0)  // Bottom-Right
                            )
                        } else {
                            Log.e("MapActivity", "Failed to decode snapshot bitmap")
                        }
                        
                        updateTilesOverlay(time, isDay)
                        map.invalidate()
                    }
                } else {
                    Log.e("MapActivity", "Snapshot request failed: ${connection.responseCode}")
                    withContext(Dispatchers.Main) {
                        updateTilesOverlay(time, isDay)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("MapActivity", "Error fetching snapshot", e)
                withContext(Dispatchers.Main) {
                    updateTilesOverlay(time, isDay)
                }
            }
        }
    }

    private fun updateTilesOverlay(time: String, isDay: Boolean) {
        // Remove old tiles overlay if exists and clean up resources
        weatherTilesOverlay?.let { overlay ->
            map.overlays.remove(overlay)
            overlay.onDetach(map)
        }

        // Create new Tile Source
        val safeTimeName = time.replace(":", "-")
        val uniqueSourceName = "Sat_${safeTimeName}_$isDay"
        
        val tileSource = object : OnlineTileSourceBase(
            uniqueSourceName, 1, 20, 256, "", 
            arrayOf("https://view.eumetsat.int/geoserver/wms?service=WMS")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                val west = tile2lon(x, zoom)
                val north = tile2lat(y, zoom)
                val east = tile2lon(x + 1, zoom)
                val south = tile2lat(y + 1, zoom)
                
                // Use a helper to build the URL for this specific tile
                val bbox = org.osmdroid.util.BoundingBox(north, east, south, west)
                val resolution = if (zoom < 5) 128 else 256
                return buildWmsUrl(bbox, time, isDay, resolution, resolution)
            }
        }

        // Create Provider and Overlay
        val provider = MapTileProviderBasic(this, tileSource)
        
        // Tell the provider to invalidate the map when tiles are ready!
        provider.setTileRequestCompleteHandler(map.tileRequestCompleteHandler)

        weatherTilesOverlay = TilesOverlay(provider, this)
        weatherTilesOverlay?.loadingBackgroundColor = Color.TRANSPARENT // Transparent to show snapshot below
        weatherTilesOverlay?.loadingLineColor = Color.TRANSPARENT
        
        // Add to overlays at the correct Z-index
        // We want: [0] Snapshot -> [1] Tiles -> [2] Marker
        // So we insert Tiles just after the Snapshot.
        val snapshotIndex = map.overlays.indexOf(snapshotOverlay)
        if (snapshotIndex >= 0) {
            map.overlays.add(snapshotIndex + 1, weatherTilesOverlay)
        } else {
            // Fallback: add to bottom if snapshot not found
            map.overlays.add(0, weatherTilesOverlay)
        }
        
        map.invalidate()
    }

    private fun buildWmsUrl(
        bbox: org.osmdroid.util.BoundingBox, 
        time: String, 
        isDay: Boolean, 
        width: Int, 
        height: Int
    ): String {
        val layers = "backgrounds:ne_gray," +
                (if (isDay) "mtg_fd:rgb_cloudtype"
                else "msg_iodc:rgb_fog,msg_fes:rgb_fog") +
                ",backgrounds:ne_boundary_lines_land,backgrounds:ne_10m_coastline"
        
        val bboxString = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", 
            bbox.lonWest, bbox.latSouth, bbox.lonEast, bbox.latNorth)

        return "https://view.eumetsat.int/geoserver/wms?service=WMS" +
                "&VERSION=1.1.1" +
                "&REQUEST=GetMap" +
                "&layers=$layers" +
                "&width=$width&height=$height" +
                "&format=image/jpeg" +
                "&time=$time" +
                "&crs=EPSG:4326" +
                "&bbox=$bboxString"
    }

    // Helper functions for tile math
    private fun tile2lon(x: Int, z: Int): Double {
        return x / 2.0.pow(z.toDouble()) * 360.0 - 180
    }

    private fun tile2lat(y: Int, z: Int): Double {
        val n = PI - (2.0 * PI * y) / 2.0.pow(z.toDouble())
        return (180.0 / PI * atan(0.5 * (Math.exp(n) - Math.exp(-n))))
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        map.onDetach()
    }
}
