package fr.matthstudio.themeteo.satImgs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import fr.matthstudio.themeteo.satImgs.FileUtils
import fr.matthstudio.themeteo.R
import java.io.File
import android.content.Context

class MainSatActivity : ComponentActivity() {
    lateinit var imageCloudType: ImageView
    lateinit var imageFog: ImageView
    lateinit var seekBar: SeekBar
    lateinit var dateText: TextView

    var dates = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sat_main)
        imageFog = findViewById(R.id.satImgFOG)
        imageCloudType = findViewById(R.id.satImgCLT)
        seekBar = findViewById(R.id.seekBar)
        dateText = findViewById(R.id.date_text_view)

        val context = this

        dates.addAll(FileUtils.Companion.downloadSatMaps(context.cacheDir.absolutePath + "/sat_imgs/", 10, 720))

        //removeOldImgs(filesDir.absolutePath + "/sat_imgs/")

        imageCloudType.setOnClickListener {
            // Create the Intent and add the string as an extra
            val intent = Intent(this, FullScreenSatImage::class.java).apply {
                putExtra("MASK", "mtg_fd:rgb_cloudtype")
            }

            // Start the activity
            startActivity(intent)
        }

        imageFog.setOnClickListener {
            // Create the Intent and add the string as an extra
            val intent = Intent(this, FullScreenSatImage::class.java).apply {
                putExtra("MASK", "mtg_fd:rgb_fog")
            }

            // Start the activity
            startActivity(intent)
        }

        imageCloudType.setImageURI(
            File(
                context.cacheDir.absolutePath + "/sat_imgs/",
                "mtg_fd_rgb_cloudtype_${dates[0].replace(":", "_")}.jpg"
            ).toUri() as Uri?)
        imageFog.setImageURI(
            File(
                context.cacheDir.absolutePath + "/sat_imgs/",
                "mtg_fd_rgb_fog_${dates[0].replace(":", "_")}.jpg"
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
                imageCloudType.setImageURI(
                    File(
                        context.cacheDir.absolutePath + "/sat_imgs/",
                        "mtg_fd_rgb_cloudtype_${dates[imageId].replace(":", "_")}.jpg"
                    ).toUri() as Uri?)
                imageFog.setImageURI(
                    File(
                        context.cacheDir.absolutePath + "/sat_imgs/",
                        "mtg_fd_rgb_fog_${dates[imageId].replace(":", "_")}.jpg"
                    ).toUri() as Uri?)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun removeOldImgs(targetDirectory: String) {
        val validFileNames = mutableListOf<String>()
        for (i in 0..dates.size - 1)
            validFileNames.add("${targetDirectory}mtg_fd_rgb_cloudtype_${dates[0].replace(":", "_")}.jpg")
        for (i in 0..dates.size - 1)
            validFileNames.add("${targetDirectory}mtg_fd_rgb_fog_${dates[0].replace(":", "_")}.jpg")

        val directory = File(targetDirectory)
        val files = directory.listFiles()
        val filesNames = mutableListOf<String>()
        files?.forEach { it
            filesNames.add(it.toString())
        }

        filesNames.forEach { i ->
            if (!validFileNames.contains(i))
                File(i).delete()
        }
    }
}