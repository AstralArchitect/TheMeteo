/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import fr.matthstudio.themeteo.utilsActivities.LauncherActivity
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

val LocIdentKey = ActionParameters.Key<LocationIdentifier>("location_identifier")

class WeatherWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("WeatherWidget", "provideGlance started for glanceId: $id")
        val app = context.applicationContext as TheMeteo
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)

        val savedDefault = try {
            app.container.userSettingsRepository.defaultLocation.first() ?: LocationIdentifier.CurrentUserLocation
        } catch (e: Exception) {
            LocationIdentifier.CurrentUserLocation
        }

        val locJson = prefs[WidgetUtils.KEY_LOCATION]
        val selectedLocation = if (locJson != null) {
            WidgetUtils.deserializeLocation(locJson) ?: savedDefault
        } else {
            savedDefault
        }

        Log.d("WeatherWidget", "glanceId: $id -> selectedLocation: $selectedLocation")

        val manager = WidgetCacheManager(context)
        val data = manager.refreshIfNeeded(selectedLocation, includeMultiDay = false)

        Log.d("WeatherWidget", "glanceId: $id -> data result: hourly=${data?.hourly?.size}, daily=${data?.daily?.size}")

        provideContent {
            val currentPrefs = currentState<Preferences>()

            val forecastState = if (data?.hourly != null) WeatherDataState.SuccessShortTermHourly(data.hourly)
            else WeatherDataState.Error("No data")

            val currentCityNameState by AppStateManager.currentCityName.collectAsState(initial = null)
            val cityName = data?.cityName ?: currentCityNameState
            val locationName = when (selectedLocation) {
                is LocationIdentifier.CurrentUserLocation -> {
                    if (!cityName.isNullOrBlank()) cityName
                    else context.getString(R.string.current_location)
                }
                is LocationIdentifier.Saved -> selectedLocation.location.name
            }

            val colorTheme = currentPrefs[WidgetUtils.KEY_COLOR_THEME] ?: WidgetUtils.THEME_SYSTEM
            val transparency = currentPrefs[WidgetUtils.KEY_TRANSPARENCY] ?: 0

            val userSettings = AppStateManager.userSettings.value ?: return@provideContent

            val todayDaily = data?.daily?.firstOrNull()
            val todayMax = todayDaily?.maxTemperature
            val todayMin = todayDaily?.minTemperature

            GlanceTheme {
                WeatherWidgetContent(
                    state = forecastState,
                    todayMax = todayMax,
                    todayMin = todayMin,
                    tempUnit = userSettings.temperatureUnit,
                    windUnit = userSettings.windUnit,
                    locationName = locationName,
                    selectedLocation = selectedLocation,
                    transparency = transparency,
                    theme = colorTheme
                )
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @androidx.compose.runtime.Composable
    internal fun WeatherWidgetContent(
        state: WeatherDataState,
        todayMax: Float?,
        todayMin: Float?,
        tempUnit: TemperatureUnit,
        windUnit: WindUnit,
        locationName: String,
        selectedLocation: LocationIdentifier,
        transparency: Int,
        theme: String
    ) {
        val size = LocalSize.current
        val alpha = (100 - transparency) / 100f
        
        // Dynamic sizing based on widget size
        val dimensionUsed = min(size.width, size.height)
        val baseTextSize = (dimensionUsed.value / 12f).sp
        val bigTextSize = (dimensionUsed.value / 6.5f).sp
        val smallTextSize = (baseTextSize.value - 2).sp

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

        val maxTempStr = todayMax?.let { UnitConverter.formatTemperature(it, tempUnit, roundToInt = true) }
        val minTempStr = todayMin?.let { UnitConverter.formatTemperature(it, tempUnit, roundToInt = true) }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(backgroundProvider.getColor(LocalContext.current).copy(alpha = alpha))
                .padding(10.dp)
                .clickable(
                    actionStartActivity<LauncherActivity>(
                        actionParametersOf(ActionParameters.Key<String>("LOCATION") to WidgetUtils.serializeLocation(selectedLocation))
                    )
                ),
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
                    Spacer(modifier = GlanceModifier.width(28.dp))
                    Text(
                        text = locationName,
                        style = TextStyle(
                            color = textColorProvider,
                            fontSize = smallTextSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Image(
                        provider = ImageProvider(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        modifier = GlanceModifier
                            .size(22.dp)
                            .clickable(actionRunCallback<RefreshWidgetAction>()),
                        colorFilter = ColorFilter.tint(textColorVariantProvider)
                    )
                }

                when (state) {
                    is WeatherDataState.Loading -> {
                        Text(text = "...", style = TextStyle(color = textColorProvider))
                    }
                    is WeatherDataState.Error -> {
                        Text(text = "Error", style = TextStyle(color = GlanceTheme.colors.error, fontSize = baseTextSize))
                    }
                    is WeatherDataState.SuccessShortTermHourly -> {
                        val nowHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
                        val current = state.data.find { it.time == nowHour } ?: state.data.firstOrNull()
                        if (current != null) {
                            val weatherWord = weatherCodeToSimpleWord(current.wmoCode)
                            val temp = UnitConverter.formatTemperature(current.temperature, tempUnit, roundToInt = true)
                            val wind = UnitConverter.formatWind(current.windSpeed, windUnit)
                            val rain = current.precipitation
                            val isNight by AppStateManager.isNight.collectAsState(initial = false)

                            Row(
                                modifier = GlanceModifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    provider = WidgetUtils.getIconImageProvider(
                                        context = LocalContext.current,
                                        word = weatherWord,
                                        isNight = isNight,
                                        sizePx = 190
                                    ),
                                    contentDescription = null,
                                    modifier = GlanceModifier.size(60.dp)
                                )

                                Spacer(modifier = GlanceModifier.width(10.dp))

                                Column {
                                    // Temperature actuelle
                                    Text(
                                        text = temp,
                                        style = TextStyle(
                                            color = textColorProvider,
                                            fontSize = bigTextSize,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    // Max / Min du jour
                                    if (maxTempStr != null && minTempStr != null) {
                                        Text(
                                            text = "$maxTempStr / $minTempStr",
                                            style = TextStyle(
                                                color = textColorVariantProvider,
                                                fontSize = smallTextSize,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = GlanceModifier.height(2.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Wind with Icon
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        provider = ImageProvider(R.drawable.wind),
                                        contentDescription = null,
                                        modifier = GlanceModifier.size(21.dp),
                                        colorFilter = ColorFilter.tint(textColorVariantProvider)
                                    )
                                    Spacer(modifier = GlanceModifier.width(3.dp))
                                    Text(
                                        text = wind,
                                        style = TextStyle(
                                            color = textColorVariantProvider,
                                            fontSize = smallTextSize
                                        )
                                    )
                                }

                                // Rain with Icon
                                if (rain > 0.05) {
                                    Spacer(modifier = GlanceModifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Image(
                                            provider = ImageProvider(R.drawable.rainy_1),
                                            contentDescription = null,
                                            modifier = GlanceModifier.size(21.dp),
                                            colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF64B5F6)))
                                        )
                                        Spacer(modifier = GlanceModifier.width(3.dp))
                                        Text(
                                            text = "${rain.toSmartString()}mm",
                                            style = TextStyle(
                                                color = textColorVariantProvider,
                                                fontSize = smallTextSize
                                            )
                                        )
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