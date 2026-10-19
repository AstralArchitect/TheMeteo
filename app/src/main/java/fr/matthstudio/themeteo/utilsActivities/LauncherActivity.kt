/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.utilsActivities

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.BuildConfig
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.ResolvedPolicyInfo
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.UserSettingsRepository
import fr.matthstudio.themeteo.dayChoserActivity.DayChooserActivity
import fr.matthstudio.themeteo.forecastMainActivity.ForecastMainActivity
import fr.matthstudio.themeteo.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.widget.WidgetUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locationExtra = intent?.getStringExtra("LOCATION")
        if (locationExtra != null) {
            val location = WidgetUtils.deserializeLocation(locationExtra)
            if (location != null) {
                AppStateManager.updateSelectedLocation(location)
            }
        }

        val app = (application as TheMeteo)
        val container = app.container
        val userSettingsRepo = container.userSettingsRepository

        lifecycleScope.launch {
            val weatherService = WeatherService()
            val remotePolicy = weatherService.getPolicyUpdateInfo()
            if (remotePolicy != null) {
                if (remotePolicy.isUpdateRequired(BuildConfig.VERSION_CODE)) {
                    val isAvailable = fr.matthstudio.themeteo.utilClasses.AppUpdateHelper.isPlayStoreUpdateAvailable(this@LauncherActivity)
                    if (isAvailable) {
                        AppStateManager.updateIsForcedUpdateRequired(true)
                        showForcedUpdateScreen()
                        return@launch
                    } else {
                        AppStateManager.updateIsForcedUpdateRequired(false)
                    }
                } else {
                    AppStateManager.updateIsForcedUpdateRequired(false)
                }
                val resolved = remotePolicy.getPolicyForVersion(BuildConfig.VERSION_CODE)
                if (resolved != null) {
                    AppStateManager.updateResolvedPolicyInfo(resolved)
                }
            }

            val isGcuAccepted = userSettingsRepo.gcuAccepted.first()
            if (!isGcuAccepted) {
                // Si c'est la première fois que l'utilisateur ouvre l'application, alors on met la position à Paris et on met Paris par défaut temporairement
                val location = LocationIdentifier.Saved(
                    SavedLocation(
                        "Paris",
                        48.8566,
                        2.3,
                        "France"
                    )
                )
                container.userLocationsRepository.addLocation(location.location)
                AppStateManager.updateSelectedLocation(location)
                userSettingsRepo.updateDefaultLocation(location)
                
                showOnboardingFlow(userSettingsRepo)
            } else {
                checkFirebaseConsent(userSettingsRepo)
            }
        }
    }

    private fun showForcedUpdateScreen() {
        setContent {
            val isForcedUpdateRequired by AppStateManager.isForcedUpdateRequired.collectAsState()
            val scope = rememberCoroutineScope()
            TheMeteoTheme(themeMode = ThemeMode.FIXED) {
                if (isForcedUpdateRequired) {
                    fr.matthstudio.themeteo.forecastMainActivity.ForcedUpdateDialog(
                        onUpdateClick = {
                            scope.launch {
                                fr.matthstudio.themeteo.utilClasses.AppUpdateHelper.promptUpdateIfAvailable(this@LauncherActivity)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun showOnboardingFlow(userSettingsRepo: UserSettingsRepository) {
        setContent {
            val settings by AppStateManager.userSettings.collectAsState()
            val currentWmo by AppStateManager.currentWmoCode.collectAsState()
            var step by remember { mutableStateOf(OnboardingStep.GCU) }

            TheMeteoTheme(
                themeMode = settings?.themeMode ?: ThemeMode.FIXED,
                currentWmoCode = currentWmo,
                isNight = false
            ) {
                when (step) {
                    OnboardingStep.GCU -> {
                        GcuDialog(
                            onAccept = {
                                lifecycleScope.launch {
                                    userSettingsRepo.updateGcuAccepted(true)
                                    val resolved = AppStateManager.resolvedPolicyInfo.value
                                    if (resolved != null) {
                                        userSettingsRepo.updateLastGcuUpdate(resolved.lastGcuUpdate)
                                    }
                                    step = OnboardingStep.PRIVACY_POLICY
                                }
                            },
                            onDecline = {
                                finish()
                            }
                        )
                    }
                    OnboardingStep.PRIVACY_POLICY -> {
                        GcuDialog(
                            onAccept = {
                                lifecycleScope.launch {
                                    val resolved = AppStateManager.resolvedPolicyInfo.value
                                    if (resolved != null) {
                                        userSettingsRepo.updateLastPrivacyPolicyUpdate(resolved.lastPrivacyPolicyUpdate)
                                    }
                                    checkFirebaseConsent(userSettingsRepo)
                                }
                            },
                            onDecline = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private enum class OnboardingStep {
        GCU,
        PRIVACY_POLICY
    }

    private suspend fun checkFirebaseConsent(userSettingsRepo: UserSettingsRepository) {
        if (BuildConfig.FIREBASE_ENABLED) {
            val consent = userSettingsRepo.firebaseConsent.first()
            if (consent == "PENDING") {
                setContent {
                    val settings by AppStateManager.userSettings.collectAsState()
                    val currentWmo by AppStateManager.currentWmoCode.collectAsState()

                    TheMeteoTheme(
                        themeMode = settings?.themeMode ?: ThemeMode.FIXED,
                        currentWmoCode = currentWmo,
                        isNight = false
                    ) {
                        ConsentDialog(
                            onAccept = {
                                lifecycleScope.launch {
                                    userSettingsRepo.updateFirebaseConsent("GRANTED")
                                    navigateToNextScreen()
                                }
                            },
                            onDecline = {
                                lifecycleScope.launch {
                                    userSettingsRepo.updateFirebaseConsent("DENIED")
                                    navigateToNextScreen()
                                }
                            }
                        )
                    }
                }
                return
            }
        }
        navigateToNextScreen()
    }

    private fun navigateToNextScreen() {
        val userSettingsRepo = (this.application as TheMeteo).container.userSettingsRepository
        val currentScreen = runBlocking { userSettingsRepo.defaultScreen.first() }
            ?: fr.matthstudio.themeteo.DefaultScreen.FORECAST_MAIN

        val locationExtra = intent?.getStringExtra("LOCATION")

        val nextIntent = if (currentScreen == DefaultScreen.DAY_CHOSER) {
            Intent(this, DayChooserActivity::class.java).apply {
                putExtra("LAUNCHER", true)
                if (locationExtra != null) putExtra("LOCATION", locationExtra)
            }
        } else {
            Intent(this, ForecastMainActivity::class.java).apply {
                putExtra("LAUNCHER", true)
                if (locationExtra != null) putExtra("LOCATION", locationExtra)
            }
        }
        startActivity(nextIntent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Aidez-nous à améliorer l'application") },
        text = {
            Column {
                Text("Vous pouvez nous aider en partageant des rapports d'erreur anonymes en cas de plantage. Voulez-vous activer l'envoi automatique de ces rapports ?")
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { showSheet = true },
                    modifier = Modifier.padding(0.dp)
                ) {
                    Text("En savoir plus sur les services utilisés")
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Accepter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Refuser")
            }
        }
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Services et Confidentialité",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                ServiceSection(
                    title = "Firebase Crashlytics (Facultatif)",
                    description = "Utilisé pour collecter des rapports d'erreur détaillés lorsqu'un problème survient. Cela nous aide à identifier et corriger les bugs rapidement."
                )

                ServiceSection(
                    title = "Open-Meteo & Météo-France (Obligatoire)",
                    description = "Services tiers utilisés pour récupérer les données météo et les vigilances. Votre position est envoyée de manière anonyme pour obtenir les prévisions locales."
                )

                ServiceSection(
                    title = "Google Maps & OSMDroid (Obligatoire)",
                    description = "Utilisés pour l'affichage des cartes et la sélection de lieux. Ces services peuvent collecter des données d'utilisation conformément à leurs propres politiques de confidentialité."
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Note : Toutes les données de crash collectées sont anonymisées et ne permettent pas de vous identifier personnellement. Firebase Analytics a été totalement désactivé.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun ServiceSection(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(description, fontSize = 14.sp)
    }
}

@Composable
fun GcuDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var hasScrolledToBottom by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val resolvedPolicy by AppStateManager.resolvedPolicyInfo.collectAsState()
    val weatherService = remember { WeatherService() }

    LaunchedEffect(Unit) {
        if (resolvedPolicy == null) {
            val remote = weatherService.getPolicyUpdateInfo()
            val resolved = remote?.getPolicyForVersion(BuildConfig.VERSION_CODE)
            if (resolved != null) {
                AppStateManager.updateResolvedPolicyInfo(resolved)
            }
        }
    }

    val policyInfo = resolvedPolicy ?: return

    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.gcu_title)) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        hasError = false
                                        hasScrolledToBottom = false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
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
                                loadUrl(policyInfo.gcuUrl)
                            }
                        },
                        update = { webView ->
                            webView.loadUrl(policyInfo.gcuUrl)
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
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.decline))
            }
        }
    )
}

@Composable
fun PrivacyPolicyDialog(onContinue: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val resolvedPolicy by AppStateManager.resolvedPolicyInfo.collectAsState()
    val weatherService = remember { WeatherService() }

    LaunchedEffect(Unit) {
        if (resolvedPolicy == null) {
            val remote = weatherService.getPolicyUpdateInfo()
            val resolved = remote?.getPolicyForVersion(BuildConfig.VERSION_CODE)
            if (resolved != null) {
                AppStateManager.updateResolvedPolicyInfo(resolved)
            }
        }
    }

    val policyInfo = resolvedPolicy ?: return

    AlertDialog(
        onDismissRequest = { },
        title = { Text(stringResource(R.string.privacy_policy_title)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    hasError = false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    isLoading = false
                                    hasError = true
                                }
                            }
                            loadUrl(policyInfo.privacyPolicyUrl)
                        }
                    },
                    update = { webView ->
                        webView.loadUrl(policyInfo.privacyPolicyUrl)
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
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                enabled = !isLoading && !hasError
            ) {
                Text(stringResource(R.string.continue_button))
            }
        }
    )
}
