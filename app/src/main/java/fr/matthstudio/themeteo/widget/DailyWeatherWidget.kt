/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.TheMeteo
import fr.matthstudio.themeteo.UserSettings
import fr.matthstudio.themeteo.WeatherDataState
import fr.matthstudio.themeteo.data.DailyForecastUI
import fr.matthstudio.themeteo.data.ForecastType
import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.data.WindUnit
import fr.matthstudio.themeteo.forecastMainActivity.weatherCodeToSimpleWord
import fr.matthstudio.themeteo.utilClasses.UnitConverter
import fr.matthstudio.themeteo.utilClasses.toSmartString
import fr.matthstudio.themeteo.utilsActivities.LauncherActivity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class DailyWeatherWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("DailyWeatherWidget", "provideGlance started for glanceId: $id")
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

        Log.d("DailyWeatherWidget", "glanceId: $id -> selectedLocation: $selectedLocation")

        val manager = WidgetCacheManager(context)
        val data = manager.refreshIfNeeded(selectedLocation)

        Log.d("DailyWeatherWidget", "glanceId: $id -> data result: daily=${data?.daily?.size}")

        provideContent {
            val currentPrefs = currentState<Preferences>()

            val dailyState = if (data?.daily != null) WeatherDataState.SuccessDaily(data.daily)
            else WeatherDataState.Error("No data")

            val currentCityNameState by AppStateManager.currentCityName.collectAsState(initial = null)
            val cityName = data?.cityName ?: currentCityNameState
            val locationName = when (selectedLocation) {
                is LocationIdentifier.CurrentUserLocation -> {
                    if (!cityName.isNullOrBlank()) "$cityName (GPS)"
                    else "${context.getString(R.string.current_location)} (GPS)"
                }
                is LocationIdentifier.Saved -> selectedLocation.location.name
            }

            val colorTheme = currentPrefs[WidgetUtils.KEY_COLOR_THEME] ?: WidgetUtils.THEME_SYSTEM
            val transparency = currentPrefs[WidgetUtils.KEY_TRANSPARENCY] ?: 0

            val userSettings = AppStateManager.userSettings.value ?: return@provideContent

            GlanceTheme {
                DailyWidgetContent(
                    state = dailyState,
                    tempUnit = userSettings.temperatureUnit,
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
    internal fun DailyWidgetContent(
        state: WeatherDataState,
        tempUnit: TemperatureUnit,
        locationName: String,
        selectedLocation: LocationIdentifier,
        transparency: Int,
        theme: String
    ) {
        val size = LocalSize.current
        val alpha = (100 - transparency) / 100f
        
        // Dynamic sizing based on widget size
        val baseTextSize = (size.width.value / 16f).coerceIn(11f, 15f).sp
        val smallTextSize = (baseTextSize.value - 2).coerceIn(9f, 13f).sp

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
                .cornerRadius(16.dp)
                .background(backgroundProvider.getColor(LocalContext.current).copy(alpha = alpha))
                .padding(12.dp)
                .clickable(
                    actionStartActivity<LauncherActivity>(
                        actionParametersOf(ActionParameters.Key<String>("LOCATION") to WidgetUtils.serializeLocation(selectedLocation))
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                Spacer(modifier = GlanceModifier.height(8.dp))

                when (state) {
                    is WeatherDataState.Loading -> {
                        Text(text = "...", style = TextStyle(color = textColorProvider))
                    }
                    is WeatherDataState.SuccessDaily -> {
                        val today = LocalDate.now()
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            state.data.filter { !it.date.isBefore(today) }.take(5).forEach { day ->
                                DailyRow(day, tempUnit, baseTextSize, smallTextSize, textColorProvider, textColorVariantProvider)
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
        day: DailyForecastUI,
        tempUnit: TemperatureUnit,
        fontSize: androidx.compose.ui.unit.TextUnit,
        smallFontSize: androidx.compose.ui.unit.TextUnit,
        textColor: ColorProvider,
        textColorVariant: ColorProvider
    ) {
        val locale = remember { Locale.getDefault() }
        val dayName = if (day.date == LocalDate.now()) LocalContext.current.getString(R.string.today)
        else day.date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)

        val weatherWord = weatherCodeToSimpleWord(day.wmoCode)
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
                provider = WidgetUtils.getIconImageProvider(
                    context = LocalContext.current,
                    word = weatherWord,
                    isNight = false,
                    sizePx = 96
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp)
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Rain if any (Utilisation du textColorVariant)
            if (day.precipitationSum > 0.1f) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.width(45.dp)) {
                    Image(
                        provider = ImageProvider(R.drawable.rainy_1),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp),
                        colorFilter = ColorFilter.tint(textColorVariant)
                    )
                    Text(
                        text = day.precipitationSum.toSmartString(),
                        style = TextStyle(color = textColorVariant, fontSize = smallFontSize)
                    )
                }
            } else if (day.precipitationProbability >= 40) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.width(45.dp)) {
                    Image(
                        provider = ImageProvider(R.drawable.rainy_1),
                        contentDescription = null,
                        modifier = GlanceModifier.size(12.dp),
                        colorFilter = ColorFilter.tint(textColorVariant)
                    )
                    Text(
                        text = "${day.precipitationProbability}%",
                        style = TextStyle(color = textColorVariant, fontSize = smallFontSize)
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