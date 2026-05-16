/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.widget

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.matthstudio.themeteo.LocationIdentifier
import fr.matthstudio.themeteo.R
import fr.matthstudio.themeteo.forecastMainActivity.SimpleWeatherWord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WidgetUtils {

    val KEY_LOCATION = stringPreferencesKey("widget_location")
    val KEY_COLOR_THEME = stringPreferencesKey("widget_color_theme")
    val KEY_TRANSPARENCY = intPreferencesKey("transparency")
    val KEY_TEXT_SIZE = intPreferencesKey("text_size")

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
            null -> R.drawable.clear_day // Fallback
        }
    }

    fun getBaseTextSize(textSizeIndex: Int): TextUnit {
        return when (textSizeIndex) {
            0 -> 10.sp
            1 -> 13.sp
            else -> 16.sp
        }
    }

    fun getBigTextSize(textSizeIndex: Int): TextUnit {
        return when (textSizeIndex) {
            0 -> 18.sp
            1 -> 22.sp
            else -> 28.sp
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
