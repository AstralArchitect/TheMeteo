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
import fr.matthstudio.themeteo.TheMeteo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class RainMapViewModel(private val applicationContext: Application) : ViewModel() {

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
                val response = client.get("https://api.rainviewer.com/public/weather-maps.json")
                    .body<RainViewerResponse>()

                // Combine past and nowcast
                val allPastFrames = response.radar.past.map { TimeFrame (
                    time = it.time,
                    path = it.path,
                    isForecast = false
                ) }
                val allForecastFrames = response.radar.nowcast.map { TimeFrame (
                    time = it.time,
                    path = it.path,
                    isForecast = true
                ) }
                val allFrames = allPastFrames + allForecastFrames
                
                if (allFrames.isNotEmpty()) {
                    _uiState.value = RainMapUiState.Success(
                        host = response.host,
                        frames = allFrames,
                        lastPastIndex = response.radar.past.size - 1
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
        super.onCleared()
        client.close()
    }
}

sealed class RainMapUiState {
    object Loading : RainMapUiState()
    data class Success(val host: String, val frames: List<TimeFrame>, val lastPastIndex: Int) : RainMapUiState()
    data class Error(val message: String) : RainMapUiState()
}
