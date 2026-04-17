/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.forecastMainActivity

import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Nature
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.SevereCold
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.strictmode.FragmentStrictMode
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import kotlin.math.roundToInt

@Preview()
@Composable
fun PolicyUpdateDialogPreview() {
    PolicyUpdateDialog(onAccept = {})
}

@Composable
fun AirQualityDetailsDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    val environmentalData by viewModel.environmentalData.collectAsState()
    val data = environmentalData ?: return

    // État du jour sélectionné
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val currentDay = data.days.getOrNull(selectedDayIndex) ?: data.days.first()

    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Color.Transparent else Color.Black.copy(
                    alpha = 0.6f
                )
            )
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.environmental_details),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // NAVIGATION PAR JOURS (TRENDS)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        data.days.forEachIndexed { index, day ->
                            val isSelected = index == selectedDayIndex
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedDayIndex = index },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (index == 0) stringResource(R.string.today) else day.dateLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    // Point de couleur représentant le jour
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(day.globalColor)
                                    )
                                }
                            }
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // SECTION 1 : QUALITÉ DE L'AIR
                        item {
                            EnvironmentalSectionHeader(
                                title = stringResource(R.string.air_quality),
                                icon = Icons.Rounded.Air,
                                color = currentDay.airQuality.color ?: MaterialTheme.colorScheme.outline
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val air = currentDay.airQuality
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(air.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${air.value} AQI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }

                                if (air.minAqi > 0 || air.maxAqi > 0) {
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            text = "${stringResource(R.string.min)}: ${air.minAqi}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${stringResource(R.string.max)}: ${air.maxAqi}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${stringResource(R.string.avg)}: ${air.avgAqi}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(air.color.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(
                                                (air.value.toFloat() / 100f).coerceIn(
                                                    0f,
                                                    1f
                                                )
                                            )
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(air.color)
                                    )
                                }
                            }
                        }

                        // Polluants
                        currentDay.airQuality.let { air ->
                            item {
                                Text(stringResource(R.string.air_components), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    air.pollutants.forEach { pollutant ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(
                                                1.dp,
                                                if (pollutant.color != Color.Gray) pollutant.color.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (pollutant.color != Color.Gray) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(pollutant.color)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Column {
                                                    Text(pollutant.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(pollutant.value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SECTION 2 : POLLEN (Toujours dispo via prévisions)
                        item {
                            EnvironmentalSectionHeader(
                                title = stringResource(R.string.pollen_risks),
                                icon = Icons.Rounded.Grain,
                                color = currentDay.pollen.color
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOfNotNull(
                                    Triple(currentDay.pollen.tree, stringResource(R.string.trees), Icons.Rounded.Nature),
                                    Triple(currentDay.pollen.grass, stringResource(R.string.weed), Icons.Rounded.LocalFlorist),
                                    Triple(currentDay.pollen.weed, stringResource(R.string.grasses), Icons.Rounded.Grass)
                                ).forEach { (type, name, icon) ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        Box {
                                            EnvironmentalGauge(
                                                value = (type?.level?.toFloat() ?: 0f) / 4f,
                                                color = type?.color ?: MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.size(80.dp)
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .offset(y = 6.dp)
                                            )
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .offset(y = (-11).dp),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "${type?.level ?: 0}/4",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        // Display a short description (ex: Low, Moderate, etc.)
                                        Text(
                                            text = getPollenShortDescFromLevel(type?.level ?: 0),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Specific plants components (types) and their levels/descriptions
                            if (currentDay.pollen.plants.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    currentDay.pollen.plants.forEach { plant ->
                                        val color = if(!isSystemInDarkTheme())
                                            plant.color.copy(red = plant.color.red * 0.5f, green = plant.color.green * 0.5f, blue = plant.color.blue * 0.5f)
                                        else plant.color
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = plant.color.copy(alpha = 0.1f),
                                            border = BorderStroke(1.dp, plant.color.copy(alpha = 0.3f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    ResponsiveText(
                                                        text = plant.name,
                                                        modifier = Modifier.weight(1f),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = color,
                                                        maxLines = 1
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "${plant.level}/4",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = color,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                                if (!plant.description.isNullOrEmpty() && plant.description != "null") {
                                                    Text(
                                                        text = plant.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SECTION 3 : CONSEILS SANTÉ (Basés sur l'air ou pollen du jour)
                        /*item {
                            EnvironmentalSectionHeader(
                                title = stringResource(R.string.health_advice),
                                icon = Icons.Rounded.HealthAndSafety,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val adviceList = currentDay.airQuality.healthAdvice ?: emptyList()
                            if (adviceList.isNotEmpty()) {
                                adviceList.forEach { advice ->
                                    HealthAdviceCard(advice.title, advice.advice)
                                }
                            } else {
                                HealthAdviceCard("Information", "Continuez à surveiller les indices pour adapter vos activités.")
                            }
                        }*/

                        item {
                            Text(
                                text = "Powered by Google Maps Platform",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
fun SunMoonDetailsDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    val dailyForecast by viewModel.dailyForecast.collectAsState()
    val context = LocalContext.current

    // Animation state
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    fun animateAndDismiss() {
        visibleState.targetState = false
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Color.Transparent else Color.Black.copy(
                    alpha = 0.6f
                )
            )
            .clickable { animateAndDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            Surface(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth()
                    .height(LocalConfiguration.current.screenHeightDp.dp * 0.8f)
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                ) {
                    Text(
                        "Sun Details",
                        //stringResource(R.string.sun_moon_details),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Sun Section
                        Column {
                            Text(stringResource(R.string.sun_path), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            SunPathVisualization(viewModel)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Moon Section
                        MoonDetailsSection(viewModel)

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        SunMoonCompass(viewModel)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { animateAndDismiss() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
fun PolicyUpdateDialog(onAccept: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var hasScrolledToBottom by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    val gcuUrl = "https://astralarchitect.github.io/TheMeteo-privacy-policy/terms.html"

    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.policy_update_title)) },
        text = {
            Column {
                Text(stringResource(R.string.policy_update_message), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        hasError = false
                                        hasScrolledToBottom = false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        // If content is small and doesn't scroll, it might already be at bottom
                                        /*if (!canScrollVertically(1)) {
                                            hasScrolledToBottom = true
                                        }*/
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        isLoading = false
                                        hasError = true
                                    }
                                }
                                setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
                                    if (!canScrollVertically(1)) {
                                        hasScrolledToBottom = true
                                    }
                                }
                                loadUrl(gcuUrl)
                            }
                        },
                        update = { webView ->
                            if (refreshTrigger > 0) {
                                webView.loadUrl(gcuUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    if (hasError) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.gcu_content_error),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Button(onClick = { refreshTrigger++ }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                if (!hasScrolledToBottom && !isLoading && !hasError) {
                    Text(
                        text = stringResource(R.string.gcu_scroll_to_bottom),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                enabled = !isLoading && !hasError && hasScrolledToBottom
            ) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = {

        }
    )
}

@Composable
fun WeatherDetailsDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    val actualReading =
        (viewModel.hourlyForecast.collectAsState().value as? WeatherDataState.SuccessHourly)?.data?.first()
            ?: return

    val userSettings by viewModel.userSettings.collectAsState()

    // Créer un état pour gérer l'animation de visibilité
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    // Fonction de fermeture qui attend la fin de l'animation
    val scope = rememberCoroutineScope()
    fun animateAndDismiss() {
        visibleState.targetState = false
        onDismiss()
    }

    val details = listOfNotNull(
        actualReading.temperature?.let {
            WeatherDetailItem(
                Icons.Rounded.Thermostat,
                stringResource(R.string.temperature),
                UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
            )
        },
        actualReading.apparentTemperature?.let {
            WeatherDetailItem(
                Icons.Rounded.DeviceThermostat,
                stringResource(R.string.a_temperature_unit),
                UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
            )
        },
        actualReading.skyInfo.uvIndex?.let { uv ->
            WeatherDetailItem(
                Icons.Rounded.WbSunny,
                stringResource(R.string.uv_index),
                "$uv",
                getUVDescription(uv)
            )
        },
        actualReading.dewpoint?.let {
            WeatherDetailItem(
                Icons.Rounded.Water,
                stringResource(R.string.dew_point),
                UnitConverter.formatTemperature(it, userSettings.temperatureUnit, userSettings.roundToInt)
            )
        },
        actualReading.humidity?.let { hu ->
            WeatherDetailItem(Icons.Rounded.WaterDrop, stringResource(R.string.humidity), "${hu.toSmartString()}%")
        },
        actualReading.precipitationData.precipitation?.let { precip ->
            val subValue = buildString {
                actualReading.precipitationData.precipitationProbability?.let { append("Prob: $it% ") }
                actualReading.precipitationData.rain?.takeIf { it > 0 }?.let {
                    if (isNotEmpty() && !endsWith(" ")) append("\n")
                    append("Rain: ${it.toSmartString()} mm ")
                }
                actualReading.precipitationData.snowfall?.takeIf { it > 0 }?.let {
                    if (isNotEmpty() && !endsWith(" ")) append("\n")
                    append("Snow: ${it.toSmartString()} cm")
                }
            }.trim()
            WeatherDetailItem(
                Icons.Rounded.Umbrella,
                stringResource(R.string.precipitation),
                "${precip.toSmartString()} mm",
                subValue.takeIf { it.isNotEmpty() }
            )
        },
        actualReading.precipitationData.snowDepth?.takeIf { it > 0 }?.let {
            WeatherDetailItem(Icons.Rounded.SevereCold, stringResource(R.string.snow_depth), "${it.toSmartString()} cm")
        },
        actualReading.wind.windspeed?.let { ws ->
            val subValue = buildString {
                actualReading.wind.windDirection?.let { append("Direction: $it°") }
                actualReading.wind.windGusts?.let {
                    if (isNotEmpty()) append("\n")
                    append("${stringResource(R.string.gusts)}: ${UnitConverter.formatWind(it, userSettings.windUnit)}")
                }
            }
            WeatherDetailItem(
                Icons.Rounded.Air,
                stringResource(R.string.wind_speed),
                UnitConverter.formatWind(ws, userSettings.windUnit),
                subValue.takeIf { it.isNotEmpty() }
            )
        },
        actualReading.pressure?.let {
            WeatherDetailItem(Icons.Rounded.Compress, stringResource(R.string.pressure), "${it.toSmartString()} hPa")
        },
        actualReading.skyInfo.cloudcoverTotal?.let { cct ->
            // If low cloud cover is available, all levels are too
            val subValue = if (actualReading.skyInfo.cloudcoverLow != null) {
                "Low: ${actualReading.skyInfo.cloudcoverLow.toSmartString()}%\nMid: ${actualReading.skyInfo.cloudcoverMid?.toSmartString()}%\nHigh: ${actualReading.skyInfo.cloudcoverHigh?.toSmartString()}%"
            } else null
            WeatherDetailItem(Icons.Rounded.Cloud, stringResource(R.string.cloud_cover), "${cct.toSmartString()}%", subValue)
        },
        actualReading.skyInfo.opacity?.let { op ->
            WeatherDetailItem(
                Icons.Rounded.Opacity,
                stringResource(R.string.opacity),
                "${op.toSmartString()}%",
                actualReading.skyInfo.shortwaveRadiation?.let { "Radiation: ${it.roundToInt()} W/m²" }
            )
        },
        actualReading.skyInfo.visibility?.let { vis ->
            val visibility = if (vis < 1000) vis else (vis.toDouble() / 1000.0).roundToInt()
            val unit = if (vis >= 1000) "km" else "m"
            WeatherDetailItem(Icons.Rounded.Visibility, stringResource(R.string.visibility), "$visibility $unit")
        }
    )

    // 1. LE SCRIM (Voile de fond)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Color.Transparent else Color.Black.copy(
                    alpha = 0.6f
                )
            )
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        // Utiliser AnimatedVisibility pour le contenu
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn() + scaleIn(initialScale = 0.8f), // Zoom progressif
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            // 2. LE CONTENU DU DIALOGUE (Animation de zoom)
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .clickable(enabled = false) { } // Empêche de fermer en cliquant sur le blanc
                    .animateEnterExit(
                        enter = scaleIn(initialScale = 0.8f) + fadeIn(),
                        exit = scaleOut(targetScale = 0.8f) + fadeOut()
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface, // Couleur Material You pour le Dialog
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            stringResource(R.string.weather_details),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(details) { detail ->
                                WeatherDetailCard(detail)
                            }
                        }

                        TextButton(
                            onClick = { animateAndDismiss() },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(top = 16.dp)
                        ) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }
    }
}