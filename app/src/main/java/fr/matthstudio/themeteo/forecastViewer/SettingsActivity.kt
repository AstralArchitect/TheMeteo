package fr.matthstudio.themeteo.forecastViewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherCache
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme
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
                            contentDescription = "Retour"
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
                onModelSelected = { newModel ->
                    scope.launch {
                        // On met à jour via le repository contenu dans le cache
                        cache.userSettingsRepository.updateModel(newModel)
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            RoundTemperatureSetting(
                isChecked = userSettings.roundToInt ?: true,
                onCheckedChange = { shouldRound ->
                    scope.launch {
                        // On met à jour via le repository contenu dans le cache
                        cache.userSettingsRepository.updateRoundToInt(shouldRound)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSetting(
    currentModel: String,
    onModelSelected: (String) -> Unit
) {
    val models = listOf(
        "best_match" to "Meilleur Modèle (Défaut)",
        "ecmwf_ifs" to "ECMWF IFS HRES 9km",
        "ecmwf_aifs025_single" to "ECMWF AIFS 0.25° Single",
        "meteofrance_seamless" to "Météo France Seamless",
        "gfs_seamless" to "NCEP GFS Seamless",
        "icon_seamless" to "DWD ICON Seamless",
        "gem_seamless" to "GEM Seamless",
        "ukmo_seamless" to "UK Met Office Seamless",
    )

    var expanded by remember { mutableStateOf(false) }

    Column {
        Text("Modèle Météo", style = MaterialTheme.typography.titleMedium)
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
                value = models.firstOrNull { it.first == currentModel }?.second ?: currentModel,
                onValueChange = {},
                readOnly = true,
                label = { Text("Modèle sélectionné") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.second) },
                        onClick = {
                            onModelSelected(model.first)
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
            Text("Arrondir les valeurs", style = MaterialTheme.typography.titleMedium)
            Text(
                "Affiche les valeurs en nombres entiers dans les graphiques.",
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
