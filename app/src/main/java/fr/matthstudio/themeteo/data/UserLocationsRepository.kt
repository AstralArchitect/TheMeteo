package fr.matthstudio.themeteo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable // Pour pouvoir le convertir en JSON et le stocker
data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String // Utile pour l'affichage
)

class UserLocationsRepository(private val dataStore: DataStore<Preferences>) {
    // Clé pour stocker la liste en JSON
    private val LOCATIONS_KEY = stringPreferencesKey("saved_locations_json")

    // Flow pour obtenir la liste des lieux en temps réel
    val savedLocations: Flow<List<SavedLocation>> = dataStore.data.map { preferences ->
        val jsonString = preferences[LOCATIONS_KEY]
        if (jsonString != null) {
            // Utilisez un bloc try-catch pour la robustesse
            try {
                Json.decodeFromString<List<SavedLocation>>(jsonString)
            } catch (e: Exception) {
                // En cas d'erreur de désérialisation, retournez une liste vide
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    // Fonction pour ajouter un lieu
    suspend fun addLocation(location: SavedLocation) {
        dataStore.edit { preferences ->
            // 1. Lire la liste actuelle en utilisant .first() pour obtenir la valeur immédiate du Flow
            val currentListJson = preferences[LOCATIONS_KEY]
            val currentList = if (currentListJson != null) {
                Json.decodeFromString<List<SavedLocation>>(currentListJson)
            } else {
                emptyList()
            }

            // 2. Ajouter le nouvel élément (en s'assurant qu'il n'est pas déjà présent)
            if (!currentList.contains(location)) {
                val newList = currentList + location
                // 3. Encoder la nouvelle liste en JSON et la sauvegarder
                preferences[LOCATIONS_KEY] = Json.encodeToString(newList)
            }
        }
    }

    // Fonction pour supprimer un lieu
    suspend fun removeLocation(location: SavedLocation) {
        dataStore.edit { preferences ->
            val currentListJson = preferences[LOCATIONS_KEY]
            val currentList = if (currentListJson != null) {
                Json.decodeFromString<List<SavedLocation>>(currentListJson)
            } else {
                return@edit // Rien à faire si la liste est vide
            }

            // 1. Filtrer la liste pour supprimer l'élément
            val newList = currentList.filterNot { it == location }

            // 2. Sauvegarder la nouvelle liste
            preferences[LOCATIONS_KEY] = Json.encodeToString(newList)
        }
    }
}