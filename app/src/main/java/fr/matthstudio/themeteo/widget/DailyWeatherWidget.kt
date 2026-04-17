/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.widget

import android.content.Context
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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.matthstudio.themeteo.DailyReading
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.forecastMainActivity.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import fr.matthstudio.themeteo.utilsActivities.LauncherActivity
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class DailyWeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TheMeteo
        val weatherCache = app.weatherCache

        provideContent {
            val userSettings = weatherCache.userSettings.collectAsState().value
            val selectedLocation = weatherCache.selectedLocation.collectAsState().value
            val dailyState = weatherCache.get(LocalDate.now(), 5).collectAsState(initial = WeatherDataState.Loading).value

            val locationName = when (selectedLocation) {
                is LocationIdentifier.CurrentUserLocation -> context.getString(R.string.current_location)
                is LocationIdentifier.Saved -> selectedLocation.location.name
            }

            GlanceTheme {
                DailyWidgetContent(
                    state = dailyState,
                    tempUnit = userSettings.temperatureUnit,
                    locationName = locationName,
                    transparency = userSettings.widgetTransparency,
                    textSizeIndex = userSettings.widgetTextSize
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DailyWidgetContent(
        state: WeatherDataState,
        tempUnit: TemperatureUnit,
        locationName: String,
        transparency: Int,
        textSizeIndex: Int
    ) {
        val alpha = (100 - transparency) / 100f
        val baseTextSize = when(textSizeIndex) {
            0 -> 10.sp
            1 -> 12.sp
            else -> 14.sp
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground.getColor(LocalContext.current).copy(alpha = alpha))
                .padding(12.dp)
                .clickable(actionStartActivity<LauncherActivity>()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = locationName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )

                Spacer(modifier = GlanceModifier.height(8.dp))

                when (state) {
                    is WeatherDataState.Loading -> {
                        Text(text = "...", style = TextStyle(color = GlanceTheme.colors.onSurface))
                    }
                    is WeatherDataState.SuccessDaily -> {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            state.data.take(5).forEach { day ->
                                DailyRow(day, tempUnit, baseTextSize)
                            }
                        }
                    }
                    else -> {
                        Text(text = "Error", style = TextStyle(color = GlanceTheme.colors.error))
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun DailyRow(day: DailyReading, tempUnit: TemperatureUnit, fontSize: androidx.compose.ui.unit.TextUnit) {
        val dayName = if (day.date == LocalDate.now()) LocalContext.current.getString(R.string.today)
                      else day.date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
        
        val weatherWord = weatherCodeToSimpleWord(day.wmo)
        val maxTemp = UnitConverter.formatTemperature(day.maxTemperature, tempUnit, roundToInt = true)
        val minTemp = UnitConverter.formatTemperature(day.minTemperature, tempUnit, roundToInt = true)

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dayName,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = fontSize, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.width(40.dp)
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            weatherWord?.let { word ->
                Image(
                    provider = ImageProvider(getIconRes(word)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp)
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Rain if any
            if (day.precipitation != null && day.precipitation!! > 0.1) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.width(45.dp)) {
                    Image(
                        provider = ImageProvider(R.drawable.rainy_1),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF64B5F6)))
                    )
                    Text(
                        text = "${day.precipitation!!.toSmartString()}",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = (fontSize.value - 2).sp)
                    )
                }
            } else {
                Spacer(modifier = GlanceModifier.width(45.dp))
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Text(
                text = "$maxTemp / $minTemp",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = fontSize, fontWeight = FontWeight.Medium)
            )
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
