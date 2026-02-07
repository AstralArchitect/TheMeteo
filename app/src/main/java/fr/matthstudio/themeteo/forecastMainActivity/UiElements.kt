package fr.matthstudio.themeteo.forecastMainActivity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import fr.matthstudio.themeteo.AllHourlyVarsReading
import fr.matthstudio.themeteo.DailyReading
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.dayGraphsActivity.DayGraphsActivity
import fr.matthstudio.themeteo.dayGraphsActivity.GenericGraphGlobal
import fr.matthstudio.themeteo.dayGraphsActivity.GraphType
import java.io.IOException
import java.time.format.TextStyle
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
fun getSimpleWeather(value: AllHourlyVarsReading): SimpleWeather {
    var simpleWeather: SimpleWeather by remember {
        mutableStateOf(SimpleWeather("Ciel clair", SimpleWeatherWord.SUNNY))
    }

    LaunchedEffect(value) {

        val skyState = value.skyInfo
        val precipitation = value.precipitationData.precipitation
        val rain = value.precipitationData.rain
        val snow = value.precipitationData.snowfall
        val wCode = value.wmo

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

enum class ChosenVar {
    TEMPERATURE,
    APPARENT_TEMPERATURE,
    PRECIPITATION,
    WIND
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small
    ) {
        Box(
            modifier = Modifier.padding()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
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

/*@Composable
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
}*/

// Helper function for UV levels
@Composable
fun getUVDescription(uv: Int): String {
    return when {
        uv < 3 -> stringResource(R.string.uv_low)
        uv < 6 -> stringResource(R.string.uv_moderate)
        uv < 8 -> stringResource(R.string.uv_high)
        uv < 11 -> stringResource(R.string.uv_very_high)
        else -> stringResource(R.string.uv_extreme)
    }
}

@Composable
fun getUVColor(uv: Int): Color {
    return when {
        uv < 3 -> Color(0xFFB2FF59)
        uv < 6 -> Color(0xFFFFAB40)
        uv < 8 -> Color(0xFFFF5252)
        uv < 11 -> Color(0xFFE040FB)
        else -> Color(0x00FFFFFF)
    }
}

// 1. LE PANNEAU QUI S'OUVRE DEPUIS LE BAS (BOTTOM SHEET)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationManagementSheet(
    viewModel: WeatherViewModel,
    onDismiss: () -> Unit,
    onAddLocationClick: () -> Unit, // Pour ouvrir le dialogue d'ajout
    sheetState: SheetState
) {
    val savedLocations by viewModel.savedLocations.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MaterialTheme.colorScheme.surface.copy (alpha = 0.7f) else MaterialTheme.colorScheme.surface,
        scrimColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Color.Transparent else Color.Black.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp))
        {
            Text(stringResource(R.string.manage_locations), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            LazyColumn {
                // Item pour la position actuelle
                item {
                    LocationRow(
                        name = stringResource(R.string.current_location),
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

            Button(
                onClick = onAddLocationClick,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(vertical = 16.dp)
                    .size(56.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(24.dp))
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

    var showMapPicker by remember { mutableStateOf(false) }

    if (showMapPicker) {
        Dialog(onDismissRequest = { showMapPicker = false }) {
            MapPickerScreen(
                viewModel = viewModel,
                onLocationSelected = { coords, name ->
                    viewModel.addLocationFromMap(coords, name)
                    showMapPicker = false
                    onDismiss()
                },
                onDismiss = { showMapPicker = false ; onDismiss() }
            )
        }
    }

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
                // Bouton pour ouvrir la carte
                Button(onClick = { showMapPicker = true }) {
                    Text(stringResource(R.string.pick_on_map))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
fun MapPickerScreen(
    viewModel: WeatherViewModel,
    onLocationSelected: (GpsCoordinates, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Position initiale : GPS, fallback Paris
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(
                viewModel.userLocation.value?.latitude ?: 48.8566,
                viewModel.userLocation.value?.longitude ?: 2.3522
            ),
            10f
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        )

        // Indicateur visuel au centre de la carte (une épingle fixe)
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp),
            tint = Color.Red
        )

        // Bouton de confirmation
        Button(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            onClick = {
                val target = cameraPositionState.position.target
                val coords = GpsCoordinates(target.latitude, target.longitude)

                // Utilisation du Geocoder pour trouver le nom de la ville
                val cityName = try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(target.latitude, target.longitude, 1)
                    addresses?.firstOrNull()?.locality ?: "Position personnalisée"
                } catch (e: Exception) {
                    "Position personnalisée"
                }

                onLocationSelected(coords, cityName)
            }
        ) {
            Text("Choisir ce lieu")
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationPermissionHandler(
    viewModel: WeatherViewModel
) {
    val locationPermissionState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    LaunchedEffect(locationPermissionState.allPermissionsGranted) {
        if (locationPermissionState.allPermissionsGranted) {
            // Si l'utilisateur vient d'accepter, on force le rafraîchissement
            // Cela va invalider le cache et relancer la recherche GPS dans WeatherCache
            viewModel.refresh()
        }
    }

    if (!locationPermissionState.allPermissionsGranted) {
        LaunchedEffect(Unit) {
            locationPermissionState.launchMultiplePermissionRequest()
        }
    }
}

@Composable
fun GenericGraph(
    viewModel: WeatherViewModel,
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState()
) {
    val fullForecast by viewModel.hourlyForecast.collectAsState()
    var roundToInt = viewModel.userSettings.collectAsState().value.roundToInt ?: true

    if (graphType == GraphType.PRECIPITATION || graphType == GraphType.RAIN ||
        graphType == GraphType.SNOWFALL
    )
        roundToInt = false

    GenericGraphGlobal(
        fullForecast,
        roundToInt,
        graphType,
        graphColor,
        valueRange,
        scrollState
    )
}

/*@Composable
fun BarsGraph(
    viewModel: WeatherViewModel,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState()
) {
    val forecast by viewModel.minutelyForecast15.collectAsState()

    if (forecast.isEmpty()) {
        return // Ne rien dessiner si les données ne sont pas prêtes
    }

    Box(
        modifier = Modifier
            .width(1000.dp) // Largeur fixe pour correspondre aux autres graphiques
            .horizontalScroll(scrollState),
    ) {
        val rainColor = Color(0xFF64B5F6) // Bleu pour la pluie
        val snowColor = Color.White       // Blanc pour la neige
        val textColor: Int = AndroidColor.rgb(
            MaterialTheme.colorScheme.onBackground.red,
            MaterialTheme.colorScheme.onBackground.green,
            MaterialTheme.colorScheme.onBackground.blue
        )

        Canvas(
            modifier = Modifier
                .width(1000.dp)
                .height(150.dp)
        ) {
            val xPadding = 50f
            val yPadding = 100f

            // Déterminer la valeur maximale pour l'échelle Y
            val maxPrecipitation = forecast.maxOfOrNull { it.rain + it.snowfall }?.toFloat() ?: 1f
            val maxValue = valueRange?.endInclusive?.coerceAtLeast(maxPrecipitation) ?: maxPrecipitation

            if (maxValue <= 0f) return@Canvas // Éviter la division par zéro si aucune précipitation

            val canvasWidth = size.width
            val xStep = (canvasWidth - 2 * xPadding) / (forecast.size - 1)
            val barWidth = xStep * 0.7f // Laisser un peu d'espace entre les barres
            val yScale = (size.height - 2 * yPadding) / maxValue

            forecast.forEachIndexed { index, reading ->
                val x = xPadding + (index * xStep)
                val rainHeight = (reading.rain * yScale).toFloat()
                val snowHeight = (reading.snowfall * yScale).toFloat()
                val totalHeight = rainHeight + snowHeight

                // 1. Dessiner la barre de pluie (en bas)
                if (rainHeight > 0) {
                    drawRect(
                        color = rainColor,
                        topLeft = Offset(x - barWidth / 2, size.height - yPadding - rainHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, rainHeight)
                    )
                }

                // 2. Dessiner la barre de neige (empilée sur la pluie)
                if (snowHeight > 0) {
                    drawRect(
                        color = snowColor,
                        topLeft = Offset(x - barWidth / 2, size.height - yPadding - rainHeight - snowHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth, snowHeight)
                    )
                }

                // 3. Dessiner la valeur totale au-dessus de la barre (si > 0)
                val totalPrecipitationValue = reading.rain + reading.snowfall
                if (totalPrecipitationValue > 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format(Locale.getDefault(), "%.1f", totalPrecipitationValue), // Formatter avec une décimale
                        x,
                        size.height - yPadding - totalHeight - 15f, // Position au-dessus de la barre
                        Paint().apply {
                            textAlign = Paint.Align.CENTER
                            textSize = 25f
                            color = textColor
                        }
                    )
                }

                // 4. Dessiner la minute en bas
                drawContext.canvas.nativeCanvas.drawText(
                    reading.time.format(DateTimeFormatter.ofPattern("mm")),
                    x,
                    size.height - yPadding + 90f,
                    Paint().apply {
                        textAlign = Paint.Align.CENTER
                        textSize = 35f
                        color = textColor
                    }
                )
                // Dessiner l'heure au dessus de la minute si elle est égale à 0 et dessiner une barre vertical devant celle-ci
                if (reading.time.minute == 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        reading.time.format(DateTimeFormatter.ofPattern("HH")) + "h",
                        x,
                        size.height - yPadding + 60f,
                        Paint().apply {
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.CENTER
                            textSize = 35f
                            color = textColor
                        }
                    )
                    drawRect(
                        color = Color.Gray,
                        topLeft = Offset(x - xStep / 2f, 0f),
                        size = androidx.compose.ui.geometry.Size(2f, size.height)
                    )
                }
            }
        }
    }
}*/

@Composable
fun WeatherIconGraph(
    viewModel: WeatherViewModel,
    scrollState: ScrollState = rememberScrollState()
) {
    // Get the forecast
    val forecast by viewModel.hourlyForecast.collectAsState()
    // Charger les icônes
    val iconWeatherFolder = "file:///android_asset/icons/weather/"
    val sunnyDayIconPath: String = iconWeatherFolder + "clear-day.svg"
    val sunnyNightIconPath: String = iconWeatherFolder + "clear-night.svg"
    val sunnyCloudyDayIconPath: String = iconWeatherFolder + "cloudy-3-day.svg"
    val sunnyCloudyNightIconPath: String = iconWeatherFolder + "cloudy-3-night.svg"
    val sunnyCloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val cloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val foggyIconPath: String = iconWeatherFolder + "fog.svg"
    val dustIconPath: String = iconWeatherFolder + "dust.svg"
    val drizzleDayIconPath: String = iconWeatherFolder + "rainy-1-day.svg"
    val drizzleNightIconPath: String = iconWeatherFolder + "rainy-1-night.svg"
    val drizzleIconPath: String = iconWeatherFolder + "rainy-1.svg"
    val rainy1DayIconPath: String = iconWeatherFolder + "rainy-2-day.svg"
    val rainy1NightIconPath: String = iconWeatherFolder + "rainy-2-night.svg"
    val rainy1IconPath: String = iconWeatherFolder + "rainy-2.svg"
    val rainy2DayIconPath: String = iconWeatherFolder + "rainy-3-day.svg"
    val rainy2NightIconPath: String = iconWeatherFolder + "rainy-3-night.svg"
    val rainy2IconPath: String = iconWeatherFolder + "rainy-3.svg"
    val hailIconPath: String = iconWeatherFolder + "hail.svg"
    val snowy1IconPath: String = iconWeatherFolder + "snowy-1.svg"
    val snowy2IconPath: String = iconWeatherFolder + "snowy-2.svg"
    val snowy3IconPath: String = iconWeatherFolder + "snowy-3.svg"
    val snowyMixIconPath: String = iconWeatherFolder + "rain-and-snow-mix.svg"
    val stormyIconPath: String = iconWeatherFolder + "thunderstorms.svg"

    val simpleWeatherList = mutableListOf<Pair<SimpleWeatherWord, Boolean?>>()

    if ((forecast as WeatherDataState.SuccessHourly).data.first().skyInfo.shortwaveRadiation != null) {
        for (index in 0..23) {
            simpleWeatherList.add(
                Pair(
                    getSimpleWeather((forecast as WeatherDataState.SuccessHourly).data[index]).word,
                    (forecast as WeatherDataState.SuccessHourly).data[index].skyInfo.shortwaveRadiation!! >= 1.0
                )
            )
        }
    } else {
        for (index in 0..23) {
            simpleWeatherList.add(Pair(getSimpleWeather((forecast as WeatherDataState.SuccessHourly).data[index]).word, null))
        }
    }

    Box(
        modifier = Modifier
            .width(1000.dp) // Largeur fixe, identique à GenericGraph
            .horizontalScroll(scrollState), // ScrollState partagé
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), // La Row prend toute la largeur du Box (1000.dp)
            horizontalArrangement = Arrangement.SpaceAround, // L'arrangement gère l'espacement
            verticalAlignment = Alignment.CenterVertically
        ) {
            simpleWeatherList.forEach { (weatherWord, isDay) ->
                val fileName = when (weatherWord) {
                    SimpleWeatherWord.SUNNY -> if (isDay != null) if (isDay) sunnyDayIconPath else sunnyNightIconPath else sunnyDayIconPath
                    SimpleWeatherWord.SUNNY_CLOUDY -> if (isDay != null) if (isDay) sunnyCloudyDayIconPath else sunnyCloudyNightIconPath else sunnyCloudyIconPath
                    SimpleWeatherWord.CLOUDY -> cloudyIconPath
                    SimpleWeatherWord.FOGGY -> foggyIconPath
                    SimpleWeatherWord.DUST -> dustIconPath
                    SimpleWeatherWord.DRIZZLY -> if (isDay != null) if (isDay) drizzleDayIconPath else drizzleNightIconPath else drizzleIconPath
                    SimpleWeatherWord.RAINY1 -> if (isDay != null) if (isDay) rainy1DayIconPath else rainy1NightIconPath else rainy1IconPath
                    SimpleWeatherWord.RAINY2 -> if (isDay != null) if (isDay) rainy2DayIconPath else rainy2NightIconPath else rainy2IconPath
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
                        .width(41.5.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}