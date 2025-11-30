package fr.matthstudio.themeteo.forecastViewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastViewer.data.WeatherViewModelFactory
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme

class SettingsActivity : ComponentActivity() {

    private val weatherViewModel: WeatherViewModel by viewModels { WeatherViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // L'initialisation est importante pour que le ViewModelFactory fonctionne
        WeatherViewModelFactory.initialize(this)

        setContent {
            TheMeteoTheme {
                SettingsScreen(viewModel = weatherViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: WeatherViewModel) {
    val activity = LocalActivity.current
    val userSettings by viewModel.userSettings.collectAsState()

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
                currentModel = userSettings.model ?: "best_match",
                onModelSelected = { viewModel.updateModel(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            RoundTemperatureSetting(
                isChecked = userSettings.roundToInt ?: true,
                onCheckedChange = { viewModel.updateRoundToInt(it) }
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
    // Liste des modèles disponibles (valeur API, nom affiché)
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