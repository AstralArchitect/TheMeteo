package fr.matthstudio.themeteo.satImgs

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

class FileUtils {
    companion object {
        fun downloadSatMaps(targetDirectory: String, timesteps: Int, resolution: Int = 1080,
                            bbox_start: Pair<Int, Int> = Pair(-20, 30),
                            bbox_end: Pair<Int, Int> = Pair(20, 60),
        ): List<String>{
            val baseURL = "https://view.eumetsat.int/geoserver/wms?service=WMS&REQUEST=GetMap" +
                    "&width=$resolution&height=$resolution" +
                    "&format=image/jpeg&" +
                    "bbox=${bbox_start.first},${bbox_start.second},${bbox_end.first},${bbox_end.second}"
            val layers = listOf("mtg_fd:rgb_cloudtype", "mtg_fd:rgb_fog")

            // Calcule les moments pour les 5 dernières cartes à intervalle de 30 minutes
            val times = mutableListOf<String>()
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            // Applique le retard de 30 minutes pour la dernière carte disponible
            calendar.add(Calendar.MINUTE, -30)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")

            for (i in 0 until timesteps) { // Utilisez le paramètre timesteps
                // Arrondir aux 30 minutes précédentes
                val minutes = calendar.get(Calendar.MINUTE)
                if (minutes < 30) {
                    calendar.set(Calendar.MINUTE, 0)
                } else {
                    calendar.set(Calendar.MINUTE, 30)
                }
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                times.add(dateFormat.format(calendar.time))
                calendar.add(Calendar.MINUTE, -30) // Recule de 30 minutes pour la carte précédente
            }
            times.reverse()

            // Check if the sat image dir exists, if not create it
            if(!File(targetDirectory).exists())
                File(targetDirectory).mkdirs()

            // perform request and store it as file
            for (layer in layers) {
                for (time in times) {
                    val fileName = "${layer.replace(":", "_")}_${time.replace(":", "_")}.jpg"
                    val outputFile = File(targetDirectory, fileName)
                    if(outputFile.exists())
                        continue
                    val staticsLayer = ",backgrounds:ne_boundary_lines_land,backgrounds:ne_10m_coastline"

                    val url = URL("$baseURL&layers=${layer + staticsLayer}&time=$time")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"

                    Thread {
                        try {
                            connection.inputStream.use { inputStream ->
                                FileOutputStream(outputFile).use { fileOutputStream ->
                                    inputStream.copyTo(fileOutputStream)
                                }
                            }
                            Log.d(
                                "FileUtils",
                                "Successfully downloaded $fileName to ${outputFile.absolutePath}"
                            )
                        } catch (e: Exception) {
                            Log.e("FileUtils", "Error downloading file: ${e.message}")
                            e.printStackTrace()
                        } finally {
                            connection.disconnect()
                        }
                    }.start()
                }
            }
            return times
        }
        fun downloadSatMapFullScreen(
            targetDirectory: String, timesteps: Int, layer: String,
            width: Int = 1080,
            height: Int = 1080,
            bbox_start: Pair<Int, Int> = Pair(-20, 30),
            bbox_end: Pair<Int, Int> = Pair(20, 60),
        ): List<String> {
            val baseURL = "https://view.eumetsat.int/geoserver/wms?service=WMS&REQUEST=GetMap" +
                    "&width=$width&height=$height" +
                    "&format=image/jpeg&" +
                    "bbox=${bbox_start.first},${bbox_start.second},${bbox_end.first},${bbox_end.second}"

            // Calcule les moments pour les 5 dernières cartes à intervalle de 30 minutes
            val times = mutableListOf<String>()
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            // Applique le retard de 30 minutes pour la dernière carte disponible
            calendar.add(Calendar.MINUTE, -30)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")

            for (i in 0 until timesteps) { // Utilisez le paramètre timesteps
                // Arrondir aux 30 minutes précédentes
                val minutes = calendar.get(Calendar.MINUTE)
                if (minutes < 30) {
                    calendar.set(Calendar.MINUTE, 0)
                } else {
                    calendar.set(Calendar.MINUTE, 30)
                }
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                times.add(dateFormat.format(calendar.time))
                calendar.add(Calendar.MINUTE, -30) // Recule de 30 minutes pour la carte précédente
            }
            times.reverse()

            // Check if the sat image dir exists, if not create it
            if(!File(targetDirectory).exists())
                File(targetDirectory).mkdirs()

            // perform request and store it as file
            for (time in times) {
                val fileName = "${layer.replace(":", "_")}_${time.replace(":", "_")}_fullscreen.jpg"
                val outputFile = File(targetDirectory, fileName)
                if(outputFile.exists())
                    continue
                val staticsLayer = ",backgrounds:ne_boundary_lines_land,backgrounds:ne_10m_coastline"

                val url = URL("$baseURL&layers=${layer + staticsLayer}&time=$time")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                Thread {
                    try {
                        connection.inputStream.use { inputStream ->
                            FileOutputStream(outputFile).use { fileOutputStream ->
                                inputStream.copyTo(fileOutputStream)
                            }
                        }
                        Log.d(
                            "FileUtils",
                            "Successfully downloaded $fileName to ${outputFile.absolutePath}"
                        )
                    } catch (e: Exception) {
                        Log.e("FileUtils", "Error downloading file: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        connection.disconnect()
                    }
                }.start()
            }
            return times
        }
    }
}