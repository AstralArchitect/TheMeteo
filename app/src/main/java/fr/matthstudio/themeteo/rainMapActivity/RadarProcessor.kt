/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.nio.ByteBuffer

/**
 * Utilitaire pour le traitement multithreadé des données radar en Kotlin.
 */
object RadarProcessor {

    /**
     *
     * Traite un ByteBuffer de pixels ARGB_8888 sur place (in-place).
     * Très économe car aucune nouvelle allocation n'est faite.
     */
    suspend fun processImage(
        buffer: ByteBuffer,
        width: Int,
        height: Int
    ) = coroutineScope {
        buffer.rewind()
        // On utilise un IntBuffer pour manipuler les pixels (4 octets) plus facilement
        val intBuffer = buffer.asIntBuffer()
        
        val numThreads = Runtime.getRuntime().availableProcessors()
        val chunkHeight = height / numThreads

        val jobs = (0 until numThreads).map { threadIdx ->
            async(Dispatchers.Default) {
                val startY = threadIdx * chunkHeight
                val endY = if (threadIdx == numThreads - 1) height else (threadIdx + 1) * chunkHeight
                
                for (y in startY until endY) {
                    val rowOffset = y * width
                    for (x in 0 until width) {
                        val index = rowOffset + x
                        // Lecture, transformation et écriture sur place
                        val pixel = intBuffer.get(index)
                        intBuffer.put(index, transformPixel(pixel))
                    }
                }
            }
        }
        jobs.awaitAll()
    }

    /**
     * Placeholder pour la conversion d'un seul pixel.
     * À implémenter avec votre logique métier (palette, mm/h, etc.).
     */
    private fun transformPixel(color: Int): Int {
        val grey = Color.red(color) // On prend le canal rouge (gris) comme intensité brute
        val alpha = Color.alpha(color)

        if (grey == 0 || alpha == 0)  return Color.TRANSPARENT

        val MAX_PRECIP = 100.0f
        val intensite: Float = (grey * MAX_PRECIP) / 255.0f

        return when {
            intensite <= 0.0f  -> Color.argb(0, 0, 0, 0)             // 0 mm : Sec (Transparent)
            intensite < 1.0f   -> Color.argb(alpha, 180, 220, 255)   // Bleu pâle (Bruine / Traces)
            intensite < 4.0f   -> Color.argb(alpha, 50, 130, 240)    // Bleu (Pluie faible)
            intensite < 8.0f   -> Color.argb(alpha, 40, 180, 75)     // Vert (Pluie modérée - dès 4 mm)
            intensite < 15.0f  -> Color.argb(alpha, 255, 210, 0)    // Jaune (Pluie soutenue)
            intensite < 25.0f  -> Color.argb(alpha, 255, 120, 0)    // Orange (Pluie forte - dès 15 mm)
            intensite < 40.0f  -> Color.argb(alpha, 230, 30, 30)     // Rouge (Pluie très forte / Orage)
            intensite < 60.0f  -> Color.argb(alpha, 255, 255, 255)     // Violet / Magenta (Diluvien)
            else               -> Color.argb(alpha, 255, 255, 255)     // Blanc (Extrême)
        }
    }
}
