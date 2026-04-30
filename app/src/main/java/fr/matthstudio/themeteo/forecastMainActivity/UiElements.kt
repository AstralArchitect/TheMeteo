/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.forecastMainActivity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotInterested
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.ExploreOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Flood
import androidx.compose.material.icons.rounded.SevereCold
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tsunami
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
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
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.WeatherService
import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.dayGraphsActivity.GenericGraphGlobal
import fr.matthstudio.themeteo.dayGraphsActivity.GraphType
import fr.matthstudio.themeteo.dayGraphsActivity.WeatherIconGraphGlobal
import fr.matthstudio.themeteo.utilClasses.PhaseType
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.VigilanceInfos
import fr.matthstudio.themeteo.utilClasses.toSmartString
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin

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
    var word: SimpleWeatherWord?,
    var image: ImageBitmap? = null
)

fun weatherCodeToSimpleWord(code: Int?): SimpleWeatherWord? {
    if (code == null) return null
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
        val precipitation = value.precipitationData.precipitation ?: 0.0
        val rain = value.precipitationData.rain ?: 0.0
        val snow = value.precipitationData.snowfall ?: 0.0
        val wCode = value.wmo

        val cloudLow = skyState.cloudcoverLow ?: 0
        val cloudMid = skyState.cloudcoverMid ?: 0
        val cloudHigh = skyState.cloudcoverHigh ?: 0
        val opacity = skyState.opacity ?: 0

        val newWeather = SimpleWeather("", SimpleWeatherWord.SUNNY)
        newWeather.word = weatherCodeToSimpleWord(wCode)

        var skySentence = ""
        var modifier: String? = null

        // 1. État du ciel
        if (opacity in 1..30) {
            skySentence = skySunny
            if (cloudHigh > 50) modifier = modWithVeil
        } else if (max(cloudLow, cloudMid) <= 25) {
            skySentence = if (cloudHigh > 50) skyVeiled else skyClear
        } else if (max(cloudLow, cloudMid) <= 50) {
            skySentence = skyScattered
            if (cloudHigh > 50) modifier = modWithVeiledSky
        } else if (max(cloudLow, cloudMid) <= 75) {
            skySentence = skyPartlyCloudy
            if (cloudHigh > 50) modifier = modWithVeil
        } else {
            skySentence = skyOvercast
        }

        // 2. Précipitations (Priorité à la neige)
        var precipModifier: String? = null
        if (precipitation >= 0.1) {
            if (snow >= 0.1) {
                precipModifier = if (snow < 0.5) modWithLightSnow else if (snow < 1.0) modWithModerateSnow else modWithHeavySnow
            } else if (rain >= 0.1) {
                precipModifier = if (rain < 0.5) modWithLightRain else if (rain < 3.0) modWithModerateRain else if (rain < 10.0) modWithHeavyRain else modWithTorrentialRain
            } else {
                precipModifier = modWithPrecipitation
            }
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
                    null -> Icons.Default.NotInterested
                }

                if (dayReading.wmoEnsemble != null) {
                    val userSettings by viewModel.userSettings.collectAsState()
                    val isBatterySaverActive by (LocalContext.current.applicationContext as TheMeteo).weatherCache.isBatterySaverActive.collectAsState()
                    val animated = userSettings.enableAnimatedIcons && !isBatterySaverActive
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EnsembleIconSmall(dayReading.wmoEnsemble.best, animated, weatherIconFilter)
                        EnsembleIconSmall(dayReading.wmoEnsemble.worst, animated, weatherIconFilter)
                    }
                } else {
                    if (fileName is String) {
                        AsyncImage(
                            model = fileName,
                            contentDescription = "Icône météo actuelle",
                            modifier = Modifier
                                .width(30.dp)
                                .height(30.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = weatherIconFilter
                        )
                    } else {
                        Image(
                            imageVector = fileName as ImageVector,
                            contentDescription = "Icône météo actuelle",
                            modifier = Modifier
                                .width(30.dp)
                                .height(30.dp),
                            contentScale = ContentScale.Fit,
                            colorFilter = weatherIconFilter
                        )
                    }
                }
                val userSettings by viewModel.userSettings.collectAsState()
                Text(
                    text = "${UnitConverter.formatTemperature(dayReading.maxTemperature, userSettings.temperatureUnit,
                        roundToInt = true,
                        showUnitSymbol = false,
                        showDegreeSymbol = true
                    )} / ${UnitConverter.formatTemperature(dayReading.minTemperature, userSettings.temperatureUnit,
                        roundToInt = true,
                        showUnitSymbol = false,
                        showDegreeSymbol = true
                    )}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TemperatureRangeBar(
    minTemp: Double,
    maxTemp: Double,
    minOverallTemp: Double,
    maxOverallTemp: Double,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    val range = maxOverallTemp - minOverallTemp
    if (range <= 0) return

    val startFactor = (minTemp - minOverallTemp) / range
    val endFactor = (maxTemp - minOverallTemp) / range

    Canvas(modifier = modifier
        .height(4.dp)
        .fillMaxWidth()) {
        val width = size.width
        val height = size.height
        val startX = (width * startFactor).toFloat()
        val endX = (width * endFactor).toFloat()

        // Background track
        drawRoundRect(
            color = Color.White.copy(alpha = 0.2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2, height / 2)
        )

        // Range bar
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFFFF8A65))
            ),
            topLeft = Offset(startX, 0f),
            size = androidx.compose.ui.geometry.Size(endX - startX, height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2, height / 2)
        )
    }
}

