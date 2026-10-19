/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.forecastMainActivity

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import fr.matthstudio.themeteo.TheMeteo
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

fun getLottieIconPath(word: SimpleWeatherWord, isNight: Boolean = false, darkTheme: Boolean = false): String {
    val baseFolder = if (darkTheme) "icons/weather/night/" else "icons/weather/day/"
    return baseFolder + when (word) {
        SimpleWeatherWord.SUNNY -> if (isNight) "clear-night.json" else "clear-day.json"
        SimpleWeatherWord.MOSTLY_CLEAR -> if (isNight) "mostly-clear-night.json" else "mostly-clear-day.json"
        SimpleWeatherWord.PARTLY_CLOUDY -> if (isNight) "partly-cloudy-night.json" else "partly-cloudy-day.json"
        SimpleWeatherWord.VEILED -> if (isNight) "veiled-night.json" else "veiled-day.json"
        SimpleWeatherWord.CLOUDY -> "cloudy.json"
        SimpleWeatherWord.FOGGY -> if (isNight) "fog-night.json" else "fog-day.json"
        SimpleWeatherWord.HAZE -> if (isNight) "haze-night.json" else "haze-day.json"
        SimpleWeatherWord.DUST -> "dust.json"
        SimpleWeatherWord.DRIZZLY -> "drizzle.json"
        SimpleWeatherWord.RAINY1 -> "rain.json"
        SimpleWeatherWord.RAINY2 -> "extreme-rain.json"
        SimpleWeatherWord.RAIN_SHOWER, SimpleWeatherWord.RAIN_SHOWER1 -> if (isNight) "showers-night.json" else "showers-day.json"
        SimpleWeatherWord.RAIN_SHOWER2 -> if (isNight) "extreme-shower-night.json" else "extreme-shower-day.json"
        SimpleWeatherWord.HAIL -> "hail.json"
        SimpleWeatherWord.SNOWY1 -> "overcast-snow.json"
        SimpleWeatherWord.SNOWY2 -> "extreme-snow.json"
        SimpleWeatherWord.SNOWY3 -> "snow.json"
        SimpleWeatherWord.SNOWY_MIX -> "extreme-sleet.json"
        SimpleWeatherWord.STORMY -> "thunderstorms-rain.json"
        SimpleWeatherWord.STORMY_HAIL -> "thunderstorms-hail.json"
        SimpleWeatherWord.EXTREME_STORMY -> "severe-thunderstorm-extreme.json"
        SimpleWeatherWord.EXTREME_STORMY_HAIL -> "thunderstorms-extreme-hail.json"
    }
}

@Composable
fun LottieWeatherIcon(
    iconPath: String,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current

    val composition = if (isPreview) {
        remember(iconPath) {
            try {
                context.assets.open(iconPath).use { stream ->
                    LottieCompositionFactory.fromJsonInputStreamSync(stream, iconPath).value
                }
            } catch (e: Exception) {
                null
            }
        }
    } else {
        val asyncComp by rememberLottieComposition(
            LottieCompositionSpec.Asset(iconPath),
            cacheKey = iconPath
        )
        asyncComp
    }

    if (isPreview) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { 0.25f },
                modifier = modifier
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.WbCloudy,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        if (animate) {
            val progress by animateLottieCompositionAsState(
                composition = composition,
                isPlaying = true,
                iterations = LottieConstants.IterateForever
            )

            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = modifier
            )
        } else {
            LottieAnimation(
                composition = composition,
                progress = { 0.25f },
                modifier = modifier
            )
        }
    }
}

// Helper function to convert ISO8601 string (e.g., "2024-05-30T05:58:00+02:00") to LocalDateTime
fun String.toEventLocalDateTime(appContext: Context): LocalDateTime? {
    return try {
        // Try parsing with timezone offset (OffsetDateTime) and convert to system local time
        OffsetDateTime.parse(this).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime()
    } catch (e: Exception) {
        try {
            // Fallback: ZonedDateTime (if zone ID is used) and convert to system local time
            ZonedDateTime.parse(this).withZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime()
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