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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    init {
        fetchAvailableTimestamps()
    }

    private fun fetchAvailableTimestamps() {
        viewModelScope.launch {
            try {
                _uiState.value = RainMapUiState.Loading

                val boundsDeferred = async { fetchMetadataBounds() }

                val files = getRadarFiles(count = 10)
                val imageDeferreds = files.map { file ->
                    async { fetchRadar(file.url, file.timestamp) }
                }

                val bounds = boundsDeferred.await()
                val fetchedFrames = imageDeferreds.awaitAll().filterNotNull()

                if (fetchedFrames.isNotEmpty()) {
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
    }

    private suspend fun fetchRadar(url: String, timestamp: Long): TimeFrame? {
        return try {
            val response = client.get(url)
            val inputStream = response.bodyAsChannel().toInputStream()

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return null

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

        var latestTime = currentTime.minusMinutes(5)
        val roundedMinute = (latestTime.minute / 5) * 5
        latestTime = latestTime.withMinute(roundedMinute).withSecond(0).withNano(0)

        return (0 until count).map { i ->
            val time = latestTime.minusMinutes((i * 5).toLong())
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
