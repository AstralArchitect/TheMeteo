package fr.matthstudio.themeteo.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.matthstudio.themeteo.R

data class WeatherModel(
    val apiName: String,
    val settingNameResId: Int? = null,
    val settingNameRaw: String? = null,
    val sourceName: String,
    val isGlobal: Boolean,
    val minLat: Double = -90.0,
    val maxLat: Double = 90.0,
    val minLon: Double = -180.0,
    val maxLon: Double = 180.0,
    val predictionDays: Int,
    val isEnsemble: Boolean = false,
    val secondaryModelApiName: String? = null
) {
    fun isAvailableAt(lat: Double, lon: Double): Boolean {
        if (isGlobal) return true
        return lat in minLat..maxLat && lon in minLon..maxLon
    }
}

@Composable
fun WeatherModel.getDisplayName(): String {
    return settingNameResId?.let { stringResource(it) } ?: settingNameRaw ?: apiName
}

object WeatherModelRegistry {
    val models = listOf(
        // --- Modèles Déterministes ---
        WeatherModel("best_match", settingNameResId = R.string.meilleur_mod_le_par_d_faut, sourceName = "Open-Meteo", isGlobal = true, predictionDays = 15),
        WeatherModel("meteofrance_seamless", settingNameRaw = "Météo France Seamless", sourceName = "Météo France", isGlobal = true, predictionDays = 3, secondaryModelApiName = "best_match"),
        WeatherModel("meteofrance_arpege_world", settingNameRaw = "Météo France ARPEGE World", sourceName = "ARPEGE", isGlobal = true, predictionDays = 4, secondaryModelApiName = "meteofrance_seamless"),
        WeatherModel("meteofrance_arpege_europe", settingNameRaw = "Météo France ARPEGE Europe", sourceName = "ARPEGE", isGlobal = false, minLat = 32.0, maxLat = 70.0, minLon = -20.0, maxLon = 40.0, predictionDays = 4, secondaryModelApiName = "meteofrance_seamless"),
        WeatherModel("meteofrance_arome_france", settingNameRaw = "Météo France AROME", sourceName = "AROME", isGlobal = false, minLat = 38.0, maxLat = 55.0, minLon = -8.0, maxLon = 12.0, predictionDays = 2, secondaryModelApiName = "meteofrance_seamless"),
        WeatherModel("meteofrance_arome_france_hd", settingNameRaw = "Météo France AROME HD", sourceName = "AROME HD", isGlobal = false, minLat = 38.0, maxLat = 55.0, minLon = -8.0, maxLon = 12.0, predictionDays = 2, secondaryModelApiName = "meteofrance_arome_france"),
        WeatherModel("knmi_harmonie_arome_europe", settingNameRaw = "KNMI Harmonie Arome Europe", sourceName = "Harmonie Arome", isGlobal = false, minLat = 45.0, maxLat = 60.0, minLon = -10.0, maxLon = 20.0, predictionDays = 2, secondaryModelApiName = "best_match"),
        WeatherModel("knmi_harmonie_arome_netherlands", settingNameRaw = "KNMI Harmonie Arome Netherlands", sourceName = "Harmonie Arome", isGlobal = false, minLat = 50.0, maxLat = 54.0, minLon = 3.0, maxLon = 8.0, predictionDays = 2, secondaryModelApiName = "knmi_harmonie_arome_europe"),
        WeatherModel("dmi_harmonie_arome_europe", settingNameRaw = "DMI Harmonie Arome Europe", sourceName = "Harmonie Arome", isGlobal = false, minLat = 40.0, maxLat = 75.0, minLon = -20.0, maxLon = 50.0, predictionDays = 2, secondaryModelApiName = "best_match"),
        WeatherModel("ukmo_seamless", settingNameRaw = "UK Met Office Seamless", sourceName = "UK Met Office", isGlobal = true, predictionDays = 5, secondaryModelApiName = "best_match"),
        WeatherModel("ukmo_global_deterministic_10km", settingNameRaw = "UK Met Office Global 10km", sourceName = "UK Met Office", isGlobal = true, predictionDays = 5, secondaryModelApiName = "ukmo_seamless"),
        WeatherModel("ukmo_uk_deterministic_2km", settingNameRaw = "UK Met Office UK 2km", sourceName = "UK Met Office", isGlobal = false, minLat = 49.0, maxLat = 61.0, minLon = -11.0, maxLon = 2.0, predictionDays = 2, secondaryModelApiName = "ukmo_seamless"),
        WeatherModel("meteoswiss_icon_seamless", settingNameRaw = "MeteoSwiss ICON Seamless", sourceName = "MeteoSwiss", isGlobal = false, minLat = 45.0, maxLat = 48.0, minLon = 5.0, maxLon = 11.0, predictionDays = 3, secondaryModelApiName = "best_match"),
        WeatherModel("meteoswiss_icon_ch1", settingNameRaw = "MeteoSwiss ICON CH1", sourceName = "MeteoSwiss", isGlobal = false, minLat = 45.0, maxLat = 48.0, minLon = 5.0, maxLon = 11.0, predictionDays = 3, secondaryModelApiName = "meteoswiss_icon_seamless"),
        WeatherModel("meteoswiss_icon_ch2", settingNameRaw = "MeteoSwiss ICON CH2", sourceName = "MeteoSwiss", isGlobal = false, minLat = 45.0, maxLat = 48.0, minLon = 5.0, maxLon = 11.0, predictionDays = 3, secondaryModelApiName = "meteoswiss_icon_seamless"),
        WeatherModel("icon_seamless", settingNameRaw = "ICON Seamless", sourceName = "Deutscher Wetterdienst", isGlobal = true, predictionDays = 7, secondaryModelApiName = "best_match"),
        WeatherModel("icon_d2", settingNameRaw = "DWD ICON D2", sourceName = "Deutscher Wetterdienst", isGlobal = false, minLat = 44.0, maxLat = 56.0, minLon = 2.0, maxLon = 16.0, predictionDays = 2, secondaryModelApiName = "icon_seamless"),
        WeatherModel("icon_eu", settingNameRaw = "DWD ICON EU", sourceName = "Deutscher Wetterdienst", isGlobal = false, minLat = 34.0, maxLat = 70.0, minLon = -25.0, maxLon = 40.0, predictionDays = 3, secondaryModelApiName = "icon_seamless"),
        WeatherModel("icon_global", settingNameRaw = "ICON Global", sourceName = "Deutscher Wetterdienst", isGlobal = true, predictionDays = 7, secondaryModelApiName = "icon_seamless"),
        WeatherModel("kma_gdps", settingNameRaw = "KMA GDPS", sourceName = "KMA", isGlobal = true, predictionDays = 10, secondaryModelApiName = "best_match"),
        WeatherModel("kma_ldps", settingNameRaw = "KMA LDPS", sourceName = "KMA", isGlobal = false, minLat = 32.0, maxLat = 40.0, minLon = 124.0, maxLon = 132.0, predictionDays = 3, secondaryModelApiName = "best_match"),
        WeatherModel("bom_access_global", settingNameRaw = "BOM ACCESS Global", sourceName = "BOM", isGlobal = true, predictionDays = 10, secondaryModelApiName = "best_match"),
        WeatherModel("cma_grapes_global", settingNameRaw = "CMA GRAPES Global", sourceName = "CMA", isGlobal = true, predictionDays = 10, secondaryModelApiName = "best_match"),
        WeatherModel("ecmwf_aifs025_single", settingNameRaw = "ECMWF AIFS 0.25° Single", sourceName = "ECMWF AIFS", isGlobal = true, predictionDays = 14, secondaryModelApiName = "ecmwf_ifs"),
        WeatherModel("ecmwf_ifs025", settingNameRaw = "ECMWF IFS 0.25°", sourceName = "ECMWF IFS", isGlobal = true, predictionDays = 14, secondaryModelApiName = "best_match"),
        WeatherModel("ecmwf_ifs", settingNameRaw = "ECMWF IFS HRES 9km", sourceName = "ECMWF IFS", isGlobal = true, predictionDays = 14, secondaryModelApiName = "best_match"),
        WeatherModel("gfs_seamless", settingNameRaw = "NCEP GFS Seamless", sourceName = "GFS", isGlobal = true, predictionDays = 15, secondaryModelApiName = "best_match"),
        WeatherModel("gfs_global", settingNameRaw = "NCEP GFS Global 0.11°/0.25°", sourceName = "GFS", isGlobal = true, predictionDays = 15, secondaryModelApiName = "gfs_seamless"),
        WeatherModel("ncep_aigfs025", settingNameRaw = "NCEP AIGFS 0.25°", sourceName = "AIGFS", isGlobal = true, predictionDays = 14, secondaryModelApiName = "best_match"),
        WeatherModel("ncep_hgefs025_ensemble_mean", settingNameRaw = "NCEP HGEFS 0.25° Ensemble Mean", sourceName = "NCEP", isGlobal = true, predictionDays = 16, secondaryModelApiName = "best_match"),
        WeatherModel("gfs_graphcast025", settingNameRaw = "NCEP GFS GraphCast", sourceName = "GFS", isGlobal = true, predictionDays = 15, secondaryModelApiName = "best_match"),
        WeatherModel("gem_seamless", settingNameRaw = "GEM Seamless", sourceName = "GEM", isGlobal = true, predictionDays = 9, secondaryModelApiName = "best_match"),
        WeatherModel("gem_global", settingNameRaw = "GEM Global", sourceName = "GEM", isGlobal = true, predictionDays = 9, secondaryModelApiName = "gem_seamless"),
        WeatherModel("gem_regional", settingNameRaw = "GEM Regional", sourceName = "GEM", isGlobal = false, minLat = 40.0, maxLat = 60.0, minLon = -140.0, maxLon = -50.0, predictionDays = 4, secondaryModelApiName = "gem_seamless"),
        WeatherModel("jma_gms", settingNameRaw = "JMA GSM", sourceName = "JMA", isGlobal = true, predictionDays = 10, secondaryModelApiName = "jma_seamless"),
        WeatherModel("jma_msm", settingNameRaw = "JMA MSM", sourceName = "JMA", isGlobal = false, minLat = 20.0, maxLat = 50.0, minLon = 120.0, maxLon = 150.0, predictionDays = 3, secondaryModelApiName = "jma_seamless"),
        WeatherModel("jma_seamless", settingNameRaw = "JMA Seamless", sourceName = "JMA", isGlobal = true, predictionDays = 10, secondaryModelApiName = "best_match"),

        // --- Modèles d'Ensemble ---
        WeatherModel("ecmwf_ifs025_ensemble", settingNameRaw = "ECMWF IFS Ensemble 0.25°", sourceName = "ECMWF IFS Ensemble", isGlobal = true, predictionDays = 14, isEnsemble = true),
        WeatherModel("ecmwf_aifs025_ensemble", settingNameRaw = "ECMWF AIFS 0.25° Ensemble", sourceName = "ECMWF AIFS Ensemble", isGlobal = true, predictionDays = 14, isEnsemble = true),
        WeatherModel("ncep_gefs_seamless", settingNameRaw = "GFS Ensemble Seamless", sourceName = "NCEP GFS Ensemble", isGlobal = true, predictionDays = 34, isEnsemble = true),
        WeatherModel("icon_seamless_eps", settingNameRaw = "ICON Ensemble Seamless", sourceName = "DWD ICON Ensemble", isGlobal = true, predictionDays = 6, isEnsemble = true),
        WeatherModel("gem_global_ensemble", settingNameRaw = "GEM Ensemble Seamless", sourceName = "GEM Ensemble", isGlobal = true, predictionDays = 34, isEnsemble = true),
        WeatherModel("ukmo_global_ensemble_20km", settingNameRaw = "UKMO Ensemble Seamless", sourceName = "UKMO Ensemble", isGlobal = true, predictionDays = 7, isEnsemble = true),
    )

    fun getModel(apiName: String, isEnsemble: Boolean = false) = 
        models.find { it.apiName == apiName && it.isEnsemble == isEnsemble } ?: models[0]
    
    fun getAvailableModels(lat: Double, lon: Double, isEnsemble: Boolean) = 
        models.filter { it.isAvailableAt(lat, lon) && it.isEnsemble == isEnsemble }
}
