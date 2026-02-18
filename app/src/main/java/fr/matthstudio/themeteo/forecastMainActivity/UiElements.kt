package fr.matthstudio.themeteo.forecastMainActivity

import android.Manifest
import android.content.Intent
import android.location.Geocoder
import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import fr.matthstudio.themeteo.AllHourlyVarsReading
import fr.matthstudio.themeteo.CurrentWeatherReading
import fr.matthstudio.themeteo.DailyReading
import fr.matthstudio.themeteo.GeocodingResult
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.dayGraphsActivity.DayGraphsActivity
import fr.matthstudio.themeteo.dayGraphsActivity.GenericGraphGlobal
import fr.matthstudio.themeteo.dayGraphsActivity.GraphType
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.text.lowercase

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
    HAZE,         // Brume
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

fun getModelSourceText(model: String?): String {
    return when (model) {
        "best_match" -> "Open-Meteo"
        "ecmwf_ifs" -> "ECMWF IFS"
        "ecmwf_aifs025_single" -> "ECMWF AIFS"
        "meteofrance_seamless" -> "Météo France"
        "gfs_seamless" -> "NCEP GFS"
        "icon_seamless" -> "DWD ICON"
        "gem_seamless" -> "GEM"
        "gfs_graphcast025" -> "GFS GraphCast"
        "ukmo_seamless" -> "UK Met Office"
        else -> "Open-Meteo"
    }
}

