/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.ui.theme
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import fr.matthstudio.themeteo.data.ThemeMode

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

private val mediumContrastLightColorScheme = lightColorScheme(
    primary = primaryLightMediumContrast,
    onPrimary = onPrimaryLightMediumContrast,
    primaryContainer = primaryContainerLightMediumContrast,
    onPrimaryContainer = onPrimaryContainerLightMediumContrast,
    secondary = secondaryLightMediumContrast,
    onSecondary = onSecondaryLightMediumContrast,
    secondaryContainer = secondaryContainerLightMediumContrast,
    onSecondaryContainer = onSecondaryContainerLightMediumContrast,
    tertiary = tertiaryLightMediumContrast,
    onTertiary = onTertiaryLightMediumContrast,
    tertiaryContainer = tertiaryContainerLightMediumContrast,
    onTertiaryContainer = onTertiaryContainerLightMediumContrast,
    error = errorLightMediumContrast,
    onError = onErrorLightMediumContrast,
    errorContainer = errorContainerLightMediumContrast,
    onErrorContainer = onErrorContainerLightMediumContrast,
    background = backgroundLightMediumContrast,
    onBackground = onBackgroundLightMediumContrast,
    surface = surfaceLightMediumContrast,
    onSurface = onSurfaceLightMediumContrast,
    surfaceVariant = surfaceVariantLightMediumContrast,
    onSurfaceVariant = onSurfaceVariantLightMediumContrast,
    outline = outlineLightMediumContrast,
    outlineVariant = outlineVariantLightMediumContrast,
    scrim = scrimLightMediumContrast,
    inverseSurface = inverseSurfaceLightMediumContrast,
    inverseOnSurface = inverseOnSurfaceLightMediumContrast,
    inversePrimary = inversePrimaryLightMediumContrast,
    surfaceDim = surfaceDimLightMediumContrast,
    surfaceBright = surfaceBrightLightMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestLightMediumContrast,
    surfaceContainerLow = surfaceContainerLowLightMediumContrast,
    surfaceContainer = surfaceContainerLightMediumContrast,
    surfaceContainerHigh = surfaceContainerHighLightMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestLightMediumContrast,
)

private val highContrastLightColorScheme = lightColorScheme(
    primary = primaryLightHighContrast,
    onPrimary = onPrimaryLightHighContrast,
    primaryContainer = primaryContainerLightHighContrast,
    onPrimaryContainer = onPrimaryContainerLightHighContrast,
    secondary = secondaryLightHighContrast,
    onSecondary = onSecondaryLightHighContrast,
    secondaryContainer = secondaryContainerLightHighContrast,
    onSecondaryContainer = onSecondaryContainerLightHighContrast,
    tertiary = tertiaryLightHighContrast,
    onTertiary = onTertiaryLightHighContrast,
    tertiaryContainer = tertiaryContainerLightHighContrast,
    onTertiaryContainer = onTertiaryContainerLightHighContrast,
    error = errorLightHighContrast,
    onError = onErrorLightHighContrast,
    errorContainer = errorContainerLightHighContrast,
    onErrorContainer = onErrorContainerLightHighContrast,
    background = backgroundLightHighContrast,
    onBackground = onBackgroundLightHighContrast,
    surface = surfaceLightHighContrast,
    onSurface = onSurfaceLightHighContrast,
    surfaceVariant = surfaceVariantLightHighContrast,
    onSurfaceVariant = onSurfaceVariantLightHighContrast,
    outline = outlineLightHighContrast,
    outlineVariant = outlineVariantLightHighContrast,
    scrim = scrimLightHighContrast,
    inverseSurface = inverseSurfaceLightHighContrast,
    inverseOnSurface = inverseOnSurfaceLightHighContrast,
    inversePrimary = inversePrimaryLightHighContrast,
    surfaceDim = surfaceDimLightHighContrast,
    surfaceBright = surfaceBrightLightHighContrast,
    surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
    surfaceContainerLow = surfaceContainerLowLightHighContrast,
    surfaceContainer = surfaceContainerLightHighContrast,
    surfaceContainerHigh = surfaceContainerHighLightHighContrast,
    surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
)

