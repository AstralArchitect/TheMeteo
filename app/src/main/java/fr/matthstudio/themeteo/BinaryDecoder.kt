package fr.matthstudio.themeteo

import android.util.Log
import com.google.protobuf.ByteString
import fr.matthstudio.themeteo.AppStateManager
import fr.matthstudio.themeteo.CDNMeteoMetadata
import fr.matthstudio.themeteo.data.DailySynopticForecastUI
import fr.matthstudio.themeteo.data.ExtendedForecastUI
import fr.matthstudio.themeteo.data.LocalDateTimeSerializer
import fr.matthstudio.themeteo.data.MediumTermStepUI
import fr.matthstudio.themeteo.data.SynopticScenarioUI
import fr.matthstudio.themeteo.data.SynopticStepUI
import fr.matthstudio.themeteo.proto.LongTermForecast
import fr.matthstudio.themeteo.proto.ExtendedForecastPointPayload
import fr.matthstudio.themeteo.proto.LongTermScenario
import fr.matthstudio.themeteo.proto.MediumTermStep
import fr.matthstudio.themeteo.proto.ShortTermPointPayload
import fr.matthstudio.themeteo.proto.ShortTermStep
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable

class ProtobufVersionIncompatibleException(val targetVersion: Int) : Exception("Protobuf payload version $targetVersion is incompatible with this client app version.")

/**
 * Représente un pas horaire décodé et prêt pour l'affichage UI.
 */
@Serializable
data class ShortTermStepUI(
    @Serializable(with = LocalDateTimeSerializer::class)
    val time: LocalDateTime,
    val wmoCode: Int,
    val temperature: Float,
    val apparentTemperature: Float,
    val pressure: Float,
    val windSpeed: Float,
    val windDirection: Float,
    val windGust: Float,
    val precipitation: Float,
    val snowfall: Float,
    val maxPossiblePrecipitation: Float,
    val precipitationProbability: Int?,
    val thunderstormProbability: Int?,
    val humidity: Int?,
    val cloudCoverLow: Int?,
    val cloudCoverMid: Int?,
    val cloudCoverHigh: Int?,
    val cloudCoverTotal: Int?
)

/**
 * Conteneur pour les prévisions court terme (48h).
 */
@Serializable
data class ShortTermForecastUI(
    val latitude: Float,
    val longitude: Float,
    @Serializable(with = LocalDateTimeSerializer::class)
    val runHour: LocalDateTime,
    val steps: List<ShortTermStepUI>
)

/**
 * Utilitaire technique pour décoder les types de données compactés (Float16, Int8, UInt8)
 * provenant des payloads Protobuf du serveur.
 */
object BinaryDecoder {

    /**
     * Décode un Float16 (IEEE 754 half-precision, Little-Endian) en Float32.
     */
    fun decodeFloat16(bytes: ByteString, floatIndex: Int): Float {
        val offset = floatIndex * 2
        if (offset < 0 || offset + 1 >= bytes.size()) return Float.NaN

        val b1 = bytes.byteAt(offset).toInt() and 0xFF
        val b2 = bytes.byteAt(offset + 1).toInt() and 0xFF

        // Reconstruction en Little-Endian (16 bits)
        val h = (b2 shl 8) or b1

        val s = (h and 0x8000) shl 16             // Signe décalé en Float32 (bit 31)
        val e = (h and 0x7C00) shr 10             // Exposant
        val m = h and 0x03FF                      // Mantisse

        return when (e) {
            0 -> {
                if (m == 0) {
                    Float.fromBits(s)
                } else {
                    val res = Math.scalb(m.toFloat(), -24)
                    if (s != 0) -res else res
                }
            }
            31 -> {
                if (m == 0) {
                    if (s != 0) Float.NEGATIVE_INFINITY else Float.POSITIVE_INFINITY
                } else {
                    Float.NaN
                }
            }
            else -> {
                val resExp = (e - 15 + 127) shl 23
                val resMant = m shl 13
                Float.fromBits(s or resExp or resMant)
            }
        }
    }

