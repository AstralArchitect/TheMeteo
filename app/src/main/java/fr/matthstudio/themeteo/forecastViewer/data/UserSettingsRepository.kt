package fr.matthstudio.themeteo.forecastViewer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserSettingsRepository(private val dataStore: DataStore<Preferences>) {

    // 1. Définir les clés pour chaque paramètre
    private object PreferencesKeys {
        val MODEL = stringPreferencesKey("user_model")
        val ROUND_TO_INT = booleanPreferencesKey("round_to_int")
    }

    // 2. Exposer les paramètres sous forme de Flow pour une observation en temps réel

    /**
     * Flow pour le modèle météo sélectionné.
     * Fournit une valeur par défaut si aucune n'est définie.
     */
    val model: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MODEL]
    }

    /**
     * Flow pour le réglage d'arrondi des températures.
     * La valeur par défaut est `true`.
     */
    val roundToInt: Flow<Boolean?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ROUND_TO_INT]
    }

    // 3. Fonctions pour mettre à jour les paramètres

    /**
     * Met à jour le modèle météo enregistré.
     */
    suspend fun updateModel(newModel: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MODEL] = newModel
        }
    }

    /**
     * Met à jour le paramètre d'arrondi des températures.
     */
    suspend fun updateRoundToInt(shouldRound: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ROUND_TO_INT] = shouldRound
        }
    }
}