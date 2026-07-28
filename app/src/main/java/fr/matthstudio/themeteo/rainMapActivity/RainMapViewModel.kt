/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json

class RainMapViewModel(private val applicationContext: Application) : ViewModel() {

    private val weatherCache = (applicationContext as TheMeteo).weatherCache
    val userSettings = weatherCache.userSettings

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentWmo: StateFlow<Int?> = combine(
        weatherCache.selectedLocation,
        weatherCache.userSettings
    ) { _, _ ->
    }.flatMapLatest {
        weatherCache.get(LocalDateTime.now(), 1)
    }.map { state ->
        when (state) {
            is WeatherDataState.SuccessHourly -> state.data.firstOrNull()?.wmo
            is WeatherDataState.Error -> (state.staleData as? WeatherDataState.SuccessHourly)?.data?.firstOrNull()?.wmo
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _uiState = MutableStateFlow<RainMapUiState>(RainMapUiState.Loading)
    val uiState: StateFlow<RainMapUiState> = _uiState.asStateFlow()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private companion object {
        const val RADAR_DOWNSAMPLE_FACTOR = 2
        const val ORIGINAL_SIZE = 3472
        const val DOWNSAMPLED_SIZE = ORIGINAL_SIZE / RADAR_DOWNSAMPLE_FACTOR
    }

    // Pool de mémoire pour 8 images de DOWNSAMPLED_SIZE x DOWNSAMPLED_SIZE pixels (ARGB_8888 = 4 bytes par pixel)
    // Réduit à 8 pour limiter la pression sur la RAM totale du système.
    private val memoryPool = RadarMemoryPool(DOWNSAMPLED_SIZE * DOWNSAMPLED_SIZE * 4, 8)

    // Bitmap réutilisable pour le décodage (évite la fragmentation du tas JVM)
    private var reusableBitmap: Bitmap? = null

    init {
        fetchAvailableTimestamps()
    }

    private fun fetchAvailableTimestamps() {
        viewModelScope.launch {
            try {
                _uiState.value = RainMapUiState.Loading

                // On limite à 8 frames pour économiser la mémoire
                val files = getRadarFiles(count = 8)
                val images = mutableListOf<TimeFrame>()

                for (file in files) {
                    val frame = fetchRadar(file.url, file.timestamp)
                    if (frame != null) {
                        images.add(frame)
                    } else {
                        break
                    }
                    // Laisser le système souffler entre deux gros téléchargements
                    yield()
                }
                
                for (frame in images) {
                    frame.buffer?.let {
                        RadarProcessor.processImage(it, frame.width, frame.height)
                    }
                }
                
                if (images.isNotEmpty()) {
                    _uiState.value = RainMapUiState.Success(
                        frames = images
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

    override fun onCleared() {
        client.close()
        memoryPool.clear()
        reusableBitmap?.recycle()
        reusableBitmap = null
    }
    
    private suspend fun fetchRadar(url: String, timestamp: Long): TimeFrame? {
        return try {
            // 1. Streaming au lieu de readBytes()
            val response = client.get(url)
            val channel = response.bodyAsChannel()
            
            val buffer = memoryPool.acquire()

            // 2. Configuration du décodage avec réutilisation
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
                inSampleSize = RADAR_DOWNSAMPLE_FACTOR
                // Si on a déjà une bitmap de la bonne taille, on la réutilise
                reusableBitmap?.let { 
                    if (it.width == DOWNSAMPLED_SIZE && it.height == DOWNSAMPLED_SIZE) {
                        inBitmap = it
                    }
                }
            }

            val inputStream = channel.toInputStream()
            val tempBitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return null
            
            // Stocker pour la prochaine fois
            if (reusableBitmap == null) {
                reusableBitmap = tempBitmap
            }
            
            // 3. Transfert vers le buffer natif
            buffer.rewind()
            tempBitmap.copyPixelsToBuffer(buffer)
            
            val width = tempBitmap.width
            val height = tempBitmap.height

            TimeFrame(
                time = timestamp,
                buffer = buffer,
                width = width,
                height = height
            )
        } catch (e: Exception) {
            null
        }
    }

    data class RadarFile(val url: String, val timestamp: Long)

    /**
     * Génère une liste de noms de fichiers radar basés sur l'heure actuelle.
     * Le dernier fichier est calculé selon la formule : roundToMod5(T - 6mn).
     *
     * @param currentTime L'heure de référence (par défaut l'heure actuelle).
     * @param count Le nombre de fichiers à générer (par défaut 12 pour 1 heure).
     * @return Une liste d'objets RadarFile.
     */
    fun getRadarFiles(
        currentTime: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
        count: Int = 8
    ): List<RadarFile> {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")
        val baseUrl = "https://radar-images.19374629.xyz/"

        // Calcul du dernier timestamp disponible : roundToMod5(T - 5
        // )
        var latestTime = currentTime.minusMinutes(5)
        val roundedMinute = (latestTime.minute / 5) * 5
        latestTime = latestTime.withMinute(roundedMinute).withSecond(0).withNano(0)

        return (0 until count).map { i ->
            val time = latestTime.minusMinutes((i * 5).toLong())
            val filename = "radar_${time.format(formatter)}00.png"
            val timestamp = time.toEpochSecond(ZoneOffset.UTC)
            RadarFile(baseUrl + filename, timestamp)
        }.reversed() // Du plus ancien au plus récent
    }
}

sealed class RainMapUiState {
    object Loading : RainMapUiState()
    data class Success(val frames: List<TimeFrame>) : RainMapUiState()
    data class Error(val message: String) : RainMapUiState()
}
