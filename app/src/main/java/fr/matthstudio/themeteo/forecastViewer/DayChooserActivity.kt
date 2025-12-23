package fr.matthstudio.themeteo.forecastViewer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastViewer.data.WeatherViewModelFactory
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.collections.get
import kotlin.math.roundToInt

val weatherModelPredictionTime = mapOf(
    "best_match" to 15,
    "ecmwf_ifs" to 14,
    "ecmwf_aifs025_single" to 14,
    "meteofrance_seamless" to 3,
    "gfs_seamless" to 15,
    "icon_seamless" to 6,
    "gem_seamless" to 9,
    "ukmo_seamless" to 5,
)

class DayChooserActivity : ComponentActivity() {

    // On utilise la factory pour obtenir l'instance partagée du ViewModel
    private val weatherViewModel: WeatherViewModel by viewModels { WeatherViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // connecter le ViewModel au Repository
        WeatherViewModelFactory.initialize(this)

        val selectedLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("SELECTED_LOCATION", LocationIdentifier::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("SELECTED_LOCATION") as? LocationIdentifier
        }

        if (selectedLocation == null) {
            Log.e("DayChooserActivity", "selectedLocation is null. You must pass a location to start this activity")
            finish()
            return
        }

        weatherViewModel.selectLocation(selectedLocation)

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
                                    weatherViewModel.loadDailyForecast(userLoc.latitude, userLoc.longitude,
                                        weatherModelPredictionTime[weatherViewModel.userSettings.value.model] ?: 10)
                                } ?: run {
                                    // Si la position GPS n'est pas encore connue,
                                    // on se rabat sur Paris comme valeur par défaut.
                                    // (Le ViewModel essaiera toujours d'obtenir le GPS en parallèle)
                                    weatherViewModel.loadDailyForecast(48.85, 2.35,
                                        weatherModelPredictionTime[weatherViewModel.userSettings.value.model] ?: 10)
                                }
                            }
                            // Cas 2 : Un lieu sauvegardé (ex: "Paris") est sélectionné
                            is LocationIdentifier.Saved -> {
                                // On utilise les coordonnées du lieu sauvegardé
                                weatherViewModel.loadDailyForecast(
                                    locationIdentifier.location.latitude,
                                    locationIdentifier.location.longitude,
                                    weatherModelPredictionTime[weatherViewModel.userSettings.value.model] ?: 10
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
    val dailyForecast by weatherViewModel.dailyForecast.collectAsState()
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
            text = stringResource(R.string.next_days_temperature_forecast, dailyForecast.size),
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
                    text = errorMessage ?: stringResource(R.string.unknown_error),
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
                    SingleDailyForecastCard(dayReading, weatherViewModel)
                }
            }
        }
    }
}

/**
 * A Composable function to display a single daily forecast in a Card.
 *
 * @param dayReading The DailyTemperatureReading data for the specific day.
 * @param viewModel The viewModel of the activity.
 */
