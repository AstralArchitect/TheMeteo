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
import fr.matthstudio.themeteo.data.LocationProvider
import fr.matthstudio.themeteo.notifications.WeatherNotificationWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

class TheMeteo : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
    // Le cache est maintenant initialisé avec les dépendances du container.
    lateinit var weatherCache: WeatherCache

    override fun onCreate() {
        super.onCreate()
        container = AppDataContainer(this)
        // Read from cache the data
        // ------------------------
        // check if the file exists
        val cacheFile = File(cacheDir, "weather_cache_data.json")
        if (!cacheFile.exists()) {
            weatherCache = WeatherCache(
                userLocationsRepository = container.userLocationsRepository,
                userSettingsRepository = container.userSettingsRepository,
                applicationScope = CoroutineScope(Dispatchers.Default),
                locationProvider = LocationProvider(this),
                applicationContext = this
            )
            setupWorkManager()
            return
        }
        // read the file's content
        try {
            val json = Json {
                allowStructuredMapKeys = true
            }
            val fileContent = cacheFile.readText()
            val value = json.decodeFromString<MutableMap<LocationIdentifier, MutableMap<String, ModelDataCache>>>(fileContent)
            weatherCache = WeatherCache(
                userLocationsRepository = container.userLocationsRepository,
                userSettingsRepository = container.userSettingsRepository,
                applicationScope = CoroutineScope(Dispatchers.Default),
                locationProvider = LocationProvider(this),
                cache = value,
                applicationContext = this
            )
        } catch (e: Exception) {
            Log.e("TheMeteo", "Error loading cache", e)
            weatherCache = WeatherCache(
                userLocationsRepository = container.userLocationsRepository,
                userSettingsRepository = container.userSettingsRepository,
                applicationScope = CoroutineScope(Dispatchers.Default),
                locationProvider = LocationProvider(this),
                applicationContext = this
            )
        }
        setupWorkManager()
        val testWork = OneTimeWorkRequestBuilder<WeatherNotificationWorker>().build()
        WorkManager.getInstance(this).enqueue(testWork)
    }

    private fun setupWorkManager() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherNotificationWorker>(
            1, TimeUnit.HOURS // Exécution toutes les heures
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeatherAlertWork",
            ExistingPeriodicWorkPolicy.UPDATE, // Mettre à jour pour prendre en compte les changements
            weatherWorkRequest
        )
    }

    fun saveCache() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("saveCache", "Saving cache to disk...")
                val json = Json {
                    allowStructuredMapKeys = true
                }
                val serializedValue = json.encodeToString(weatherCache.getRawCache())
                File(cacheDir, "weather_cache_data.json").writeText(serializedValue)
            } catch (e: Exception) {
                Log.e("TheMeteo", "Error saving cache", e)
            }
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