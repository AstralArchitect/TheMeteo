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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
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
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import fr.matthstudio.themeteo.utilsActivities.LauncherActivity
import java.time.LocalDateTime

val LocIdentKey = ActionParameters.Key<LocationIdentifier>("location_identifier")

class WeatherWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as TheMeteo
        val weatherCache = app.weatherCache

        provideContent {
            val prefs = currentState<Preferences>()
            val userSettings = weatherCache.userSettings.collectAsState().value
            val selectedLocation = userSettings.defaultLocation
            
            val forecastState = weatherCache.get(LocalDateTime.now(), 1, selectedLocation).collectAsState(initial = WeatherDataState.Loading).value

            val locationName = when (selectedLocation) {
                is LocationIdentifier.CurrentUserLocation -> context.getString(R.string.current_location)
                is LocationIdentifier.Saved -> selectedLocation.location.name
            }

            val colorTheme = prefs[WidgetUtils.KEY_COLOR_THEME] ?: WidgetUtils.THEME_SYSTEM
            val transparency = prefs[WidgetUtils.KEY_TRANSPARENCY] ?: 0
            val textSize = prefs[WidgetUtils.KEY_TEXT_SIZE] ?: 1

            GlanceTheme {
                WeatherWidgetContent(
                    state = forecastState,
                    tempUnit = userSettings.temperatureUnit,
                    windUnit = userSettings.windUnit,
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
    internal fun WeatherWidgetContent(
        state: WeatherDataState,
        tempUnit: TemperatureUnit,
        windUnit: WindUnit,
        locationName: String,
        selectedLocation: LocationIdentifier,
        transparency: Int,
        textSizeIndex: Int,
        theme: String
    ) {
        val alpha = (100 - transparency) / 100f
        val baseTextSize = WidgetUtils.getBaseTextSize(textSizeIndex)
        val bigTextSize = WidgetUtils.getBigTextSize(textSizeIndex)
        
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
                .padding(8.dp)
                .clickable(actionStartActivity<LauncherActivity>()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    // Refresh Button
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

                when (state) {
                    is WeatherDataState.Loading -> {
                        Text(text = "...", style = TextStyle(color = textColorProvider))
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
                                Image(
                                    provider = ImageProvider(WidgetUtils.getIconRes(weatherWord)),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(56.dp)
                                )

                                Spacer(modifier = GlanceModifier.width(12.dp))

                                Column {
                                    // Temperature
                                    Text(
                                        text = temp,
                                        style = TextStyle(
                                            color = textColorProvider,
                                            fontSize = bigTextSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    // Wind with Icon
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            provider = ImageProvider(R.drawable.wind),
                                            contentDescription = null,
                                            modifier = GlanceModifier.size(14.dp),
                                            colorFilter = ColorFilter.tint(ColorProvider(Color(0xFFAED581)))
                                        )
                                        Spacer(modifier = GlanceModifier.width(4.dp))
                                        Text(
                                            text = wind,
                                            style = TextStyle(
                                                color = textColorVariantProvider,
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
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as TheMeteo
        val selectedLocation = parameters[LocIdentKey] ?: LocationIdentifier.CurrentUserLocation

        if (selectedLocation !is LocationIdentifier.CurrentUserLocation) {
            app.weatherCache.rmCacheLoc(selectedLocation)

            WeatherWidget().updateAll(context)
            DailyWeatherWidget().updateAll(context)
        } else {
            app.weatherCache.refreshCurrentLocationSuspend()

            app.weatherCache.rmCacheLoc(selectedLocation)

            WeatherWidget().updateAll(context)
            DailyWeatherWidget().updateAll(context)
        }
    }
}