    /**
     * Décode un Int8 signé en Int (-128 à 127).
     */
    fun decodeInt8(bytes: ByteString, byteIndex: Int): Int {
        if (byteIndex < 0 || byteIndex >= bytes.size()) return 0
        return bytes.byteAt(byteIndex).toInt()
    }

    /**
     * Décode un UInt8 non signé en Int (0 à 255).
     */
    fun decodeUint8(bytes: ByteString, byteIndex: Int): Int {
        if (byteIndex < 0 || byteIndex >= bytes.size()) return 255
        return bytes.byteAt(byteIndex).toInt() and 0xFF
    }

    /**
     * Décode un ShortTermStep compacté selon le protocole quantisé :
     * - packedFloatsF16 (5 floats : vent, direction, rafales, pluie, neige)
     * - packedBytesI8 (11 octets : wmo, temp, appTemp, press, 7 pourcentages)
     */
    fun parseShortTermStep(
        step: ShortTermStep,
        zeroZeroZulu: LocalDateTime,
        isArome: Boolean
    ): ShortTermStepUI {
        val time = zeroZeroZulu.plusHours(step.hourOffsetFrom00Z.toLong())
        val bytes = step.packedBytesI8
        val floats = step.packedFloatsF16

        // 1. WMO (Octet 0 : uint8, 255 = non disponible -> -1)
        val wmoRaw = decodeUint8(bytes, 0)
        val wmoCode = if (wmoRaw == 255) -1 else wmoRaw

        // 2. Températures (Octets 1 et 2 : int8 signé avec pas 0.5°C pour AROME, 1.0°C pour IFS)
        val tempDivisor = if (isArome) 2.0f else 1.0f
        val temperature = decodeInt8(bytes, 1).toFloat() / tempDivisor
        val apparentTemperature = decodeInt8(bytes, 2).toFloat() / tempDivisor

        // 3. Pression MSL (Octet 3 : uint8 non signé, offset de 850 hPa)
        val pressure = 850.0f + decodeUint8(bytes, 3).toFloat()

        // 4. Variables Float16 (6 variables compactées)
        val windSpeed = decodeFloat16(floats, 0)
        val windDirection = decodeFloat16(floats, 1)
        val windGust = decodeFloat16(floats, 2)
        val precipitation = decodeFloat16(floats, 3)
        val snowfall = decodeFloat16(floats, 4)
        var maxPossiblePrecipitation = decodeFloat16(floats, 5).let { if (it.isNaN()) null else it }
        if (((maxPossiblePrecipitation ?: precipitation) - precipitation) < 5) {
            maxPossiblePrecipitation = precipitation
        }

        // 5. Pourcentages (Octets 4 à 10 : int8 signé, -1 si NaN / indisponible)
        fun decodePct(idx: Int): Int? {
            val v = decodeInt8(bytes, idx)
            return if (v < 0) null else v
        }

        return ShortTermStepUI(
            time = time,
            wmoCode = wmoCode,
            temperature = temperature,
            apparentTemperature = apparentTemperature,
            pressure = pressure,
            windSpeed = windSpeed,
            windDirection = windDirection,
            windGust = windGust,
            precipitation = precipitation,
            snowfall = snowfall,
            maxPossiblePrecipitation = maxPossiblePrecipitation ?: precipitation,
            precipitationProbability = decodePct(4),
            thunderstormProbability = decodePct(5),
            humidity = decodePct(6),
            cloudCoverLow = decodePct(7),
            cloudCoverMid = decodePct(8),
            cloudCoverHigh = decodePct(9),
            cloudCoverTotal = decodePct(10)
        )
    }

