package fr.matthstudio.themeteo

import fr.matthstudio.themeteo.data.GpsCoordinates
import fr.matthstudio.themeteo.data.SavedLocation
import fr.matthstudio.themeteo.data.ThemeMode
import fr.matthstudio.themeteo.utilClasses.FullSunData
import fr.matthstudio.themeteo.utilClasses.MoonData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object AppStateManager {
    private val _selectedLocation = MutableStateFlow<LocationIdentifier>(LocationIdentifier.CurrentUserLocation)
    val selectedLocation = _selectedLocation.asStateFlow()

    private val _savedLocations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val savedLocations = _savedLocations.asStateFlow()

    private val _currentGpsPosition = MutableStateFlow<GpsCoordinates?>(null)
    val currentGpsPosition = _currentGpsPosition.asStateFlow()

    private val _userSettings = MutableStateFlow<UserSettings?>(null)
    val userSettings = _userSettings.asStateFlow()

    private val _currentCityName = MutableStateFlow<String?>(null)
    val currentCityName = _currentCityName.asStateFlow()

    private val _isNight = MutableStateFlow(false)
    val isNight = _isNight.asStateFlow()

    private val _isBatterySaverActive = MutableStateFlow(false)
    val isBatterySaverActive = _isBatterySaverActive.asStateFlow()

    private val _isLocationPermissionGranted = MutableStateFlow(false)
    val isLocationPermissionGranted = _isLocationPermissionGranted.asStateFlow()

    private val _sunData = MutableStateFlow<FullSunData?>(null)
    val sunData = _sunData.asStateFlow()

    private val _moonData = MutableStateFlow<MoonData?>(null)
    val moonData = _moonData.asStateFlow()

    private val _currentWmoCode = MutableStateFlow<Int?>(null)
    val currentWmoCode = _currentWmoCode.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.FIXED)
    val themeMode = _themeMode.asStateFlow()

    private val _resolvedPolicyInfo = MutableStateFlow<ResolvedPolicyInfo?>(null)
    val resolvedPolicyInfo = _resolvedPolicyInfo.asStateFlow()

    private val _isForcedUpdateRequired = MutableStateFlow(false)
    val isForcedUpdateRequired = _isForcedUpdateRequired.asStateFlow()

    private val _inAppUpdateEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val inAppUpdateEvent = _inAppUpdateEvent.asSharedFlow()

    fun triggerInAppUpdate() {
        _inAppUpdateEvent.tryEmit(Unit)
    }

    fun updateIsForcedUpdateRequired(required: Boolean) {
        _isForcedUpdateRequired.value = required
    }

    fun updateSelectedLocation(location: LocationIdentifier) {
        _selectedLocation.value = location
    }

    fun updateSavedLocations(locations: List<SavedLocation>) {
        _savedLocations.value = locations
    }

    fun updateGpsPosition(coords: GpsCoordinates?) {
        _currentGpsPosition.value = coords
    }

    fun updateUserSettings(settings: UserSettings) {
        _userSettings.value = settings
    }

    fun updateCityName(name: String?) {
        _currentCityName.value = name
    }

    fun updateIsNight(night: Boolean) {
        _isNight.value = night
    }

    fun updateBatterySaver(active: Boolean) {
        _isBatterySaverActive.value = active
    }

    fun updateLocationPermission(granted: Boolean) {
        _isLocationPermissionGranted.value = granted
    }

    fun updateSunData(data: FullSunData?) {
        _sunData.value = data
    }

    fun updateMoonData(data: MoonData?) {
        _moonData.value = data
    }

    fun updateCurrentWmoCode(wmo: Int?) {
        _currentWmoCode.value = wmo
    }

    fun updateThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun updateResolvedPolicyInfo(info: ResolvedPolicyInfo) {
        _resolvedPolicyInfo.value = info
    }
}