@Composable
fun DailyForecastRow(
    dayReading: DailyReading,
    isExpanded: Boolean,
    minOverallTemp: Double,
    maxOverallTemp: Double,
    userSettings: UserSettings,
    isBatterySaverActive: Boolean,
    onClick: () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val weatherIconFilter = remember(isDark) {
        if (!isDark) {
            ColorFilter.colorMatrix(ColorMatrix().apply {
                // Assombrit légèrement les icônes statiques en mode clair
                setToScale(0.7f, 0.7f, 0.7f, 1f)
            })
        } else null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Jour
            Text(
                text = if (dayReading.date == LocalDate.now()) stringResource(R.string.today)
                else dayReading.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(50.dp)
            )

            // Icône
            val weatherWord = weatherCodeToSimpleWord(dayReading.wmo)
            
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (dayReading.wmoEnsemble != null) {
                    Row {
                        EnsembleIconSmall(dayReading.wmoEnsemble.best, userSettings.enableAnimatedIcons && !isBatterySaverActive, weatherIconFilter)
                        EnsembleIconSmall(dayReading.wmoEnsemble.worst, userSettings.enableAnimatedIcons && !isBatterySaverActive, weatherIconFilter)
                    }
                } else if (weatherWord != null) {
                    LottieWeatherIcon(
                        iconPath = getLottieIconPath(weatherWord, false),
                        animate = userSettings.enableAnimatedIcons && !isBatterySaverActive,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Précipitations + vent
            Row(
                modifier = Modifier.width(80.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                if (dayReading.precipitation != null && dayReading.precipitation > 0.1) {
                    Icon(
                        Icons.Rounded.Water,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = if (isDark) Color(0xFF64B5F6) else Color(0xFF356486)
                    )
                    Spacer(Modifier.width(2.dp))
                    ResponsiveText(
                        text = "${dayReading.precipitation.toSmartString()}mm",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Color(0xFF64B5F6) else Color(0xFF356486)
                    )
                }
                Spacer(Modifier.width(8.dp))
                if ( (dayReading.maxWind.windspeed != null && dayReading.maxWind.windspeed >= 30) || (dayReading.maxWind.windGusts != null && dayReading.maxWind.windGusts >= 45) ) {
                    Icon(
                        Icons.Rounded.Air,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Températures
            Text(
                text = UnitConverter.formatTemperature(dayReading.minTemperature, userSettings.temperatureUnit, true, showUnitSymbol = false),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(35.dp)
            )

            TemperatureRangeBar(
                minTemp = dayReading.minTemperature ?: 0.0,
                maxTemp = dayReading.maxTemperature ?: 0.0,
                minOverallTemp = minOverallTemp,
                maxOverallTemp = maxOverallTemp,
                unit = userSettings.temperatureUnit,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Text(
                text = UnitConverter.formatTemperature(dayReading.maxTemperature, userSettings.temperatureUnit, true, showUnitSymbol = false),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Start,
                modifier = Modifier.width(35.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                expandedContent()
            }
        }
    }
}

@Composable
fun EnsembleIconSmall(wmo: Int?, animated: Boolean, filter: ColorFilter?) {
    if (wmo == null) return
    val weatherWord = weatherCodeToSimpleWord(wmo)!!

    LottieWeatherIcon(
        iconPath = getLottieIconPath(weatherWord, false),
        animate = animated,
        modifier = Modifier.size(30.dp)
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationManagementSheet(
    savedLocations: List<SavedLocation>,
    selectedLocation: LocationIdentifier,
    currentWeathers: WeatherDataState,
    userSettings: UserSettings,
    isPermissionGranted: Boolean,
    onSelectLocation: (LocationIdentifier) -> Unit,
    onRemoveLocation: (SavedLocation) -> Unit,
    onRenameLocation: (SavedLocation, String) -> Unit,
    onReorderLocations: (List<SavedLocation>) -> Unit,
    onSetDefaultLocation: (LocationIdentifier) -> Unit,
    onAddLocationClick: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    // État local pour la liste réorganisable
    var listState by remember(savedLocations) { mutableStateOf(savedLocations) }
    val lazyListState = rememberLazyListState()
    var renamingLocation by remember { mutableStateOf<SavedLocation?>(null) }

    if (renamingLocation != null) {
        RenameLocationDialog(
            currentLocation = renamingLocation!!,
            onRename = { location, newName ->
                onRenameLocation(location, newName)
                renamingLocation = null
            },
            onDismiss = { renamingLocation = null }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) MaterialTheme.colorScheme.secondaryContainer.copy (alpha = 0.7f) else MaterialTheme.colorScheme.surface,
        scrimColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Color.Transparent else Color.Black.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp))
        {
            Text(stringResource(R.string.manage_locations), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Item pour la position actuelle - Visible uniquement si permission accordée
                if (isPermissionGranted) {
                    item {
                        LocationRow(
                            name = stringResource(R.string.current_location),
                            isSelected = selectedLocation is LocationIdentifier.CurrentUserLocation,
                            isDefault = userSettings.defaultLocation is LocationIdentifier.CurrentUserLocation,
                            temperatureUnit = userSettings.temperatureUnit,
                            roundToInt = userSettings.roundToInt,
                            onClick = {
                                onSelectLocation(LocationIdentifier.CurrentUserLocation)
                                onDismiss()
                            },
                            onDelete = null, // On ne peut pas supprimer la position actuelle
                            onSetAsDefault = { onSetDefaultLocation(LocationIdentifier.CurrentUserLocation) }
                        )
                    }
                }

                // Liste des lieux sauvegardés
                items(listState.size, key = { listState[it].name + listState[it].latitude + listState[it].longitude }) { index ->
                    val location = listState[index]
                    val isDefault = (userSettings.defaultLocation as? LocationIdentifier.Saved)?.location == location
                    val currentWeather = (currentWeathers as? WeatherDataState.SuccessCurrent)?.data[Pair(location.latitude, location.longitude)]
                    
                    var itemOffset by remember { mutableStateOf(0f) }
                    val currentIndex by rememberUpdatedState(index)
                    val itemHeight = 64f // Matching the Modifier.height(64.dp) below

                    LocationRow(
                        name = location.name,
                        isSelected = (selectedLocation as? LocationIdentifier.Saved)?.location == location,
                        currentWeatherReading = currentWeather,
                        temperatureUnit = userSettings.temperatureUnit,
                        roundToInt = userSettings.roundToInt,
                        isDefault = isDefault,
                        onClick = {
                            onSelectLocation(LocationIdentifier.Saved(location))
                            onDismiss()
                        },
                        onDelete = { onRemoveLocation(location) },
                        onRename = { renamingLocation = location },
                        onSetAsDefault = { onSetDefaultLocation(LocationIdentifier.Saved(location)) },
                        modifier = Modifier
                            .animateItem()
                            .offset(y = itemOffset.dp)
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { /* Optionnel : retour haptique, non utilisé ici */ },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        itemOffset += dragAmount.y / density

                                        // Logique de swap corrigée
                                        val threshold =
                                            32f // Seuil pour déclencher le swap (moitié de la hauteur)
                                        if (itemOffset > threshold && currentIndex < listState.size - 1) {
                                            val newList = listState.toMutableList()
                                            val item = newList.removeAt(currentIndex)
                                            newList.add(currentIndex + 1, item)
                                            listState = newList
                                            // Ajustement de l'offset pour compenser le changement de position "home"
                                            itemOffset -= itemHeight
                                            onReorderLocations(newList)
                                        } else if (itemOffset < -threshold && currentIndex > 0) {
                                            val newList = listState.toMutableList()
                                            val item = newList.removeAt(currentIndex)
                                            newList.add(currentIndex - 1, item)
                                            listState = newList
                                            // Ajustement de l'offset pour compenser le changement de position "home"
                                            itemOffset += itemHeight
                                            onReorderLocations(newList)
                                        }
                                    },
                                    onDragEnd = { itemOffset = 0f },
                                    onDragCancel = { itemOffset = 0f }
                                )
                            },
                        dragHandle = {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Réorganiser",
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(4.dp)
                            )
                        }
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

@Composable
fun LocationRow(
    modifier: Modifier = Modifier,
    name: String,
    isSelected: Boolean,
    isDefault: Boolean,
    currentWeatherReading: CurrentWeatherReading? = null,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    roundToInt: Boolean = true,
    onSetAsDefault: () -> Unit,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?, 
    onRename: (() -> Unit)? = null,
    dragHandle: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .5f) else Color.Transparent,
                MaterialTheme.shapes.small
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (dragHandle != null) {
                    dragHandle()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
                    fontWeight = if (isSelected) FontWeight.Bold else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row (verticalAlignment = Alignment.CenterVertically) {
                if (currentWeatherReading != null) {
                    Icon(
                        imageVector = getStateIconFromWord(weatherCodeToSimpleWord(currentWeatherReading.wmo)!!),
                        contentDescription = "Icône météo actuelle",
                        tint = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = UnitConverter.formatTemperature(currentWeatherReading.temperature, temperatureUnit, roundToInt),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else null
                    )
                }
                if (onRename != null) {
                    IconButton(onClick = onRename) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Renommer le lieu",
                            tint = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon (
                    imageVector = if (isDefault) Icons.Default.Star else Icons.Default.StarOutline,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
fun RenameLocationDialog(
    currentLocation: SavedLocation,
    onRename: (SavedLocation, String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(currentLocation.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer le lieu") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nouveau nom") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newName.isNotBlank()) {
                        onRename(currentLocation, newName)
                    }
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}


// 3. LA BOÎTE DE DIALOGUE POUR LA RECHERCHE ET L'AJOUT
@Composable
fun AddLocationDialog(
    searchResults: List<GeocodingResult>, // Remplacez par votre type réel de résultat
    userLocation: GpsCoordinates?,
    weatherService: WeatherService,
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
                weatherService = weatherService,
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
fun EnvironmentalGauge(
    value: Float, // 0.0 to 1.0
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            // Fond de la jauge (gris discret)
            drawArc(
                color = color.copy(alpha = 0.15f),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
            // Partie active de la jauge (couleur API)
            drawArc(
                color = color,
                startAngle = 140f,
                sweepAngle = 260f * value.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

@Composable
fun getPollenShortDescFromLevel(level: Int): String {
    return when (level) {
        0 -> stringResource(R.string.none)
        1 -> stringResource(R.string.low)
        2 -> stringResource(R.string.moderate)
        3 -> stringResource(R.string.high)
        4 -> stringResource(R.string.very_high)
        5 -> stringResource(R.string.extreme)
        else -> stringResource(R.string.unknown)
    }
}

@Composable
fun MapPickerScreen(
    initialLocation: GpsCoordinates?,
    weatherService: WeatherService,
    onLocationSelected: (GpsCoordinates, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

                scope.launch {
                    val cityName = weatherService.getCityNameFromCoords(
                        target.latitude,
                        target.longitude,
                        context
                    ) ?: context.getString(R.string.custom_location)
                    
                    onLocationSelected(coords, cityName)
                }
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

    // Trigger refresh when any permission is granted to immediately fetch location
    val anyPermissionGranted = locationPermissionState.permissions.any { it.status.isGranted }
    LaunchedEffect(anyPermissionGranted) {
        if (anyPermissionGranted) {
            viewModel.refreshLocation()
        }
    }
}

@Composable
fun GenericGraph(
    viewModel: WeatherViewModel,
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
    graphType: GraphType,
    graphColor: Color,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    scrollState: ScrollState = rememberScrollState()
) {
    val fullForecast by viewModel.hourlyForecast.collectAsState()
    var roundToInt = viewModel.userSettings.collectAsState().value.roundToInt

    if (graphType == GraphType.PRECIPITATION || graphType == GraphType.RAIN ||
        graphType == GraphType.SNOWFALL
    )
        roundToInt = false

    GenericGraphGlobal(
        fullForecast,
        roundToInt,
        temperatureUnit,
        windUnit,
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
    temperatureUnit: TemperatureUnit,
    windUnit: WindUnit,
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
        temperatureUnit,
        windUnit,
        graphType,
        graphColor,
        valueRange,
        scrollState,
        contentWidth,
        contentHeight,
        compactHourFormat,
        sparseMode = true
    )
}

@Composable
fun WeatherIconGraph(
    viewModel: WeatherViewModel,
    forecast: WeatherDataState?,
    scrollState: ScrollState = rememberScrollState(),
    contentWidth: Dp = 1000.dp,
    showPairsOnly: Boolean = false
) {
    // Get the forecast
    val defaultForecast by viewModel.hourlyForecast.collectAsState()
    val userSettings by viewModel.userSettings.collectAsState()
    val isBatterySaverActive by (LocalContext.current.applicationContext as TheMeteo).weatherCache.isBatterySaverActive.collectAsState()

    WeatherIconGraphGlobal(
        forecast ?: defaultForecast,
        scrollState,
        userSettings,
        isBatterySaverActive,
        contentWidth,
        showPairsOnly
    )
}

@Composable
fun HealthAdviceCard(title: String, advice: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(advice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EnvironmentalSectionHeader(title: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SunPathVisualization(viewModel: WeatherViewModel) {
    val sunData by viewModel.sunData.collectAsState()
    if (sunData == null) return
    val data = sunData!!

    val context = LocalContext.current
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    val now = LocalDateTime.now()
    val totalSecondsDay = 24 * 3600f
    val currentSeconds = now.hour * 3600f + now.minute * 60f + now.second

    // hier = dailyData[0], aujourd'hui = dailyData[1], demain = dailyData[2]
    val yesterday = data.dailyData.getOrNull(0)
    val today = data.dailyData.getOrNull(1)
    val tomorrow = data.dailyData.getOrNull(2)

    val sr0 = today?.sunrise?.let { it.hour * 3600f + it.minute * 60f + it.second } ?: (6f * 3600f)
    val ss0 = today?.sunset?.let { it.hour * 3600f + it.minute * 60f + it.second } ?: (18f * 3600f)

    val isShifted = ss0 < sr0

    val (srUsed, ssUsed, windowStart, windowEnd) = if (isShifted && tomorrow != null) {
        // Logique Shifting : centré sur le midi solaire
        val ss1 = tomorrow.sunset.let { it.hour * 3600f + it.minute * 60f + it.second } ?: ss0
        val ss1Shifted = ss1 + totalSecondsDay
        val noon = (sr0 + ss1Shifted) / 2f
        listOf(sr0, ss1Shifted, noon - totalSecondsDay / 2, noon + totalSecondsDay / 2)
    } else {
        // Logique Standard : 00:00 à 24:00
        val ss0Fixed = if (ss0 < sr0) ss0 + totalSecondsDay else ss0
        listOf(sr0, ss0Fixed, 0f, totalSecondsDay)
    }

    val noonSecs = (srUsed + ssUsed) / 2f

    var sunSecs = currentSeconds
    // Normalize currentSeconds into [windowStart, windowEnd]
    while (sunSecs < windowStart) sunSecs += totalSecondsDay
    while (sunSecs > windowEnd) sunSecs -= totalSecondsDay
    val sunProgress = (sunSecs - windowStart) / totalSecondsDay

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        // --- ENTÊTE : Durée du jour et Zenith ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                if (today != null && tomorrow != null && yesterday != null) {
                    if (sunSecs > srUsed && sunSecs < ssUsed) {
                        Text(
                            text = stringResource(R.string.day_duration_no_value),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "%dh %dmin",
                                today.dayLength.toHours(), today.dayLength.toMinutes() % 60
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (sunSecs > ssUsed) {
                        val nightLength = Duration.between(tomorrow.sunrise, today.sunset.plusHours(24))
                        Text(
                            text = "Night Duration",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "%dh %dmin",
                                nightLength.toHours(), nightLength.seconds % 60
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Zenith",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.getDefault(), "%.1f°", today?.zenithElevation ?: 0.0),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- VISUALISATION PRINCIPALE (Canvas) ---
        BoxWithConstraints (
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val widthDp = maxWidth

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val horizonY = size.height / 2

                // Curve crosses horizon at sunrise/sunset relative to noon
                val crossingCos = cos(2.0 * PI * (srUsed - noonSecs) / totalSecondsDay).toFloat()

                // Normalize scale so day peak is at top and night trough at bottom
                val scaleY = (size.height / 2 - 25.dp.toPx()) / (1f + abs(crossingCos))

                // Draw Sine Path (24h cycle centered or standard)
                val path = Path()
                val segments = 150
                for (i in 0..segments) {
                    val tSecs = windowStart + (i.toFloat() / segments) * totalSecondsDay
                    val x = (i.toFloat() / segments) * size.width
                    val yOffset =
                        cos(2.0 * PI * (tSecs - noonSecs) / totalSecondsDay).toFloat() - crossingCos
                    val y = horizonY - yOffset * scaleY
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = Color.Gray.copy(alpha = 0.3f),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(10f, 10f),
                            0f
                        )
                    )
                )

                // Horizon line
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(0f, horizonY),
                    end = Offset(size.width, horizonY),
                    strokeWidth = 1.dp.toPx()
                )

                // Current sun position on the curve
                val currentYOffset =
                    cos(2.0 * PI * (sunSecs - noonSecs) / totalSecondsDay).toFloat() - crossingCos
                val sunY = horizonY - currentYOffset * scaleY

                val isDay = currentYOffset > 0
                val sunColor = if (isDay) Color(0xFFFFD700) else Color(0xFFB0C4DE)

                drawCircle(
                    color = sunColor,
                    radius = 8.dp.toPx(),
                    center = Offset(sunProgress * size.width, sunY)
                )

                if (isDay) {
                    drawCircle(
                        color = sunColor.copy(alpha = 0.3f),
                        radius = 12.dp.toPx(),
                        center = Offset(sunProgress * size.width, sunY)
                    )
                }
            }

            // Time markers
            Box(modifier = Modifier.fillMaxSize()) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

                // Absolute markers (Dynamic based on window)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    fun formatSecs(secs: Float): String {
                        val s = (secs % totalSecondsDay + totalSecondsDay) % totalSecondsDay
                        val m = (s / 60).toInt()
                        val h = (m / 60)
                        val mm = (s % 60).toInt()
                        return String.format(Locale.getDefault(), "%02d:%02d", h, mm)
                    }
                    Text(formatSecs(windowStart), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatSecs((windowStart + windowEnd) / 2f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatSecs(windowEnd), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }

                // Solar Noon Marker at Zenith (Standard mode only)
                if (!isShifted) {
                    val noonX = (noonSecs / totalSecondsDay) * widthDp.value
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = noonX.dp, y = 8.dp) // Placed near the top
                            .graphicsLayer { translationX = -size.width / 2 }
                    ) {
                        Text(
                            text = (noonSecs % totalSecondsDay).let { s ->
                                val m = (s / 60).toInt()
                                val h = (m / 60)
                                val mm = m % 60
                                String.format(Locale.getDefault(), "%02d:%02d", h, mm)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            //color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- PIED DE PAGE : Heures et Azimut précis ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.WbSunny, null, Modifier.size(22.dp), tint = Color(0xFFFFB300))
                ResponsiveText(today?.sunrise?.format(formatter) ?: "--:--", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                ResponsiveText(String.format(Locale.getDefault(), "Az: %.1f°", today?.sunriseAzimuth ?: 0.0), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            Column(
                modifier = Modifier.weight(2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ResponsiveText(
                    text = String.format(Locale.getDefault(), "ELEV: %.4f°", data.currentPosition.elevation),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                ResponsiveText(
                    text = String.format(Locale.getDefault(), "AZIMUTH: %.4f°", data.currentPosition.azimuth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.WbTwilight, null, Modifier.size(22.dp), tint = Color(0xFFFF7043))
                ResponsiveText(today?.sunset?.format(formatter) ?: "--:--", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                ResponsiveText(String.format(Locale.getDefault(), "Az: %.1f°", today?.sunsetAzimuth ?: 0.0), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }

        // --- SECTION HEURES DORÉES ---
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFB300).copy(alpha = 0.1f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ResponsiveText("Heure Dorée Matin", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                Text("${data.dailyData[1].goldenHourMorning.first.format(formatter)} - ${data.dailyData[1].goldenHourMorning.second.format(formatter)}", style = MaterialTheme.typography.bodySmall)
            }
            VerticalDivider(modifier = Modifier.height(30.dp), thickness = 1.dp, color = Color(0xFFFFB300).copy(alpha = 0.3f))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                ResponsiveText("Heure Dorée Soir", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100))
                Text("${data.dailyData[1].goldenHourEvening.first.format(formatter)} - ${data.dailyData[1].goldenHourEvening.second.format(formatter)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SunMoonCompass(viewModel: WeatherViewModel) {
    val sunData by viewModel.sunData.collectAsState()
    val moonData by viewModel.moonData.collectAsState()
    val context = LocalContext.current

    if (sunData == null || moonData == null) return

    val sunToday = sunData!!.dailyData.getOrNull(1) ?: return
    val moonPos = moonData!!.currentPosition
    val sunPos = sunData!!.currentPosition

    // Sensor-based rotation state
    var isRotationEnabled by remember { mutableStateOf(false) }
    var currentHeading by remember { mutableFloatStateOf(0f) }

    // Sensor Logic
    DisposableEffect(isRotationEnabled) {
        if (!isRotationEnabled) return@DisposableEffect onDispose {}

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotVec = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        var smoothedAz = 0f
        val alpha = 0.15f
        val rMat = FloatArray(9)
        val iMat = FloatArray(9)
        val orient = FloatArray(3)
        val accelVals = FloatArray(3)
        val magnetVals = FloatArray(3)
        var haveAccel = false
        var haveMag = false

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val R = FloatArray(9)
                        val ori = FloatArray(3)
                        SensorManager.getRotationMatrixFromVector(R, event.values)
                        SensorManager.getOrientation(R, ori)
                        updateAzimuth(Math.toDegrees(ori[0].toDouble()).toFloat())
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        for (i in event.values.indices) accelVals[i] += alpha * (event.values[i] - accelVals[i])
                        haveAccel = true
                        maybeUpdateFallback()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        for (i in event.values.indices) magnetVals[i] += alpha * (event.values[i] - magnetVals[i])
                        haveMag = true
                        maybeUpdateFallback()
                    }
                }
            }

            private fun maybeUpdateFallback() {
                if (haveAccel && haveMag) {
                    if (SensorManager.getRotationMatrix(rMat, iMat, accelVals, magnetVals)) {
                        SensorManager.getOrientation(rMat, orient)
                        updateAzimuth(Math.toDegrees(orient[0].toDouble()).toFloat())
                    }
                }
            }

            private fun updateAzimuth(rawDeg: Float) {
                var deg = (rawDeg + 360f) % 360f

                // Add display rotation
                val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        context.display?.rotation ?: Surface.ROTATION_0
                    } catch (e: Exception) {
                        Surface.ROTATION_0
                    }
                } else {
                    Surface.ROTATION_0
                }

                val rotationDegrees = when (rotation) {
                    Surface.ROTATION_0 -> 0f
                    Surface.ROTATION_90 -> 90f
                    Surface.ROTATION_180 -> 180f
                    Surface.ROTATION_270 -> 270f
                    else -> 0f
                }
                deg = (deg + rotationDegrees + 360f) % 360f

                var diff = deg - smoothedAz
                if (abs(diff) > 180f) diff -= 360f * sign(diff)
                smoothedAz = (smoothedAz + alpha * diff + 360f) % 360f
                currentHeading = smoothedAz
            }
        }

        if (rotVec != null) {
            sensorManager.registerListener(listener, rotVec, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(48.dp))
            Text(
                text = stringResource(R.string.compass_view),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { isRotationEnabled = !isRotationEnabled }) {
                Icon(
                    if (isRotationEnabled) Icons.Rounded.Explore else Icons.Rounded.ExploreOff,
                    contentDescription = stringResource(R.string.enable_rotation),
                    tint = if (isRotationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
                .graphicsLayer {
                    if (isRotationEnabled) {
                        rotationZ = -currentHeading
                    }
                }
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    CircleShape
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.width / 2

                // Outer Circle
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.1f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Markers for N, E, S, W
                val markerLength = 10.dp.toPx()
                for (angle in 0 until 360 step 90) {
                    val rad = (angle - 90) * (PI / 180.0)
                    val start = Offset(
                        center.x + (radius - markerLength) * cos(rad).toFloat(),
                        center.y + (radius - markerLength) * sin(rad).toFloat()
                    )
                    val end = Offset(
                        center.x + radius * cos(rad).toFloat(),
                        center.y + radius * sin(rad).toFloat()
                    )
                    drawLine(Color.Gray, start, end, strokeWidth = 2.dp.toPx())
                }

                // Sunrise Azimuth
                drawAzimuthMarker(
                    center, radius, sunToday.sunriseAzimuth,
                    color = Color(0xFFFFB300),
                    label = "Rise"
                )

                // Sunset Azimuth
                drawAzimuthMarker(
                    center, radius, sunToday.sunsetAzimuth,
                    color = Color(0xFFFF7043),
                    label = "Set"
                )

                // Current Sun Position
                drawAzimuthPointer(
                    center, radius * cos(sunPos.elevation * (PI / 180.0)).toFloat(), sunPos.azimuth,
                    color = if (sunPos.elevation >= 0 ) Color(0xFFFFD700) else Color(0x00FFD700),
                    iconRadius = 8.dp.toPx()
                )

                // Current Moon Position
                drawAzimuthPointer(
                    center, radius * cos(moonPos.elevation.toDouble() * (PI / 180.0)).toFloat(), moonPos.azimuth,
                    color = if (moonPos.elevation >= 0 ) Color(0xFFB0C4DE) else Color(0x00B0C4DE),
                    iconRadius = 6.dp.toPx()
                )
            }

            // Central text for North
            Text(
                "N",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter)
            )
            Text(
                "S",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            Text(
                "E",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            )
            Text(
                "W",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegendItem(Color(0xFFFFB300), stringResource(R.string.sunrise))
            LegendItem(Color(0xFFFF7043), stringResource(R.string.sunset))
            LegendItem(Color(0xFFFFD700), stringResource(R.string.sun))
            LegendItem(Color(0xFFB0C4DE), stringResource(R.string.moon))
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAzimuthMarker(
    center: Offset,
    radius: Float,
    azimuth: Double,
    color: Color,
    label: String
) {
    val rad = (azimuth - 90) * (PI / 180.0)
    val pos = Offset(
        center.x + radius * cos(rad).toFloat(),
        center.y + radius * sin(rad).toFloat()
    )

    drawCircle(
        color = color,
        radius = 4.dp.toPx(),
        center = pos
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAzimuthPointer(
    center: Offset,
    length: Float,
    azimuth: Double,
    color: Color,
    iconRadius: Float
) {
    val rad = (azimuth - 90) * (PI / 180.0)
    val pointerEnd = Offset(
        center.x + length * cos(rad).toFloat(),
        center.y + length * sin(rad).toFloat()
    )

    drawLine(
        color = color,
        start = center,
        end = pointerEnd,
        strokeWidth = 2.dp.toPx()
    )

    drawCircle(
        color = color,
        radius = iconRadius,
        center = pointerEnd
    )

    // Add glow
    drawCircle(
        color = color.copy(alpha = 0.3f),
        radius = iconRadius + 4.dp.toPx(),
        center = pointerEnd
    )
}


@Composable
fun MoonDetailsSection(viewModel: WeatherViewModel) {
    val moonData by viewModel.moonData.collectAsState()
    if (moonData == null) return
    val data = moonData!!
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    val phaseName = when (data.dailyEvents.phase.phaseType) {
        PhaseType.NEW_MOON -> stringResource(R.string.moon_phase_new_moon)
        PhaseType.WAXING_CRESCENT -> stringResource(R.string.moon_phase_waxing_crescent)
        PhaseType.FIRST_QUARTER -> stringResource(R.string.moon_phase_first_quarter)
        PhaseType.WAXING_GIBBOUS -> stringResource(R.string.moon_phase_waxing_gibbous)
        PhaseType.FULL_MOON -> stringResource(R.string.moon_phase_full_moon)
        PhaseType.WANING_GIBBOUS -> stringResource(R.string.moon_phase_waning_gibbous)
        PhaseType.LAST_QUARTER -> stringResource(R.string.moon_phase_last_quarter)
        PhaseType.WANING_CRESCENT -> stringResource(R.string.moon_phase_waning_crescent)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        // --- En-tête : Phase et Illumination ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.moon_phase), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(phaseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.illumination, String.format(Locale.getDefault(), "%.1f%%", data.dailyEvents.phase.fractionIlluminated * 100)), style = MaterialTheme.typography.bodyMedium)
            }

            // Icone dynamique de la lune
            Canvas(modifier = Modifier.size(48.dp)) {
                val r = size.width / 2
                val center = Offset(r, r)

                // 1. Fond : Partie sombre de la lune
                drawCircle(
                    color = Color(0xFF2C3E50).copy(alpha = 0.4f),
                    radius = r,
                    center = center
                )

                val fraction = data.dailyEvents.phase.fractionIlluminated.toFloat()
                val isWaxing = data.dailyEvents.phase.ageDays < 14.765

                if (fraction > 0.01f) {
                    val path = Path()
                    val x = r * (1f - 2f * fraction)
                    val limbSweep = if (isWaxing) 180f else -180f
                    val terminatorSweep = -limbSweep

                    // 2. Dessiner la partie illuminée
                    path.moveTo(center.x, center.y - r)

                    // Arc du bord extérieur (Limb)
                    path.arcTo(
                        rect = androidx.compose.ui.geometry.Rect(center.x - r, center.y - r, center.x + r, center.y + r),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = limbSweep,
                        forceMoveTo = false
                    )

                    // Arc du terminateur (la ligne entre jour et nuit sur la lune)
                    path.arcTo(
                        rect = androidx.compose.ui.geometry.Rect(center.x - abs(x), center.y - r, center.x + abs(x), center.y + r),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = if (x > 0) terminatorSweep else limbSweep,
                        forceMoveTo = false
                    )

                    path.close()

                    drawPath(
                        path = path,
                        color = Color(0xFFF5F5F5) // Blanc cassé / Argent
                    )

                    // Petit effet de lueur
                    drawCircle(
                        color = Color(0xFFF5F5F5).copy(alpha = 0.1f),
                        radius = r + 2.dp.toPx(),
                        center = center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Infos de Position ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ResponsiveText(stringResource(R.string.moonrise), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ResponsiveText(data.dailyEvents.moonrise?.format(formatter) ?: "--:--", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (data.dailyEvents.moonriseAzimuth != null) {
                    ResponsiveText(String.format(Locale.getDefault(), "Az: %.1f°", data.dailyEvents.moonriseAzimuth), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            Column(
                modifier = Modifier.weight(2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ResponsiveText(
                    text = String.format(Locale.getDefault(), "ELEV: %.2f°", data.currentPosition.elevation),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF5C6BC0), // Indigo pour la lune
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                ResponsiveText(
                    text = String.format(Locale.getDefault(), "AZIMUTH: %.4f°", data.currentPosition.azimuth),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF7986CB),
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ResponsiveText(stringResource(R.string.moonset), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ResponsiveText(data.dailyEvents.moonset?.format(formatter) ?: "--:--", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (data.dailyEvents.moonsetAzimuth != null) {
                    ResponsiveText(String.format(Locale.getDefault(), "Az: %.1f°", data.dailyEvents.moonsetAzimuth), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = String.format(Locale.getDefault(), "Distance: %,d km", data.currentPosition.distanceKm.toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun VigilanceDetailsDialog(vigilanceData: VigilanceInfos, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val dayFormatter = DateTimeFormatter.ofPattern("dd/MM")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Color.Transparent else Color.Black.copy(
                    alpha = 0.6f
                )
            )
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f)
        ) {
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        stringResource(
                            R.string.vigilance_alerts_dept_code,
                            vigilanceData.departmentCode
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(vigilanceData.alerts) { alert ->
                            val alertColor = when (alert.maxColorId) {
                                1 -> Color(0xFF4CAF50)
                                2 -> Color(0xFFFFEB3B)
                                3 -> Color(0xFFFF9800)
                                4 -> Color(0xFFF44336)
                                else -> MaterialTheme.colorScheme.outline
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        alertColor.copy(alpha = 0.1f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                val isDark = isSystemInDarkTheme()
                                val itemContentColor = if (!isDark) when (alert.maxColorId) {
                                    2 -> Color(0xFF422B00) // Marron très foncé
                                    3 -> Color(0xFFE65100) // Orange foncé
                                    4 -> Color(0xFFB71C1C) // Rouge foncé
                                    else -> MaterialTheme.colorScheme.onSurface
                                } else alertColor

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = getPhenomenonIcon(alert.phenomenonId),
                                        contentDescription = null,
                                        tint = itemContentColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(mapPhenomenonIdToName(alert.phenomenonId)),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = itemContentColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                alert.steps.forEach { step ->
                                    val start = OffsetDateTime.parse(step.beginTime)
                                    val end = OffsetDateTime.parse(step.endTime)
                                    val isToday = start.toLocalDate() == LocalDate.now()

                                    val stepColor = when (step.colorId) {
                                        1 -> Color(0xFF4CAF50)
                                        2 -> Color(0xFFFFEB3B)
                                        3 -> Color(0xFFFF9800)
                                        4 -> Color(0xFFF44336)
                                        else -> Color.Gray
                                    }

                                    Row(
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier
                                            .size(10.dp)
                                            .background(stepColor, CircleShape))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${start.format(formatter)} - ${end.format(formatter)} ${if (!isToday) "(${start.format(dayFormatter)})" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, "https://vigilance.meteofrance.fr/fr".toUri())
                                context.startActivity(intent)
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.official_website), style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResponsiveText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    targetTextSizeHeight: TextUnit = style.fontSize
) {
    var textSize by remember { mutableStateOf(targetTextSizeHeight) }
    var readyToDraw by remember { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        fontSize = textSize,
        style = style,
        maxLines = maxLines,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow) {
                textSize = (textSize.value * 0.9f).sp
            } else {
                readyToDraw = true
            }
        }
    )
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

fun getPhenomenonIcon(phenomenonId: String): ImageVector {
    return when (phenomenonId) {
        "1" -> Icons.Rounded.Air // Vent
        "2" -> Icons.Rounded.Water // Pluie / Innondation
        "3" -> Icons.Rounded.FlashOn // Orages
        "4" -> Icons.Rounded.Flood // Crues
        "5" -> Icons.Rounded.AcUnit // Neige
        "6" -> Icons.Rounded.Thermostat // Chaud
        "7" -> Icons.Rounded.SevereCold // Froid
        "9" -> Icons.Rounded.Tsunami // Waves
        else -> Icons.Rounded.Warning
    }
}