private val mediumContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkMediumContrast,
    onPrimary = onPrimaryDarkMediumContrast,
    primaryContainer = primaryContainerDarkMediumContrast,
    onPrimaryContainer = onPrimaryContainerDarkMediumContrast,
    secondary = secondaryDarkMediumContrast,
    onSecondary = onSecondaryDarkMediumContrast,
    secondaryContainer = secondaryContainerDarkMediumContrast,
    onSecondaryContainer = onSecondaryContainerDarkMediumContrast,
    tertiary = tertiaryDarkMediumContrast,
    onTertiary = onTertiaryDarkMediumContrast,
    tertiaryContainer = tertiaryContainerDarkMediumContrast,
    onTertiaryContainer = onTertiaryContainerDarkMediumContrast,
    error = errorDarkMediumContrast,
    onError = onErrorDarkMediumContrast,
    errorContainer = errorContainerDarkMediumContrast,
    onErrorContainer = onErrorContainerDarkMediumContrast,
    background = backgroundDarkMediumContrast,
    onBackground = onBackgroundDarkMediumContrast,
    surface = surfaceDarkMediumContrast,
    onSurface = onSurfaceDarkMediumContrast,
    surfaceVariant = surfaceVariantDarkMediumContrast,
    onSurfaceVariant = onSurfaceVariantDarkMediumContrast,
    outline = outlineDarkMediumContrast,
    outlineVariant = outlineVariantDarkMediumContrast,
    scrim = scrimDarkMediumContrast,
    inverseSurface = inverseSurfaceDarkMediumContrast,
    inverseOnSurface = inverseOnSurfaceDarkMediumContrast,
    inversePrimary = inversePrimaryDarkMediumContrast,
    surfaceDim = surfaceDimDarkMediumContrast,
    surfaceBright = surfaceBrightDarkMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkMediumContrast,
    surfaceContainerLow = surfaceContainerLowDarkMediumContrast,
    surfaceContainer = surfaceContainerDarkMediumContrast,
    surfaceContainerHigh = surfaceContainerHighDarkMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkMediumContrast,
)

private val highContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkHighContrast,
    onPrimary = onPrimaryDarkHighContrast,
    primaryContainer = primaryContainerDarkHighContrast,
    onPrimaryContainer = onPrimaryContainerDarkHighContrast,
    secondary = secondaryDarkHighContrast,
    onSecondary = onSecondaryDarkHighContrast,
    secondaryContainer = secondaryContainerDarkHighContrast,
    onSecondaryContainer = onSecondaryContainerDarkHighContrast,
    tertiary = tertiaryDarkHighContrast,
    onTertiary = onTertiaryDarkHighContrast,
    tertiaryContainer = tertiaryContainerDarkHighContrast,
    onTertiaryContainer = onTertiaryContainerDarkHighContrast,
    error = errorDarkHighContrast,
    onError = onErrorDarkHighContrast,
    errorContainer = errorContainerDarkHighContrast,
    onErrorContainer = onErrorContainerDarkHighContrast,
    background = backgroundDarkHighContrast,
    onBackground = onBackgroundDarkHighContrast,
    surface = surfaceDarkHighContrast,
    onSurface = onSurfaceDarkHighContrast,
    surfaceVariant = surfaceVariantDarkHighContrast,
    onSurfaceVariant = onSurfaceVariantDarkHighContrast,
    outline = outlineDarkHighContrast,
    outlineVariant = outlineVariantDarkHighContrast,
    scrim = scrimDarkHighContrast,
    inverseSurface = inverseSurfaceDarkHighContrast,
    inverseOnSurface = inverseOnSurfaceDarkHighContrast,
    inversePrimary = inversePrimaryDarkHighContrast,
    surfaceDim = surfaceDimDarkHighContrast,
    surfaceBright = surfaceBrightDarkHighContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
    surfaceContainerLow = surfaceContainerLowDarkHighContrast,
    surfaceContainer = surfaceContainerDarkHighContrast,
    surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

// Weather Schemes logic
private val sunnyLightScheme = lightScheme.copy(
    primary = primarySunnyLight,
    onPrimary = onPrimarySunnyLight,
    primaryContainer = primaryContainerSunnyLight,
    onPrimaryContainer = onPrimaryContainerSunnyLight,
    secondary = secondarySunnyLight,
    onSecondary = onSecondarySunnyLight,
    secondaryContainer = secondaryContainerSunnyLight,
    onSecondaryContainer = onSecondaryContainerSunnyLight,
    tertiary = tertiarySunnyLight,
    onTertiary = onTertiarySunnyLight,
    tertiaryContainer = tertiaryContainerSunnyLight,
    onTertiaryContainer = onTertiaryContainerSunnyLight,
    background = backgroundSunnyLight,
    surface = backgroundSunnyLight
)
private val sunnyDarkScheme = darkScheme.copy(
    primary = primarySunnyDark,
    onPrimary = onPrimarySunnyDark,
    primaryContainer = primaryContainerSunnyDark,
    onPrimaryContainer = onPrimaryContainerSunnyDark,
    secondary = secondarySunnyDark,
    onSecondary = onSecondarySunnyDark,
    secondaryContainer = secondaryContainerSunnyDark,
    onSecondaryContainer = onSecondaryContainerSunnyDark,
    tertiary = tertiarySunnyDark,
    onTertiary = onTertiarySunnyDark,
    tertiaryContainer = tertiaryContainerSunnyDark,
    onTertiaryContainer = onTertiaryContainerSunnyDark,
    background = backgroundSunnyDark,
    surface = backgroundSunnyDark
)

