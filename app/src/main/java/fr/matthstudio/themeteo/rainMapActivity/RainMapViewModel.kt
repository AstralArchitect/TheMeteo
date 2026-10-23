/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class RainMapViewModel(private val applicationContext: Application) : ViewModel() {

    val userSettings: StateFlow<UserSettings?> = AppStateManager.userSettings

    val currentWmo: StateFlow<Int?> = AppStateManager.currentWmoCode

    private val _uiState = MutableStateFlow<RainMapUiState>(RainMapUiState.Loading)
    val uiState: StateFlow<RainMapUiState> = _uiState.asStateFlow()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    init {
        fetchAvailableTimestamps()
    }

    companion object {
        private const val TARGET_IMAGE_COUNT = 8
        private const val CANDIDATE_IMAGE_COUNT = 14
    }

    private fun fetchAvailableTimestamps() {
        viewModelScope.launch {
            try {
                _uiState.value = RainMapUiState.Loading

                val boundsDeferred = async { fetchMetadataBounds() }

                // Génère les candidats récents pour garantir le téléchargement d'exactement 8 images valides
                val files = getRadarFiles(count = CANDIDATE_IMAGE_COUNT)
                val imageDeferreds = files.map { file ->
                    async { fetchRadar(file.url, file.timestamp) }
                }

                val bounds = boundsDeferred.await()
                val fetchedFrames = imageDeferreds.awaitAll()
                    .filterNotNull()
                    .takeLast(TARGET_IMAGE_COUNT) // Conserve exactement 8 images radar

                if (fetchedFrames.isNotEmpty()) {
                    recycleBitmaps()
                    _uiState.value = RainMapUiState.Success(
                        frames = fetchedFrames,
                        bounds = bounds
                    )
                } else {
                    _uiState.value = RainMapUiState.Error("No data available")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                (applicationContext as TheMeteo).container.telemetryManager.logException(e)
                _uiState.value = RainMapUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun fetchMetadataBounds(): RadarBounds {
        return try {
            val response = client.get("https://radar-images.19374629.xyz/metadata.bin")
            val bytes = response.body<ByteArray>()
            if (bytes.size >= 32) {
                val buffer = ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                RadarBounds(
                    north = buffer.double,
                    south = buffer.double,
                    west = buffer.double,
                    east = buffer.double
                )
            } else {
                RadarBounds()
            }
        } catch (e: Exception) {
            RadarBounds()
        }
    }

    override fun onCleared() {
        client.close()
        recycleBitmaps()
    }

    private fun recycleBitmaps() {
        val currentState = _uiState.value
        if (currentState is RainMapUiState.Success) {
            currentState.frames.forEach { frame ->
                frame.bitmap?.takeIf { !it.isRecycled }?.recycle()
            }
        }
    }

    private val radarCacheDir by lazy {
        File(applicationContext.cacheDir, "radar_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun cleanOldRadarCache() {
        try {
            val threeHoursAgo = System.currentTimeMillis() - (3 * 3600 * 1000L)
            radarCacheDir.listFiles()?.forEach { file ->
                if (file.lastModified() < threeHoursAgo) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("RainMapViewModel", "Error cleaning radar cache", e)
        }
    }

    private suspend fun fetchRadar(url: String, timestamp: Long): TimeFrame? {
        return try {
            cleanOldRadarCache()

            val fileName = url.substringAfterLast("/")
            val cachedFile = File(radarCacheDir, fileName)
            val threeHoursAgo = System.currentTimeMillis() - (3 * 3600 * 1000L)

            val bitmap: Bitmap = if (cachedFile.exists() && cachedFile.length() > 0 && cachedFile.lastModified() >= threeHoursAgo) {
                BitmapFactory.decodeFile(cachedFile.absolutePath) ?: run {
                    cachedFile.delete()
                    downloadAndCacheRadar(url, cachedFile)
                }
            } else {
                downloadAndCacheRadar(url, cachedFile)
            } ?: return null

            TimeFrame(
                time = timestamp,
                bitmap = bitmap,
                width = bitmap.width,
                height = bitmap.height
            )
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun downloadAndCacheRadar(url: String, targetFile: File): Bitmap? {
        return try {
            val response = client.get(url)
            val bytes = response.body<ByteArray>()
            if (bytes.isNotEmpty()) {
                targetFile.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    data class RadarFile(val url: String, val timestamp: Long)

    /**
     * Génère une liste de noms de fichiers radar basés sur l'heure actuelle.
     * Le dernier fichier est calculé selon la formule : roundToMod15(T - 15mn).
     *
     * @param currentTime L'heure de référence (par défaut l'heure actuelle).
     * @param count Le nombre de fichiers à générer (par défaut 8 images).
     * @return Une liste d'objets RadarFile.
     */
    fun getRadarFiles(
        currentTime: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
        count: Int = 8
    ): List<RadarFile> {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        val baseUrl = "https://radar-images.19374629.xyz/"

        var latestTime = currentTime.minusMinutes(15)
        val roundedMinute = (latestTime.minute / 15) * 15
        latestTime = latestTime.withMinute(roundedMinute).withSecond(0).withNano(0)

        return (0 until count).map { i ->
            val time = latestTime.minusMinutes((i * 15).toLong())
            val filename = "radar_${time.format(formatter)}00.webp"
            val timestamp = time.toEpochSecond(ZoneOffset.UTC)
            RadarFile(baseUrl + filename, timestamp)
        }.reversed() // Du plus ancien au plus récent
    }
}

data class RadarBounds(
    val north: Double = 51.50,
    val south: Double = 41.30,
    val west: Double = -5.50,
    val east: Double = 9.80
)

sealed class RainMapUiState {
    object Loading : RainMapUiState()
    data class Success(val frames: List<TimeFrame>, val bounds: RadarBounds = RadarBounds()) : RainMapUiState()
    data class Error(val message: String) : RainMapUiState()
}