    /**
     * Décode un MediumTermStep compacté selon le protocole quantisé :
     * - packedFloatsF16 (9 floats = 18 octets : vent P10/P50/P90, direction, rafales P10/P50/P90, pluie, neige)
     * - packedBytesI8 (16 octets : wmo, 6 températures, 3 pressions, 6 pourcentages)
     */
    fun parseMediumTermStep(step: MediumTermStep, zeroZeroZulu: LocalDateTime): MediumTermStepUI {
        val time = zeroZeroZulu.plusHours(step.hourOffsetFrom00Z.toLong())
        val floats = step.packedFloatsF16
        val bytes = step.packedBytesI8

        // 1. WMO (Octet 0 : uint8, 255 = non disponible -> -1)
        val wmoRaw = decodeUint8(bytes, 0)
        val wmoCode = if (wmoRaw == 255) -1 else wmoRaw

        // 2. Températures (Octets 1 à 6 : int8 signé, pas de 1.0°C)
        val tempP10 = decodeInt8(bytes, 1).toFloat()
        val tempP50 = decodeInt8(bytes, 2).toFloat()
        val tempP90 = decodeInt8(bytes, 3).toFloat()
        val apparentTempP10 = decodeInt8(bytes, 4).toFloat()
        val apparentTempP50 = decodeInt8(bytes, 5).toFloat()
        val apparentTempP90 = decodeInt8(bytes, 6).toFloat()

        // 3. Pressions MSL (Octets 7 à 9 : uint8 non signé, offset 850 hPa)
        val pressureP10 = 850.0f + decodeUint8(bytes, 7).toFloat()
        val pressureP50 = 850.0f + decodeUint8(bytes, 8).toFloat()
        val pressureP90 = 850.0f + decodeUint8(bytes, 9).toFloat()

        // 4. Variables Float16 (9 variables)
        val windSpeedP10 = decodeFloat16(floats, 0)
        val windSpeedP50 = decodeFloat16(floats, 1)
        val windSpeedP90 = decodeFloat16(floats, 2)
        val windDirection = decodeFloat16(floats, 3)
        val windGustP10 = decodeFloat16(floats, 4)
        val windGustP50 = decodeFloat16(floats, 5)
        val windGustP90 = decodeFloat16(floats, 6)
        val totalPrecipitation = decodeFloat16(floats, 7)
        val snowPrecipitation = decodeFloat16(floats, 8)

        // 5. Pourcentages (Octets 10 à 17 : int8)
        val precipitationProbability = decodeInt8(bytes, 10)
        val instabilityProbability = decodeInt8(bytes, 11)
        val humidityP10 = decodeInt8(bytes, 12)
        val humidityP50 = decodeInt8(bytes, 13)
        val humidityP90 = decodeInt8(bytes, 14)
        val cloudCoverTotal = decodeInt8(bytes, 15)
        val cloudCoverP10 = if (bytes.size() >= 18) decodeInt8(bytes, 16) else cloudCoverTotal
        val cloudCoverP90 = if (bytes.size() >= 18) decodeInt8(bytes, 17) else cloudCoverTotal

        return MediumTermStepUI(
            time = time,
            wmoCode = wmoCode,
            tempP10 = tempP10,
            tempP50 = tempP50,
            tempP90 = tempP90,
            apparentTempP10 = apparentTempP10,
            apparentTempP50 = apparentTempP50,
            apparentTempP90 = apparentTempP90,
            pressureP10 = pressureP10,
            pressureP50 = pressureP50,
            pressureP90 = pressureP90,
            windSpeedP10 = windSpeedP10,
            windSpeedP50 = windSpeedP50,
            windSpeedP90 = windSpeedP90,
            windDirection = windDirection,
            windGustP10 = windGustP10,
            windGustP50 = windGustP50,
            windGustP90 = windGustP90,
            totalPrecipitation = totalPrecipitation,
            snowPrecipitation = snowPrecipitation,
            precipitationProbability = precipitationProbability,
            instabilityProbability = instabilityProbability,
            humidityP10 = humidityP10,
            humidityP50 = humidityP50,
            humidityP90 = humidityP90,
            cloudCoverTotal = cloudCoverTotal,
            cloudCoverP10 = cloudCoverP10,
            cloudCoverP90 = cloudCoverP90
        )
    }