private val cloudyLightScheme = lightScheme.copy(
    primary = primaryCloudyLight,
    onPrimary = onPrimaryCloudyLight,
    primaryContainer = primaryContainerCloudyLight,
    onPrimaryContainer = onPrimaryContainerCloudyLight,
    secondary = secondaryCloudyLight,
    onSecondary = onSecondaryCloudyLight,
    secondaryContainer = secondaryContainerCloudyLight,
    onSecondaryContainer = onSecondaryContainerCloudyLight,
    tertiary = tertiaryCloudyLight,
    onTertiary = onTertiaryCloudyLight,
    tertiaryContainer = tertiaryContainerCloudyLight,
    background = backgroundCloudyLight,
    surface = backgroundCloudyLight
)
private val cloudyDarkScheme = darkScheme.copy(
    primary = primaryCloudyDark,
    onPrimary = onPrimaryCloudyDark,
    primaryContainer = primaryContainerCloudyDark,
    onPrimaryContainer = onPrimaryContainerCloudyDark,
    secondary = secondaryCloudyDark,
    onSecondary = onSecondaryCloudyDark,
    secondaryContainer = secondaryContainerCloudyDark,
    onSecondaryContainer = onSecondaryContainerCloudyDark,
    tertiary = tertiaryCloudyDark,
    onTertiary = onTertiaryCloudyDark,
    tertiaryContainer = tertiaryContainerCloudyDark,
    onTertiaryContainer = onSecondaryContainerCloudyDark, // Fallback cohérent pour le gris
    background = backgroundCloudyDark,
    surface = backgroundCloudyDark
)

private val rainyLightScheme = lightScheme.copy(
    primary = primaryRainyLight,
    onPrimary = onPrimaryRainyLight,
    primaryContainer = primaryContainerRainyLight,
    onPrimaryContainer = onPrimaryContainerRainyLight,
    secondary = secondaryRainyLight,
    onSecondary = onSecondaryRainyLight,
    secondaryContainer = secondaryContainerRainyLight,
    onSecondaryContainer = onSecondaryContainerRainyLight,
    tertiary = tertiaryRainyLight,
    onTertiary = onTertiaryRainyLight,
    tertiaryContainer = tertiaryContainerRainyLight,
    onTertiaryContainer = onTertiaryContainerRainyLight,
    background = backgroundRainyLight,
    surface = backgroundRainyLight
)
private val rainyDarkScheme = darkScheme.copy(
    primary = primaryRainyDark,
    onPrimary = onPrimaryRainyDark,
    primaryContainer = primaryContainerRainyDark,
    onPrimaryContainer = onPrimaryContainerRainyDark,
    secondary = secondaryRainyDark,
    onSecondary = onSecondaryRainyDark,
    secondaryContainer = secondaryContainerRainyDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryRainyDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    background = backgroundRainyDark,
    surface = backgroundRainyDark
)

private val snowyLightScheme = lightScheme.copy(
    primary = primarySnowyLight,
    onPrimary = onPrimarySnowyLight,
    primaryContainer = primaryContainerSnowyLight,
    onPrimaryContainer = onPrimaryContainerSnowyLight,
    secondary = secondarySnowyLight,
    onSecondary = onSecondarySnowyLight,
    secondaryContainer = secondaryContainerSnowyLight,
    onSecondaryContainer = onSecondaryContainerSnowyLight,
    tertiary = tertiarySnowyLight,
    onTertiary = onTertiarySnowyLight,
    tertiaryContainer = tertiaryContainerSnowyLight,
    onTertiaryContainer = onTertiaryContainerSnowyLight,
    background = backgroundSnowyLight,
    surface = backgroundSnowyLight
)
private val snowyDarkScheme = darkScheme.copy(
    primary = primarySnowyDark,
    onPrimary = onPrimarySnowyDark,
    primaryContainer = primaryContainerSnowyDark,
    onPrimaryContainer = onPrimaryContainerSnowyDark,
    secondary = secondarySnowyDark,
    onSecondary = onSecondarySnowyDark,
    secondaryContainer = secondaryContainerSnowyDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiarySnowyDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    background = backgroundSnowyDark,
    surface = backgroundSnowyDark
)

