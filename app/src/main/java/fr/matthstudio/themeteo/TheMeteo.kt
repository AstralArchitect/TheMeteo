/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */

package fr.matthstudio.themeteo

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import fr.matthstudio.themeteo.data.AppContainer
import fr.matthstudio.themeteo.data.AppDataContainer
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.LocationProvider
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.notifications.WeatherNotificationWorker
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

class TheMeteo : Application(), ImageLoaderFactory, Configuration.Provider {
    lateinit var container: AppContainer
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)

        setupAppStateManager()
        setupWorkManager()

        try {
            val testWork = OneTimeWorkRequestBuilder<WeatherNotificationWorker>().build()
            WorkManager.getInstance(this).enqueue(testWork)
        } catch (e: Throwable) {
            Log.e("TheMeteo", "Error enqueuing testWork: ${e.message}", e)
        }
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
                if (AppStateManager.selectedLocation.value == LocationIdentifier.CurrentUserLocation) {
                    AppStateManager.updateSelectedLocation(settings.defaultLocation)
                }
            }
        }

        applicationScope.launch {
            AppStateManager.isLocationPermissionGranted
                .flatMapLatest { granted ->
                    if (granted) {
                        launch {
                            val coords = container.locationProvider.getCurrentLocation()
                            if (coords != null) {
                                AppStateManager.updateGpsPosition(coords)
                            }
                        }
                        container.locationProvider.locationFlow
                    } else {
                        flowOf(null)
                    }
                }
                .collect { coords ->
                    AppStateManager.updateGpsPosition(coords)
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
            WeatherNotificationWorker.updateWorkInterval(this, 60)
        } catch (e: Throwable) {
            Log.e("TheMeteo", "WorkManager initialization failed: ${e.message}", e)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
}