    fun decodeScenarioV1(
        senario: LongTermScenario,
        zeroZeroZulu: LocalDateTime,
        dayNumber: Int,
        long: Float
    ): SynopticScenarioUI? {
        if (!senario.isValid) return null
        val floats = senario.packedFloatsF16
        val bytes = senario.packedBytesI8
        val nSteps = 7
        val offsets = if (long >= 0.0) {
            intArrayOf(-18, -12, -6, 0, 6, 12, 18)
        } else {
            intArrayOf(-6, 0, 6, 12, 18, 24, 30)
        }
        val localOffsetHours = Math.round(long / 15.0f).toInt().coerceIn(-12, 14)

        val rawSteps = (0 until nSteps).map { i ->
            val utcTime = zeroZeroZulu.plusHours(dayNumber * 24L + offsets[i])
            val stepTime = utcTime.plusHours(localOffsetHours.toLong())

            val wmoRaw = decodeUint8(bytes, 0 * nSteps + i)
            val stepWmo = if (wmoRaw == 255) -1 else wmoRaw

            SynopticStepUI(
                time = stepTime,
                wmoCode = stepWmo,
                tempP10 = decodeInt8(bytes, 1 * nSteps + i).toFloat(),
                tempP50 = decodeInt8(bytes, 2 * nSteps + i).toFloat(),
                tempP90 = decodeInt8(bytes, 3 * nSteps + i).toFloat(),
                apparentTempP10 = decodeInt8(bytes, 4 * nSteps + i).toFloat(),
                apparentTempP50 = decodeInt8(bytes, 5 * nSteps + i).toFloat(),
                apparentTempP90 = decodeInt8(bytes, 6 * nSteps + i).toFloat(),
                pressureP10 = 850.0f + decodeUint8(bytes, 7 * nSteps + i).toFloat(),
                pressureP50 = 850.0f + decodeUint8(bytes, 8 * nSteps + i).toFloat(),
                pressureP90 = 850.0f + decodeUint8(bytes, 9 * nSteps + i).toFloat(),
                windSpeedP10 = decodeFloat16(floats, 0 * nSteps + i),
                windSpeedP50 = decodeFloat16(floats, 1 * nSteps + i),
                windSpeedP90 = decodeFloat16(floats, 2 * nSteps + i),
                windDirection = decodeFloat16(floats, 3 * nSteps + i),
                windGustP10 = decodeFloat16(floats, 4 * nSteps + i),
                windGustP50 = decodeFloat16(floats, 5 * nSteps + i),
                windGustP90 = decodeFloat16(floats, 6 * nSteps + i),
                precipitation = decodeFloat16(floats, 7 * nSteps + i),
                visibility = decodeFloat16(floats, 8 * nSteps + i),
                humidityP10 = decodeInt8(bytes, 10 * nSteps + i),
                humidityP50 = decodeInt8(bytes, 11 * nSteps + i),
                humidityP90 = decodeInt8(bytes, 12 * nSteps + i),
                precipitationProbability = decodeInt8(bytes, 13 * nSteps + i),
                cloudCoverTotal = decodeInt8(bytes, 14 * nSteps + i)
            )
        }

        val dayUtcMidnight = zeroZeroZulu.plusHours((dayNumber * 24).toLong())
        val localDate = dayUtcMidnight.plusHours(localOffsetHours.toLong()).toLocalDate()
        val dayFiltered = rawSteps.filter { it.time.toLocalDate() == localDate }
        val steps = if (dayFiltered.size == 4) dayFiltered else rawSteps.take(4)

        val maxWmo = steps.maxOfOrNull { it.wmoCode } ?: 0
        val scenarioWmo = if (maxWmo > 4) maxWmo else steps.groupingBy { it.wmoCode }.eachCount().maxByOrNull { it.value }?.key ?: maxWmo

        return SynopticScenarioUI(probability = senario.probability, wmoCode = scenarioWmo, steps = steps)
    }

