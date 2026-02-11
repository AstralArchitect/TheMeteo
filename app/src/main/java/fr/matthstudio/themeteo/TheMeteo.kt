package fr.matthstudio.themeteo

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import fr.matthstudio.themeteo.data.AppContainer
import fr.matthstudio.themeteo.data.AppDataContainer
import fr.matthstudio.themeteo.data.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.io.File

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
    }

    fun saveCache() {
        try {
            Log.d("onTerminate", "Saving cache...")
            val json = Json {
                allowStructuredMapKeys = true
            }
            val serializedValue = json.encodeToString(weatherCache.getRawCache())
            File(cacheDir, "weather_cache_data.json").writeText(serializedValue)
        } catch (e: Exception) {
            Log.e("TheMeteo", "Error saving cache", e)
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