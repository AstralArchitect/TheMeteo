/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherCache
import fr.matthstudio.themeteo.forecastMainActivity.getPollenShortDescFromLevel
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.widget.DailyWeatherWidget
import fr.matthstudio.themeteo.widget.WeatherWidget
import fr.matthstudio.themeteo.widget.WidgetUtils
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
            val userSettings by weatherCache.userSettings.collectAsState()
            val currentWmo = remember { mutableStateOf<Int?>(null) }

            LaunchedEffect(weatherCache.selectedLocation) {
                weatherCache.get(java.time.LocalDateTime.now(), 1).collect { state ->
                    currentWmo.value = when (state) {
                        is fr.matthstudio.themeteo.WeatherDataState.SuccessHourly -> state.data.firstOrNull()?.wmo
                        is fr.matthstudio.themeteo.WeatherDataState.Error -> (state.staleData as? fr.matthstudio.themeteo.WeatherDataState.SuccessHourly)?.data?.firstOrNull()?.wmo
                        else -> null
                    }
                }
            }

            TheMeteoTheme(
                themeMode = userSettings.themeMode,
                currentWmoCode = currentWmo.value,
                isNight = false
            ) {
                WidgetSettingsScreen(
                    weatherCache = weatherCache,
                    appWidgetId = appWidgetId,
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
    weatherCache: WeatherCache,
    appWidgetId: Int,
    onBack: () -> Unit
) {
    val settings by weatherCache.userSettings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var selectedTheme by remember { mutableStateOf(WidgetUtils.THEME_SYSTEM) }
    var transparency by remember { mutableIntStateOf(0) }
    var textSize by remember { mutableIntStateOf(0) }

    // Load current settings if they exist
    LaunchedEffect(appWidgetId) {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            val prefs = getAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            )
            prefs[WidgetUtils.KEY_COLOR_THEME]?.let { selectedTheme = it }
            prefs[WidgetUtils.KEY_TRANSPARENCY]?.let { transparency = it }
            prefs[WidgetUtils.KEY_TEXT_SIZE]?.let { textSize = it }
        }
    }

    fun triggerWidgetUpdate() {
        scope.launch {
            WeatherWidget().updateAll(context)
            DailyWeatherWidget().updateAll(context)
        }
    }

    fun saveThemeToWidget(theme: String) {
        selectedTheme = theme
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            scope.launch {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetUtils.KEY_COLOR_THEME] = theme
                }
                WeatherWidget().update(context, glanceId)
                DailyWeatherWidget().update(context, glanceId)
            }
        }
    }

    fun saveTransparencyToWidget(transp: Int) {
        transparency = transp
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            scope.launch {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetUtils.KEY_TRANSPARENCY] = transparency
                }
                WeatherWidget().update(context, glanceId)
                DailyWeatherWidget().update(context, glanceId)
            }
        }
    }

    fun saveTextSizeToWidget(textS: Int) {
        textSize = textS
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            scope.launch {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetUtils.KEY_TEXT_SIZE] = textSize
                }
                WeatherWidget().update(context, glanceId)
                DailyWeatherWidget().update(context, glanceId)
            }
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
                },
                actions = {
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.Check, contentDescription = "Done")
                        }
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
            // --- THEME SELECTION ---
            Text(
                text = "Widget Theme",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ThemeOption("System", Color.Gray, selectedTheme == WidgetUtils.THEME_SYSTEM) { saveThemeToWidget(WidgetUtils.THEME_SYSTEM) }
                ThemeOption("System Inv.", Color.LightGray, selectedTheme == WidgetUtils.THEME_SYSTEM_INVERTED) { saveThemeToWidget(
                    WidgetUtils.THEME_SYSTEM_INVERTED) }
                ThemeOption("Blue", Color(0xFF2196F3), selectedTheme == WidgetUtils.THEME_BLUE) { saveThemeToWidget(WidgetUtils.THEME_BLUE) }
                ThemeOption("Green", Color(0xFF4CAF50), selectedTheme == WidgetUtils.THEME_GREEN) { saveThemeToWidget(WidgetUtils.THEME_GREEN) }
                ThemeOption("Warm", Color(0xFFFF9800), selectedTheme == WidgetUtils.THEME_WARM) { saveThemeToWidget(WidgetUtils.THEME_WARM) }
                ThemeOption("Dark", Color(0xFF212121), selectedTheme == WidgetUtils.THEME_DARK) { saveThemeToWidget(WidgetUtils.THEME_DARK) }
                ThemeOption("Light", Color(0xFFF5F5F5), selectedTheme == WidgetUtils.THEME_LIGHT) { saveThemeToWidget(WidgetUtils.THEME_LIGHT) }
            }

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
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF4B6CB7), Color(0xFF182848))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val previewLocationName = when (val loc = settings.defaultLocation) {
                        is LocationIdentifier.CurrentUserLocation -> stringResource(R.string.current_location)
                        is LocationIdentifier.Saved -> loc.location.name
                    }
                    WidgetPreviewCard(
                        title = "Current Weather (2x1)",
                        locationName = previewLocationName,
                        transparency = transparency,
                        textSizeIndex = textSize,
                        isDaily = false,
                        theme = selectedTheme
                    )
                    WidgetPreviewCard(
                        title = "Daily Forecast (3x2)",
                        locationName = previewLocationName,
                        transparency = transparency,
                        textSizeIndex = textSize,
                        isDaily = true,
                        theme = selectedTheme
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- CONTROLS SECTION ---
            Text(
                text = "${stringResource(R.string.widget_transparency)}: ${transparency}%",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Slider(
                value = transparency.toFloat(),
                onValueChange = { 
                    scope.launch { 
                        saveTransparencyToWidget(it.toInt())
                        triggerWidgetUpdate()
                    }
                },
                valueRange = 0f..100f,
                steps = 10
            )

            Spacer(modifier = Modifier.height(24.dp))

            val textSizeLabel = when(textSize) {
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
                value = textSize.toFloat(),
                onValueChange = { 
                    scope.launch { 
                        saveTextSizeToWidget(it.toInt())
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
fun ThemeOption(label: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color, RoundedCornerShape(20.dp))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun WidgetPreviewCard(title: String, locationName: String, transparency: Int, textSizeIndex: Int, isDaily: Boolean, theme: String) {
    val alpha = (100 - transparency) / 100f
    
    val backgroundColor = when(theme) {
        WidgetUtils.THEME_BLUE -> Color(0xFFE3F2FD)
        WidgetUtils.THEME_GREEN -> Color(0xFFE8F5E9)
        WidgetUtils.THEME_WARM -> Color(0xFFFFF3E0)
        WidgetUtils.THEME_DARK -> Color(0xFF1C1B1F)
        WidgetUtils.THEME_LIGHT -> Color(0xFFF9FAEF)
        WidgetUtils.THEME_SYSTEM -> MaterialTheme.colorScheme.secondaryContainer
        WidgetUtils.THEME_SYSTEM_INVERTED -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when(theme) {
        WidgetUtils.THEME_DARK -> Color.White
        WidgetUtils.THEME_LIGHT -> Color.Black
        WidgetUtils.THEME_BLUE, WidgetUtils.THEME_GREEN, WidgetUtils.THEME_WARM -> 
            if (transparency < 50) Color.Black else Color.White
        WidgetUtils.THEME_SYSTEM -> if (transparency < 75) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
        WidgetUtils.THEME_SYSTEM_INVERTED -> if (transparency > 75) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
        else -> Color.White
    }

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
            color = backgroundColor.copy(alpha = alpha),
            contentColor = contentColor,
            shape = RoundedCornerShape(28.dp)
        ) {
            if (!isDaily) {
                // Small Widget Preview
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(locationName, style = MaterialTheme.typography.labelSmall.copy(fontSize = (baseTextSize.value - 2).sp, fontWeight = FontWeight.Medium))
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
                    Text(locationName, style = MaterialTheme.typography.labelSmall.copy(fontSize = baseTextSize, fontWeight = FontWeight.Bold))
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
