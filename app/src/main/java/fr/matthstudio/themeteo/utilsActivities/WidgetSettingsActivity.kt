package fr.matthstudio.themeteo.utilsActivities

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.widget.DailyWeatherWidget
import fr.matthstudio.themeteo.widget.WeatherWidget
import kotlinx.coroutines.launch

class WidgetSettingsActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
        }

        enableEdgeToEdge()
        val weatherCache = (application as TheMeteo).weatherCache
        
        setContent {
            TheMeteoTheme {
                WidgetSettingsScreen(
                    weatherCache = weatherCache,
                    onBack = { 
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            setResult(RESULT_OK, resultValue)
                        }
                        finish() 
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    weatherCache: fr.matthstudio.themeteo.WeatherCache,
    onBack: () -> Unit
) {
    val settings by weatherCache.userSettings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun triggerWidgetUpdate() {
        scope.launch {
            WeatherWidget().updateAll(context)
            DailyWeatherWidget().updateAll(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- PREVIEW SECTION ---
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 16.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(Color(0xFF4B6CB7), Color(0xFF182848))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WidgetPreviewCard(
                        title = "Current Weather (2x1)",
                        transparency = settings.widgetTransparency,
                        textSizeIndex = settings.widgetTextSize,
                        isDaily = false
                    )
                    WidgetPreviewCard(
                        title = "Daily Forecast (3x2)",
                        transparency = settings.widgetTransparency,
                        textSizeIndex = settings.widgetTextSize,
                        isDaily = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- CONTROLS SECTION ---
            Text(
                text = "${stringResource(R.string.widget_transparency)}: ${settings.widgetTransparency}%",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Slider(
                value = settings.widgetTransparency.toFloat(),
                onValueChange = { 
                    scope.launch { 
                        weatherCache.userSettingsRepository.updateWidgetTransparency(it.toInt()) 
                        triggerWidgetUpdate()
                    }
                },
                valueRange = 0f..100f,
                steps = 10
            )

            Spacer(modifier = Modifier.height(24.dp))

            val textSizeLabel = when(settings.widgetTextSize) {
                0 -> stringResource(R.string.text_size_small)
                1 -> stringResource(R.string.text_size_medium)
                else -> stringResource(R.string.text_size_large)
            }
            Text(
                text = "${stringResource(R.string.widget_text_size)}: $textSizeLabel",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Slider(
                value = settings.widgetTextSize.toFloat(),
                onValueChange = { 
                    scope.launch { 
                        weatherCache.userSettingsRepository.updateWidgetTextSize(it.toInt()) 
                        triggerWidgetUpdate()
                    }
                },
                valueRange = 0f..2f,
                steps = 1
            )
        }
    }
}

@Composable
fun WidgetPreviewCard(title: String, transparency: Int, textSizeIndex: Int, isDaily: Boolean) {
    val alpha = (100 - transparency) / 100f
    val baseTextSize = when(textSizeIndex) {
        0 -> 10.sp
        1 -> 12.sp
        else -> 14.sp
    }
    val bigTextSize = when(textSizeIndex) {
        0 -> 18.sp
        1 -> 22.sp
        else -> 28.sp
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 4.dp))
        Surface(
            modifier = if (isDaily) Modifier.size(width = 240.dp, height = 160.dp) else Modifier.size(width = 180.dp, height = 90.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 4.dp
        ) {
            if (!isDaily) {
                // Small Widget Preview
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Paris", style = MaterialTheme.typography.labelSmall.copy(fontSize = (baseTextSize.value - 2).sp, fontWeight = FontWeight.Medium))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(id = R.drawable.clear_day), contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Unspecified)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("22°", style = MaterialTheme.typography.headlineMedium.copy(fontSize = bigTextSize, fontWeight = FontWeight.Bold))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Rounded.Air, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFAED581))
                                Text(" 12", style = MaterialTheme.typography.labelSmall.copy(fontSize = baseTextSize))
                            }
                        }
                    }
                }
            } else {
                // Daily Widget Preview
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Paris", style = MaterialTheme.typography.labelSmall.copy(fontSize = baseTextSize, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    repeat(3) { // Show 3 days in preview
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Mon.", style = MaterialTheme.typography.labelSmall.copy(fontSize = baseTextSize), modifier = Modifier.width(35.dp))
                            Icon(painter = painterResource(id = R.drawable.clear_day), contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Unspecified)
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(40.dp)) {
                                Icon(imageVector = Icons.Rounded.WaterDrop, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color(0xFF64B5F6))
                                Text("0.5", style = MaterialTheme.typography.labelSmall.copy(fontSize = (baseTextSize.value - 2).sp))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text("24° / 15°", style = MaterialTheme.typography.labelSmall.copy(fontSize = baseTextSize))
                        }
                    }
                }
            }
        }
    }
}
