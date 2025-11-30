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
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastViewer.data.SavedLocation
import fr.matthstudio.themeteo.forecastViewer.data.WeatherViewModelFactory
import fr.matthstudio.themeteo.forecastViewer.ui.theme.TheMeteoTheme
import fr.matthstudio.themeteo.satImgs.MainSatActivity
import io.ktor.utils.io.InternalAPI
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Énumération pour représenter les conditions météo de manière simple et robuste.
 */
enum class SimpleWeatherWord {
    STORMY,       // Orageux
    HAIL,         // Grêle
    SNOWY1,        // Neigeux
    SNOWY2,        // Neige
    SNOWY3,        // Neige forte
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
    val isDaytime: Boolean = (viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.skyInfo?.shortwaveRadiation ?: 1.0) >= 1.0

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
    val drizzleImageBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, imagesFolder + "drizzle.png")) }
    val rainy1ImageBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, imagesFolder + "rainy1.jpg")) }
    val rainy2ImageBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, imagesFolder + "rainy2.jpg")) }
    val snowyImageBitmap: ImageBitmap? by remember { mutableStateOf(loadImageBitmapFromAssets(context, imagesFolder + "snowy.jpg")) }

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
    val snowy1IconPath: String = iconWeatherFolder + "snowy-1.svg"
    val snowy2IconPath: String = iconWeatherFolder + "snowy-2.svg"
    val snowy3IconPath: String = iconWeatherFolder + "snowy-3.svg"
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
                SimpleWeatherWord.DRIZZLY -> drizzleImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.RAINY1 -> rainy1ImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.RAINY2 -> rainy2ImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.SNOWY1 -> snowyImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.SNOWY2 -> snowyImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.SNOWY3 -> snowyImageBitmap ?: ImageBitmap(1, 1)
                SimpleWeatherWord.STORMY -> rainy2ImageBitmap ?: ImageBitmap(1, 1)
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
                        .align(Alignment.Center)
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
                        .width(58.dp)
                        .height(58.dp),
                    contentScale = ContentScale.Fit
                )

                Column {
                    Text(
                        text = "${viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.temperature?.roundToInt() ?: 0}°C",
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

            // 15 minutely forecast
            FifteenMinutelyForecastCard(viewModel)

            // Actual situation
            ActualSituationCard(
                viewModel = viewModel,
                onCloudIconClick = { showCloudInfoDialog = true }
            )

            //Sunset/Sunrise
            SunsetSunriseCard(viewModel)

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
        70, 71 -> SimpleWeatherWord.SNOWY1            // Light Snow
        72, 73 -> SimpleWeatherWord.SNOWY2            // Snow
        74, 75 -> SimpleWeatherWord.SNOWY3            // Heavy Snow
        in 76..79 -> SimpleWeatherWord.SNOWY_MIX// Snow Grains
        in 80..82 -> SimpleWeatherWord.RAINY2   // Rain showers
        83, 84 -> SimpleWeatherWord.SNOWY_MIX         // Rain and snow mixed showers -> classified as Snowy
        85, 86 -> SimpleWeatherWord.SNOWY3            // Snow showers
        in 87..90 -> SimpleWeatherWord.HAIL     // Hail showers
        in 91..94 -> SimpleWeatherWord.STORMY   // Rain/Drizzle with Thunderstorm (but we will let STORMY override)
        in 95..99 -> SimpleWeatherWord.STORMY
        else -> SimpleWeatherWord.SUNNY               // Fallback for unknown codes
    }
}

