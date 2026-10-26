/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import kotlin.math.round

@Serializable
data class CityDiskEntry(
    val key: String,
    val name: String,
    val lastAccessed: Long = System.currentTimeMillis()
)

/**
 * Cache disque persistant pour les noms de ville issus du géocodage inverse.
 * Fonctionne selon une politique LRU (Least Recently Used) avec une taille maximale fixée.
 * Lorsque la limite est atteinte, les entrées les moins récemment fréquentées sont évincées.
 */
class CityNameDiskCache(
    private val context: Context,
    private val maxEntries: Int = 100
) {
    private val mutex = Mutex()
    private val cacheFile = File(context.cacheDir, "city_names_lru.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // Map ordonnée en RAM basée sur l'ordre d'accès (accessOrder = true).
    // Quand size > maxEntries, l'entrée la moins récemment consultée est automatiquement évincée.
    private val memoryLru = object : LinkedHashMap<String, String>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > maxEntries
        }
    }

    private var isLoaded = false

    /**
     * Arrondit les coordonnées à 2 décimales (~1.1 km).
     * Permet d'obtenir un hit immédiat pour tous les déplacements au sein d'une même commune/zone.
     */
    fun formatKey(lat: Double, lon: Double): String {
        val rLat = round(lat * 100.0) / 100.0
        val rLon = round(lon * 100.0) / 100.0
        return String.format(Locale.US, "%.2f,%.2f", rLat, rLon)
    }

    private suspend fun ensureLoadedLocked() {
        if (isLoaded) return
        isLoaded = true
        withContext(Dispatchers.IO) {
            try {
                if (cacheFile.exists()) {
                    val content = cacheFile.readText()
                    if (content.isNotBlank()) {
                        val entries = json.decodeFromString<List<CityDiskEntry>>(content)
                        // Trie par ordre d'accès pour reconstruire l'ordre LRU exact
                        entries.sortedBy { it.lastAccessed }.takeLast(maxEntries).forEach { entry ->
                            memoryLru[entry.key] = entry.name
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CityNameDiskCache", "Erreur lors du chargement du cache disque des villes", e)
            }
        }
    }

    suspend fun get(lat: Double, lon: Double): String? = mutex.withLock {
        ensureLoadedLocked()
        val key = formatKey(lat, lon)
        val name = memoryLru[key]
        if (name != null) {
            saveToDiskLocked()
        }
        name
    }

    suspend fun put(lat: Double, lon: Double, name: String) = mutex.withLock {
        ensureLoadedLocked()
        val key = formatKey(lat, lon)
        memoryLru[key] = name
        saveToDiskLocked()
    }

    private suspend fun saveToDiskLocked() {
        withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val entries = memoryLru.entries.map { (k, v) ->
                    CityDiskEntry(key = k, name = v, lastAccessed = now)
                }
                val content = json.encodeToString(entries)
                val tempFile = File(context.cacheDir, "city_names_lru.json.tmp")
                tempFile.writeText(content)
                if (tempFile.exists()) {
                    tempFile.renameTo(cacheFile)
                }
            } catch (e: Exception) {
                Log.e("CityNameDiskCache", "Erreur lors de la sauvegarde du cache disque des villes", e)
            }
        }
    }
}
