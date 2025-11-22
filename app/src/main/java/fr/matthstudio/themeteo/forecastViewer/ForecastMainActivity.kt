package fr.matthstudio.themeteo.forecastViewer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastViewer.data.WeatherViewModelFactory
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.satImgs.MainSatActivity
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import fr.matthstudio.themeteo.forecastViewer.data.SavedLocation
import io.ktor.util.collections.getValue
import io.ktor.utils.io.InternalAPI
import kotlin.collections.get
import kotlin.math.max

/**
 * Énumération pour représenter les conditions météo de manière simple et robuste.
 * Chaque cas peut être associé à une icône et à un niveau de "priorité".
 *
 * @param priority Plus le chiffre est élevé, plus le phénomène est important.
 */
enum class SimpleWeatherWord {
    STORMY,       // Orageux
    HAIL,         // Grêle
    SNOWY,        // Neigeux
    SNOWY_MIX,    // Neige et pluie
    RAINY2,       // Pluvieux
    RAINY1,       // Pluvieux
    DRIZZLY,      // Bruine
    DUST,         // Dust storm
    FOGGY,        // Brouillard
    CLOUDY,       // Nuageux
    SUNNY_CLOUDY, // Partiellement nuageux
    SUNNY,        // Ensoleillé
}

data class SimpleWeather (
    var sentence: String,
    var word: SimpleWeatherWord,
    var image: ImageBitmap? = null
)

fun loadImageBitmapFromAssets(context: Context, fileName: String): ImageBitmap? {
    return try {
        context.assets.open(fileName).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
        }
    } catch (e: IOException) {
        e.printStackTrace()
        // Gérer l'erreur, par exemple en retournant null ou une image par défaut
        null
    }
}

class ForecastMainActivity : ComponentActivity() {

    // La bonne pratique pour instancier un ViewModel dans une Activity
    private val weatherViewModel: WeatherViewModel by viewModels { WeatherViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WeatherViewModelFactory.initialize(this)
        weatherViewModel.initializeLocationClient(this)

        // C'est ici que vous appelez votre fonction Composable principale
        enableEdgeToEdge()
        setContent {
            // MaterialTheme est un Composable qui applique un style à votre app (couleurs, polices...)
            // C'est une bonne pratique d'envelopper votre UI avec.
            TheMeteoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WeatherScreen(weatherViewModel)
                }
            }
        }
    }
}