@Composable
fun SingleDailyForecastCard(dayReading: DailyReading, viewModel: WeatherViewModel) {
    val context = LocalContext.current

    // Charger les icônes
    val iconWeatherFolder = "file:///android_asset/icons/weather/"
    val sunnyDayIconPath: String = iconWeatherFolder + "clear-day.svg"
    val sunnyCloudyDayIconPath: String = iconWeatherFolder + "cloudy-3-day.svg"
    val cloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val foggyIconPath: String = iconWeatherFolder + "fog.svg"
    val dustIconPath: String = iconWeatherFolder + "dust.svg"
    val drizzleIconPath: String = iconWeatherFolder + "rainy-1.svg"
    val rainy1IconPath: String = iconWeatherFolder + "rainy-2.svg"
    val rainy2IconPath: String = iconWeatherFolder + "rainy-3.svg"
    val hailIconPath: String = iconWeatherFolder + "hail.svg"
    val snowy1IconPath: String = iconWeatherFolder + "snowy-1.svg"
    val snowy2IconPath: String = iconWeatherFolder + "snowy-2.svg"
    val snowy3IconPath: String = iconWeatherFolder + "snowy-3.svg"
    val snowyMixIconPath: String = iconWeatherFolder + "rain-and-snow-mix.svg"
    val stormyIconPath: String = iconWeatherFolder + "thunderstorms.svg"

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
                        putExtra("START_DATE_TIME", dayReading.date.atTime(0, 0))
                        putExtra("SELECTED_LOCATION", viewModel.selectedLocation.value)
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

            Row {
                val weatherWord = weatherCodeToSimpleWord(dayReading.wmo)
                val fileName = when (weatherWord) {
                    SimpleWeatherWord.SUNNY -> sunnyDayIconPath
                    SimpleWeatherWord.SUNNY_CLOUDY -> sunnyCloudyDayIconPath
                    SimpleWeatherWord.CLOUDY -> cloudyIconPath
                    SimpleWeatherWord.FOGGY -> foggyIconPath
                    SimpleWeatherWord.DUST -> dustIconPath
                    SimpleWeatherWord.DRIZZLY -> drizzleIconPath
                    SimpleWeatherWord.RAINY1 -> rainy1IconPath
                    SimpleWeatherWord.RAINY2 -> rainy2IconPath
                    SimpleWeatherWord.HAIL -> hailIconPath
                    SimpleWeatherWord.SNOWY1 -> snowy1IconPath
                    SimpleWeatherWord.SNOWY2 -> snowy2IconPath
                    SimpleWeatherWord.SNOWY3 -> snowy3IconPath
                    SimpleWeatherWord.SNOWY_MIX -> snowyMixIconPath
                    SimpleWeatherWord.STORMY -> stormyIconPath
                }

                AsyncImage(
                    model = fileName,
                    contentDescription = "Icône météo actuelle",
                    modifier = Modifier
                        .width(30.dp)
                        .height(30.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "Max: ${dayReading.maxTemperature.roundToInt()}°C / Min: ${dayReading.minTemperature.roundToInt()}°C",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DailyForecastCard(viewModel: WeatherViewModel) {
    val context = LocalContext.current

    // On récupère l'état du lieu sélectionné depuis le ViewModel
    val dailyForecast by viewModel.dailyForecast.collectAsState()

    Card(
        modifier = Modifier
            .padding(24.dp)
            .clickable(true, onClick = { // Make the card clickable
                // Create an Intent to launch DayChooserActivity
                val intent = Intent(context, DayChooserActivity::class.java).apply {
                    putExtra("SELECTED_LOCATION", viewModel.selectedLocation.value)
                }
                // Start the activity
                context.startActivity(intent)
            })
    ) {
        if (viewModel.isLoading.collectAsState().value) {
            Box(
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator()
            }
            return@Card
        }

        Text(
            text = stringResource(R.string.daily_forecast),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 5.dp, bottom = 5.dp)
        )

        if (viewModel.dailyForecast.collectAsState().value.isEmpty())
            return@Card

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), // Space between cards
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(dailyForecast) { dayReading ->
                DailyWeatherBox(dayReading, viewModel)
            }
        }
    }
}

@Composable
fun DailyWeatherBox(dayReading: DailyReading, viewModel: WeatherViewModel) {
    val context = LocalContext.current

    // Charger les icônes
    val iconWeatherFolder = "file:///android_asset/icons/weather/"
    val sunnyDayIconPath: String = iconWeatherFolder + "clear-day.svg"
    val sunnyCloudyDayIconPath: String = iconWeatherFolder + "cloudy-3-day.svg"
    val cloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val foggyIconPath: String = iconWeatherFolder + "fog.svg"
    val dustIconPath: String = iconWeatherFolder + "dust.svg"
    val drizzleIconPath: String = iconWeatherFolder + "rainy-1.svg"
    val rainy1IconPath: String = iconWeatherFolder + "rainy-2.svg"
    val rainy2IconPath: String = iconWeatherFolder + "rainy-3.svg"
    val hailIconPath: String = iconWeatherFolder + "hail.svg"
    val snowy1IconPath: String = iconWeatherFolder + "snowy-1.svg"
    val snowy2IconPath: String = iconWeatherFolder + "snowy-2.svg"
    val snowy3IconPath: String = iconWeatherFolder + "snowy-3.svg"
    val snowyMixIconPath: String = iconWeatherFolder + "rain-and-snow-mix.svg"
    val stormyIconPath: String = iconWeatherFolder + "thunderstorms.svg"

    Surface(
        modifier = Modifier
            .width(85.dp)
            .padding(4.dp)
            .clickable { // Make the card clickable
                // Create an Intent to launch DayGraphsActivity
                val intent = Intent(context, DayGraphsActivity::class.java).apply {
                    putExtra("START_DATE_TIME", dayReading.date.atTime(0, 0))
                    putExtra("SELECTED_LOCATION", viewModel.selectedLocation.value)
                }
                // Start the activity
                context.startActivity(intent)
            },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.padding()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Get the full day name (e.g., "Monday")
                Text(
                    text = dayReading.date.dayOfWeek.getDisplayName(
                        TextStyle.FULL,
                        Locale.getDefault()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val weatherWord = weatherCodeToSimpleWord(dayReading.wmo)
                val fileName = when (weatherWord) {
                    SimpleWeatherWord.SUNNY -> sunnyDayIconPath
                    SimpleWeatherWord.SUNNY_CLOUDY -> sunnyCloudyDayIconPath
                    SimpleWeatherWord.CLOUDY -> cloudyIconPath
                    SimpleWeatherWord.FOGGY -> foggyIconPath
                    SimpleWeatherWord.DUST -> dustIconPath
                    SimpleWeatherWord.DRIZZLY -> drizzleIconPath
                    SimpleWeatherWord.RAINY1 -> rainy1IconPath
                    SimpleWeatherWord.RAINY2 -> rainy2IconPath
                    SimpleWeatherWord.HAIL -> hailIconPath
                    SimpleWeatherWord.SNOWY1 -> snowy1IconPath
                    SimpleWeatherWord.SNOWY2 -> snowy2IconPath
                    SimpleWeatherWord.SNOWY3 -> snowy3IconPath
                    SimpleWeatherWord.SNOWY_MIX -> snowyMixIconPath
                    SimpleWeatherWord.STORMY -> stormyIconPath
                }

                AsyncImage(
                    model = fileName,
                    contentDescription = "Icône météo actuelle",
                    modifier = Modifier
                        .width(30.dp)
                        .height(30.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "${dayReading.maxTemperature.roundToInt()}°/${dayReading.minTemperature.roundToInt()}°",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}