@Composable
fun getSimpleWeather(viewModel: WeatherViewModel, index: Int = 0): SimpleWeather {
    var simpleWeather: SimpleWeather by remember {
        mutableStateOf(SimpleWeather("Ciel clair", SimpleWeatherWord.SUNNY))
    }

    val hourlyForecast by viewModel.hourlyForecast.collectAsState()

    LaunchedEffect(
        hourlyForecast.map { it.skyInfo },
        hourlyForecast.map { it.precipitationData },
        hourlyForecast.map { it.wmo },
        index
    ) {
        if (hourlyForecast.isEmpty())
            return@LaunchedEffect

        val skyState = hourlyForecast.map { it.skyInfo }[index]
        val precipitation = hourlyForecast.map { it.precipitationData.precipitation }[index].toFloat()
        val rain = hourlyForecast.map { it.precipitationData.rain }[index].toFloat()
        val snow = hourlyForecast.map { it.precipitationData.snowfall }[index].toFloat()
        val wCode = hourlyForecast.map { it.wmo }[index]

        // Créer une NOUVELLE instance à chaque calcul.
        val newWeather = SimpleWeather("Initial", SimpleWeatherWord.SUNNY) // Les valeurs sont temporaires

        // Set the weather word
        newWeather.word = weatherCodeToSimpleWord(wCode)

        // Set the weather sentence
        // 1. Tester d'abord l'opactité
        if (skyState.opacity in 1..30) {
            newWeather.sentence = "Ciel ensoleillé"
            newWeather.image
            if (skyState.cloudcoverHigh > 50)
                newWeather.sentence += " avec voile"
        }
        // 2. tester par couverture nuageuse
        else if (max(skyState.cloudcoverLow, skyState.cloudcoverMid) <= 25) {
            if (skyState.cloudcoverHigh > 50)
                newWeather.sentence = "Ciel voilé"
            else {
                newWeather.sentence = "Ciel clair"
            }
        } else if (max(skyState.cloudcoverLow, skyState.cloudcoverMid) <= 50) {
            newWeather.sentence = "Nuages épars"
            if (skyState.cloudcoverHigh > 50)
                newWeather.sentence += " avec ciel voilé"
        } else if (max(skyState.cloudcoverLow, skyState.cloudcoverMid) <= 75) {
            newWeather.sentence = "Ciel partiellement couvert"
            if (skyState.cloudcoverHigh > 50)
                newWeather.sentence += " avec voile"
        } else {
            newWeather.sentence = "Ciel couvert"
        }
        // 3. Ajouter la pluie / neige
        if (precipitation >= 0.1f) {
            // prioriser la neige
            if (snow >= 0.1f)
            {
                newWeather.sentence += if (snow < 0.5) " avec neige légère"
                else if (snow < 1.0) " avec neige modérée"
                else " avec neige forte"
            } else if (rain >= 0.1f) {
                newWeather.sentence += if (rain < 0.5) " avec pluie légère"
                else if (rain < 3.0) " avec pluie modérée"
                else if (rain < 10.0) " avec pluie forte"
                else " avec pluie torrentielle"
            }
            else newWeather.sentence += " avec precipitation"
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
                    viewModel.getLocationAndLoad24hForecastPlusDailyForecastPlus15MinutelyForecast(context, startDateTime)
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
                viewModel.load24hHourlyAnd24hMinutelyForecast(
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
            viewModel.getLocationAndLoad24hForecastPlusDailyForecastPlus15MinutelyForecast(context, startDateTime)
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
        if (viewModel._isLoadingHourly.collectAsState().value) {
            Box(
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator()
            }
            return@Card
        }

        Text(
            text = stringResource(R.string.hourly_forecast),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 5.dp, bottom = 5.dp)
        )

        if (viewModel.hourlyForecast.collectAsState().value.isEmpty())
            return@Card

        Column (
            modifier = Modifier.padding(start = 8.dp, end= 16.dp, top = 0.dp)
        ) {
            val scrollState = rememberScrollState()
            when (variable) {
                ChosenVar.TEMPERATURE -> GenericGraph(viewModel, GraphType.TEMP, Color(0xFFFFF176), scrollState = scrollState)
                ChosenVar.APPARENT_TEMPERATURE -> if (viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.apparentTemperature != null)
                    GenericGraph(viewModel, GraphType.A_TEMP, Color(0xFFFFB300), scrollState = scrollState)
                ChosenVar.PRECIPITATION -> GenericGraph(viewModel, GraphType.PRECIPITATION, Color(0xFF039BE5), scrollState = scrollState)
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
                if (it == ChosenVar.APPARENT_TEMPERATURE && viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.apparentTemperature == null)
                {
                    return@forEach
                }
                val isSelected = variable == it

                OutlinedButton(
                    modifier = Modifier
                        .padding(4.dp),
                    enabled = !isSelected,
                    onClick = { variable = it }
                ) {
                    Text(
                        when(it){
                            ChosenVar.TEMPERATURE -> stringResource(R.string.temperature_unit)
                            ChosenVar.APPARENT_TEMPERATURE -> stringResource(R.string.a_temperature_unit)
                            ChosenVar.PRECIPITATION -> stringResource(R.string.precipitation_unit)
                            ChosenVar.WIND -> stringResource(R.string.wind_speed_unit)
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun FifteenMinutelyForecastCard(viewModel: WeatherViewModel) {
    if (viewModel.minutelyForecast15.collectAsState().value.isEmpty() ||
        (viewModel.minutelyForecast15.collectAsState().value.maxOf{it.rain} == 0.0 &&
                viewModel.minutelyForecast15.collectAsState().value.maxOf{it.snowfall} == 0.0)
        )
        return

    Card(
        modifier = Modifier.padding(24.dp)
    ) {
        if (viewModel._isLoadingHourly.collectAsState().value) {
            Box(
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator()
            }
            return@Card
        }

        Text(
            text = stringResource(R.string._15_minutely_precipitation_forecast),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 5.dp, bottom = 5.dp)
        )

        if (viewModel.hourlyForecast.collectAsState().value.isEmpty())
            return@Card

        Column (
            modifier = Modifier.padding(start = 8.dp, end= 16.dp, bottom = 16.dp, top = 0.dp)
        ) {
            BarsGraph(viewModel)
        }
    }
}

@Composable
fun ActualSituationCard(
    viewModel: WeatherViewModel,
    onCloudIconClick: () -> Unit
) {
    val temp = viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.temperature
    val apparentTemperature =
        viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.apparentTemperature
    val precipitation =
        viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.precipitationData?.precipitation
    val precipitationProbability =
        viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.precipitationData?.precipitationProbability
    val cloudcover = viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.skyInfo?.cloudcoverTotal
    val windspeed = viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.windspeed
    val pressure = viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.pressure
    val humidity = viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.humidity
    val dewpoint = viewModel.hourlyForecast.collectAsState().value.firstOrNull()?.dewpoint

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
                        Text("${temp ?: 0}°C")
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
                        Text("${apparentTemperature ?: 0}°C (A)")
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
                        Text("${dewpoint ?: 0}°C")
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
                        Text("${precipitationProbability ?: 0}%")
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
                        Text("${precipitation ?: 0}mm")
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
                        Text("${windspeed ?: 0}km/h")
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
                        Text("${pressure ?: 0}hPa")
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
                        Text("${humidity ?: 0}%")
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
                        Text("${cloudcover ?: 0}%")
                    }
                }
            }
        }
    }
}

@Composable
fun CloudInfoDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    // Collecter les différents états de couverture nuageuse depuis le ViewModel
    val houryForecast by viewModel.hourlyForecast.collectAsState()

    // Récupérer les valeurs
    // cloud cover
    val total = houryForecast.firstOrNull()?.skyInfo?.cloudcoverTotal ?: 0
    val low = houryForecast.firstOrNull()?.skyInfo?.cloudcoverLow ?: 0
    val mid = houryForecast.firstOrNull()?.skyInfo?.cloudcoverMid ?: 0
    val high = houryForecast.firstOrNull()?.skyInfo?.cloudcoverHigh ?: 0
    // Solar radiation
    val shortwaveRadiation = houryForecast.firstOrNull()?.skyInfo?.shortwaveRadiation
    val directRadiation = houryForecast.firstOrNull()?.skyInfo?.directRadiation
    val diffuseRadiation = houryForecast.firstOrNull()?.skyInfo?.diffuseRadiation
    // opacity
    val opacity = houryForecast.firstOrNull()?.skyInfo?.opacity ?: 0
    // temp and dew point
    val temp = (houryForecast.firstOrNull()?.temperature ?: 0f).toDouble()
    val dewpoint = (houryForecast.firstOrNull()?.dewpoint ?: 0f).toDouble()

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
                if (shortwaveRadiation != null) {
                    InfoRow(
                        label = stringResource(R.string.total_radiation),
                        value = "$shortwaveRadiation W/m²"
                    )
                    InfoRow(
                        label = stringResource(R.string.direct_radiation),
                        value = "$directRadiation W/m²"
                    )
                    InfoRow(
                        label = stringResource(R.string.diffuse_radiation),
                        value = "$diffuseRadiation W/m²"
                    )
                    InfoRow(label = stringResource(R.string.opacity), value = "$opacity%")
                }
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

@Composable
fun SunsetSunriseCard(viewModel: WeatherViewModel) {
    val context = LocalContext.current

    // variable qui stocke l'état de l'info dialog
    val showInfoDialog = remember { mutableStateOf(false) }
    if (showInfoDialog.value) {
        SunInfoDialog(viewModel) {
            showInfoDialog.value = false
        }
    }
    // S'assure que les données sont disponibles
    val sunriseSunsetForecast by viewModel.sunriseSunsetForecast.collectAsState()
    if (sunriseSunsetForecast.isEmpty() || sunriseSunsetForecast.first().sunset == null ||
        sunriseSunsetForecast.first().sunrise == null || sunriseSunsetForecast[1].sunset == null ||
        sunriseSunsetForecast[1].sunrise == null) return

    // Si le soleil n'est pas encore couché, afficher les données du jour. Sinon celles du lendemain
    val now = LocalDateTime.now()
    val sunset = LocalDateTime.parse(
        sunriseSunsetForecast.first().sunset!!, DateTimeFormatter.ISO_DATE_TIME)
    val sunriseSunset = if (now < sunset)
        sunriseSunsetForecast.first()
    else
        sunriseSunsetForecast[1]

    Card(
        modifier = Modifier
            .padding(24.dp)
            .clickable(onClick = { showInfoDialog.value = true })
    ) {
        if (viewModel._isLoadingHourly.collectAsState().value) {
            Box(
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator()
            }
            return@Card
        }
        Column {
            Text(
                text = stringResource(R.string.sun),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 5.dp, bottom = 5.dp)
            )

            val textColor = android.graphics.Color.rgb(MaterialTheme.colorScheme.onSurface.red,
                MaterialTheme.colorScheme.onSurface.green, MaterialTheme.colorScheme.onSurface.blue)

            // Le Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(24.dp) // Ajout de padding pour l'esthétique
            ) {
                // 1. Parser les chaînes de caractères en objets LocalDateTime
                val sunriseDateTime = LocalDateTime.parse(
                    sunriseSunset.sunrise!!,
                    DateTimeFormatter.ISO_DATE_TIME
                )
                val sunsetDateTime = LocalDateTime.parse(
                    sunriseSunset.sunset!!,
                    DateTimeFormatter.ISO_DATE_TIME
                )

                // 2. Convertir ces LocalDateTime en millisecondes depuis l'époque Unix
                // On utilise le fuseau horaire du système par défaut pour la conversion.
                val sunriseTimeMillis =
                    sunriseDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val sunsetTimeMillis =
                    sunsetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val now = System.currentTimeMillis()

                val width = size.width
                val height = size.height

                // 1. DESSINER LA COURBE (TRAJECTOIRE DU SOLEIL)
                val path = Path().apply {
                    val diameter = width - 24.dp.toPx()
                    val radius = diameter / 2f
                    // Ajoute un arc pour former un demi-cercle.
                    // L'arc est contenu dans un rectangle englobant (bounding box).
                    // Les angles de départ et de balayage sont mesurés en degrés,
                    // où 0 degré est à 3 heures sur une horloge.
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            left = 24.dp.toPx(),
                            top = height - radius, // Le haut du rectangle est à `height - radius` pour que le bas soit à `height + radius`.
                            right = diameter,
                            bottom = height + radius
                        ),
                        startAngleDegrees = -180f, // Commence à 9 heures
                        sweepAngleDegrees =  180f, // Balaye de 180 degrés dans le sens des aiguilles d'une montre
                        forceMoveTo = false
                    )
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2196F3), // Couleur du haut (plus foncée)
                            Color(0xFF4FC3F7), // Couleur du bas  (plus claire)
                            Color(0xFFF3D7A1)
                        )
                    ),
                    style = Stroke(10f),
                )

                // 2. CALCULER LA POSITION DU SOLEIL
                val totalDaylightSeconds = (sunsetTimeMillis - sunriseTimeMillis)
                val elapsedSecondsSinceSunrise =
                    (now - sunriseTimeMillis).coerceIn(0, totalDaylightSeconds)
                val sunProgress =
                    (elapsedSecondsSinceSunrise.toFloat() / totalDaylightSeconds.toFloat()).coerceIn(
                        0f,
                        1f
                    )

                // Si le soleil n'est pas levé ou déjà couché, ajuster la progression
                val isSunUp = now in sunriseTimeMillis..sunsetTimeMillis
                val finalSunProgress =
                    if (isSunUp) sunProgress else if (now < sunriseTimeMillis) 0f else 1f

                // Obtenir la position (x, y) sur le chemin en fonction de la progression
                val pathMeasure = androidx.compose.ui.graphics.PathMeasure()
                pathMeasure.setPath(path, false)
                val sunPosition = pathMeasure.getPosition(pathMeasure.length * finalSunProgress)

                // 3. DESSINER LE SOLEIL
                if (isSunUp) {
                    drawCircle(
                        color = Color.Yellow,
                        radius = 30f,
                        center = sunPosition
                    )
                    // Ajoute un léger halo
                    drawCircle(
                        color = Color.Yellow.copy(alpha = 0.4f),
                        radius = 40f,
                        center = sunPosition
                    )
                }

                // 4. DESSINER L'HORIZON
                drawRect(
                    color = Color(0xFF8BC34A).copy(alpha = 0.8f),
                    topLeft = Offset(0f, height), // Commence en bas à gauche du Canvas
                    size = androidx.compose.ui.geometry.Size(width, 5f) // La hauteur de l'horizon
                )

                // 5. AFFICHER LES HEURES DE LEVER ET DE COUCHER
                val textPaint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    textSize = 35f // Taille du texte
                    color = textColor
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                val sunriseText = context.getString(
                    R.string.sunrise,
                    timeFormatter.format(Date(sunriseTimeMillis))
                )
                val sunsetText =
                    context.getString(R.string.sunset, timeFormatter.format(Date(sunsetTimeMillis)))
                // 1. Calculer la durée totale en secondes
                val durationInSeconds = sunsetDateTime.toEpochSecond(ZoneOffset.UTC) - sunriseDateTime.toEpochSecond(ZoneOffset.UTC)
                // 2. Convertisser les secondes en heures et minutes
                val hours = durationInSeconds / 3600
                val minutes = (durationInSeconds % 3600) / 60
                // 3. Formater la chaîne de caractères manuellement
                val dayDurationText = context.getString(R.string.day_duration,
                    String.format(Locale.getDefault(), "%dh%02d", hours, minutes)
                )

                // Dessiner l'heure du lever
                drawContext.canvas.nativeCanvas.drawText(
                    sunriseText,
                    24.dp.toPx(),
                    height + 40f, // Positionnement sous la courbe
                    textPaint.apply { textAlign = android.graphics.Paint.Align.CENTER }
                )

                // Dessiner l'heure du coucher
                drawContext.canvas.nativeCanvas.drawText(
                    sunsetText,
                    width - 24.dp.toPx(),
                    height + 40f, // Positionnement sous la courbe
                    textPaint.apply { textAlign = android.graphics.Paint.Align.CENTER }
                )

                // 6. AFFICHER LA DURÉE DU JOUR AU CENTRE
                drawContext.canvas.nativeCanvas.drawText(
                    dayDurationText,
                    width / 2,
                    height / 2, // Positionnement sous la courbe
                    textPaint.apply { textAlign = android.graphics.Paint.Align.CENTER }
                )
            }
        }
    }
}

