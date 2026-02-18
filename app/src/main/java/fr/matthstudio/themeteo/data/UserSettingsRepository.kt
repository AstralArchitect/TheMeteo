package fr.matthstudio.themeteo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.matthstudio.themeteo.DefaultScreen
import fr.matthstudio.themeteo.LocationIdentifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

class UserSettingsRepository(private val dataStore: DataStore<Preferences>) {

    // 1. Définir les clés pour chaque paramètre
    private object PreferencesKeys {
        val MODEL = stringPreferencesKey("user_model")
        val ROUND_TO_INT = booleanPreferencesKey("round_to_int")
        val DEFAULT_LOCATION = stringPreferencesKey("default_location")
        val DEFAULT_SCREEN = intPreferencesKey("default_screen")
        val ENABLE_MODEL_FALLBACK = booleanPreferencesKey("enable_model_fallback")
        val ENABLE_ANIMATED_ICONS = booleanPreferencesKey("enable_animated_icons")
        val FIREBASE_CONSENT = stringPreferencesKey("firebase_consent")
    }

    // 2. Exposer les paramètres sous forme de Flow pour une observation en temps réel

    /**
     * Flow pour le consentement Firebase (PENDING, GRANTED, DENIED).
     */
    val firebaseConsent: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FIREBASE_CONSENT] ?: "PENDING"
    }

    /**
     * Flow pour l'activation des icônes animées.
     */
    val enableAnimatedIcons: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ENABLE_ANIMATED_ICONS] ?: true
    }

    /**
     * Flow pour l'activation du fallback de modèle (complétion des données).
     */
    val enableModelFallback: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ENABLE_MODEL_FALLBACK] ?: true
    }

    /**
     * Flow pour le modèle météo sélectionné.
     * Fournit null si aucune n'est définie.
     */
    val model: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MODEL]
    }

    /**
     * Flow pour le réglage d'arrondi des températures.
     * La valeur par défaut est `true`.
     */
    val roundToInt: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ROUND_TO_INT] ?: true
    }

    /**
     * Flow pour la localisation par défaut.
     * Fournit null si aucune n'est définie.
     */
    val defaultLocation: Flow<LocationIdentifier?> = dataStore.data.map { preferences ->
        val data = preferences[PreferencesKeys.DEFAULT_LOCATION] ?: return@map null
        Json.decodeFromString<LocationIdentifier>(data)
    }

    /**
     * Flow pour l'activité par défaut.
     * Fournit une valeur par défaut si aucune n'est définie.
     */
    val defaultScreen: Flow<DefaultScreen?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_SCREEN]?.let { index ->
            DefaultScreen.entries.getOrNull(index)
        }
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

    /**
     * Met à jour la localisation par défaut.
     */
    suspend fun updateDefaultLocation(newLocation: LocationIdentifier) {
        dataStore.edit {
            preferences ->
            preferences[PreferencesKeys.DEFAULT_LOCATION] = Json.encodeToString(newLocation)
        }
    }

    /**
     * Met à jour le paramètre d'activation du fallback de modèle.
     */
    suspend fun updateEnableModelFallback(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_MODEL_FALLBACK] = enabled
        }
    }

    /**
     * Met à jour le paramètre d'activation des icônes animées.
     */
    suspend fun updateEnableAnimatedIcons(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENABLE_ANIMATED_ICONS] = enabled
        }
    }

    /**
     * Met à jour le consentement Firebase.
     */
    suspend fun updateFirebaseConsent(consent: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIREBASE_CONSENT] = consent
        }
    }

    /**
     * Met à jour l'activité par défaut.
     */
    suspend fun updateDefaultActivity(newScreen: DefaultScreen) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_SCREEN] = newScreen.ordinal
        }
    }
}