fun weatherCodeToSimpleWord(code: Int): SimpleWeatherWord {
    return when (code) {
        0 -> SimpleWeatherWord.SUNNY
        1, 2 -> SimpleWeatherWord.SUNNY_CLOUDY
        3 -> SimpleWeatherWord.CLOUDY                 // Overcast
        4 -> SimpleWeatherWord.CLOUDY                 // Smoke
        5 -> SimpleWeatherWord.HAZE                   // Haze
        6, 7, 8, 9 -> SimpleWeatherWord.DUST          // Dust
        in 10..12 -> SimpleWeatherWord.FOGGY          // Mist/Shallow fog
        in 13..19 -> SimpleWeatherWord.CLOUDY         // Lightning, Squalls, etc.
        20 -> SimpleWeatherWord.DRIZZLY               // Past hour: Drizzle/Snow grains
        21 -> SimpleWeatherWord.RAINY1                // Past hour: Rain
        22 -> SimpleWeatherWord.SNOWY1                // Past hour: Snow
        23 -> SimpleWeatherWord.SNOWY_MIX             // Past hour: Rain and Snow
        24 -> SimpleWeatherWord.DRIZZLY               // Past hour: Freezing rain
        25 -> SimpleWeatherWord.RAINY2                // Past hour: Showers
        26 -> SimpleWeatherWord.SNOWY2                // Past hour: Snow showers
        27 -> SimpleWeatherWord.HAIL                  // Past hour: Hail
        28 -> SimpleWeatherWord.FOGGY                 // Past hour: Fog
        29 -> SimpleWeatherWord.STORMY                // Past hour: Thunderstorm
        in 30..39 -> SimpleWeatherWord.DUST           // Duststorms, sandstorms
        in 40..49 -> SimpleWeatherWord.FOGGY    // Fog
        in 50..59 -> SimpleWeatherWord.DRIZZLY  // Drizzle
        in 60..69 -> SimpleWeatherWord.RAINY1   // Rain
        70, 71 -> SimpleWeatherWord.SNOWY1            // Light Snow
        72, 73 -> SimpleWeatherWord.SNOWY2            // Snow
        74, 75 -> SimpleWeatherWord.SNOWY3            // Heavy Snow
        in 76..79 -> SimpleWeatherWord.SNOWY_MIX// Snow Grains
        in 80..82 -> SimpleWeatherWord.RAINY2   // Rain showers
        83, 84 -> SimpleWeatherWord.SNOWY_MIX         // Rain and snow mixed showers
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
        mutableStateOf(SimpleWeather("", SimpleWeatherWord.SUNNY))
    }

    val skySunny = stringResource(R.string.sky_sunny)
    val skyClear = stringResource(R.string.sky_clear)
    val skyVeiled = stringResource(R.string.sky_veiled)
    val skyScattered = stringResource(R.string.sky_scattered)
    val skyPartlyCloudy = stringResource(R.string.sky_partly_cloudy)
    val skyOvercast = stringResource(R.string.sky_overcast)
    
    val modWithVeil = stringResource(R.string.mod_with_veil)
    val modWithVeiledSky = stringResource(R.string.mod_with_veiled_sky)
    val modWithLightSnow = stringResource(R.string.mod_with_light_snow)
    val modWithModerateSnow = stringResource(R.string.mod_with_moderate_snow)
    val modWithHeavySnow = stringResource(R.string.mod_with_heavy_snow)
    val modWithLightRain = stringResource(R.string.mod_with_light_rain)
    val modWithModerateRain = stringResource(R.string.mod_with_moderate_rain)
    val modWithHeavyRain = stringResource(R.string.mod_with_heavy_rain)
    val modWithTorrentialRain = stringResource(R.string.mod_with_torrential_rain)
    val modWithPrecipitation = stringResource(R.string.mod_with_precipitation)
    
    val sentenceFormat = stringResource(R.string.weather_sentence_format)

    LaunchedEffect(value) {
        val skyState = value.skyInfo
        val precipitation = value.precipitationData.precipitation
        val rain = value.precipitationData.rain
        val snow = value.precipitationData.snowfall
        val wCode = value.wmo

        val newWeather = SimpleWeather("", SimpleWeatherWord.SUNNY)
        newWeather.word = weatherCodeToSimpleWord(wCode)

        var skySentence = ""
        var modifier: String? = null

        // 1. État du ciel
        if (skyState.opacity in 1..30) {
            skySentence = skySunny
            if (skyState.cloudcoverHigh > 50) modifier = modWithVeil
        } else if (max(skyState.cloudcoverLow, skyState.cloudcoverMid) <= 25) {
            skySentence = if (skyState.cloudcoverHigh > 50) skyVeiled else skyClear
        } else if (max(skyState.cloudcoverLow, skyState.cloudcoverMid) <= 50) {
            skySentence = skyScattered
            if (skyState.cloudcoverHigh > 50) modifier = modWithVeiledSky
        } else if (max(skyState.cloudcoverLow, skyState.cloudcoverMid) <= 75) {
            skySentence = skyPartlyCloudy
            if (skyState.cloudcoverHigh > 50) modifier = modWithVeil
        } else {
            skySentence = skyOvercast
        }

        // 2. Précipitations (Priorité à la neige)
        var precipModifier: String? = null
        if (precipitation >= 0.1f) {
            precipModifier = if (snow >= 0.1f) {
                if (snow < 0.5) modWithLightSnow else if (snow < 1.0) modWithModerateSnow else modWithHeavySnow
            } else if (rain >= 0.1f) {
                if (rain < 0.5) modWithLightRain else if (rain < 3.0) modWithModerateRain else if (rain < 10.0) modWithHeavyRain else modWithTorrentialRain
            } else modWithPrecipitation
        }

        // Assemblage final
        var finalSentence = skySentence
        if (modifier != null) {
            finalSentence = String.format(sentenceFormat, finalSentence, modifier)
        }
        if (precipModifier != null) {
            finalSentence = String.format(sentenceFormat, finalSentence, precipModifier)
        }
        
        newWeather.sentence = finalSentence
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
fun DailyWeatherBox(dayReading: DailyReading, viewModel: WeatherViewModel, onClick: () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val weatherIconFilter = remember(isDark) {
        if (!isDark) {
            ColorFilter.colorMatrix(ColorMatrix().apply {
                setToScale(0.8f, 0.8f, 0.8f, 1f)
            })
        } else null
    }

    // Charger les icônes
    val iconWeatherFolder = "file:///android_asset/icons/weather/"
    val sunnyDayIconPath: String = iconWeatherFolder + "clear-day.svg"
    val sunnyCloudyDayIconPath: String = iconWeatherFolder + "cloudy-3-day.svg"
    val cloudyIconPath: String = iconWeatherFolder + "cloudy.svg"
    val foggyIconPath: String = iconWeatherFolder + "fog.svg"
    val hazeIconPath: String = iconWeatherFolder + "haze.svg"
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
                onClick()
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
                // Get the day name (e.g., "Mon.")
                Text(
                    text = dayReading.date.dayOfWeek.getDisplayName(
                        TextStyle.SHORT,
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
                    SimpleWeatherWord.HAZE -> hazeIconPath
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
                    contentScale = ContentScale.Fit,
                    colorFilter = weatherIconFilter
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
    savedLocations: List<SavedLocation>,
    selectedLocation: LocationIdentifier,
    currentWeathers: WeatherDataState,
    defaultLocation: LocationIdentifier,
    onSelectLocation: (LocationIdentifier) -> Unit,
    onRemoveLocation: (SavedLocation) -> Unit,
    onSetDefaultLocation: (LocationIdentifier) -> Unit,
    onAddLocationClick: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
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
                        isDefault = defaultLocation is LocationIdentifier.CurrentUserLocation,
                        onClick = {
                            onSelectLocation(LocationIdentifier.CurrentUserLocation)
                            onDismiss()
                        },
                        onDelete = null, // On ne peut pas supprimer la position actuelle
                        onSetAsDefault = { onSetDefaultLocation(LocationIdentifier.CurrentUserLocation) }
                    )
                }

                // Liste des lieux sauvegardés
                items(savedLocations) { location ->
                    // On vérifie si defaultLocation est un type 'Saved' et si sa localisation interne est la même
                    val isDefault = (defaultLocation as? LocationIdentifier.Saved)?.location == location
                    val currentWeather = (currentWeathers as? WeatherDataState.SuccessCurrent)?.data[Pair(location.latitude, location.longitude)]
                    LocationRow(
                        name = location.name,
                        isSelected = (selectedLocation as? LocationIdentifier.Saved)?.location == location,
                        currentWeatherReading = currentWeather,
                        isDefault = isDefault,
                        onClick = {
                            onSelectLocation(LocationIdentifier.Saved(location))
                            onDismiss()
                        },
                        onDelete = { onRemoveLocation(location) },
                        onSetAsDefault = { onSetDefaultLocation(LocationIdentifier.Saved(location)) }
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
    isDefault: Boolean,
    currentWeatherReading: CurrentWeatherReading? = null,
    onSetAsDefault: () -> Unit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? // Nullable car la position actuelle n'a pas de bouton de suppression
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else Color.Transparent,
                MaterialTheme.shapes.small
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontWeight = if (isSelected) FontWeight.Bold else null
            )
            Row (verticalAlignment = Alignment.CenterVertically) {
                if (currentWeatherReading != null) {
                    Icon(
                        imageVector = getStateIconFromWord(weatherCodeToSimpleWord(currentWeatherReading.wmo)),
                        contentDescription = "Icône météo actuelle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${currentWeatherReading.temperature.roundToInt()}°",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else null
                    )
                }
                Icon (
                    imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(
                        enabled = true,
                        onClick = onSetAsDefault
                    )
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Supprimer le lieu",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// 3. LA BOÎTE DE DIALOGUE POUR LA RECHERCHE ET L'AJOUT
@Composable
fun AddLocationDialog(
    searchResults: List<GeocodingResult>, // Remplacez par votre type réel de résultat
    userLocation: GpsCoordinates?,
    onSearch: (String) -> Unit,
    onLocationSelected: (LocationIdentifier) -> Unit,
    onAddLocation: (SavedLocation) -> Unit,
    onMapLocationAdded: (GpsCoordinates, String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    var showMapPicker by remember { mutableStateOf(false) }

    if (showMapPicker) {
        Dialog(onDismissRequest = { showMapPicker = false }) {
            MapPickerScreen(
                initialLocation = userLocation,
                onLocationSelected = { coords, name ->
                    onMapLocationAdded(coords, name)
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
                        onSearch(it) // Déclenche la recherche via le ViewModel
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
                                    onAddLocation(newLocation)
                                    onLocationSelected(LocationIdentifier.Saved(newLocation))
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
    initialLocation: GpsCoordinates?,
    onLocationSelected: (GpsCoordinates, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Position initiale : GPS, fallback Paris
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(
                initialLocation?.latitude ?: 48.8566,
                initialLocation?.longitude ?: 2.3522
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
                .size(40.dp)
                // On remonte l'icône de 20dp (la moitié de sa taille)
                // pour que le bas de l'icône soit au centre du parent
                .offset(y = (-20).dp),
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
                    addresses?.firstOrNull()?.locality ?: context.getString(R.string.custom_location)
                } catch (e: Exception) {
                    context.getString(R.string.custom_location)
                }

                onLocationSelected(coords, cityName)
            }
        ) {
            Text(stringResource(R.string.pick_this_location))
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

    if (!locationPermissionState.allPermissionsGranted) {
        LaunchedEffect(Unit) {
            locationPermissionState.launchMultiplePermissionRequest()
        }
    }

    // Bug fix: Trigger refresh when permissions are granted to immediately fetch location
    LaunchedEffect(locationPermissionState.allPermissionsGranted) {
        if (locationPermissionState.allPermissionsGranted) {
            viewModel.refreshLocation()
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

@Composable
fun AdvancedGraph(
    fullForecast: WeatherDataState,
    roundToInt: Boolean,
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState(),
    contentWidth: Dp = 1000.dp,
    contentHeight: Dp = 150.dp,
    compactHourFormat: Boolean = false
) {
    var roundToInt = roundToInt

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
        scrollState,
        contentWidth,
        contentHeight,
        compactHourFormat
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
    val hazeIconPath: String = iconWeatherFolder + "haze.svg"
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
                    SimpleWeatherWord.HAZE -> hazeIconPath
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

/**
 * Mappe les identifiants numériques de phénomènes Météo-France vers leurs noms en français.
 * Basé sur la nomenclature officielle de l'API Vigilance.
 */
fun mapPhenomenonIdToName(id: String): Int = when (id) {
    "1" -> R.string.violent_wind
    "2" -> R.string.rain_flood
    "3" -> R.string.thunderstorms_phenomenon
    "4" -> R.string.floods
    "5" -> R.string.snow_ice
    "6" -> R.string.heatwave
    "7" -> R.string.extreme_cold
    "8" -> R.string.flooding
    "9" -> R.string.waves_submersion
    else -> R.string.unknown_phenomenon
}