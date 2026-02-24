package fr.matthstudio.themeteo.utilClasses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VigilanceInfos(
    val departmentCode: String,
    val maxColorId: Int, // Niveau maximum global (le pire entre J et J1)
    val alerts: List<PhenomenonAlert>
)

@Serializable
data class PhenomenonAlert(
    val phenomenonId: String,
    val maxColorId: Int, // Niveau maximum pour ce phénomène précis sur les 2 jours
    val steps: List<AlertStep> // Chronologie complète (début/fin)
)

@Serializable
data class AlertStep(
    val beginTime: String,
    val endTime: String,
    val colorId: Int
)

@Serializable
data class VigilanceMapResponse(
    val product: MapProduct,
    val meta: MapMeta
)

@Serializable
data class MapMeta(
    @SerialName("snapshot_id") val snapshotId: String,
    @SerialName("product_datetime") val productDatetime: String,
    @SerialName("generation_timestamp") val generationTimestamp: String
)

@Serializable
data class MapProduct(
    @SerialName("warning_type") val warningType: String,
    @SerialName("update_time") val updateTime: String,
    @SerialName("domain_id") val domainId: String,
    @SerialName("global_max_color_id") val globalMaxColorId: String,
    val periods: List<VigilancePeriod>
)

@Serializable
data class VigilancePeriod(
    val echeance: String, // "J" ou "J1"
    @SerialName("begin_validity_time") val beginValidityTime: String,
    @SerialName("end_validity_time") val endValidityTime: String,
    @SerialName("text_items") val textItems: MapTextItems,
    val timelaps: Timelaps,
    @SerialName("max_count_items") val maxCountItems: List<ColorSummaryItem>,
    @SerialName("per_phenomenon_items") val perPhenomenonItems: List<PhenomenonSummaryItem>
)

@Serializable
data class MapTextItems(
    val title: String,
    val text: List<String>
)

@Serializable
data class Timelaps(
    @SerialName("domain_ids") val domainIds: List<DomainVigilance>
)

@Serializable
data class DomainVigilance(
    @SerialName("domain_id") val domainId: String, // Code département (ex: "75") ou "FRA"
    @SerialName("max_color_id") val maxColorId: Int,
    @SerialName("phenomenon_items") val phenomenonItems: List<PhenomenonItem>
)

@Serializable
data class PhenomenonItem(
    @SerialName("phenomenon_id") val phenomenonId: String,
    @SerialName("phenomenon_max_color_id") val phenomenonMaxColorId: Int,
    @SerialName("timelaps_items") val timelapsItems: List<TimelapsStep>
)

@Serializable
data class TimelapsStep(
    @SerialName("begin_time") val beginTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("color_id") val colorId: Int
)

@Serializable
data class ColorSummaryItem(
    @SerialName("color_id") val colorId: Int,
    @SerialName("color_name") val colorName: String,
    val count: Int,
    @SerialName("text_count") val textCount: String
)

@Serializable
data class PhenomenonSummaryItem(
    @SerialName("phenomenon_id") val phenomenonId: String,
    @SerialName("any_color_count") val anyColorCount: Int,
    @SerialName("phenomenon_counts") val phenomenonCounts: List<ColorSummaryItem>
)