/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.forecastMainActivity

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import fr.matthstudio.themeteo.TheMeteo
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

fun getStateIconFromWord(word: SimpleWeatherWord): ImageVector {
    return when (word) {
        SimpleWeatherWord.STORMY -> Icons.Rounded.Thunderstorm
        SimpleWeatherWord.HAIL, SimpleWeatherWord.SNOWY1, SimpleWeatherWord.SNOWY2, SimpleWeatherWord.SNOWY3 -> Icons.Rounded.AcUnit // Flocon
        SimpleWeatherWord.SNOWY_MIX -> Icons.Rounded.Grain // Pluie + Neige (approximation)
        SimpleWeatherWord.RAINY1, SimpleWeatherWord.RAINY2 -> Icons.Rounded.Umbrella
        SimpleWeatherWord.DRIZZLY -> Icons.Rounded.WaterDrop // Bruine
        SimpleWeatherWord.DUST -> Icons.Rounded.Public // Vent (approximation poussière)
        SimpleWeatherWord.HAZE -> Icons.Rounded.Dehaze // Brume
        SimpleWeatherWord.FOGGY -> Icons.Rounded.Visibility // Brouillard
        SimpleWeatherWord.CLOUDY -> Icons.Rounded.Cloud
        SimpleWeatherWord.SUNNY_CLOUDY -> Icons.Rounded.WbCloudy
        SimpleWeatherWord.SUNNY -> Icons.Rounded.WbSunny
    }
}

fun getLottieIconPath(word: SimpleWeatherWord, isNight: Boolean = false): String {
    val baseFolder = "icons/weather/"
    return baseFolder + when (word) {
        SimpleWeatherWord.SUNNY -> if (isNight) "clear-night.json" else "clear-day.json"
        SimpleWeatherWord.SUNNY_CLOUDY -> if (isNight) "partly-cloudy-night.json" else "partly-cloudy-day.json"
        SimpleWeatherWord.CLOUDY -> "cloudy.json"
        SimpleWeatherWord.FOGGY -> if (isNight) "fog-night.json" else "fog-day.json"
        SimpleWeatherWord.HAZE -> if (isNight) "haze-night.json" else "haze-day.json"
        SimpleWeatherWord.DUST -> if (isNight) "dust-night.json" else "dust-day.json"
        SimpleWeatherWord.DRIZZLY -> "drizzle.json"
        SimpleWeatherWord.RAINY1 -> "rain.json"
        SimpleWeatherWord.RAINY2 -> "extreme-rain.json"
        SimpleWeatherWord.HAIL -> "hail.json"
        SimpleWeatherWord.SNOWY1 -> "overcast-snow.json"
        SimpleWeatherWord.SNOWY2 -> "extreme-snow.json"
        SimpleWeatherWord.SNOWY3 -> "snow.json"
        SimpleWeatherWord.SNOWY_MIX -> "extreme-sleet.json"
        SimpleWeatherWord.STORMY -> if (isNight) "thunderstorms-night.json" else "thunderstorms-day.json"
    }
}

@Composable
fun LottieWeatherIcon(
    iconPath: String,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(iconPath))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = animate,
        iterations = LottieConstants.IterateForever
    )

    LottieAnimation(
        composition = composition,
        progress = { if (animate) progress else 0f },
        modifier = modifier
    )
}

// Helper function to convert ISO8601 string (e.g., "2024-05-30T05:58:00+02:00") to LocalDateTime
fun String.toEventLocalDateTime(appContext: Context): LocalDateTime? {
    return try {
        // Try parsing with timezone offset (OffsetDateTime)
        OffsetDateTime.parse(this).toLocalDateTime()
    } catch (e: Exception) {
        try {
            // Fallback: ZonedDateTime (if zone ID is used)
            ZonedDateTime.parse(this).toLocalDateTime()
        } catch (e2: Exception) {
            try {
                // Fallback: Simple LocalDateTime (if no offset/zone is included)
                LocalDateTime.parse(this)
            } catch (e3: Exception) {
                // Handle parsing error
                (appContext as TheMeteo).container.telemetryManager.logException(e3)
                null
            }
        }
    }
}