    fun decodeScenarioV2(
        senario: LongTermScenario,
        zeroZeroZulu: LocalDateTime,
        dayNumber: Int,
        long: Float
    ): SynopticScenarioUI? {
        if (!senario.isValid) return null
        val floats = senario.packedFloatsF16
        val bytes = senario.packedBytesI8
        val nSteps = 4
        val shift = Math.round(long / 90.0f).coerceIn(-2, 2)
        val offsets = intArrayOf(
            0 - 6 * shift,
            6 - 6 * shift,
            12 - 6 * shift,
            18 - 6 * shift
        )
        val localOffsetHours = Math.round(long / 15.0f).toInt().coerceIn(-12, 14)

        val steps = (0 until nSteps).map { i ->
            val utcTime = zeroZeroZulu.plusHours(dayNumber * 24L + offsets[i])
            val stepTime = utcTime.plusHours(localOffsetHours.toLong())

            val wmoRaw = decodeUint8(bytes, 0 * nSteps + i)
            val stepWmo = if (wmoRaw == 255) -1 else wmoRaw

            SynopticStepUI(
                time = stepTime,
                wmoCode = stepWmo,
                tempP10 = decodeInt8(bytes, 1 * nSteps + i).toFloat(),
                tempP50 = decodeInt8(bytes, 2 * nSteps + i).toFloat(),
                tempP90 = decodeInt8(bytes, 3 * nSteps + i).toFloat(),
                apparentTempP10 = decodeInt8(bytes, 4 * nSteps + i).toFloat(),
                apparentTempP50 = decodeInt8(bytes, 5 * nSteps + i).toFloat(),
                apparentTempP90 = decodeInt8(bytes, 6 * nSteps + i).toFloat(),
                pressureP10 = 850.0f + decodeUint8(bytes, 7 * nSteps + i).toFloat(),
                pressureP50 = 850.0f + decodeUint8(bytes, 8 * nSteps + i).toFloat(),
                pressureP90 = 850.0f + decodeUint8(bytes, 9 * nSteps + i).toFloat(),
                windSpeedP10 = decodeFloat16(floats, 0 * nSteps + i),
                windSpeedP50 = decodeFloat16(floats, 1 * nSteps + i),
                windSpeedP90 = decodeFloat16(floats, 2 * nSteps + i),
                windDirection = decodeFloat16(floats, 3 * nSteps + i),
                windGustP10 = decodeFloat16(floats, 4 * nSteps + i),
                windGustP50 = decodeFloat16(floats, 5 * nSteps + i),
                windGustP90 = decodeFloat16(floats, 6 * nSteps + i),
                precipitation = decodeFloat16(floats, 7 * nSteps + i),
                visibility = decodeFloat16(floats, 8 * nSteps + i),
                humidityP10 = decodeInt8(bytes, 10 * nSteps + i),
                humidityP50 = decodeInt8(bytes, 11 * nSteps + i),
                humidityP90 = decodeInt8(bytes, 12 * nSteps + i),
                precipitationProbability = decodeInt8(bytes, 13 * nSteps + i),
                cloudCoverTotal = decodeInt8(bytes, 14 * nSteps + i)
            )
        }

        val maxWmo = steps.maxOfOrNull { it.wmoCode } ?: 0
        val scenarioWmo = if (maxWmo > 4) maxWmo else steps.groupingBy { it.wmoCode }.eachCount().maxByOrNull { it.value }?.key ?: maxWmo

        return SynopticScenarioUI(probability = senario.probability, wmoCode = scenarioWmo, steps = steps)
    }

    /**
     * Bibliothèque de fonctions de décodage selon la version du schéma Protobuf.
     * Sélectionne automatiquement le décodeur approprié (v1, v2, etc.).
     */
    fun decodeSenario(
        senario: LongTermScenario,
        zeroZeroZulu: LocalDateTime,
        dayNumber: Int,
        long: Float,
        version: Int = 2
    ): SynopticScenarioUI? {
        val detectedVersion = when {
            version >= 2 || senario.packedFloatsF16.size() == 72 || senario.packedBytesI8.size() == 60 -> 2
            version == 1 || senario.packedFloatsF16.size() == 126 || senario.packedBytesI8.size() == 105 -> 1
            else -> version
        }

        return when (detectedVersion) {
            1 -> decodeScenarioV1(senario, zeroZeroZulu, dayNumber, long)
            2 -> decodeScenarioV2(senario, zeroZeroZulu, dayNumber, long)
            else -> {
                Log.e("BinaryDecoder", "Incompatible Protobuf version: $detectedVersion")
                AppStateManager.triggerInAppUpdate()
                throw ProtobufVersionIncompatibleException(detectedVersion)
            }
        }
    }

