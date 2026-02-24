package fr.matthstudio.themeteo.utilsActivities


import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import fr.matthstudio.themeteo.BuildConfig
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.dayChoserActivity.DayChooserActivity
import fr.matthstudio.themeteo.forecastMainActivity.ForecastMainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as TheMeteo).container
        val userSettings = container.userSettingsRepository
        val telemetryManager = container.telemetryManager

        if (BuildConfig.FIREBASE_ENABLED) {
            val consent = runBlocking { userSettings.firebaseConsent.first() }
            if (consent == "PENDING") {
                setContent {
                    ConsentDialog(
                        onAccept = {
                            lifecycleScope.launch {
                                userSettings.updateFirebaseConsent("GRANTED")
                                telemetryManager.setConsentGranted(true)
                                navigateToNextScreen()
                            }
                        },
                        onDecline = {
                            lifecycleScope.launch {
                                userSettings.updateFirebaseConsent("DENIED")
                                telemetryManager.setConsentGranted(false)
                                navigateToNextScreen()
                            }
                        }
                    )
                }
                return
            } else if (consent == "GRANTED") {
                telemetryManager.setConsentGranted(true)
            }
        }

        navigateToNextScreen()
    }

    private fun navigateToNextScreen() {
        val userSettings = (this.application as TheMeteo).container.userSettingsRepository
        val currentScreen = runBlocking { userSettings.defaultScreen.first() }
            ?: fr.matthstudio.themeteo.DefaultScreen.FORECAST_MAIN
        val intent = if (currentScreen == fr.matthstudio.themeteo.DefaultScreen.DAY_CHOSER) {
            Intent(this, DayChooserActivity::class.java).apply {
                putExtra("LAUNCHER", true)
            }
        } else {
            Intent(this, ForecastMainActivity::class.java).apply {
                putExtra("LAUNCHER", true)
            }
        }
        startActivity(intent)
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
