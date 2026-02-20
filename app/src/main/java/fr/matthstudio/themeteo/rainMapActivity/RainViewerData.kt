package fr.matthstudio.themeteo.rainMapActivity

import kotlinx.serialization.Serializable

@Serializable
data class RainViewerResponse(
    val version: String,
    val generated: Long,
    val host: String,
    val radar: RadarData,
    val satellite: SatelliteData? = null
)

@Serializable
data class RadarData(
    val past: List<TimeFrame>,
    val nowcast: List<TimeFrame>
)

@Serializable
data class SatelliteData(
    val infrared: List<TimeFrame>
)

@Serializable
data class TimeFrame(
    val time: Long,
    val path: String
)