private val stormyLightScheme = lightScheme.copy(
    primary = primaryStormyLight,
    onPrimary = onPrimaryStormyLight,
    primaryContainer = primaryContainerStormyLight,
    onPrimaryContainer = onPrimaryContainerStormyLight,
    secondary = secondaryStormyLight,
    onSecondary = onSecondaryStormyLight,
    secondaryContainer = secondaryContainerStormyLight,
    onSecondaryContainer = onSecondaryContainerStormyLight,
    tertiary = tertiaryStormyLight,
    onTertiary = onTertiaryStormyLight,
    tertiaryContainer = tertiaryContainerStormyLight,
    onTertiaryContainer = onTertiaryContainerStormyLight,
    background = backgroundStormyLight,
    surface = backgroundStormyLight
)
private val stormyDarkScheme = darkScheme.copy(
    primary = primaryStormyDark,
    onPrimary = onPrimaryStormyDark,
    primaryContainer = primaryContainerStormyDark,
    onPrimaryContainer = onPrimaryContainerStormyDark,
    secondary = secondaryStormyDark,
    onSecondary = onSecondaryStormyDark,
    secondaryContainer = secondaryContainerStormyDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryStormyDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    background = backgroundStormyDark,
    surface = backgroundStormyDark
)

private val nightLightScheme = lightScheme.copy(
    primary = primaryNightLight,
    onPrimary = onPrimaryNightLight,
    primaryContainer = primaryContainerNightLight,
    onPrimaryContainer = onPrimaryContainerNightLight,
    secondary = secondaryNightLight,
    onSecondary = onSecondaryNightLight,
    secondaryContainer = secondaryContainerNightLight,
    onSecondaryContainer = onSecondaryContainerNightLight,
    tertiary = tertiaryNightLight,
    onTertiary = onTertiaryNightLight,
    tertiaryContainer = tertiaryContainerNightLight,
    onTertiaryContainer = onTertiaryContainerNightLight,
    background = backgroundNightLight,
    surface = backgroundNightLight
)
private val nightDarkScheme = darkScheme.copy(
    primary = primaryNightDark,
    onPrimary = onPrimaryNightDark,
    primaryContainer = primaryContainerNightDark,
    onPrimaryContainer = onPrimaryContainerNightDark,
    secondary = secondaryNightDark,
    onSecondary = onSecondaryNightDark,
    secondaryContainer = secondaryContainerNightDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryNightDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    background = backgroundNightDark,
    surface = backgroundNightDark
)

fun getWeatherColorScheme(wmo: Int?, isNight: Boolean, darkTheme: Boolean): androidx.compose.material3.ColorScheme {
    return when (wmo) {
        0, 1 -> {
            if (isNight) {
                if (darkTheme) nightDarkScheme else nightLightScheme
            } else {
                if (darkTheme) sunnyDarkScheme else sunnyLightScheme
            }
        }
        2, 3, 45, 48 -> if (darkTheme) cloudyDarkScheme else cloudyLightScheme // Partly cloudy, overcast, fog
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> if (darkTheme) rainyDarkScheme else rainyLightScheme // Drizzle, Rain, Showers
        71, 73, 75, 77, 85, 86 -> if (darkTheme) snowyDarkScheme else snowyLightScheme // Snow
        95, 96, 99 -> if (darkTheme) stormyDarkScheme else stormyLightScheme // Thunderstorm
        else -> if (darkTheme) darkScheme else lightScheme
    }
}

val unspecified_scheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
)

@Composable
fun TheMeteoTheme(
    themeMode: ThemeMode = ThemeMode.FIXED,
    currentWmoCode: Int? = null,
    isNight: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable() () -> Unit
) {
  val colorScheme = when {
      themeMode == ThemeMode.SYSTEM && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      
      themeMode == ThemeMode.WEATHER -> getWeatherColorScheme(currentWmoCode, isNight, darkTheme)
      
      darkTheme -> darkScheme
      else -> lightScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    content = content
  )
}

