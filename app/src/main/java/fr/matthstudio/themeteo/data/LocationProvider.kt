/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.navigationevent.NavigationEventDispatcher
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
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

    private fun getPriority(): Int {
        return if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
    }

    suspend fun getCurrentLocation(): GpsCoordinates? = suspendCancellableCoroutine { continuation ->
        if (!checkLocationPermission()) {
            Log.e("getCurrentLocation", "Permission not allowed")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val cancellationTokenSource = CancellationTokenSource()
        
        // Success listener for fresh location
        fusedLocationClient.getCurrentLocation(
            getPriority(),
            cancellationTokenSource.token
        ).addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                continuation.resume(GpsCoordinates(task.result.latitude, task.result.longitude))
            } else {
                // Fallback to last location
                fusedLocationClient.lastLocation.addOnCompleteListener { lastTask ->
                    val lastLoc = if (lastTask.isSuccessful) lastTask.result else null
                    continuation.resume(lastLoc?.let { GpsCoordinates(it.latitude, it.longitude) })
                }
            }
        }

        continuation.invokeOnCancellation {
            cancellationTokenSource.cancel()
        }
    }

    /**
     * Vérifie si les paramètres de localisation du système sont activés.
     */
    fun checkLocationSettings(onResult: (Boolean, Exception?) -> Unit) {
        val locationRequest = LocationRequest.Builder(getPriority(), 10000L).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
        
        val client: SettingsClient = LocationServices.getSettingsClient(context)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            onResult(true, null)
        }
        task.addOnFailureListener { exception ->
            onResult(false, exception)
        }
    }

    /**
     * Un Flow qui émet des mises à jour de localisation.
     */
    @SuppressLint("MissingPermission")
    val locationFlow: Flow<GpsCoordinates> = callbackFlow {
        if (!checkLocationPermission()) {
            close()
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(getPriority(), 10000L)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000L)
            .setMaxUpdateDelayMillis(15000L)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trySend(GpsCoordinates(location.latitude, location.longitude))
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener { e ->
            close(e)
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
