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

enum class ForecastType {
    DETERMINISTIC,
    ENSEMBLE
}

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT,
    KELVIN
}

enum class WindUnit {
    KPH,
    MPH
}

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
        val GCU_CONSENT = booleanPreferencesKey("gcu_consent")
        val LAST_GCU_UPDATE = stringPreferencesKey("last_gcu_update")
        val LAST_PRIVACY_POLICY_UPDATE = stringPreferencesKey("last_privacy_policy_update")
        val HAS_OPENED_APP_ONCE = booleanPreferencesKey("has_opened_app_once")
        val FORECAST_TYPE = intPreferencesKey("forecast_type")
        val TEMPERATURE_UNIT = intPreferencesKey("temperature_unit")
        val WIND_UNIT = intPreferencesKey("wind_unit")
        val WIDGET_TRANSPARENCY = intPreferencesKey("widget_transparency")
        val WIDGET_TEXT_SIZE = intPreferencesKey("widget_text_size")
    }

    // 2. Exposer les paramètres sous forme de Flow pour une observation en temps réel

    /**
     * Flow pour la transparence du widget (0-100).
     */
    val widgetTransparency: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.WIDGET_TRANSPARENCY] ?: 50
    }

    /**
     * Flow pour la taille du texte du widget (petit, moyen, grand -> 0, 1, 2).
     */
    val widgetTextSize: Flow<Int> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.WIDGET_TEXT_SIZE] ?: 1
    }

    /**
     * Flow pour l'unité de température (CELSIUS, FAHRENHEIT, KELVIN).
     */
    val temperatureUnit: Flow<TemperatureUnit> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.TEMPERATURE_UNIT]?.let { index ->
            TemperatureUnit.entries.getOrNull(index)
        } ?: TemperatureUnit.CELSIUS
    }

    /**
     * Flow pour l'unité de vent (KPH, MPH).
     */
    val windUnit: Flow<WindUnit> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.WIND_UNIT]?.let { index ->
            WindUnit.entries.getOrNull(index)
        } ?: WindUnit.KPH
    }

    /**
     * Flow pour le type de prévision (DETERMINISTIC ou ENSEMBLE).
     */
    val forecastType: Flow<ForecastType> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FORECAST_TYPE]?.let { index ->
            ForecastType.entries.getOrNull(index)
        } ?: ForecastType.DETERMINISTIC
    }

    /**
     * Flow pour le consentement Firebase (PENDING, GRANTED, DENIED).
     */
    val firebaseConsent: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FIREBASE_CONSENT] ?: "PENDING"
    }

    /**
     * Flow pour l'acceptation des CGU.
     */
    val gcuAccepted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GCU_CONSENT] ?: false
    }

    /**
     * Flow pour la date de la dernière mise à jour des CGU acceptée.
     */
    val lastGcuUpdate: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_GCU_UPDATE]
    }

    /**
     * Flow pour la date de la dernière mise à jour de la politique de confidentialité.
     */
    val lastPrivacyPolicyUpdate: Flow<String?> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_PRIVACY_POLICY_UPDATE]
    }

    /**
     * Flow pour savoir si l'application a déjà été ouverte une fois.
     */
    val hasOpenedAppOnce: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HAS_OPENED_APP_ONCE] ?: false
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
     * Met à jour l'acceptation des CGU.
     */
    suspend fun updateGcuAccepted(accepted: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GCU_CONSENT] = accepted
        }
    }

    /**
     * Met à jour la date de la dernière mise à jour des CGU.
     */
    suspend fun updateLastGcuUpdate(date: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_GCU_UPDATE] = date
        }
    }

    /**
     * Met à jour la date de la dernière mise à jour de la politique de confidentialité.
     */
    suspend fun updateLastPrivacyPolicyUpdate(date: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_PRIVACY_POLICY_UPDATE] = date
        }
    }

    /**
     * Met à jour le flag indiquant si l'application a déjà été ouverte une fois.
     */
    suspend fun updateHasOpenedAppOnce(hasOpened: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_OPENED_APP_ONCE] = hasOpened
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

    /**
     * Met à jour le type de prévision.
     */
    suspend fun updateForecastType(type: ForecastType) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FORECAST_TYPE] = type.ordinal
        }
    }

    /**
     * Met à jour l'unité de température.
     */
    suspend fun updateTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TEMPERATURE_UNIT] = unit.ordinal
        }
    }

    /**
     * Met à jour l'unité de vent.
     */
    suspend fun updateWindUnit(unit: WindUnit) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIND_UNIT] = unit.ordinal
        }
    }

    /**
     * Met à jour la transparence du widget.
     */
    suspend fun updateWidgetTransparency(transparency: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIDGET_TRANSPARENCY] = transparency
        }
    }

    /**
     * Met à jour la taille du texte du widget.
     */
    suspend fun updateWidgetTextSize(sizeIndex: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WIDGET_TEXT_SIZE] = sizeIndex
        }
    }
}