@OptIn(InternalAPI::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val tempForecast by viewModel.temperatureForecast.collectAsState()
    val isDaytime: Boolean = (viewModel.skyInfoForecast.collectAsState().value.firstOrNull()?.shortwave_radiation ?: 1.0) >= 1.0

    val userSettings by viewModel.userSettings.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val context = LocalContext.current

    // --- GESTION DE L'ÉTAT DE L'UI ---
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    var showLocationSheet by remember { mutableStateOf(false) }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showCloudInfoDialog by remember { mutableStateOf(false) }

    // --- LOGIQUE DE CHARGEMENT ---
    // Elle demande la localisation uniquement si "Position Actuelle" est sélectionné.
    GetPermissionAndLoadWeather(viewModel, null)

    // Charger les images
    val imagesFolder = "images/"
    val sunnyImageBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, imagesFolder + "clear.jpg")) }
    val sunnyCloudyImageBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, imagesFolder + "mid-cloudy.jpg")) }
    val cloudyImageBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, imagesFolder + "overcast.jpg")) }
    val rainyImageBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, imagesFolder + "rainy.jpg")) }

    // icons paths
    val iconWeatherFolder = "file:///android_asset/icons/weather/"
    val sunnyDayIconPath: String = iconWeatherFolder + "clear-day.svg"
    val sunnyNightIconPath: String = iconWeatherFolder + "clear-night.svg"
    val sunnyCloudyDayIconPath: String = iconWeatherFolder + "cloudy-3-day.svg"
    val sunnyCloudyNightIconPath: String = iconWeatherFolder + "cloudy-3-night.svg"
    val cloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val foggyIconPath: String = iconWeatherFolder + "fog.svg"
    val dustIconPath: String = iconWeatherFolder + "dust.svg"
    val drizzleDayIconPath: String = iconWeatherFolder + "rainy-1-day.svg"
    val drizzleNightIconPath: String = iconWeatherFolder + "rainy-1-night.svg"
    val rainy1DayIconPath: String = iconWeatherFolder + "rainy-2-day.svg"
    val rainy1NightIconPath: String = iconWeatherFolder + "rainy-2-night.svg"
    val rainy2DayIconPath: String = iconWeatherFolder + "rainy-3-day.svg"
    val rainy2NightIconPath: String = iconWeatherFolder + "rainy-3-night.svg"
    val hailIconPath: String = iconWeatherFolder + "hail.svg"
    val snowyIconPath: String = iconWeatherFolder + "snowy-2.svg"
    val snowyMixIconPath: String = iconWeatherFolder + "rain-and-snow-mix.svg"
    val stormyIconPath: String = iconWeatherFolder + "thunderstorms.svg"

    val simpleWeather = getSimpleWeather(viewModel)

    // Définissez ici la hauteur de votre section image
    val imageHeight = 200.dp
    // Définissez ici le montant du chevauchement de la Column de contenu
    val overlapAmount = 32.dp // La Column va remonter de 32dp sur l'image

    // --- AFFICHAGE CONDITIONNEL DES PANNEAUX ---
    if (showLocationSheet) {
        LocationManagementSheet(
            viewModel = viewModel,
            onDismiss = { showLocationSheet = false },
            onAddLocationClick = {
                showLocationSheet = false // Ferme le premier panneau
                showAddLocationDialog = true // Ouvre le second
            }
        )
    }

    if (showAddLocationDialog) {
        AddLocationDialog(
            viewModel = viewModel,
            onDismiss = { showAddLocationDialog = false }
        )
    }

    // --- LA COLONE DE CONTENU ---
    Column (
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Section de l'image de fond et de la température actuelle, et de la selection du lieu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight) // Hauteur fixe pour la section de l'image
                .background(Color.Gray) // Une couleur de fallback si l'image ne charge pas
        ) {
            if (viewModel.isLoading.collectAsState().value) {
                Card (
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator()
                }
                return@Box
            }
            val bitmap = when (simpleWeather.word) {
                SimpleWeatherWord.SUNNY -> sunnyImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.SUNNY_CLOUDY -> sunnyCloudyImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.CLOUDY -> cloudyImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.RAINY1 -> rainyImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.RAINY2 -> rainyImageBitmap ?: ImageBitmap(1, 1)

                SimpleWeatherWord.STORMY -> null
                else -> null
            }

            Image(
                bitmap = bitmap ?: ImageBitmap(1, 1),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box (
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WindowInsets.safeDrawing.asPaddingValues()),
                //verticalAlignment = Alignment.CenterVertically,
                //horizontalArrangement = Arrangement.Center
            ) {
                // --- SÉLECTEUR DE LIEU PAR-DESSUS L'IMAGE ---
                Row (
                    modifier = Modifier
                        .align (Alignment.Center)
                        .clickable { showLocationSheet = true }, // OUVRE LE PANNEAU
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Lieu actuel",
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    // Affiche le nom du lieu actuellement sélectionné
                    Text(
                        text = when (val loc = selectedLocation) {
                            is LocationIdentifier.CurrentUserLocation -> "Position Actuelle"
                            is LocationIdentifier.Saved -> loc.location.name
                        },
                        style = MaterialTheme.typography.titleLarge.copy(color = Color.White),
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Changer de lieu",
                        tint = Color.White
                    )
                }

                // --- BOUTON POUR LES PARAMÈTRES ---
                IconButton(
                    onClick = {
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        //.padding(WindowInsets.safeDrawing.asPaddingValues()) // Respecte les zones système
                        //.padding(8.dp) // Ajoute un peu d'espace autour
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ouvrir les paramètres",
                        tint = Color.White // Assure une bonne visibilité sur l'image
                    )
                }
            }

            // Icone de temps simpliste avec température actuelle
            Row (
                modifier = Modifier
                    .align(Alignment.BottomStart) // Aligne le texte en bas à gauche
                    .padding(start = 24.dp, bottom = 50.dp)
            ) {
                val fileName = when (simpleWeather.word) {
                    SimpleWeatherWord.SUNNY -> if (isDaytime) sunnyDayIconPath else sunnyNightIconPath
                    SimpleWeatherWord.SUNNY_CLOUDY -> if (isDaytime) sunnyCloudyDayIconPath else sunnyCloudyNightIconPath
                    SimpleWeatherWord.CLOUDY -> cloudyIconPath
                    SimpleWeatherWord.FOGGY -> foggyIconPath
                    SimpleWeatherWord.DUST -> dustIconPath
                    SimpleWeatherWord.DRIZZLY -> if (isDaytime) drizzleDayIconPath else drizzleNightIconPath
                    SimpleWeatherWord.RAINY1 -> if (isDaytime) rainy1DayIconPath else rainy1NightIconPath
                    SimpleWeatherWord.RAINY2 -> if (isDaytime) rainy2DayIconPath else rainy2NightIconPath
                    SimpleWeatherWord.HAIL -> hailIconPath
                    SimpleWeatherWord.SNOWY -> snowyIconPath
                    SimpleWeatherWord.SNOWY_MIX -> snowyMixIconPath
                    SimpleWeatherWord.STORMY -> stormyIconPath
                }

                AsyncImage(
                    model = fileName,
                    contentDescription = "Icône météo actuelle",
                    modifier = Modifier
                        .width(58.dp)
                        .height(58.dp),
                    contentScale = ContentScale.Fit
                )

                Column {
                    Text(
                        text = "${tempForecast.firstOrNull()?.temperature?.roundToInt() ?: 0}°C",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                    Text(
                        text = simpleWeather.sentence,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth() // Prend toute la largeur disponible
                .offset(y = -overlapAmount) // Remonte la colonne par le montant de overlapAmount
                .background(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                )
                .zIndex(1f), // Assure que cette colonne est au-dessus de l'image
            verticalArrangement = Arrangement.Top, // Les éléments commencent en haut de cette colonne
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .background(color = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(errorMessage ?: "Erreur inconnue", color = MaterialTheme.colorScheme.error)
                }
            }

            // Hourly forecast 24h
            HourlyForecast(viewModel)

            // Daily forecast
            DailyForecastCard(viewModel)

            // Actual situation
            situationCard(
                viewModel = viewModel,
                onCloudIconClick = { showCloudInfoDialog = true }
            )

            // Si l'état est vrai, affichez le dialogue
            if (showCloudInfoDialog) {
                CloudInfoDialog(
                    viewModel = viewModel,
                    onDismiss = { showCloudInfoDialog = false } // Ferme le dialogue
                )
            }

            Text(
                "Date : ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}"
            )

            when (selectedLocation) {
                is LocationIdentifier.CurrentUserLocation -> {
                    Text(
                        "Position : ${viewModel.userLocation.collectAsState().value?.latitude}," +
                                " ${viewModel.userLocation.collectAsState().value?.longitude}"
                    )
                }
                is LocationIdentifier.Saved -> {
                    Text(
                        "Position : ${(selectedLocation as LocationIdentifier.Saved).location.latitude}," +
                                " ${(selectedLocation as LocationIdentifier.Saved).location.longitude}"
                    )
                }
            }

            Text("Source : ${getModelSourceText(userSettings.model)}")

            Button(
                onClick = {
                    val intent = Intent(context, MainSatActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.padding(24.dp)
            ) {
                Text(stringResource(R.string.open_satellite_view))
            }
        }
    }
}

fun getModelSourceText(model: String?): String {
    return when (model) {
        "best_match" -> "Open Meteo"
        "ecmwf_ifs" -> "ECMWF IFS"
        "ecmwf_aifs025_single" -> "ECMWF AIFS"
        "meteofrance_seamless" -> "Météo France"
        "gfs_seamless" -> "NCEP GFS"
        "icon_seamless" -> "DWD ICON"
        "gem_seamless" -> "GEM"
        "gfs_graphcast025" -> "GFS GraphCast"
        "ukmo_seamless" -> "UK Met Office"
        else -> "Open-Meteo" // Fallback générique
    }
}

fun weatherCodeToSimpleWord(code: Int): SimpleWeatherWord {
    return when (code) {
        0 -> SimpleWeatherWord.SUNNY
        1, 2 -> SimpleWeatherWord.SUNNY_CLOUDY
        3 -> SimpleWeatherWord.CLOUDY                 // Overcast
        in 4..19 -> SimpleWeatherWord.CLOUDY    // Haze, smoke, dust, etc. treated as "cloudy"
        in 20..29 -> SimpleWeatherWord.CLOUDY   // Phenomena in the past hour
        in 30..39 -> SimpleWeatherWord.CLOUDY   // Duststorms, sandstorms
        in 40..49 -> SimpleWeatherWord.FOGGY    // Fog
        in 50..59 -> SimpleWeatherWord.DRIZZLY  // Drizzle
        in 60..69 -> SimpleWeatherWord.RAINY1   // Rain
        in 70..79 -> SimpleWeatherWord.SNOWY
        in 80..82 -> SimpleWeatherWord.RAINY2   // Rain showers
        83, 84 -> SimpleWeatherWord.SNOWY_MIX         // Rain and snow mixed showers -> classified as Snowy
        85, 86 -> SimpleWeatherWord.SNOWY             // Snow showers
        in 87..90 -> SimpleWeatherWord.HAIL     // Hail showers
        in 91..94 -> SimpleWeatherWord.STORMY   // Rain/Drizzle with Thunderstorm (but we will let STORMY override)
        in 95..99 -> SimpleWeatherWord.STORMY
        else -> SimpleWeatherWord.SUNNY               // Fallback for unknown codes
    }
}

@Composable
fun getSimpleWeather(viewModel: WeatherViewModel, index: Int = 0): SimpleWeather {
    val context = LocalContext.current
    var simpleWeather: SimpleWeather by remember {
        mutableStateOf(SimpleWeather("Ciel clair", SimpleWeatherWord.SUNNY))
    }

    val precipitationForecast by viewModel.precipitationForecast.collectAsState()
    val skyStateForecast by viewModel.skyInfoForecast.collectAsState()
    val wCodeForecast by viewModel.wmoForecast.collectAsState()

    LaunchedEffect(precipitationForecast, skyStateForecast, wCodeForecast) {
        if (precipitationForecast.isEmpty() ||
            wCodeForecast.isEmpty() ||
            skyStateForecast.isEmpty())
            return@LaunchedEffect

        val skyState = skyStateForecast[index]
        val precipitation = precipitationForecast[index].precipitation.toFloat()
        val wCode = wCodeForecast[index].wmo

        // Créer une NOUVELLE instance à chaque calcul.
        val newWeather = SimpleWeather("Initial", SimpleWeatherWord.SUNNY) // Les valeurs sont temporaires

        // Set the weather word
        newWeather.word = weatherCodeToSimpleWord(wCode)

        // Set the weather sentence
        // 1. Tester d'abord l'opactité
        if (skyState.opacity in 1..30) {
            newWeather.sentence = "Ciel ensoleillé"
            newWeather.image
            if (skyState.cloudcover_high > 50)
                newWeather.sentence += " avec voile"
        }
        // 2. tester par couverture nuageuse
        else if (max(skyState.cloudcover_low, skyState.cloudcover_mid) <= 25) {
            if (skyState.cloudcover_high > 50)
                newWeather.sentence = "Ciel voilé"
            else {
                newWeather.sentence = "Ciel clair"
            }
        } else if (max(skyState.cloudcover_low, skyState.cloudcover_mid) <= 50) {
            newWeather.sentence = "Nuages épars"
            if (skyState.cloudcover_high > 50)
                newWeather.sentence += " avec ciel voilé"
        } else if (max(skyState.cloudcover_low, skyState.cloudcover_mid) <= 75) {
            newWeather.sentence = "Ciel partiellement couvert"
            if (skyState.cloudcover_high > 50)
                newWeather.sentence += " avec voile"
        } else {
            newWeather.sentence = "Ciel couvert"
        }
        // 3. Ajouter la pluie
        if (precipitation >= 0.1f) {
            newWeather.sentence += if (precipitation < 0.5f) " avec pluie légère"
            else if (precipitation < 3.0f) " avec pluie modérée"
            else if (precipitation < 10.0f) " avec pluie forte"
            else " avec pluie torrentielle"
        }

        // Remplacer l'état.
        // `simpleWeather` passe de `null` (ou une ancienne instance) à `newWeather`.
        // C'est CETTE ligne qui dit à Compose: "L'état a changé, redessine ce qui en dépend !"
        simpleWeather = newWeather
    }

    return simpleWeather
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GetPermissionAndLoadWeather(viewModel: WeatherViewModel, startDateTime: LocalDateTime?) {
    val context = LocalContext.current
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val settings by viewModel.userSettings.collectAsState()

    val locationPermissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    )

    // Cet effet ne s'exécute que lorsque le lieu sélectionné change
    LaunchedEffect(viewModel.selectedLocation.collectAsState().value, settings.model) {
        when(val locationIdentifier = selectedLocation) {
            is LocationIdentifier.CurrentUserLocation -> {
                // On a besoin de la localisation GPS
                if (locationPermissionsState.allPermissionsGranted) {
                    viewModel.getLocationAndLoad24hForecastPlusDailyForecast(context, startDateTime)
                } else if (!locationPermissionsState.shouldShowRationale) {
                    // C'est la première fois ou l'utilisateur a dit "ne plus demander"
                    locationPermissionsState.launchMultiplePermissionRequest()
                } else {
                    // L'utilisateur a refusé, on pourrait afficher un message.
                    // Pour l'instant, on ne fait rien, l'utilisateur doit sélectionner un lieu manuellement.
                    Log.d("WeatherScreen", "Permissions refusées. L'utilisateur doit choisir un lieu.")
                }
            }
            is LocationIdentifier.Saved -> {
                // Charger la météo pour le lieu sauvegardé
                viewModel.load24hForecast(
                    locationIdentifier.location.latitude,
                    locationIdentifier.location.longitude,
                    startDateTime
                )
                viewModel.loadDailyForecast(
                    locationIdentifier.location.latitude,
                    locationIdentifier.location.longitude,
                    weatherModelPredictionTime[settings.model] ?: 10
                )
            }
        }
    }

    // Gère la réponse à la demande de permission
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        // Si les permissions viennent d'être accordées ET que le lieu actuel est la position GPS
        if (locationPermissionsState.allPermissionsGranted && selectedLocation is LocationIdentifier.CurrentUserLocation) {
            viewModel.getLocationAndLoad24hForecastPlusDailyForecast(context, startDateTime)
        }
    }
}

enum class ChosenVar {
    TEMPERATURE,
    APPARENT_TEMPERATURE,
    PRECIPITATION,
    WIND
}

@Composable
fun HourlyForecast(viewModel: WeatherViewModel) {

    val context = LocalContext.current
    var variable: ChosenVar by remember { mutableStateOf(ChosenVar.TEMPERATURE) }

    Card(
        modifier = Modifier
            .padding(24.dp)
            .clickable(true, onClick = { // Make the card clickable
                // Create an Intent to launch DayGraphsActivity
                val intent = Intent(context, DayGraphsActivity::class.java).apply {
                    putExtra("SELECTED_LOCATION", viewModel.selectedLocation.value)
                    putExtra("START_DATE_TIME", LocalDateTime.now())
                }
                // Start the activity
                context.startActivity(intent)
            })
    ) {
        if (viewModel._isLoadingTemperature.collectAsState().value) {
            Box(
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator()
            }
            return@Card
        }

        Text(
            text = "Hourly forecast",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 5.dp, bottom = 5.dp)
        )

        if (viewModel.temperatureForecast.collectAsState().value.isEmpty() ||
            viewModel.precipitationForecast.collectAsState().value.isEmpty() ||
            viewModel.windspeedForecast.collectAsState().value.isEmpty())
            return@Card

        Column (
            modifier = Modifier.padding(start = 8.dp, end= 16.dp, bottom = 16.dp, top = 0.dp)
        ) {
            val scrollState = rememberScrollState()
            when (variable) {
                ChosenVar.TEMPERATURE -> GenericGraph(viewModel, GraphType.TEMP, Color(0xFFFFF176), scrollState = scrollState)
                ChosenVar.APPARENT_TEMPERATURE -> if (viewModel.apparentTemperatureForecast.collectAsState().value != null)
                    GenericGraph(viewModel, GraphType.A_TEMP, Color(0xFFFFB300), scrollState = scrollState)
                ChosenVar.PRECIPITATION -> GenericGraph(viewModel, GraphType.RAIN_RATE, Color(0xFF039BE5), scrollState = scrollState)
                ChosenVar.WIND -> GenericGraph(viewModel, GraphType.WIND_SPEED, Color(0xFF7CB342), scrollState = scrollState)
            }
            if (variable != ChosenVar.WIND)
                WeatherIconGraph(viewModel, scrollState = scrollState)
        }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ChosenVar.entries.forEach {
                if (it == ChosenVar.APPARENT_TEMPERATURE && viewModel.apparentTemperatureForecast.collectAsState().value == null)
                {
                    return@forEach
                }

                OutlinedButton(
                    modifier = Modifier.padding(8.dp),
                    onClick = { variable = it }
                ) {
                    Text(when(it){
                        ChosenVar.TEMPERATURE -> "Température °C"
                        ChosenVar.APPARENT_TEMPERATURE -> "Température ressentie °C"
                        ChosenVar.PRECIPITATION -> "Précipitation mm/h"
                        ChosenVar.WIND -> "Vitesse du vent km/h"
                    })
                }
            }
        }
    }
}

