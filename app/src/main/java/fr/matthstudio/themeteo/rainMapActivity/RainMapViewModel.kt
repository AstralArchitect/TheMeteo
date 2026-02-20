package fr.matthstudio.themeteo.rainMapActivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class RainMapViewModel : ViewModel() {

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
                val allFrames = response.radar.past + response.radar.nowcast
                
                if (allFrames.isNotEmpty()) {
                    _uiState.value = RainMapUiState.Success(
                        host = response.host,
                        frames = allFrames
                    )
                } else {
                    _uiState.value = RainMapUiState.Error("No data available")
                }
            } catch (e: Exception) {
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
    data class Success(val host: String, val frames: List<TimeFrame>) : RainMapUiState()
    data class Error(val message: String) : RainMapUiState()
}
