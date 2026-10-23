/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.data

import android.content.Context
import android.util.Log
import fr.matthstudio.themeteo.TheMeteo
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Fournit le code de domaine de vigilance Météo-France (département) pour des coordonnées GPS données.
 * Implémenté à 100 % en Kotlin pur avec les API Android natives.
 * Utilise le fichier GeoJSON officiel des zones de vigilance Météo-France (avec enclaves fusionnées).
 */
class VigilanceDomainProvider(private val context: Context? = null) {

    private data class Point(val lon: Double, val lat: Double)

    private data class Polygon(
        val outerRing: List<Point>,
        val holes: List<List<Point>> = emptyList()
    )

    private data class Zone(
        val code: String,
        val name: String,
        val polygons: List<Polygon>
    )

    private val zones: List<Zone> by lazy {
        loadZones()
    }

    private fun getContextToUse(): Context? {
        return context ?: try {
            TheMeteo.instance
        } catch (e: Exception) {
            null
        }
    }

    private fun loadZones(): List<Zone> {
        val targetContext = getContextToUse() ?: return emptyList()
        val result = mutableListOf<Zone>()
        try {
            val jsonString = targetContext.assets.open("DONNEES_EXPERTISEES_VVS_departementales.geojson")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val root = JSONObject(jsonString)
            val features = root.optJSONArray("features") ?: return emptyList()

            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val properties = feature.optJSONObject("properties") ?: continue
                val geometry = feature.optJSONObject("geometry") ?: continue

                val code = properties.optString("code", "")
                val name = properties.optString("nom", "")
                if (code.isBlank()) continue

                val type = geometry.optString("type", "")
                val coordsArray = geometry.optJSONArray("coordinates") ?: continue

                val polygons = mutableListOf<Polygon>()

                if (type == "Polygon") {
                    parsePolygonCoords(coordsArray)?.let { polygons.add(it) }
                } else if (type == "MultiPolygon") {
                    for (j in 0 until coordsArray.length()) {
                        val polyArray = coordsArray.optJSONArray(j) ?: continue
                        parsePolygonCoords(polyArray)?.let { polygons.add(it) }
                    }
                }

                if (polygons.isNotEmpty()) {
                    result.add(Zone(code = code, name = name, polygons = polygons))
                }
            }
        } catch (e: Exception) {
            Log.e("VigilanceDomainProvider", "Erreur lors du chargement des zones GeoJSON", e)
        }
        return result
    }

    private fun parsePolygonCoords(coordsArray: org.json.JSONArray): Polygon? {
        if (coordsArray.length() == 0) return null
        val outerRingArray = coordsArray.optJSONArray(0) ?: return null
        val outerRing = parseRing(outerRingArray)
        if (outerRing.isEmpty()) return null

        val holes = mutableListOf<List<Point>>()
        for (k in 1 until coordsArray.length()) {
            val holeArray = coordsArray.optJSONArray(k) ?: continue
            val hole = parseRing(holeArray)
            if (hole.isNotEmpty()) {
                holes.add(hole)
            }
        }
        return Polygon(outerRing = outerRing, holes = holes)
    }

    private fun parseRing(ringArray: org.json.JSONArray): List<Point> {
        val list = ArrayList<Point>(ringArray.length())
        for (i in 0 until ringArray.length()) {
            val pt = ringArray.optJSONArray(i) ?: continue
            val lon = pt.optDouble(0, Double.NaN)
            val lat = pt.optDouble(1, Double.NaN)
            if (!lon.isNaN() && !lat.isNaN()) {
                list.add(Point(lon, lat))
            }
        }
        return list
    }

    /**
     * Seuil maximal de distance en degrés (~25 km) pour le repli sur le littoral ou les frontières.
     * Au-delà de cette distance, la position est considérée comme hors de France et retourne null.
     */
    private val maxFallbackDistanceDegrees = 0.25

    /**
     * Recherche le domaine de vigilance Météo-France pour une position GPS (lat, lon).
     * 1. Test d'inclusion directe dans le polygone de la zone.
     * 2. Fallback au polygone le plus proche si la position déborde de la frontière ou sur le littoral (< ~25 km).
     * 3. Retourne null si la position est à l'étranger (hors de couverture Météo-France).
     */
    fun getVigilanceDomain(lat: Double, lon: Double): String? {
        val loadedZones = zones
        if (loadedZones.isEmpty()) return null

        // 1. Test d'inclusion directe
        for ((code, _, polygons) in loadedZones) {
            for (polygon in polygons) {
                if (pointInPolygon(lon, lat, polygon)) {
                    return code
                }
            }
        }

        // 2. Fallback au polygone le plus proche (limité aux zones frontalières/littorales proches)
        var minDistance = Double.MAX_VALUE
        var nearestCode: String? = null

        for ((code, _, polygons) in loadedZones) {
            for (polygon in polygons) {
                val dist = distanceToPolygon(lon, lat, polygon)
                if (dist < minDistance) {
                    minDistance = dist
                    nearestCode = code
                }
            }
        }

        // Seules les positions situées à proximité immédiate du territoire français (< ~25 km) bénéficient du repli
        return if (minDistance <= maxFallbackDistanceDegrees) nearestCode else null
    }

    private fun pointInPolygon(x: Double, y: Double, polygon: Polygon): Boolean {
        if (!pointInRing(x, y, polygon.outerRing)) return false
        for (hole in polygon.holes) {
            if (pointInRing(x, y, hole)) return false
        }
        return true
    }

    private fun pointInRing(x: Double, y: Double, ring: List<Point>): Boolean {
        val n = ring.size
        if (n < 3) return false
        var inside = false
        var p1 = ring[0]
        for (i in 0..n) {
            val p2 = ring[i % n]
            if (y > min(p1.lat, p2.lat)) {
                if (y <= max(p1.lat, p2.lat)) {
                    if (x <= max(p1.lon, p2.lon)) {
                        if (p1.lat != p2.lat) {
                            val xinters = (y - p1.lat) * (p2.lon - p1.lon) / (p2.lat - p1.lat) + p1.lon
                            if (p1.lon == p2.lon || x <= xinters) {
                                inside = !inside
                            }
                        }
                    }
                }
            }
            p1 = p2
        }
        return inside
    }

    private fun distanceToPolygon(px: Double, py: Double, polygon: Polygon): Double {
        var minDist = Double.MAX_VALUE
        minDist = min(minDist, distanceToRing(px, py, polygon.outerRing))
        for (hole in polygon.holes) {
            minDist = min(minDist, distanceToRing(px, py, hole))
        }
        return minDist
    }

    private fun distanceToRing(px: Double, py: Double, ring: List<Point>): Double {
        var minDist = Double.MAX_VALUE
        val n = ring.size
        for (i in 0 until n) {
            val a = ring[i]
            val b = ring[(i + 1) % n]
            val d = distToSegment(px, py, a.lon, a.lat, b.lon, b.lat)
            if (d < minDist) {
                minDist = d
            }
        }
        return minDist
    }

    private fun distToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val vx = bx - ax
        val vy = by - ay
        val ux = px - ax
        val uy = py - ay
        val lenSq = vx * vx + vy * vy
        if (lenSq == 0.0) return hypot(px - ax, py - ay)
        val t = (ux * vx + uy * vy) / lenSq
        val clampedT = t.coerceIn(0.0, 1.0)
        val cx = ax + clampedT * vx
        val cy = ay + clampedT * vy
        return hypot(px - cx, py - cy)
    }
}
