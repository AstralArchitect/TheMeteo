package fr.matthstudio.themeteo.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import fr.matthstudio.themeteo.forecastViewer.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// --- MODIFICATION : On ajoute des champs à notre modèle de données ---
@Serializable
data class WidgetData(
    val temperature: Double,
    val apparentTemperature: Double?,
    val wmoCode: Int,
    val location: Pair<Float, Float>, // Position
    val lastUpdateTimeMillis: Long // L'heure de la mise à jour
)

class WeatherWidgetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val weatherService = WeatherService()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

    companion object {
        const val DATA_FILE = "widget_data.json"
        // Le nom unique de notre worker de mise à jour.
        // On le met ici pour y accéder depuis le provider.
        const val UNIQUE_WORK_NAME = "WeatherWidgetInitialWorker"
    }

    override suspend fun doWork(): Result {
        try {
            Log.d("WeatherWidgetWorker", "Starting doWork...")
            // 1. Vérifier la permission
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("WeatherWidgetWorker", "Location permission not granted.")
                return Result.failure()
            }

            // 2. Récupérer la localisation
            Log.d("WeatherWidgetWorker", "Fetching location...")
            val location = suspendCoroutine { continuation ->
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) continuation.resume(loc)
                        else fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) continuation.resume(lastLoc)
                            else continuation.resumeWithException(Exception("Location not available"))
                        }.addOnFailureListener { continuation.resumeWithException(it) }
                    }.addOnFailureListener { continuation.resumeWithException(it) }
            }
            Log.d("WeatherWidgetWorker", "Location fetched: Lat ${location.latitude}, Lon ${location.longitude}")

            // --- Géolocalisation inversée pour obtenir le nom de la ville ---
            var locationName = "Position actuelle" // Valeur par défaut
            /*try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val geocoder = Geocoder(applicationContext, Locale.getDefault())
                    // On utilise la version moderne et asynchrone du Geocoder
                    geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                        addresses.firstOrNull()?.locality?.let {
                            locationName = it
                        }
                    }
                } else {
                    // Version synchrone pour les anciens Android (doit être dans un bloc try-catch)
                    @Suppress("DEPRECATION")
                    val addresses = Geocoder(applicationContext, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                    addresses?.firstOrNull()?.locality?.let {
                        locationName = it
                    }
                }
            } catch (e: Exception) {
                Log.e("WeatherWidgetWorker", "Could not get location name", e)
                // En cas d'erreur, on garde la valeur par défaut
            }*/
            Log.d("WeatherWidgetWorker", "Location name: $locationName")

            // 3. Récupérer les données météo
            val lat = location.latitude
            val lon = location.longitude
            val temperatureData = weatherService.getHourlyData(lat, lon, "best_match", LocalDateTime.now(), 1, "temperature_2m")
            val apparentTemperature = weatherService.getHourlyData(lat, lon, "best_match", LocalDateTime.now(), 1, "apparent_temperature")
            val wmoData = weatherService.getHourlyData(lat, lon, "best_match", LocalDateTime.now(), 1, "weather_code")

            Log.d("WeatherWidgetWorker", "Weather data fetched.")

            val currentTemp = temperatureData?.hourly?.temperature2m?.firstOrNull()
            val currentApparentTemp = apparentTemperature?.hourly?.apparentTemperature?.firstOrNull()
            val currentWmo = wmoData?.hourly?.weatherCode?.firstOrNull()

            if (currentTemp == null || currentWmo == null) {
                Log.e("WeatherWidgetWorker", "Failed to fetch temperature or WMO code.")
                return Result.failure()
            }

            // --- Créer le nouvel objet de données complet ---
            val widgetData = WidgetData(
                temperature = currentTemp,
                apparentTemperature = currentApparentTemp,
                wmoCode = currentWmo,
                location = Pair(lat.toFloat(), lon.toFloat()),
                lastUpdateTimeMillis = Instant.now().toEpochMilli() // Heure actuelle en millisecondes
            )
            val jsonString = Json.encodeToString(widgetData)
            val file = File(applicationContext.cacheDir, DATA_FILE)
            file.writeText(jsonString)

            Log.d("WeatherWidgetWorker", "Requesting specific widget update from main dispatcher...")
            withContext(Dispatchers.Main) {
                try {
                    // 1. Obtenir le manager de widgets Glance
                    val manager = GlanceAppWidgetManager(applicationContext)

                    // 2. Récupérer les GlanceId de tous les widgets de notre type
                    val glanceIds = manager.getGlanceIds(WeatherWidget::class.java)

                    if (glanceIds.isNotEmpty()) {
                        Log.d("WeatherWidgetWorker", "Found ${glanceIds.size} widgets to update.")
                        // 3. Boucler sur chaque ID et demander une mise à jour spécifique
                        glanceIds.forEach { glanceId ->
                            Log.d("WeatherWidgetWorker", "Updating widget with glanceId: $glanceId")
                            WeatherWidget().update(applicationContext, glanceId)
                        }
                        Log.d("WeatherWidgetWorker", "All specific widget update requests sent.")
                    } else {
                        Log.d("WeatherWidgetWorker", "No widgets found on screen to update.")
                    }
                } catch (e: Exception) {
                    Log.e("WeatherWidgetWorker", "Failed to send specific widget update request.", e)
                }
            }

            return Result.success()

        } catch (e: Exception) {
            Log.e("WeatherWidgetWorker", "Error in doWork", e)
            return Result.failure()
        }
    }
}