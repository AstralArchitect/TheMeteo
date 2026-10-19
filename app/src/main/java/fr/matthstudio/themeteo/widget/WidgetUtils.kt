/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ImageProvider
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastMainActivity.SimpleWeatherWord
import fr.matthstudio.themeteo.forecastMainActivity.getLottieIconPath
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import fr.matthstudio.themeteo.TheMeteo
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WidgetUtils {

    val KEY_LOCATION = stringPreferencesKey("widget_location")
    val KEY_COLOR_THEME = stringPreferencesKey("widget_color_theme")
    val KEY_TRANSPARENCY = intPreferencesKey("transparency")

    const val THEME_SYSTEM = "system"
    const val THEME_SYSTEM_INVERTED = "system_inverted"
    const val THEME_BLUE = "blue"
    const val THEME_GREEN = "green"
    const val THEME_WARM = "warm"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"

    fun getIconRes(word: SimpleWeatherWord?): Int {
        return when (word) {
            SimpleWeatherWord.SUNNY -> R.drawable.clear_day
            SimpleWeatherWord.PARTLY_CLOUDY, SimpleWeatherWord.MOSTLY_CLEAR, SimpleWeatherWord.VEILED -> R.drawable.cloudy_3_day
            SimpleWeatherWord.CLOUDY -> R.drawable.cloudy
            SimpleWeatherWord.FOGGY -> R.drawable.fog
            SimpleWeatherWord.HAZE -> R.drawable.fog
            SimpleWeatherWord.DUST -> R.drawable.dust
            SimpleWeatherWord.DRIZZLY -> R.drawable.rainy_1
            SimpleWeatherWord.RAINY1 -> R.drawable.rainy_2
            SimpleWeatherWord.RAINY2, SimpleWeatherWord.RAIN_SHOWER, SimpleWeatherWord.RAIN_SHOWER1, SimpleWeatherWord.RAIN_SHOWER2 -> R.drawable.rainy_3
            SimpleWeatherWord.HAIL -> R.drawable.hail
            SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3 -> R.drawable.snowy_2
            SimpleWeatherWord.SNOWY_MIX -> R.drawable.rainy_3
            SimpleWeatherWord.STORMY, SimpleWeatherWord.STORMY_HAIL, SimpleWeatherWord.EXTREME_STORMY, SimpleWeatherWord.EXTREME_STORMY_HAIL -> R.drawable.thunderstorms
            null -> R.drawable.clear_day // Fallback
        }
    }

    fun getLottieIconBitmap(
        context: Context,
        word: SimpleWeatherWord?,
        isNight: Boolean = false,
        sizePx: Int = 128
    ): Bitmap? {
        if (word == null) return null
        return try {
            val path = getLottieIconPath(word, isNight = isNight, darkTheme = false)
            val result = LottieCompositionFactory.fromAssetSync(context, path)
            val composition = result.value ?: return null
            val drawable = LottieDrawable().apply {
                setComposition(composition)
                progress = 0f
                setBounds(0, 0, sizePx, sizePx)
            }
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun getIconImageProvider(
        context: Context,
        word: SimpleWeatherWord?,
        isNight: Boolean = false,
        sizePx: Int = 128
    ): ImageProvider {
        val lottieBitmap = getLottieIconBitmap(context, word, isNight, sizePx)
        return if (lottieBitmap != null) {
            ImageProvider(lottieBitmap)
        } else {
            ImageProvider(getIconRes(word))
        }
    }

    fun serializeLocation(location: LocationIdentifier): String {
        return Json.encodeToString(location)
    }

    fun deserializeLocation(json: String): LocationIdentifier? {
        return try {
            Json.decodeFromString<LocationIdentifier>(json)
        } catch (e: Exception) {
            null
        }
    }
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d("RefreshWidgetAction", "Forced refresh requested by user for glanceId: $glanceId")
        val app = context.applicationContext as? TheMeteo ?: return

        app.container.weatherParser.clearCacheTimes()

        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val locJson = prefs[WidgetUtils.KEY_LOCATION]
        val savedDefault = try {
            app.container.userSettingsRepository.defaultLocation.first() ?: LocationIdentifier.CurrentUserLocation
        } catch (e: Exception) {
            LocationIdentifier.CurrentUserLocation
        }
        val selectedLocation = if (locJson != null) WidgetUtils.deserializeLocation(locJson) ?: savedDefault else savedDefault

        val manager = WidgetCacheManager(context)
        manager.refreshIfNeeded(selectedLocation, forceRefresh = true)

        WeatherWidget().update(context, glanceId)
        DailyWeatherWidget().update(context, glanceId)
        Log.d("RefreshWidgetAction", "Widget refresh complete for glanceId: $glanceId")
    }
}
