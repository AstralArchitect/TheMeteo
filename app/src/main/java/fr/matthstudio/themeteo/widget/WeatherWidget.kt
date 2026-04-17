package fr.matthstudio.themeteo.widget

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.forecastMainActivity.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import fr.matthstudio.themeteo.utilsActivities.LauncherActivity
import java.time.LocalDateTime

class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TheMeteo
        val weatherCache = app.weatherCache

        provideContent {
            val userSettings = weatherCache.userSettings.collectAsState().value
            val selectedLocation = weatherCache.selectedLocation.collectAsState().value
            val forecastState = weatherCache.get(LocalDateTime.now(), 1).collectAsState(initial = WeatherDataState.Loading).value

            val locationName = when (selectedLocation) {
                is LocationIdentifier.CurrentUserLocation -> context.getString(R.string.current_location)
                is LocationIdentifier.Saved -> selectedLocation.location.name
            }

            GlanceTheme {
                WeatherWidgetContent(
                    state = forecastState,
                    tempUnit = userSettings.temperatureUnit,
                    windUnit = userSettings.windUnit,
                    locationName = locationName,
                    transparency = userSettings.widgetTransparency,
                    textSizeIndex = userSettings.widgetTextSize
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun WeatherWidgetContent(
        state: WeatherDataState,
        tempUnit: TemperatureUnit,
        windUnit: WindUnit,
        locationName: String,
        transparency: Int,
        textSizeIndex: Int
    ) {
        val alpha = (100 - transparency) / 100f
        val baseTextSize = when(textSizeIndex) {
            0 -> 10.sp
            1 -> 13.sp
            else -> 16.sp
        }
        val bigTextSize = when(textSizeIndex) {
            0 -> 18.sp
            1 -> 22.sp
            else -> 28.sp
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground.getColor(LocalContext.current).copy(alpha = alpha))
                .padding(8.dp)
                .clickable(actionStartActivity<LauncherActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = locationName,
                    style = TextStyle(
                        color = ColorProvider(if (transparency < 75) lightColorScheme().onSurface else lightColorScheme().surface),
                        fontSize = (baseTextSize.value - 2).sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )

                when (state) {
                    is WeatherDataState.Loading -> {
                        Text(text = "...", style = TextStyle(color = GlanceTheme.colors.onSurface))
                    }
                    is WeatherDataState.Error -> {
                        Text(text = "Error", style = TextStyle(color = GlanceTheme.colors.error, fontSize = baseTextSize))
                    }
                    is WeatherDataState.SuccessHourly -> {
                        val current = state.data.firstOrNull()
                        if (current != null) {
                            val weatherWord = weatherCodeToSimpleWord(current.wmo)
                            val temp = current.temperature?.let { UnitConverter.formatTemperature(it, tempUnit, roundToInt = true) } ?: "--"
                            val wind = current.wind.windspeed?.let { UnitConverter.formatWind(it, windUnit) } ?: "--"
                            val rain = current.precipitationData.precipitation ?: 0.0

                            Row(
                                modifier = GlanceModifier.padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Larger Icon
                                weatherWord?.let { word ->
                                    Image(
                                        provider = ImageProvider(getIconRes(word)),
                                        contentDescription = null,
                                        modifier = GlanceModifier.size(56.dp)
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(12.dp))

                                Column {
                                    // Temperature
                                    Text(
                                        text = temp,
                                        style = TextStyle(
                                            color = ColorProvider(if (transparency < 75) lightColorScheme().onSurface else lightColorScheme().surface),
                                            fontSize = bigTextSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    // Wind with Icon
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            provider = ImageProvider(R.drawable.dust), // Using a generic icon or wind if available
                                            contentDescription = null,
                                            modifier = GlanceModifier.size(14.dp),
                                            colorFilter = ColorFilter.tint(ColorProvider(Color(0xFFAED581)))
                                        )
                                        Spacer(modifier = GlanceModifier.width(4.dp))
                                        Text(
                                            text = wind,
                                            style = TextStyle(
                                                color = ColorProvider(if (transparency < 75) lightColorScheme().onSurfaceVariant else lightColorScheme().surfaceVariant),
                                                fontSize = baseTextSize
                                            )
                                        )
                                    }

                                    // Rain with Icon
                                    if (rain > 0.1) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Image(
                                                provider = ImageProvider(R.drawable.rainy_1),
                                                contentDescription = null,
                                                modifier = GlanceModifier.size(14.dp),
                                                colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF64B5F6)))
                                            )
                                            Spacer(modifier = GlanceModifier.width(4.dp))
                                            Text(
                                                text = "${rain.toSmartString()}mm",
                                                style = TextStyle(
                                                    color = GlanceTheme.colors.onSurfaceVariant,
                                                    fontSize = baseTextSize
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun getIconRes(word: SimpleWeatherWord): Int {
        return when (word) {
            SimpleWeatherWord.SUNNY -> R.drawable.clear_day
            SimpleWeatherWord.SUNNY_CLOUDY -> R.drawable.cloudy_3_day
            SimpleWeatherWord.CLOUDY -> R.drawable.cloudy
            SimpleWeatherWord.FOGGY -> R.drawable.fog
            SimpleWeatherWord.HAZE -> R.drawable.fog
            SimpleWeatherWord.DUST -> R.drawable.dust
            SimpleWeatherWord.DRIZZLY -> R.drawable.rainy_1
            SimpleWeatherWord.RAINY1 -> R.drawable.rainy_2
            SimpleWeatherWord.RAINY2 -> R.drawable.rainy_3
            SimpleWeatherWord.HAIL -> R.drawable.hail
            SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3 -> R.drawable.snowy_2
            SimpleWeatherWord.SNOWY_MIX -> R.drawable.rainy_3
            SimpleWeatherWord.STORMY -> R.drawable.thunderstorms
        }
    }
}