@Composable
fun SunInfoDialog(viewModel: WeatherViewModel, onDismiss: () -> Unit) {
    LocalContext.current
    // Collecter les différents états de couverture nuageuse depuis le ViewModel
    val sunriseSunsetForecast by viewModel.sunriseSunsetForecast.collectAsState()

    // Convert all date variable into millis
    val todaysSunriseTimeMillis = LocalDateTime.parse(
        sunriseSunsetForecast.first().sunrise!!,
        DateTimeFormatter.ISO_DATE_TIME
    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val todaysSunsetTimeMillis = LocalDateTime.parse(
        sunriseSunsetForecast.first().sunset!!,
        DateTimeFormatter.ISO_DATE_TIME
    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val tomorrowSunriseTimeMillis = LocalDateTime.parse(
        sunriseSunsetForecast[1].sunrise!!,
        DateTimeFormatter.ISO_DATE_TIME
    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val tomorrowSunsetTimeMillis = LocalDateTime.parse(
        sunriseSunsetForecast[1].sunset!!,
        DateTimeFormatter.ISO_DATE_TIME
    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // TODAYS SUN DURATION
    // 1. Calculer la durée totale en secondes
    var durationInSeconds = LocalDateTime.parse(
        sunriseSunsetForecast.first().sunset!!,
        DateTimeFormatter.ISO_DATE_TIME
    ).toEpochSecond(ZoneOffset.UTC) - LocalDateTime.parse(
        sunriseSunsetForecast.first().sunrise!!,
        DateTimeFormatter.ISO_DATE_TIME
    ).toEpochSecond(ZoneOffset.UTC)
    // 2. Convertisser les secondes en heures et minutes
    var hours = durationInSeconds / 3600
    var minutes = (durationInSeconds % 3600) / 60
    // 3. Formater la chaîne de caractères manuellement
    val todaysDayDurationText = String.format(Locale.getDefault(), "%dh%02d", hours, minutes)
    // TOMORROW SUN DURATION
    // 1. Calculer la durée totale en secondes
    durationInSeconds = LocalDateTime.parse(
        sunriseSunsetForecast[1].sunset!!,
        DateTimeFormatter.ISO_DATE_TIME
    ).toEpochSecond(ZoneOffset.UTC) - LocalDateTime.parse(
        sunriseSunsetForecast[1].sunrise!!,
        DateTimeFormatter.ISO_DATE_TIME
    ).toEpochSecond(ZoneOffset.UTC)
    // 2. Convertisser les secondes en heures et minutes
    hours = durationInSeconds / 3600
    minutes = (durationInSeconds % 3600) / 60
    // 3. Formater la chaîne de caractères manuellement
    val tomorrowDayDurationText = String.format(Locale.getDefault(), "%dh%02d", hours, minutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.sun_infos))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp) // Espace entre les éléments
            ) {
                val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineSmall)
                InfoRow(
                    stringResource(R.string.sunrise_no_value),
                    timeFormatter.format(Date(todaysSunriseTimeMillis)) ?: "Unknown"
                )
                InfoRow(
                    stringResource(R.string.sunset_no_value),
                    timeFormatter.format(Date(todaysSunsetTimeMillis)) ?: "Unknown"
                )
                InfoRow(
                    stringResource(R.string.day_duration_no_value),
                    todaysDayDurationText
                )
                Text(stringResource(R.string.tomorrow), style = MaterialTheme.typography.headlineSmall)
                InfoRow(
                    stringResource(R.string.sunrise_no_value),
                    timeFormatter.format(Date(tomorrowSunriseTimeMillis)) ?: "Unknown"
                )
                InfoRow(
                    stringResource(R.string.sunset_no_value),
                    timeFormatter.format(Date(tomorrowSunsetTimeMillis)) ?: "Unknown"
                )
                InfoRow(
                    stringResource(R.string.day_duration_no_value),
                    tomorrowDayDurationText
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
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
                .padding(16.dp))
            {
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
            fontWeight = if (isSelected) FontWeight.Bold else null
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
