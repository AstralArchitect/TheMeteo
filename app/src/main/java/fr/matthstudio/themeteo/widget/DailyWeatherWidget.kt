/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import fr.matthstudio.themeteo.utilsActivities.LauncherActivity
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class DailyWeatherWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TheMeteo
        val weatherCache = app.weatherCache

        provideContent {
            val prefs = currentState<Preferences>()
            val userSettings = weatherCache.userSettings.collectAsState().value
            val selectedLocation = userSettings.defaultLocation
            val dailyState = weatherCache.get(LocalDate.now(), 5, selectedLocation).collectAsState(initial = WeatherDataState.Loading).value

            val locationName = when (selectedLocation) {
                is LocationIdentifier.CurrentUserLocation -> context.getString(R.string.current_location)
                is LocationIdentifier.Saved -> selectedLocation.location.name
            }

            val colorTheme = prefs[WidgetUtils.KEY_COLOR_THEME] ?: WidgetUtils.THEME_SYSTEM
            val transparency = prefs[WidgetUtils.KEY_TRANSPARENCY] ?: 0
            val textSize = prefs[WidgetUtils.KEY_TEXT_SIZE] ?: 1

            GlanceTheme {
                DailyWidgetContent(
                    state = dailyState,
                    tempUnit = userSettings.temperatureUnit,
                    locationName = locationName,
                    selectedLocation = selectedLocation,
                    transparency = transparency,
                    textSizeIndex = textSize,
                    theme = colorTheme
                )
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @androidx.compose.runtime.Composable
    internal fun DailyWidgetContent(
        state: WeatherDataState,
        tempUnit: TemperatureUnit,
        locationName: String,
        selectedLocation: LocationIdentifier,
        transparency: Int,
        textSizeIndex: Int,
        theme: String
    ) {
        val alpha = (100 - transparency) / 100f
        val baseTextSize = WidgetUtils.getBaseTextSize(textSizeIndex)

        val backgroundProvider = when(theme) {
            WidgetUtils.THEME_BLUE -> ColorProvider(Color(0xFFE3F2FD))
            WidgetUtils.THEME_GREEN -> ColorProvider(Color(0xFFE8F5E9))
            WidgetUtils.THEME_WARM -> ColorProvider(Color(0xFFFFF3E0))
            WidgetUtils.THEME_DARK -> ColorProvider(Color(0xFF1C1B1F))
            WidgetUtils.THEME_LIGHT -> ColorProvider(Color(0xFFF9FAEF))
            WidgetUtils.THEME_SYSTEM -> GlanceTheme.colors.widgetBackground
            // onWidgetBackground n'existe pas
            WidgetUtils.THEME_SYSTEM_INVERTED -> GlanceTheme.colors.onSurface
            else -> GlanceTheme.colors.widgetBackground
        }

        val textColorProvider = when(theme) {
            WidgetUtils.THEME_DARK -> ColorProvider(Color.White)
            WidgetUtils.THEME_LIGHT -> ColorProvider(Color.Black)
            WidgetUtils.THEME_BLUE, WidgetUtils.THEME_GREEN, WidgetUtils.THEME_WARM ->
                ColorProvider(if (transparency < 50) Color.Black else Color.White)
            WidgetUtils.THEME_SYSTEM_INVERTED -> if (transparency > 75) GlanceTheme.colors.onSurface else GlanceTheme.colors.surface
            else -> if (transparency < 75) GlanceTheme.colors.onSurface else GlanceTheme.colors.surface
        }

        val textColorVariantProvider = when(theme) {
            WidgetUtils.THEME_DARK -> ColorProvider(Color.White)
            WidgetUtils.THEME_LIGHT -> ColorProvider(Color.Black)
            WidgetUtils.THEME_BLUE, WidgetUtils.THEME_GREEN, WidgetUtils.THEME_WARM ->
                ColorProvider(if (transparency < 50) Color.Black else Color.White)
            WidgetUtils.THEME_SYSTEM_INVERTED -> if (transparency > 75) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.surfaceVariant
            else -> if (transparency < 75) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.surfaceVariant
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(backgroundProvider.getColor(LocalContext.current).copy(alpha = alpha))
                .padding(12.dp)
                .clickable(actionStartActivity<LauncherActivity>()),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = locationName,
                        style = TextStyle(
                            color = textColorProvider,
                            fontSize = (baseTextSize.value - 2).sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    // Refresh Button (Passe maintenant le paramètre de localisation)
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier
                            .size(16.dp)
                            .clickable(actionRunCallback<RefreshAction>(
                                actionParametersOf(LocIdentKey to selectedLocation)
                            )),
                        colorFilter = ColorFilter.tint(textColorProvider)
                    )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                when (state) {
                    is WeatherDataState.Loading -> {
                        Text(text = "...", style = TextStyle(color = textColorProvider))
                    }
                    is WeatherDataState.SuccessDaily -> {
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            state.data.take(5).forEach { day ->
                                DailyRow(day, tempUnit, baseTextSize, textColorProvider, textColorVariantProvider)
                            }
                        }
                    }
                    else -> {
                        Text(text = "Error", style = TextStyle(color = GlanceTheme.colors.error, fontSize = baseTextSize))
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    internal fun DailyRow(
        day: DailyReading,
        tempUnit: TemperatureUnit,
        fontSize: androidx.compose.ui.unit.TextUnit,
        textColor: ColorProvider,
        textColorVariant: ColorProvider
    ) {
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
                style = TextStyle(color = textColor, fontSize = fontSize, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.width(40.dp)
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            Image(
                provider = ImageProvider(WidgetUtils.getIconRes(weatherWord)),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp)
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Rain if any (Utilisation du textColorVariant)
            if (day.precipitation != null && day.precipitation!! > 0.1) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.width(45.dp)) {
                    Image(
                        provider = ImageProvider(R.drawable.rainy_1),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp),
                        colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF64B5F6)))
                    )
                    Text(
                        text = day.precipitation.toSmartString(),
                        style = TextStyle(color = textColorVariant, fontSize = (fontSize.value - 2).sp)
                    )
                }
            } else {
                Spacer(modifier = GlanceModifier.width(45.dp))
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            Text(
                text = "$maxTemp / $minTemp",
                style = TextStyle(color = textColor, fontSize = fontSize, fontWeight = FontWeight.Medium)
            )
        }
    }
}