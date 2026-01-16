package fr.matthstudio.themeteo.forecastViewer.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Une classe de données simple pour représenter une coordonnée GPS.
 */
data class GpsCoordinates(val latitude: Double, val longitude: Double)

/**
 * Fournit un Flow qui émet la position GPS actuelle de l'utilisateur.
 * Gère la logique d'abonnement et de désabonnement aux services de localisation.
 */
class LocationProvider(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Un Flow qui émet des mises à jour de localisation.
     * ATTENTION : Ce Flow ne commencera à émettre que si les permissions de localisation
     * sont accordées par l'utilisateur.
     */
    @SuppressLint("MissingPermission") // La permission est vérifiée dans l'UI avant l'appel
    val locationFlow: Flow<GpsCoordinates> = callbackFlow {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000L)
            .setMaxUpdateDelayMillis(15000L)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // Émettre la nouvelle coordonnée dans le Flow
                    launch {
                        send(GpsCoordinates(location.latitude, location.longitude))
                    }
                }
            }
        }

        // Démarrer les mises à jour de localisation
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

        // Ce bloc est appelé lorsque le Flow est annulé (le collecteur s'arrête)
        awaitClose {
            // Arrêter les mises à jour pour économiser la batterie
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
