package fr.matthstudio.themeteo.satImgs

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme

class DocActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge()
        setContent {
            TheMeteoTheme {
                Surface {
                    MyWebView(modifier = Modifier.safeDrawingPadding())
                }
            }
        }
    }
}

@Composable
fun MyWebView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView.setWebContentsDebuggingEnabled(true) // Activer le débogage
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = true
                loadUrl("file:///android_asset/sat_doc/doc.html")
            }
        }
    )
}