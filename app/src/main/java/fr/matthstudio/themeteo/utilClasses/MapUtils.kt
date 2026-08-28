/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.utilClasses

import fr.matthstudio.themeteo.BuildConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

object MapUtils {
    /**
     * Returns a CartoDB tile URL for the given coordinates and zoom level.
     *
     * @param lat Latitude
     * @param lon Longitude
     * @param zoom Zoom level
     * @param isDark Whether to use the dark theme tile set
     */
    fun getCartoTileUrl(lat: Double, lon: Double, zoom: Int, isDark: Boolean): String {
        val n = 2.0.pow(zoom.toDouble())
        val x = ((lon + 180.0) / 360.0 * n).toInt()
        val latRad = lat * PI / 180.0
        val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()

        val theme = if (isDark) "dark_all" else "light_all"
        return "https://basemaps.cartocdn.com/$theme/$zoom/$x/$y.png" +
                "?key=${BuildConfig.CARTODB_API_KEY}"
    }
}
