package fr.matthstudio.themeteo.data

import android.os.Parcelable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Parcelize
@Serializable // Pour pouvoir le convertir en JSON et le stocker
data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String // Utile pour l'affichage
) : Parcelable

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
            // 1. Lire la liste actuelle
            val currentListJson = preferences[LOCATIONS_KEY]
            val currentList = if (currentListJson != null) {
                try {
                    Json.decodeFromString<List<SavedLocation>>(currentListJson)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            // 2. Vérifier si cette position exacte (lat/lon) est déjà enregistrée
            // On compare les coordonnées plutôt que l'objet entier car le nom peut varier
            val isDuplicateCoordinates = currentList.any {
                it.latitude == location.latitude && it.longitude == location.longitude
            }

            if (!isDuplicateCoordinates) {
                // 3. Gérer la collision de noms (ex: "Paris", "Paris 1", "Paris 2"...)
                var finalName = location.name
                var counter = 1

                // Tant qu'il existe un lieu avec le même nom dans la liste
                while (currentList.any { it.name.equals(finalName, ignoreCase = true) }) {
                    finalName = "${location.name} $counter"
                    counter++
                }

                // Créer la nouvelle localisation avec le nom unique
                val locationToAdd = location.copy(name = finalName)

                // 4. Ajouter et sauvegarder
                val newList = currentList + locationToAdd
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

    /**
     * Met à jour l'ordre de la liste entière des lieux.
     */
    suspend fun reorderLocations(newList: List<SavedLocation>) {
        dataStore.edit { preferences ->
            preferences[LOCATIONS_KEY] = Json.encodeToString(newList)
        }
    }

    /**
     * Renomme une localisation existante.
     */
    suspend fun renameLocation(location: SavedLocation, newName: String) {
        dataStore.edit { preferences ->
            val currentListJson = preferences[LOCATIONS_KEY] ?: return@edit
            val currentList = try {
                Json.decodeFromString<List<SavedLocation>>(currentListJson)
            } catch (e: Exception) {
                return@edit
            }

            val newList = currentList.map {
                if (it.latitude == location.latitude && it.longitude == location.longitude) {
                    it.copy(name = newName)
                } else {
                    it
                }
            }
            preferences[LOCATIONS_KEY] = Json.encodeToString(newList)
        }
    }
}
