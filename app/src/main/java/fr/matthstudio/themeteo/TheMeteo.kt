/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */

package fr.matthstudio.themeteo

import android.app.Application
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import fr.matthstudio.themeteo.data.AppContainer
import fr.matthstudio.themeteo.data.AppDataContainer
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.notifications.WeatherNotificationWorker
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class TheMeteo : Application(), ImageLoaderFactory, Configuration.Provider {
    companion object {
        lateinit var instance: TheMeteo
            private set
    }

    lateinit var container: AppContainer
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hasInitializedSelectedLocation = false

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppDataContainer(this)

        setupWorkManager()
        setupAppStateManager()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun setupAppStateManager() {
        val initialLocationPermission = container.locationProvider.checkLocationPermission()
        AppStateManager.updateLocationPermission(initialLocationPermission)

        applicationScope.launch {
            combine(
                container.userSettingsRepository.model,
                container.userSettingsRepository.roundToInt,
                container.userSettingsRepository.defaultLocation,
                container.userSettingsRepository.defaultScreen,
                container.userSettingsRepository.enableModelFallback,
                container.userSettingsRepository.enableAnimatedIcons,
                container.userSettingsRepository.forecastType,
                container.userSettingsRepository.temperatureUnit,
                container.userSettingsRepository.windUnit,
                container.userSettingsRepository.firebaseConsent,
                container.userSettingsRepository.gcuAccepted,
                container.userSettingsRepository.lastGcuUpdate,
                container.userSettingsRepository.lastPrivacyPolicyUpdate,
                container.userSettingsRepository.hasOpenedAppOnce,
                container.userSettingsRepository.useEurAqi,
                container.userSettingsRepository.backgroundLocationAsked,
                container.userSettingsRepository.themeMode,
                container.userSettingsRepository.enableRainNotifications,
                container.userSettingsRepository.enableVigilanceNotifications,
                container.userSettingsRepository.enableDurationExtension,
                container.userSettingsRepository.expertMode
            ) { values ->
                UserSettings(
                    roundToInt = values[1] as Boolean,
                    defaultLocation = values[2] as LocationIdentifier? ?: LocationIdentifier.CurrentUserLocation,
                    defaultScreen = values[3] as DefaultScreen? ?: DefaultScreen.FORECAST_MAIN,
                    enableAnimatedIcons = values[5] as Boolean,
                    temperatureUnit = values[7] as TemperatureUnit? ?: TemperatureUnit.CELSIUS,
                    windUnit = values[8] as WindUnit? ?: WindUnit.KPH,
                    firebaseConsent = values[9] as String,
                    gcuAccepted = values[10] as Boolean,
                    lastGcuUpdate = values[11] as String?,
                    lastPrivacyPolicyUpdate = values[12] as String?,
                    hasOpenedAppOnce = values[13] as Boolean,
                    useEurAqi = values[14] as Boolean,
                    backgroundLocationAsked = values[15] as Boolean,
                    themeMode = values[16] as ThemeMode,
                    enableRainNotifications = values[17] as Boolean,
                    enableVigilanceNotifications = values[18] as Boolean,
                    enableDurationExtension = values[19] as Boolean,
                    expertMode = values[20] as Boolean
                )
            }.collect { settings ->
                AppStateManager.updateUserSettings(settings)
                AppStateManager.updateThemeMode(settings.themeMode)
                container.telemetryManager.setConsentGranted(settings.firebaseConsent == "GRANTED")

                if (!hasInitializedSelectedLocation) {
                    hasInitializedSelectedLocation = true
                    if (AppStateManager.selectedLocation.value == LocationIdentifier.CurrentUserLocation) {
                        AppStateManager.updateSelectedLocation(settings.defaultLocation)
                    }
                }

                if (settings.enableRainNotifications || settings.enableVigilanceNotifications) {
                    WeatherNotificationWorker.ensureWorkScheduled(this@TheMeteo)
                } else {
                    WeatherNotificationWorker.cancelWork(this@TheMeteo)
                }
            }
        }

        // Ne maintient l'écoute GPS continue que lorsque l'application est au premier plan (STARTED)
        // ET que "Position Actuelle" est sélectionnée, afin d'économiser la batterie et le réseau.
        applicationScope.launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    AppStateManager.isLocationPermissionGranted,
                    AppStateManager.selectedLocation
                ) { granted, selectedLoc ->
                    Pair(granted, selectedLoc is LocationIdentifier.CurrentUserLocation)
                }
                .distinctUntilChanged()
                .flatMapLatest { (granted, isCurrentSelected) ->
                    if (!granted) {
                        flowOf(null)
                    } else if (isCurrentSelected) {
                        container.locationProvider.locationFlow.onStart {
                            val coords = container.locationProvider.getCurrentLocation()
                            if (coords != null) {
                                emit(coords)
                            }
                        }
                    } else {
                        emptyFlow()
                    }
                }
                .collect { coords ->
                    AppStateManager.updateGpsPosition(coords)
                    if (coords != null) {
                        applicationScope.launch {
                            try {
                                val name = container.weatherParser.getCityName(coords.latitude, coords.longitude)
                                if (name != null) {
                                    AppStateManager.updateCityName(name)
                                }
                            } catch (e: Exception) {
                                Log.e("TheMeteo", "Error updating city name for GPS", e)
                            }
                        }
                    }
                }
            }
        }

        applicationScope.launch {
            combine(
                AppStateManager.selectedLocation,
                AppStateManager.currentGpsPosition
            ) { loc, gps ->
                when (loc) {
                    is LocationIdentifier.Saved -> fr.matthstudio.themeteo.data.GpsCoordinates(loc.location.latitude, loc.location.longitude)
                    is LocationIdentifier.CurrentUserLocation -> gps
                }
            }.collect { coords ->
                if (coords != null) {
                    container.weatherParser.updateCurrentWmoAndNight(coords.latitude, coords.longitude)
                }
            }
        }

        applicationScope.launch {
            container.userLocationsRepository.savedLocations.collect { locations ->
                AppStateManager.updateSavedLocations(locations)
            }
        }
    }

    private fun setupWorkManager() {
        try {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(this, workManagerConfiguration)
            }
        } catch (e: Throwable) {
            Log.e("TheMeteo", "WorkManager initialization failed: ${e.message}", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(30L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
