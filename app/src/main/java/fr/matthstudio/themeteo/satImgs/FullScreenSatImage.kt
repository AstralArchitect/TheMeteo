package fr.matthstudio.themeteo.satImgs

import android.net.Uri
import android.os.Bundle
import android.view.WindowMetrics
import android.widget.Button
import android.widget.ImageView
import com.github.chrisbanes.photoview.PhotoView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import fr.matthstudio.themeteo.satImgs.FileUtils
import fr.matthstudio.themeteo.R
import java.io.File
import kotlin.math.cos

class FullScreenSatImage : ComponentActivity() {

    lateinit var satImage: PhotoView
    lateinit var exitButton: Button
    lateinit var seekBar: SeekBar
    lateinit var dateText: TextView

    var dates = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_sat_image)
        satImage = findViewById(R.id.satImg)
        exitButton = findViewById(R.id.exit_button)
        seekBar = findViewById(R.id.seekBar)
        dateText = findViewById(R.id.date_text_view)

        // --- Receive the string extra ---
        val mask = intent.getStringExtra("MASK")

        if (mask == null)
            finish()

        val windowMetrics: WindowMetrics = windowManager.currentWindowMetrics
        val width = windowMetrics.bounds.width()
        val height = windowMetrics.bounds.height()

        val (bottomLeft, topRight) = adjustBoundingBox(
            Pair(-20.0, 30.0),
            Pair(20.0, 60.0),
            width, height
        )

        // Get the dates
        dates.addAll(FileUtils.downloadSatMapFullScreen(
            filesDir.absolutePath + "/sat_imgs/",
            10,
            mask!!,
             width = width,
             height = height,
            Pair(bottomLeft.first.toInt(), bottomLeft.second.toInt()),
            Pair(topRight.first.toInt(), topRight.second.toInt())
        ))

        satImage.setImageURI(
            File(
                filesDir.absolutePath + "/sat_imgs/",
                "${mask.replace(":", "_")}_${dates[0].replace(":", "_")}_fullscreen.jpg"
            ).toUri() as Uri?)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (seekBar == null)
                    return

                val imageId: Int = (progress.toFloat() / 100.0 * 9.0).toInt()
                dateText.text = dates[imageId]
                // Handle progress change here if needed.
                // For example, you might want to update the image based on the progress.
                // For now, it will update with the same image as in your original code.
                satImage.setImageURI(
                    File(
                        filesDir.absolutePath + "/sat_imgs/",
                        "${mask.replace(":", "_")}_${dates[imageId].replace(":", "_")}_fullscreen.jpg"
                    ).toUri() as Uri?)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        exitButton.setOnClickListener {
            finish()
        }
    }

    private fun adjustBoundingBox(
        bottomLeft: Pair<Double, Double>,
        topRight: Pair<Double, Double>,
        viewWidth: Int,
        viewHeight: Int
    ): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        var (minLon, minLat) = bottomLeft
        var (maxLon, maxLat) = topRight

        // 1. Calculate the geographic and view aspect ratios
        val centerLat = (minLat + maxLat) / 2.0
        val geoCorrection = cos(Math.toRadians(centerLat))

        val lonSpan = maxLon - minLon
        val latSpan = maxLat - minLat

        val geoAspectRatio = (lonSpan * geoCorrection) / latSpan
        val viewAspectRatio = viewWidth.toDouble() / viewHeight.toDouble()

        // 2. Compare ratios and adjust the bounding box
        if (geoAspectRatio > viewAspectRatio) {
            // The geographical box is wider than the view; increase latitude span
            val newLatSpan = (lonSpan * geoCorrection) / viewAspectRatio
            val latToAdd = (newLatSpan - latSpan) / 2.0
            minLat -= latToAdd
            maxLat += latToAdd
        } else {
            // The geographical box is taller than the view; increase longitude span
            val newLonSpan = (latSpan * viewAspectRatio) / geoCorrection
            val lonToAdd = (newLonSpan - lonSpan) / 2.0
            minLon -= lonToAdd
            maxLon += lonToAdd
        }

        return Pair(Pair(minLon, minLat), Pair(maxLon, maxLat))
    }
}