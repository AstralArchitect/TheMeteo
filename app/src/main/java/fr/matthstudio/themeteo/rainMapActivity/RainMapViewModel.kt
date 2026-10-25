/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.UserSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RainMapViewModel(private val applicationContext: Application) : ViewModel() {

    val userSettings: StateFlow<UserSettings?> = AppStateManager.userSettings

    val currentWmo: StateFlow<Int?> = AppStateManager.currentWmoCode

    private val _uiState = MutableStateFlow<RainMapUiState>(RainMapUiState.Loading)
    val uiState: StateFlow<RainMapUiState> = _uiState.asStateFlow()

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    init {
        fetchAvailableTimestamps()
    }

    companion object {
        private const val TAG = "RainMapViewModel"
        private const val TARGET_IMAGE_COUNT = 8
        private const val CANDIDATE_IMAGE_COUNT = 16
        private const val FETCH_BATCH_SIZE = 4

        fun Factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RainMapViewModel(application) as T
                }
            }
    }

    private data class MetadataResult(
        val bounds: RadarBounds,
        val serverTimeUtc: LocalDateTime?
    )

    fun refresh() {
        fetchAvailableTimestamps()
    }

    private fun fetchAvailableTimestamps() {
        viewModelScope.launch {
            try {
                val previousFrames = (_uiState.value as? RainMapUiState.Success)?.frames
                _uiState.value = RainMapUiState.Loading

                val (bounds, fetchedFrames) = withContext(Dispatchers.IO) {
                    cleanOldRadarCache()

                    val metadata = fetchMetadataBounds()
                    val referenceTime = metadata.serverTimeUtc ?: LocalDateTime.now(ZoneOffset.UTC)

                    // Génère les candidats du plus récent au plus ancien et s'arrête dès que 8 images valides sont obtenues
                    // afin d'éviter de télécharger et décoder inutilement les 6+ images plus anciennes.
                    val newestFirstCandidates = getRadarFiles(
                        currentTime = referenceTime,
                        count = CANDIDATE_IMAGE_COUNT
                    ).asReversed()

                    val collected = mutableListOf<TimeFrame>()
                    for (batch in newestFirstCandidates.chunked(FETCH_BATCH_SIZE)) {
                        if (collected.size >= TARGET_IMAGE_COUNT) break
                        val batchResults = batch.map { file ->
                            async { fetchRadar(file.url, file.timestamp) }
                        }.awaitAll().filterNotNull()
                        collected.addAll(batchResults)
                    }

                    // Trie du plus ancien au plus récent et recycle tout surplus éventuel du dernier lot
                    val sorted = collected.sortedBy { it.time }
                    if (sorted.size > TARGET_IMAGE_COUNT) {
                        val droppedCount = sorted.size - TARGET_IMAGE_COUNT
                        sorted.take(droppedCount).forEach { frame ->
                            frame.bitmap?.takeIf { !it.isRecycled }?.recycle()
                        }
                    }
                    metadata.bounds to sorted.takeLast(TARGET_IMAGE_COUNT)
                }

                if (fetchedFrames.isNotEmpty()) {
                    previousFrames?.forEach { it.bitmap?.takeIf { !it.isRecycled }?.recycle() }
                    _uiState.value = RainMapUiState.Success(
                        frames = fetchedFrames,
                        bounds = bounds
                    )
                } else {
                    _uiState.value = RainMapUiState.Error("No data available")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load radar timestamps", e)
                (applicationContext as TheMeteo).container.telemetryManager.logException(e)
                _uiState.value = RainMapUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun fetchMetadataBounds(): MetadataResult {
        return try {
            val response = client.get("https://radar-images.19374629.xyz/metadata.bin") {
                header(HttpHeaders.AcceptEncoding, "identity")
            }
            val serverTime = response.headers[HttpHeaders.Date]?.let { dateHeader ->
                runCatching {
                    ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime()
                }.getOrNull()
            }

            if (!response.status.isSuccess()) {
                Log.w(TAG, "metadata.bin returned HTTP ${response.status.value}")
                return MetadataResult(RadarBounds(), serverTime)
            }

            val bytes = response.readRawBytes()
            if (bytes.size >= 32) {
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val north = buffer.double
                val south = buffer.double
                val west = buffer.double
                val east = buffer.double
                if (north.isFinite() && south.isFinite() && west.isFinite() && east.isFinite() && north > south) {
                    MetadataResult(
                        bounds = RadarBounds(north = north, south = south, west = west, east = east),
                        serverTimeUtc = serverTime
                    )
                } else {
                    MetadataResult(RadarBounds(), serverTime)
                }
            } else {
                MetadataResult(RadarBounds(), serverTime)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Error fetching radar metadata.bin: ${e.message}")
            MetadataResult(RadarBounds(), null)
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
                if (file.lastModified() < threeHoursAgo || file.length() == 0L || file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning radar cache", e)
        }
    }

    private suspend fun fetchRadar(url: String, timestamp: Long): TimeFrame? {
        return try {
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
            if (e is CancellationException) throw e
            Log.w(TAG, "Error fetching radar frame $url: ${e.message}")
            null
        }
    }

    private suspend fun downloadAndCacheRadar(url: String, targetFile: File): Bitmap? {
        return try {
            val response = client.get(url) {
                header(HttpHeaders.AcceptEncoding, "identity")
            }
            if (!response.status.isSuccess()) {
                return null
            }
            // Utilise readRawBytes() au lieu de body<ByteArray>() pour éviter le crash Ktor checkContentLength
            // lorsque OkHttp décompresse de manière transparente une réponse gzip/brotli.
            val bytes = response.readRawBytes()
            if (bytes.isEmpty()) {
                return null
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            // Écriture atomique uniquement après validation du décodage WebP
            val tempFile = File(radarCacheDir, "${targetFile.name}.${System.nanoTime()}.tmp")
            runCatching {
                tempFile.writeBytes(bytes)
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }.onFailure {
                tempFile.delete()
            }
            bitmap
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed downloading radar image $url: ${e.message}")
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