@Composable
fun situationCard(
    viewModel: WeatherViewModel,
    onCloudIconClick: () -> Unit
) {
    val tempForecast by viewModel.temperatureForecast.collectAsState()
    val apparentTemperatureForecast by viewModel.apparentTemperatureForecast.collectAsState()
    val precipitationForecast by viewModel.precipitationForecast.collectAsState()
    val precipitationProbabilityForecast by viewModel.precipitationProbabilityForecast.collectAsState()
    val cloudcoverForecast by viewModel.skyInfoForecast.collectAsState()
    val windspeedForecast by viewModel.windspeedForecast.collectAsState()
    val pressureForecast by viewModel.pressureForecast.collectAsState()
    val humidityForecast by viewModel.humidityForecast.collectAsState()
    val dewpointForecast by viewModel.dewpointForecast.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()

    val context = LocalContext.current

    val folder = "icons/variables/"

    // Charger l'ImageBitmap une seule fois et la retenir
    val tempIconBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, folder + "tempIcon.png")) }
    val dewpointIconBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, folder + "dewpointIcon.png")) }
    val windIconBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, folder + "windIcon.png")) }
    val rainIconBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, folder + "rainIcon.png")) }
    val humidityIconBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, folder + "humidityIcon.png")) }
    val pressureIconBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, folder + "pressureIcon.png")) }
    val cloudcoverIconBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, folder + "cloudIcon.png")) }

    Card (
        modifier = Modifier.padding(24.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator()
            }
            return@Card
        }

        // Actual Situation text
        Text(
            text = stringResource(R.string.actual_situation),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 5.dp, bottom = 5.dp)
        )
        Column (
            modifier = Modifier
                .fillMaxWidth() // La colonne prend toute la largeur de la Card
                .padding(10.dp), // Padding autour de la grille complète
            verticalArrangement = Arrangement.SpaceEvenly // Distribue les Row verticalement de manière égale
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), // La Row prend toute la largeur disponible
                horizontalArrangement = Arrangement.SpaceEvenly // Distribue les Box horizontalement de manière égale
            ) {
                // Item 1: Température
                Box(
                    modifier = Modifier.weight(1f) // Chaque Box prend une part égale de la largeur de la Row
                ){
                    Column(
                        modifier = Modifier.padding(5.dp), // Padding interne pour le contenu de la cellule
                        horizontalAlignment = Alignment.CenterHorizontally // Centre le contenu horizontalement
                    ) {
                        Image(
                            bitmap = tempIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${tempForecast.firstOrNull()?.temperature?.roundToInt() ?: 0}°C")
                    }
                }
                // Item 2: Température ressentie
                Box(
                    modifier = Modifier.weight(1f)
                ){
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = tempIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${apparentTemperatureForecast?.firstOrNull()?.apparentTemperature?.roundToInt() ?: 0}°C (A)")
                    }
                }
                // Item 3: Point de rosée
                Box(
                    modifier = Modifier.weight(1f)
                ){
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = dewpointIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${dewpointForecast.firstOrNull()?.dewpoint ?: 0}°C")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Item 4: Probabilité de précipitation
                Box(
                    modifier = Modifier.weight(1f)
                ){
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = rainIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${precipitationProbabilityForecast?.firstOrNull()?.probability ?: 0}%")
                    }
                }
                // Item 5: Précipitation
                Box(
                    modifier = Modifier.weight(1f)
                ){
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = rainIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${precipitationForecast.firstOrNull()?.precipitation ?: 0}mm")
                    }
                }
                // Item 6: Vitesse du vent
                Box(
                    modifier = Modifier.weight(1f)
                ){
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = windIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${windspeedForecast.firstOrNull()?.windspeed ?: 0}km/h")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Item 7: Pression
                Box(
                    modifier = Modifier.weight(1f)
                ){
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = pressureIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${pressureForecast.firstOrNull()?.pressure ?: 0}hPa")
                    }
                }
                // Item 8: Humidité
                Box(
                    modifier = Modifier.weight(1f)
                ){
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = humidityIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${humidityForecast.firstOrNull()?.humidity ?: 0}%")
                    }
                }
                // Item 9: Couverture nuageuse
                Box (
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onCloudIconClick)
                ) {
                    Column(
                        modifier = Modifier.padding(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = cloudcoverIconBitmap ?: ImageBitmap(1, 1),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("${cloudcoverForecast.firstOrNull()?.cloudcover_total ?: 0}%")
                    }
                }
            }
        }
    }
}

