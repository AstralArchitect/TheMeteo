/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.utilsActivities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.BuildConfig
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.ResolvedPolicyInfo
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.data.UserSettingsRepository
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.ui.PolicyViewerDialog
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userSettingsRepository = (application as TheMeteo).container.userSettingsRepository

        setContent {
            val userSettings by AppStateManager.userSettings.collectAsState()
            val currentWmo by AppStateManager.currentWmoCode.collectAsState()

            TheMeteoTheme(
                themeMode = userSettings?.themeMode ?: ThemeMode.FIXED,
                currentWmoCode = currentWmo,
                isNight = false
            ) {
                SettingsScreen(userSettingsRepository = userSettingsRepository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userSettingsRepository: UserSettingsRepository,
    weatherService: WeatherService? = (LocalContext.current.applicationContext as? TheMeteo)?.container?.weatherService
) {
    val activity = LocalActivity.current
    val userSettingsState by AppStateManager.userSettings.collectAsState()
    val userSettings = userSettingsState ?: return
    val selectedLocation by AppStateManager.selectedLocation.collectAsState()
    val gpsPosition by AppStateManager.currentGpsPosition.collectAsState()

    // On calcule les coordonnées pour filtrer les modèles
    val currentCoords = when (val loc = selectedLocation) {
        is fr.matthstudio.themeteo.LocationIdentifier.CurrentUserLocation -> gpsPosition
        is fr.matthstudio.themeteo.LocationIdentifier.Saved -> GpsCoordinates(loc.location.latitude, loc.location.longitude)
    }

    // On a besoin d'une coroutine scope pour appeler les fonctions suspend du repository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resolvedPolicy by AppStateManager.resolvedPolicyInfo.collectAsState()

    var showEnsembleDialog by remember { mutableStateOf(false) }
    var showCrashlyticsDialog by remember { mutableStateOf(false) }
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    fun openTerms() {
        if (resolvedPolicy != null) {
            showTermsDialog = true
        } else {
            scope.launch {
                val remote = weatherService?.getPolicyUpdateInfo()
                val resolved = remote?.getPolicyForVersion(BuildConfig.VERSION_CODE)
                if (resolved != null) {
                    AppStateManager.updateResolvedPolicyInfo(resolved)
                    showTermsDialog = true
                }
            }
        }
    }

    fun openPrivacy() {
        if (resolvedPolicy != null) {
            showPrivacyDialog = true
        } else {
            scope.launch {
                val remote = weatherService?.getPolicyUpdateInfo()
                val resolved = remote?.getPolicyForVersion(BuildConfig.VERSION_CODE)
                if (resolved != null) {
                    AppStateManager.updateResolvedPolicyInfo(resolved)
                    showPrivacyDialog = true
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (status != PackageManager.PERMISSION_GRANTED) {
                showNotificationPermissionDialog = true
            }
        } else {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                showNotificationPermissionDialog = true
            }
        }
    }

    if (showNotificationPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationPermissionDialog = false },
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { Text(stringResource(R.string.notification_permission_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationPermissionDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Open app settings for older versions if notifications are disabled
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Text(stringResource(R.string.notification_permission_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationPermissionDialog = false }) {
                    Text(stringResource(R.string.ensemble_warning_cancel))
                }
            }
        )
    }

    if (showEnsembleDialog) {
        AlertDialog(
            onDismissRequest = { showEnsembleDialog = false },
            title = { Text(stringResource(R.string.ensemble_warning_title)) },
            text = { Text(stringResource(R.string.ensemble_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        userSettingsRepository.updateForecastType(ForecastType.ENSEMBLE)
                        userSettingsRepository.updateModel("ecmwf_ifs025_ensemble")
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
                        userSettingsRepository.updateFirebaseConsent("GRANTED")
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
            ThemeModeSetting(
                currentMode = userSettings.themeMode,
                onModeSelected = { newMode ->
                    scope.launch {
                        userSettingsRepository.updateThemeMode(newMode)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            RoundTemperatureSetting(
                isChecked = userSettings.roundToInt,
                onCheckedChange = { shouldRound ->
                    scope.launch {
                        userSettingsRepository.updateRoundToInt(shouldRound)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedIconsSetting(
                isChecked = userSettings.enableAnimatedIcons,
                onCheckedChange = { enabled ->
                    scope.launch {
                        userSettingsRepository.updateEnableAnimatedIcons(enabled)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            ExpertModeSetting(
                isChecked = userSettings.expertMode,
                onCheckedChange = { enabled ->
                    scope.launch {
                        userSettingsRepository.updateExpertMode(enabled)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            TemperatureUnitSetting(
                currentUnit = userSettings.temperatureUnit,
                onUnitSelected = { newUnit ->
                    scope.launch {
                        userSettingsRepository.updateTemperatureUnit(newUnit)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            AirQualityIndexSetting(
                useEurAqi = userSettings.useEurAqi,
                onSettingChange = { useEurAqi ->
                    scope.launch {
                        userSettingsRepository.updateUseEurAqi(useEurAqi)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            WindUnitSetting(
                currentUnit = userSettings.windUnit,
                onUnitSelected = { newUnit ->
                    scope.launch {
                        userSettingsRepository.updateWindUnit(newUnit)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            DefaultScreenSetting(
                isOn = userSettings.defaultScreen == DefaultScreen.FORECAST_MAIN,
                onSettingChange = { isOn ->
                    scope.launch {
                        userSettingsRepository.updateDefaultActivity(if (isOn) DefaultScreen.FORECAST_MAIN else DefaultScreen.DAY_CHOSER)

                        delay(500.milliseconds)
                        
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
                            userSettingsRepository.updateFirebaseConsent("DENIED")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            NotificationSetting(
                title = stringResource(R.string.rain_notifications_title),
                description = stringResource(R.string.rain_notifications_desc),
                isChecked = userSettings.enableRainNotifications,
                onCheckedChange = { enabled ->
                    scope.launch {
                        userSettingsRepository.updateEnableRainNotifications(enabled)
                        if (enabled) {
                            checkAndRequestNotificationPermission()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            NotificationSetting(
                title = stringResource(R.string.vigilance_notifications_title),
                description = stringResource(R.string.vigilance_notifications_desc),
                isChecked = userSettings.enableVigilanceNotifications,
                onCheckedChange = { enabled ->
                    scope.launch {
                        userSettingsRepository.updateEnableVigilanceNotifications(enabled)
                        if (enabled) {
                            checkAndRequestNotificationPermission()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { openTerms() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.gcu_title),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                OutlinedButton(
                    onClick = { openPrivacy() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.privacy_policy_title),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                text = "Build Type: ${BuildConfig.BUILD_TYPE}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )

            resolvedPolicy?.let { policy ->
                if (showTermsDialog) {
                    PolicyViewerDialog(
                        title = stringResource(R.string.gcu_title),
                        url = policy.gcuUrl,
                        onDismiss = { showTermsDialog = false }
                    )
                }

                if (showPrivacyDialog) {
                    PolicyViewerDialog(
                        title = stringResource(R.string.privacy_policy_title),
                        url = policy.privacyPolicyUrl,
                        onDismiss = { showPrivacyDialog = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeModeSetting(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val shape = RoundedCornerShape(40.dp)
    val isSPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column {
        Text(stringResource(R.string.theme_mode_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.theme_mode_desc),
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
                label = stringResource(R.string.theme_fixed),
                isSelected = currentMode == ThemeMode.FIXED,
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(ThemeMode.FIXED) }
            )
            
            if (isSPlus) {
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.5f)))
                SegmentItem(
                    label = stringResource(R.string.theme_system),
                    isSelected = currentMode == ThemeMode.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeSelected(ThemeMode.SYSTEM) }
                )
            }
            
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.White.copy(alpha = 0.5f)))
            SegmentItem(
                label = stringResource(R.string.theme_weather),
                isSelected = currentMode == ThemeMode.WEATHER,
                modifier = Modifier.weight(1f),
                onClick = { onModeSelected(ThemeMode.WEATHER) }
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
fun ExpertModeSetting(
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
            Text(stringResource(R.string.expert_mode_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.expert_mode_desc),
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
fun NotificationSetting(
    title: String,
    description: String,
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
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
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
