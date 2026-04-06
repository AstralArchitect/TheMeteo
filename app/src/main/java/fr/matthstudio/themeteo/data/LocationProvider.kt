package fr.matthstudio.themeteo.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Une classe de données simple pour représenter une coordonnée GPS.
 */
data class GpsCoordinates(val latitude: Double, val longitude: Double)

/**
 * Fournit un Flow qui émet la position GPS actuelle de l'utilisateur.
 * Gère la logique d'abonnement et de désabonnement aux services de localisation.
 */
class LocationProvider(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GpsCoordinates? = suspendCancellableCoroutine { continuation ->
        if (!checkLocationPermission()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val cancellationTokenSource = CancellationTokenSource()
        
        // Try to get fresh location
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                continuation.resume(GpsCoordinates(location.latitude, location.longitude))
            } else {
                // Fallback to last location if fresh one is null
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                    continuation.resume(lastLoc?.let { GpsCoordinates(it.latitude, it.longitude) })
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            }
        }.addOnFailureListener {
            // Fallback to last location if fresh request fails
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                continuation.resume(lastLoc?.let { GpsCoordinates(it.latitude, it.longitude) })
            }.addOnFailureListener {
                continuation.resume(null)
            }
        }

        continuation.invokeOnCancellation {
            cancellationTokenSource.cancel()
        }
    }

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
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
        }

        // Ce bloc est appelé lorsque le Flow est annulé (le collecteur s'arrête)
        awaitClose {
            // Arrêter les mises à jour pour économiser la batterie
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
