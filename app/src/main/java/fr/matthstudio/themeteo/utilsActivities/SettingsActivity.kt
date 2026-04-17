package fr.matthstudio.themeteo.utilsActivities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.matthstudio.themeteo.BuildConfig
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherCache
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.getDisplayName
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On récupère le cache directement depuis l'application
        val weatherCache = (application as TheMeteo).weatherCache

        setContent {
            TheMeteoTheme {
                // On passe le cache à l'écran des paramètres
                SettingsScreen(cache = weatherCache)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(cache: WeatherCache) {
    val activity = LocalActivity.current
    // On collecte l'état des paramètres depuis le cache
    val userSettings by cache.userSettings.collectAsState()
    val selectedLocation by cache.selectedLocation.collectAsState()
    val gpsPosition by cache.currentGpsPosition.collectAsState()

    // On calcule les coordonnées pour filtrer les modèles
    val currentCoords = when (val loc = selectedLocation) {
        is fr.matthstudio.themeteo.LocationIdentifier.CurrentUserLocation -> gpsPosition
        is fr.matthstudio.themeteo.LocationIdentifier.Saved -> GpsCoordinates(loc.location.latitude, loc.location.longitude)
    }

    // On a besoin d'une coroutine scope pour appeler les fonctions suspend du repository
    val scope = rememberCoroutineScope()
    var showEnsembleDialog by remember { mutableStateOf(false) }
    var showCrashlyticsDialog by remember { mutableStateOf(false) }

    if (showEnsembleDialog) {
        AlertDialog(
            onDismissRequest = { showEnsembleDialog = false },
            title = { Text(stringResource(R.string.ensemble_warning_title)) },
            text = { Text(stringResource(R.string.ensemble_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        cache.userSettingsRepository.updateForecastType(ForecastType.ENSEMBLE)
                        cache.userSettingsRepository.updateModel("ecmwf_ifs025_ensemble")
                    }
                    showEnsembleDialog = false
                }) {
                    Text(stringResource(R.string.ensemble_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnsembleDialog = false }) {
                    Text(stringResource(R.string.ensemble_warning_cancel))
                }
            }
        )
    }

    if (showCrashlyticsDialog) {
        AlertDialog(
            onDismissRequest = { showCrashlyticsDialog = false },
            title = { Text(stringResource(R.string.firebase_crashlytics_dialog_title)) },
            text = { Text(stringResource(R.string.firebase_crashlytics_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        cache.userSettingsRepository.updateFirebaseConsent("GRANTED")
                    }
                    showCrashlyticsDialog = false
                }) {
                    Text(stringResource(R.string.ensemble_warning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCrashlyticsDialog = false }) {
                    Text(stringResource(R.string.ensemble_warning_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back Arrow"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ModelSelectionSetting(
                currentModel = userSettings.model,
                availableModels = if (currentCoords != null) 
                    WeatherModelRegistry.getAvailableModels(currentCoords.latitude, currentCoords.longitude, userSettings.forecastType == ForecastType.ENSEMBLE)
                else 
                    WeatherModelRegistry.models.filter { it.isGlobal && it.isEnsemble == (userSettings.forecastType == ForecastType.ENSEMBLE) },
                onModelSelected = { newModel ->
                    scope.launch {
                        // On met à jour via le repository contenu dans le cache
                        cache.userSettingsRepository.updateModel(newModel)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            RoundTemperatureSetting(
                isChecked = userSettings.roundToInt,
                onCheckedChange = { shouldRound ->
                    scope.launch {
                        // On met à jour via le repository contenu dans le cache
                        cache.userSettingsRepository.updateRoundToInt(shouldRound)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            ModelFallbackSetting(
                isChecked = userSettings.enableModelFallback,
                enabled = userSettings.forecastType != ForecastType.ENSEMBLE,
                onCheckedChange = { enabled ->
                    scope.launch {
                        cache.userSettingsRepository.updateEnableModelFallback(enabled)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedIconsSetting(
                isChecked = userSettings.enableAnimatedIcons,
                onCheckedChange = { enabled ->
                    scope.launch {
                        cache.userSettingsRepository.updateEnableAnimatedIcons(enabled)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            TemperatureUnitSetting(
                currentUnit = userSettings.temperatureUnit,
                onUnitSelected = { newUnit ->
                    scope.launch {
                        cache.userSettingsRepository.updateTemperatureUnit(newUnit)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            AirQualityIndexSetting(
                useEurAqi = userSettings.useEurAqi,
                onSettingChange = { useEurAqi ->
                    scope.launch {
                        cache.userSettingsRepository.updateUseEurAqi(useEurAqi)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            WindUnitSetting(
                currentUnit = userSettings.windUnit,
                onUnitSelected = { newUnit ->
                    scope.launch {
                        cache.userSettingsRepository.updateWindUnit(newUnit)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            ForecastTypeSetting(
                currentType = userSettings.forecastType,
                onTypeSelected = { newType ->
                    if (newType == ForecastType.ENSEMBLE && userSettings.forecastType == ForecastType.DETERMINISTIC) {
                        showEnsembleDialog = true
                    } else if (newType == ForecastType.DETERMINISTIC && userSettings.forecastType == ForecastType.ENSEMBLE) {
                        scope.launch {
                            cache.userSettingsRepository.updateForecastType(newType)
                            cache.userSettingsRepository.updateModel("best_match")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            DefaultScreenSetting(
                isOn = userSettings.defaultScreen == DefaultScreen.FORECAST_MAIN,
                onSettingChange = { isOn ->
                    scope.launch {
                        // 1. Mettre à jour le paramètre
                        cache.userSettingsRepository.updateDefaultActivity(if (isOn) DefaultScreen.FORECAST_MAIN else DefaultScreen.DAY_CHOSER)
                        
                        // 2. Attendre 1 seconde
                        kotlinx.coroutines.delay(500)
                        
                        // 3. Relancer l'application
                        val packageManager = activity?.packageManager
                        val intent = packageManager?.getLaunchIntentForPackage(activity.packageName)
                        val componentName = intent?.component
                        val restartIntent = android.content.Intent.makeRestartActivityTask(componentName)
                        activity?.startActivity(restartIntent)
                        Runtime.getRuntime().exit(0)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            FirebaseCrashlyticsSetting(
                isChecked = userSettings.firebaseConsent == "GRANTED",
                onCheckedChange = { granted ->
                    if (granted) {
                        showCrashlyticsDialog = true
                    } else {
                        scope.launch {
                            cache.userSettingsRepository.updateFirebaseConsent("DENIED")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            FilledTonalButton(
                onClick = {
                    val intent = android.content.Intent(activity, WidgetSettingsActivity::class.java)
                    activity?.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Rounded.Widgets, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.widget_settings),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            FilledTonalButton(
                onClick = {
                    val intent = android.content.Intent(activity, CreditActivity::class.java)
                    activity?.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text (
                    text = stringResource(R.string.credits_sources_legal_mentions),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text (
                text = "Version Name: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text (
                text = "Version Code: ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text (
                text = "Build Type: ${BuildConfig.BUILD_TYPE}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun AirQualityIndexSetting(
    useEurAqi: Boolean,
    onSettingChange: (Boolean) -> Unit
) {
    val shape = RoundedCornerShape(40.dp)
    val selectedIndex = if (useEurAqi) 0 else 1

    Column {
        Text(stringResource(R.string.aqi_index_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.aqi_index_desc),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = shape
                )
                .clip(shape)
        ) {
            SegmentItem(
                label = stringResource(R.string.european_index),
                isSelected = selectedIndex == 0,
                modifier = Modifier.weight(1f),
                onClick = { onSettingChange(true) }
            )
            Box(modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.5f)))
            SegmentItem(
                label = stringResource(R.string.universal_index),
                isSelected = selectedIndex == 1,
                modifier = Modifier.weight(1f),
                onClick = { onSettingChange(false) }
            )
        }
    }
}

@Composable
fun ForecastTypeSetting(
    currentType: ForecastType,
    onTypeSelected: (ForecastType) -> Unit
) {
    val shape = RoundedCornerShape(40.dp)
    val selectedIndex = if (currentType == ForecastType.DETERMINISTIC) 0 else 1

    Column {
        Text(stringResource(R.string.forecast_type_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.forecast_type_desc),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = shape
                )
                .clip(shape)
        ) {
            SegmentItem(
                label = stringResource(R.string.deterministic),
                isSelected = selectedIndex == 0,
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(ForecastType.DETERMINISTIC) }
            )
            Box(modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.5f)))
            SegmentItem(
                label = stringResource(R.string.ensemble),
                isSelected = selectedIndex == 1,
                modifier = Modifier.weight(1f),
                onClick = { onTypeSelected(ForecastType.ENSEMBLE) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSetting(
    currentModel: String,
    availableModels: List<fr.matthstudio.themeteo.data.WeatherModel>,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(stringResource(R.string.weather_model_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.chose_model),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            val selectedModel = availableModels.firstOrNull { it.apiName == currentModel }
            OutlinedTextField(
                value = selectedModel?.getDisplayName() ?: currentModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Selected Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.getDisplayName()) },
                        onClick = {
                            onModelSelected(model.apiName)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RoundTemperatureSetting(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.round_values_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.round_values_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun DefaultScreenSetting(
    isOn: Boolean,
    onSettingChange: (Boolean) -> Unit
) {
    // État pour savoir quel bouton est sélectionné
    var selectedIndex = if (isOn) 0 else 1
    val shape = RoundedCornerShape(40.dp) // Forme pilule

    Column {
        Text(
            text = stringResource(R.string.app_focus),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.app_focus_desc),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = shape
                )
                .clip(shape)
        ) {
            SegmentItem(
                label = stringResource(R.string.curent_weather),
                isSelected = selectedIndex == 0,
                modifier = Modifier.weight(1f),
                onClick = { selectedIndex = 0; onSettingChange(true) }
            )

            // Ligne de séparation fine
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.5f))
            )

            SegmentItem(
                label = stringResource(R.string.daily_forecast_setting),
                isSelected = selectedIndex == 1,
                modifier = Modifier.weight(1f),
                onClick = { selectedIndex = 1; onSettingChange(false) }
            )
        }
    }
}

@Composable
fun SegmentItem(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primaryContainer
    val inactiveColor = MaterialTheme.colorScheme.surface
    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
    // Animation de la couleur de fond
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 300),
        label = "colorAnimation"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // Supprime l'effet de ripple standard si tu veux un look pur
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AnimatedIconsSetting(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.animated_icons_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.animated_icons_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ModelFallbackSetting(
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.fill_missing_vars_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                stringResource(R.string.fill_missing_vars_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun TemperatureUnitSetting(
    currentUnit: TemperatureUnit,
    onUnitSelected: (TemperatureUnit) -> Unit
) {
    val shape = RoundedCornerShape(40.dp)
    val selectedIndex = currentUnit.ordinal

    Column {
        Text(stringResource(R.string.temperature_unit_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.temperature_unit_desc),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = shape
                )
                .clip(shape)
        ) {
            SegmentItem(
                label = stringResource(R.string.celsius),
                isSelected = selectedIndex == 0,
                modifier = Modifier.weight(1f),
                onClick = { onUnitSelected(TemperatureUnit.CELSIUS) }
            )
            Box(modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.5f)))
            SegmentItem(
                label = stringResource(R.string.fahrenheit),
                isSelected = selectedIndex == 1,
                modifier = Modifier.weight(1f),
                onClick = { onUnitSelected(TemperatureUnit.FAHRENHEIT) }
            )
            Box(modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.5f)))
            SegmentItem(
                label = stringResource(R.string.kelvin),
                isSelected = selectedIndex == 2,
                modifier = Modifier.weight(1f),
                onClick = { onUnitSelected(TemperatureUnit.KELVIN) }
            )
        }
    }
}

@Composable
fun FirebaseCrashlyticsSetting(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.firebase_crashlytics_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "IMPORTANT",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Text(
                stringResource(R.string.firebase_crashlytics_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun WindUnitSetting(
    currentUnit: WindUnit,
    onUnitSelected: (WindUnit) -> Unit
) {
    val shape = RoundedCornerShape(40.dp)
    val selectedIndex = currentUnit.ordinal

    Column {
        Text(stringResource(R.string.wind_unit_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.wind_unit_desc),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = shape
                )
                .clip(shape)
        ) {
            SegmentItem(
                label = stringResource(R.string.kph),
                isSelected = selectedIndex == 0,
                modifier = Modifier.weight(1f),
                onClick = { onUnitSelected(WindUnit.KPH) }
            )
            Box(modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.5f)))
            SegmentItem(
                label = stringResource(R.string.mph),
                isSelected = selectedIndex == 1,
                modifier = Modifier.weight(1f),
                onClick = { onUnitSelected(WindUnit.MPH) }
            )
        }
    }
}
