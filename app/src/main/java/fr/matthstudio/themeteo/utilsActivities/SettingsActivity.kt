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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import fr.matthstudio.themeteo.data.WeatherModelRegistry
import fr.matthstudio.themeteo.data.GpsCoordinates
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
        ) {
            ModelSelectionSetting(
                currentModel = userSettings.model,
                availableModels = if (currentCoords != null) 
                    WeatherModelRegistry.getAvailableModels(currentCoords.latitude, currentCoords.longitude)
                else 
                    WeatherModelRegistry.models.filter { it.isGlobal },
                onModelSelected = { newModel ->
                    scope.launch {
                        // On met à jour via le repository contenu dans le cache
                        cache.userSettingsRepository.updateModel(newModel)
                        
                        // Log the event
                        (activity?.application as? TheMeteo)?.container?.telemetryManager?.logEvent(
                            "weather_model_selection",
                            mapOf("model" to newModel)
                        )
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

            Text (
                text = stringResource(R.string.app_focus),
                style = MaterialTheme.typography.titleMedium,
            )
            DefaultScreenSetting(
                isOn = userSettings.defaultScreen == DefaultScreen.FORECAST_MAIN,
                onSettingChange = { isOn ->
                    scope.launch {
                        // 1. Mettre à jour le paramètre
                        cache.userSettingsRepository.updateDefaultActivity(if (isOn) DefaultScreen.FORECAST_MAIN else DefaultScreen.DAY_CHOSER)
                        
                        // 2. Attendre 1 seconde
                        kotlinx.coroutines.delay(1000)
                        
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
            OutlinedTextField(
                value = availableModels.firstOrNull { it.apiName == currentModel }?.settingName ?: currentModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Selected Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.settingName) },
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
                modifier = Modifier.padding(top = 4.dp)
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
        // Bouton "Tous allumés"
        SegmentItem(
            label = stringResource(R.string.curent_weather),
            isSelected = selectedIndex == 0,
            modifier = Modifier.weight(1f),
            onClick = { selectedIndex = 0 ; onSettingChange(true)}
        )

        // Ligne de séparation fine (optionnelle, selon le design précis)
        Box(modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(Color.White.copy(alpha = 0.5f)))

        // Bouton "Tous éteints"
        SegmentItem(
            label = stringResource(R.string.daily_forecast_setting),
            isSelected = selectedIndex == 1,
            modifier = Modifier.weight(1f),
            onClick = { selectedIndex = 1 ; onSettingChange(false)}
        )
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
                modifier = Modifier.padding(top = 4.dp)
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
            Text(stringResource(R.string.fill_missing_vars_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.fill_missing_vars_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}