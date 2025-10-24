package fr.matthstudio.themeteo.forecastViewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels // Import viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.matthstudio.themeteo.data.WeatherViewModelFactory
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale // For TextStyle to get full day name
import kotlin.math.roundToInt // For rounding temperatures

class DayChooserActivity : ComponentActivity() {

    // On utilise la factory pour obtenir l'instance partagée du ViewModel
    private val weatherViewModel: WeatherViewModel by viewModels { WeatherViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // connecter le ViewModel au Repository
        WeatherViewModelFactory.initialize(this)

        enableEdgeToEdge()
        setContent {
            TheMeteoTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // On récupère l'état du lieu sélectionné depuis le ViewModel
                    val selectedLocation by weatherViewModel.selectedLocation.collectAsState()

                    // Ce bloc s'exécutera à chaque fois que 'selectedLocation' changera
                    LaunchedEffect(selectedLocation) {
                        when (val locationIdentifier = selectedLocation) {
                            // Cas 1 : "Position Actuelle" est sélectionnée
                            is LocationIdentifier.CurrentUserLocation -> {
                                // On utilise la dernière position GPS connue du ViewModel
                                weatherViewModel.userLocation.value?.let { userLoc ->
                                    weatherViewModel.loadDailyTemperatureForecast(userLoc.latitude, userLoc.longitude, 10)
                                } ?: run {
                                    // Si la position GPS n'est pas encore connue,
                                    // on se rabat sur Paris comme valeur par défaut.
                                    // (Le ViewModel essaiera toujours d'obtenir le GPS en parallèle)
                                    weatherViewModel.loadDailyTemperatureForecast(48.85, 2.35, 10)
                                }
                            }
                            // Cas 2 : Un lieu sauvegardé (ex: "Paris") est sélectionné
                            is LocationIdentifier.Saved -> {
                                // On utilise les coordonnées du lieu sauvegardé
                                weatherViewModel.loadDailyTemperatureForecast(
                                    locationIdentifier.location.latitude,
                                    locationIdentifier.location.longitude,
                                    10
                                )
                            }
                        }
                    }
                    // Display the DayChooser Composable
                    DayChooser(weatherViewModel = weatherViewModel)
                }
            }
        }
    }
}

/**
 * A Composable function that displays a list of Cards, each showing the date,
 * day of the week, and max/min temperature for the next 10 days (excluding today).
 *
 * @param weatherViewModel The ViewModel to observe for daily forecast data.
 */
@Composable
fun DayChooser(weatherViewModel: WeatherViewModel) {
    // Collect the daily forecast and loading state from the ViewModel
    val dailyForecast by weatherViewModel.dailyTemperatureForecast.collectAsState()
    val isLoading by weatherViewModel.isLoading.collectAsState()
    val errorMessage by weatherViewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .padding(start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Next 10 Days Temperature Forecast",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = errorMessage ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else if (dailyForecast.isEmpty()) {
            Text("No daily forecast available.", modifier = Modifier.padding(16.dp))
        } else {
            // Use LazyColumn for efficient display of a scrollable list
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp), // Space between cards
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(dailyForecast) { dayReading ->
                    DailyForecastCard(dayReading)
                }
            }
        }
    }
}

/**
 * A Composable function to display a single daily forecast in a Card.
 *
 * @param dayReading The DailyTemperatureReading data for the specific day.
 */
@Composable
fun DailyForecastCard(dayReading: DailyTemperatureReading) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f) // Cards take 90% of the screen width
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clickable { // Make the card clickable
                    // Create an Intent to launch DayGraphsActivity
                    val intent = Intent(context, DayGraphsActivity::class.java).apply {
                        putExtra("START_DATE", dayReading.date.atStartOfDay())
                    }
                    // Start the activity
                    context.startActivity(intent)
                },
            horizontalAlignment = Alignment.Start
        ) {
            val dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
            Text(
                text = dayReading.date.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium
            )
            // Get the full day name (e.g., "Monday")
            Text(
                text = dayReading.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Max: ${dayReading.maxTemperature.roundToInt()}°C / Min: ${dayReading.minTemperature.roundToInt()}°C",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}