// 1. LE PANNEAU QUI S'OUVRE DEPUIS LE BAS (BOTTOM SHEET)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationManagementSheet(
    viewModel: WeatherViewModel,
    onDismiss: () -> Unit,
    onAddLocationClick: () -> Unit // Pour ouvrir le dialogue d'ajout
) {
    val savedLocations by viewModel.savedLocations.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = onAddLocationClick) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter un lieu")
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)) {
                Text("Gérer les lieux", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

                LazyColumn {
                    // Item pour la position actuelle
                    item {
                        LocationRow(
                            name = "Position Actuelle",
                            isSelected = selectedLocation is LocationIdentifier.CurrentUserLocation,
                            onClick = {
                                viewModel.selectLocation(LocationIdentifier.CurrentUserLocation)
                                onDismiss()
                            },
                            onDelete = null // On ne peut pas supprimer la position actuelle
                        )
                    }

                    // Liste des lieux sauvegardés
                    items(savedLocations) { location ->
                        LocationRow(
                            name = location.name,
                            isSelected = (selectedLocation as? LocationIdentifier.Saved)?.location == location,
                            onClick = {
                                viewModel.selectLocation(LocationIdentifier.Saved(location))
                                onDismiss()
                            },
                            onDelete = { viewModel.removeLocation(location) }
                        )
                    }
                }
            }
        }
    }
}

