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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme

class DocActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TheMeteoTheme {
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