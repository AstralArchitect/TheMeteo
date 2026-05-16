/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.satImgs

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme

class DocActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weatherCache = (application as TheMeteo).weatherCache
        
        enableEdgeToEdge()
        setContent {
            val userSettings by weatherCache.userSettings.collectAsState()
            val currentWmo = remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(weatherCache.selectedLocation) {
                weatherCache.get(java.time.LocalDateTime.now(), 1).collect { state ->
                    currentWmo.value = when (state) {
                        is WeatherDataState.SuccessHourly -> state.data.firstOrNull()?.wmo
                        is WeatherDataState.Error -> (state.staleData as? WeatherDataState.SuccessHourly)?.data?.firstOrNull()?.wmo
                        else -> null
                    }
                }
            }

            TheMeteoTheme(
                themeMode = userSettings.themeMode,
                currentWmoCode = currentWmo.value,
                isNight = false
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Aide à l'interprétation") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        MyWebView()
                    }
                }
            }
        }
    }
}

@Composable
fun MyWebView() {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // Désactiver JS est une bonne pratique de sécurité si non nécessaire
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = true
                loadUrl("file:///android_asset/sat_doc/doc.html")
            }
        }
    )
}