// 2. LA LIGNE POUR UN LIEU INDIVIDUEL DANS LE PANNEAU
@Composable
fun LocationRow(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? // Nullable car la position actuelle n'a pas de bouton de suppression
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else null
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer le lieu", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// 3. LA BOÎTE DE DIALOGUE POUR LA RECHERCHE ET L'AJOUT
@Composable
fun AddLocationDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.geocodingResults.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_city)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchCity(it) // Déclenche la recherche via le ViewModel
                    },
                    label = { Text(stringResource(R.string.search_city)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn {
                    items(searchResults) { result ->
                        Text(
                            text = "${result.name}, ${result.region}, ${result.countryCode}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newLocation = SavedLocation(
                                        name = result.name,
                                        latitude = result.latitude,
                                        longitude = result.longitude,
                                        country = result.countryCode
                                    )
                                    viewModel.addLocation(newLocation)
                                    viewModel.selectLocation(LocationIdentifier.Saved(newLocation))
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
fun CloudInfoDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    // Collecter les différents états de couverture nuageuse depuis le ViewModel
    val skyInfoForecast by viewModel.skyInfoForecast.collectAsState()
    val temperatureForecast by viewModel.temperatureForecast.collectAsState()
    val dewpointForecast by viewModel.dewpointForecast.collectAsState()

    // Récupérer les valeurs
    // cloud cover
    val total = skyInfoForecast.firstOrNull()?.cloudcover_total ?: 0
    val low = skyInfoForecast.firstOrNull()?.cloudcover_low ?: 0
    val mid = skyInfoForecast.firstOrNull()?.cloudcover_mid ?: 0
    val high = skyInfoForecast.firstOrNull()?.cloudcover_high ?: 0
    // Solar radiation
    val shortwaveRadiation = skyInfoForecast.firstOrNull()?.shortwave_radiation ?: 0f
    val directRadiation = skyInfoForecast.firstOrNull()?.direct_radiation ?: 0f
    val diffuseRadiation = skyInfoForecast.firstOrNull()?.diffuse_radiation ?: 0f
    // opacity
    val opacity = skyInfoForecast.firstOrNull()?.opacity ?: 0
    // temp and dew point
    val temp = (temperatureForecast.firstOrNull()?.temperature ?: 0f).toDouble()
    val dewpoint = (dewpointForecast.firstOrNull()?.dewpoint ?: 0f).toDouble()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.sky_details))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp) // Espace entre les éléments
            ) {
                InfoRow(label = stringResource(R.string.total), value = "$total%")
                InfoRow(label = stringResource(R.string.low_clouds), value = "$low%")
                InfoRow(label = stringResource(R.string.mid_clouds), value = "$mid%")
                InfoRow(label = stringResource(R.string.high_clouds), value = "$high%")
                InfoRow(label = stringResource(R.string.ceiling), value = "${125 * (temp - dewpoint).toInt()}m")
                InfoRow(label = stringResource(R.string.total_radiation), value = "$shortwaveRadiation W/m²")
                InfoRow(label = stringResource(R.string.direct_radiation), value = "$directRadiation W/m²")
                InfoRow(label = stringResource(R.string.diffuse_radiation), value = "$diffuseRadiation W/m²")
                InfoRow(label = stringResource(R.string.opacity), value = "$opacity%")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

// Un petit Composable pour afficher une ligne d'information (Label + Valeur)
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontWeight = FontWeight.Bold)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}