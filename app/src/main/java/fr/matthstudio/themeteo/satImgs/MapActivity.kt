package fr.matthstudio.themeteo.satImgs

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import fr.matthstudio.themeteo.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
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

        map.setMultiTouchControls(true)
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        map.controller.setZoom(3.0)
        map.minZoomLevel = 4.0
        map.maxZoomLevel = 15.0

        val times = mutableListOf<String>()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.MINUTE, -30)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.getDefault())
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

        selectedIsoTime = times.first()
        dateText.text = selectedIsoTime

        // Initialise la source de tuiles avec la première date
        updateTileSource()
        map.invalidate()

        seekBar.max = 9

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar == null) return

                val imageId: Int = progress
                val newTime = times[imageId]

                if (selectedIsoTime != newTime) {
                    dateText.text = newTime
                    updateMapTime(newTime)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBar.progress = 100

        toggleButton.setOnClickListener {
            updateTileSource()
        }

        docButton.setOnClickListener {
            val intent = Intent(this, DocActivity::class.java)
            this.startActivity(intent)
        }
    }

    fun updateMapTime(newIsoTime: String) {
        selectedIsoTime = newIsoTime
        // Ne vide pas le cache ici, car changer le nom de la source le gérera indirectement.
        // updateTileSource() doit être appelé après la mise à jour de selectedIsoTime
        // pour que le nouveau nom de la source intègre la nouvelle date.
        updateTileSource() // Cela créera une nouvelle source avec un nom unique

        //map.invalidate()
    }

    private fun updateTileSource() {
        // Ajouter le temps sélectionné au nom de la source pour la rendre unique
        // Cela force Osmdroid à considérer chaque date comme une source de tuiles différente
        // et à ne pas utiliser les tuiles en cache des dates précédentes.
        val uniqueSourceName = "MySatSource_${selectedIsoTime ?: "default"}_${toggleButton.isChecked}"

        val myTileSource = object : OnlineTileSourceBase(
            uniqueSourceName, // Utilise le nom unique ici
            1, 20,
            256,
            "",
            arrayOf("https://view.eumetsat.int/geoserver/wms?service=WMS")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                if (selectedIsoTime == null) {
                    return ""
                }
                val zoom = MapTileIndex.getZoom(pMapTileIndex)
                Log.d("Zoom", "Zoom : $zoom")
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)

                val west = tile2lon(x, zoom)
                val north = tile2lat(y, zoom)
                val east = tile2lon(x + 1, zoom)
                val south = tile2lat(y + 1, zoom)

                val bbox = String.format(Locale.US, "%.6f,%.6f,%.6f,%.6f", west, south, east, north)

                val actualLayer = "backgrounds:ne_gray," +
                        if (toggleButton.isChecked) "mtg_fd:rgb_cloudtype"
                        else "msg_iodc:rgb_fog,msg_fes:rgb_fog"

                val staticLayers = ",backgrounds:ne_boundary_lines_land,backgrounds:ne_10m_coastline"
                val layers = actualLayer + staticLayers
                val crs = "EPSG:4326"
                val resolution = if (zoom < 5) 128 else 256

                return "https://view.eumetsat.int/geoserver/wms?service=WMS" +
                        "&REQUEST=GetMap" +
                        "&layers=$layers" +
                        "&width=$resolution&height=$resolution" +
                        "&format=image/jpeg" +
                        "&time=$selectedIsoTime" +
                        "&crs=$crs" +
                        "&bbox=$bbox"
            }
        }

        map.setTileSource(myTileSource)
    }

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