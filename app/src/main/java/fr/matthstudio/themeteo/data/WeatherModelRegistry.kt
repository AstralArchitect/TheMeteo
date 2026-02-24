package fr.matthstudio.themeteo.data

data class WeatherModel(
    val apiName: String,
    val settingName: String,
    val sourceName: String,
    val isGlobal: Boolean,
    val minLat: Double = -90.0,
    val maxLat: Double = 90.0,
    val minLon: Double = -180.0,
    val maxLon: Double = 180.0,
    val predictionDays: Int,
    val isEnsemble: Boolean = false
) {
    fun isAvailableAt(lat: Double, lon: Double): Boolean {
        if (isGlobal) return true
        return lat in minLat..maxLat && lon in minLon..maxLon
    }
}

object WeatherModelRegistry {
    val models = listOf(
        // --- Modèles Déterministes ---
        WeatherModel("best_match", "Meilleur modèle par défaut", "Open-Meteo", true, predictionDays = 15),
        WeatherModel("meteofrance_seamless", "Météo France Seamless", "Météo France", true, predictionDays = 3),
        WeatherModel("meteofrance_arpege_world", "Météo France ARPEGE World", "ARPEGE", true, predictionDays = 4),
        WeatherModel("meteofrance_arpege_europe", "Météo France ARPEGE Europe", "ARPEGE", false, 32.0, 70.0, -20.0, 40.0, 4),
        WeatherModel("meteofrance_arome_france", "Météo France AROME", "AROME", false, 38.0, 55.0, -8.0, 12.0, 2),
        WeatherModel("meteofrance_arome_france_hd", "Météo France AROME HD", "AROME HD", false, 38.0, 55.0, -8.0, 12.0, 2),
        WeatherModel("knmi_harmonie_arome_europe", "KNMI Harmonie Arome Europe", "Harmonie Arome", false, 45.0, 60.0, -10.0, 20.0, 2),
        WeatherModel("knmi_harmonie_arome_netherlands", "KNMI Harmonie Arome Netherlands", "Harmonie Arome", false, 50.0, 54.0, 3.0, 8.0, 2),
        WeatherModel("dmi_harmonie_arome_europe", "DMI Harmonie Arome Europe", "Harmonie Arome", false, 40.0, 75.0, -20.0, 50.0, 2),
        WeatherModel("ukmo_seamless", "UK Met Office Seamless", "UK Met Office", true, predictionDays = 5),
        WeatherModel("ukmo_global_deterministic_10km", "UK Met Office Global 10km", "UK Met Office", true, predictionDays = 5),
        WeatherModel("ukmo_uk_deterministic_2km", "UK Met Office UK 2km", "UK Met Office", false, 49.0, 61.0, -11.0, 2.0, 2),
        WeatherModel("meteoswiss_icon_seamless", "MeteoSwiss ICON Seamless", "MeteoSwiss", false, 45.0, 48.0, 5.0, 11.0, 3),
        WeatherModel("meteoswiss_icon_ch1", "MeteoSwiss ICON CH1", "MeteoSwiss", false, 45.0, 48.0, 5.0, 11.0, 3),
        WeatherModel("meteoswiss_icon_ch2", "MeteoSwiss ICON CH2", "MeteoSwiss", false, 45.0, 48.0, 5.0, 11.0, 3),
        WeatherModel("icon_seamless", "ICON Seamless", "Deutscher Wetterdienst", true, predictionDays = 7),
        WeatherModel("icon_d2", "DWD ICON D2", "Deutscher Wetterdienst", false, 44.0, 56.0, 2.0, 16.0, 2),
        WeatherModel("icon_eu", "DWD ICON EU", "Deutscher Wetterdienst", false, 34.0, 70.0, -25.0, 40.0, 3),
        WeatherModel("icon_global", "ICON Global", "Deutscher Wetterdienst", true, predictionDays = 7),
        WeatherModel("kma_gdps", "KMA GDPS", "KMA", true, predictionDays = 10),
        WeatherModel("kma_ldps", "KMA LDPS", "KMA", false, 32.0, 40.0, 124.0, 132.0, 3),
        WeatherModel("bom_access_global", "BOM ACCESS Global", "BOM", true, predictionDays = 10),
        WeatherModel("cma_grapes_global", "CMA GRAPES Global", "CMA", true, predictionDays = 10),
        WeatherModel("ecmwf_aifs025_single", "ECMWF AIFS 0.25° Single", "ECMWF AIFS", true, predictionDays = 14),
        WeatherModel("ecmwf_ifs025", "ECMWF IFS 0.25°", "ECMWF IFS", true, predictionDays = 14),
        WeatherModel("ecmwf_ifs", "ECMWF IFS HRES 9km", "ECMWF IFS", true, predictionDays = 14),
        WeatherModel("gfs_seamless", "NCEP GFS Seamless", "GFS", true, predictionDays = 15),
        WeatherModel("gfs_global", "NCEP GFS Global 0.11°/0.25°", "GFS", true, predictionDays = 15),
        WeatherModel("ncep_aigfs025", "NCEP AIGFS 0.25°", "AIGFS", true, predictionDays = 14),
        WeatherModel("ncep_hgefs025_ensemble_mean", "NCEP HGEFS 0.25° Ensemble Mean", "NCEP", true, predictionDays = 16),
        WeatherModel("gfs_graphcast025", "NCEP GFS GraphCast", "GFS", true, predictionDays = 15),
        WeatherModel("gem_seamless", "GEM Seamless", "GEM", true, predictionDays = 9),
        WeatherModel("gem_global", "GEM Global", "GEM", true, predictionDays = 9),
        WeatherModel("gem_regional", "GEM Regional", "GEM", false, 40.0, 60.0, -140.0, -50.0, 4),
        WeatherModel("jma_gms", "JMA GSM", "JMA", true, predictionDays = 10),
        WeatherModel("jma_msm", "JMA MSM", "JMA", false, 20.0, 50.0, 120.0, 150.0, 3),
        WeatherModel("jma_seamless", "JMA Seamless", "JMA", true, predictionDays = 10),

        // --- Modèles d'Ensemble ---
        WeatherModel("ecmwf_ifs025", "ECMWF IFS Ensemble 0.25°", "ECMWF IFS Ensemble", true, predictionDays = 14, isEnsemble = true),
        WeatherModel("ecmwf_aifs025_ensemble", "ECMWF AIFS 0.25° Ensemble", "ECMWF AIFS Ensemble", true, predictionDays = 14, isEnsemble = true),
        WeatherModel("ncep_gefs_seamless", "GFS Ensemble Seamless", "NCEP GFS Ensemble", true, predictionDays = 34, isEnsemble = true),
        WeatherModel("icon_seamless_eps", "ICON Ensemble Seamless", "DWD ICON Ensemble", true, predictionDays = 6, isEnsemble = true),
        WeatherModel("gem_global_ensemble", "GEM Ensemble Seamless", "GEM Ensemble", true, predictionDays = 34, isEnsemble = true),
        WeatherModel("ukmo_global_ensemble_20km", "UKMO Ensemble Seamless", "UKMO Ensemble", true, predictionDays = 7, isEnsemble = true),
    )

    fun getModel(apiName: String, isEnsemble: Boolean = false) = 
        models.find { it.apiName == apiName && it.isEnsemble == isEnsemble } ?: models[0]
    
    fun getAvailableModels(lat: Double, lon: Double, isEnsemble: Boolean) = 
        models.filter { it.isAvailableAt(lat, lon) && it.isEnsemble == isEnsemble }
}