    fun parseLongTermDay(
        day: LongTermForecast,
        zeroZeroZulu: LocalDateTime,
        lon: Float,
        version: Int = 2
    ): DailySynopticForecastUI? {
        val senarios = mutableListOf<SynopticScenarioUI>()
        for (i in day.scenariosList) {
            val decoded = decodeSenario(i, zeroZeroZulu, day.dayNumber, lon, version) ?: continue
            senarios.add(decoded)
        }

        if (senarios.isEmpty()) return null

        val localOffsetHours = Math.round(lon / 15.0f).toInt().coerceIn(-12, 14)
        val dayDate = senarios.firstOrNull()?.steps?.getOrNull(1)?.time?.toLocalDate()
            ?: senarios.firstOrNull()?.steps?.firstOrNull()?.time?.toLocalDate()
            ?: zeroZeroZulu.plusHours((day.dayNumber * 24).toLong()).plusHours(localOffsetHours.toLong()).toLocalDate()

        return DailySynopticForecastUI(
            dayNumber = day.dayNumber,
            date = dayDate,
            scenarios = senarios
        )
    }

    fun parseFullShortTerm(payload: ShortTermPointPayload, runUTC: LocalDateTime): ShortTermForecastUI {
        val midnightUTC = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(payload.reference00ZTimestampUtc),
            ZoneId.of("UTC")
        )

        // Détection automatique du pas de température (AROME = 0.5°C, IFS/autres = 1.0°C)
        val isArome = payload.modelName.equals("AROME", ignoreCase = true)
        val allSteps = payload.stepsList.map { parseShortTermStep(it, midnightUTC, isArome) }

        val zoneId = ZoneId.systemDefault()
        val midnightLocal = LocalDateTime.now(zoneId).toLocalDate().atStartOfDay()
        val endWindowLocal = midnightLocal.plusHours(48)

        val filteredSteps = allSteps.filter { step ->
            val stepLocal = step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDateTime()
            !stepLocal.isBefore(midnightLocal) && stepLocal.isBefore(endWindowLocal)
        }

        return ShortTermForecastUI(
            latitude = payload.latitude,
            longitude = payload.longitude,
            runHour = runUTC,
            steps = filteredSteps
        )
    }

    fun parseFullExtendedTerm(
        payload: ExtendedForecastPointPayload,
        runUTC: LocalDateTime,
        metadata: CDNMeteoMetadata? = null,
        version: Int = 2
    ): ExtendedForecastUI {
        val ver = metadata?.nSteps ?: version
        val midnightUTC = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(payload.reference00ZTimestampUtc),
            ZoneId.of("UTC")
        )

        val medSteps = payload.mediumTermStepsList.map { parseMediumTermStep(it, midnightUTC) }
        val zoneId = ZoneId.systemDefault()
        val midnightLocal = LocalDateTime.now(zoneId).toLocalDate().atStartOfDay()
        val startWindowLocal = midnightLocal.plusHours(48)

        val filteredMedSteps = medSteps.filter { step ->
            val stepLocal = step.time.atZone(ZoneId.of("UTC")).withZoneSameInstant(zoneId).toLocalDateTime()
            !stepLocal.isBefore(startWindowLocal)
        }

        val longTermDays = mutableListOf<DailySynopticForecastUI>()
        for (i in payload.longTermDaysList) {
            val parsedDay = parseLongTermDay(i, midnightUTC, payload.longitude, ver) ?: continue
            longTermDays.add(parsedDay)
        }

        return ExtendedForecastUI(
            latitude = payload.latitude,
            longitude = payload.longitude,
            runHour = runUTC,
            mediumTermSteps = filteredMedSteps,
            longTermDays = longTermDays
        )
    }
}