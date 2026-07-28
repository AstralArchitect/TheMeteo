/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.rainMapActivity

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

class RadarMemoryPool(
    private val bufferSize: Int,
    private val maxPoolSize: Int = 12
) {
    private val pool = ConcurrentLinkedQueue<ByteBuffer>()

    /**
     * Récupère un buffer disponible ou en alloue un nouveau (jusqu'à maxPoolSize).
     */
    fun acquire(): ByteBuffer {
        return pool.poll() ?: ByteBuffer.allocateDirect(bufferSize).apply {
            order(ByteOrder.nativeOrder())
        }
    }

    /**
     * Rend le buffer au pool pour réutilisation.
     */
    fun release(buffer: ByteBuffer) {
        if (pool.size < maxPoolSize) {
            buffer.clear()
            pool.offer(buffer)
        }
    }

    /**
     * Vide complètement la mémoire native allouée.
     */
    fun clear() {
        pool.clear()
        // Note: La libération réelle de la mémoire directe dépend de l'implémentation de la JVM
        // mais effacer les références permet au GC de s'en occuper plus tard.
    }
}
