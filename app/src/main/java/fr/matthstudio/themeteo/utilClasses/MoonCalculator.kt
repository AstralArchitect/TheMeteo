package fr.matthstudio.themeteo.utilClasses

import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.*

// --- ENUMS ---

enum class PhaseType {
    NEW_MOON,
    WAXING_CRESCENT,
    FIRST_QUARTER,
    WAXING_GIBBOUS,
    FULL_MOON,
    WANING_GIBBOUS,
    LAST_QUARTER,
    WANING_CRESCENT
}

// --- DATA CLASSES ---

data class MoonPhase(
    val ageDays: Double,
    val fractionIlluminated: Double,
    val phaseType: PhaseType
)

data class MoonPosition(
    val azimuth: Double,
    val elevation: Double,
    val distanceKm: Double
)

data class DailyMoonEvents(
    val date: LocalDate,
    val moonrise: LocalDateTime?,
    val moonriseAzimuth: Double?,
    val moonset: LocalDateTime?,
    val moonsetAzimuth: Double?,
    val phase: MoonPhase
)

data class MoonData(
    val dailyEvents: DailyMoonEvents,
    val currentPosition: MoonPosition
)

// --- CALCULATOR ---

class MoonCalculator(val lat: Double, val lon: Double, val zoneId: ZoneId = ZoneId.systemDefault()) {

    private val radToDeg = 180.0 / PI
    private val degToRad = PI / 180.0

    private fun getDaysSinceJ2000(dateTime: LocalDateTime): Double {
        val epoch = LocalDateTime.of(2000, 1, 1, 12, 0, 0)
        val zdt = dateTime.atZone(zoneId).withZoneSameInstant(ZoneOffset.UTC)
        val epochZdt = epoch.atZone(ZoneOffset.UTC)
        // Utilisation de la différence en millisecondes pour plus de précision temporelle
        val millis = ChronoUnit.MILLIS.between(epochZdt, zdt)
        return millis / 86400000.0
    }

    private fun getMoonCoords(d: Double): Triple<Double, Double, Double> {
        val L = (218.316 + 13.176396 * d) * degToRad
        val M = (134.963 + 13.064993 * d) * degToRad
        val F = (93.272 + 13.229350 * d) * degToRad

        val l = L + (6.289 * sin(M) * degToRad)
        val b = 5.128 * sin(F) * degToRad
        val dt = 385001.0 - 20905.0 * cos(M)

        val ra = atan2(sin(l) * cos(23.44 * degToRad) - tan(b) * sin(23.44 * degToRad), cos(l))
        val dec = asin(sin(b) * cos(23.44 * degToRad) + cos(b) * sin(23.44 * degToRad) * sin(l))

        return Triple(ra, dec, dt)
    }

    fun getMoonPosition(dateTime: LocalDateTime): MoonPosition {
        val d = getDaysSinceJ2000(dateTime)
        val (ra, dec, distance) = getMoonCoords(d)

        val h = 18.697374558 + 24.06570982441908 * d
        val lst = (h * 15.0 + lon) * degToRad
        val ha = lst - ra

        var hRad = asin((sin(lat * degToRad) * sin(dec) + cos(lat * degToRad) * cos(dec) * cos(ha)).coerceIn(-1.0, 1.0))
        val azRad = atan2(sin(ha), cos(ha) * sin(lat * degToRad) - tan(dec) * cos(lat * degToRad))

        hRad -= asin(6371.0 / distance) * cos(hRad) 

        var azDeg = azRad * radToDeg + 180.0
        if (azDeg >= 360.0) azDeg -= 360.0

        return MoonPosition(azDeg, hRad * radToDeg, distance)
    }

    fun getMoonPhase(date: LocalDate = LocalDate.now(zoneId)): MoonPhase {
        // On calcule la phase lunaire pour le milieu de la journée
        val dateTime = date.atTime(12, 0)
        val d = getDaysSinceJ2000(dateTime)
        val (ra, dec, _) = getMoonCoords(d)
        
        val M = (356.0470 + 0.9856002585 * d) * degToRad
        val L = (280.460 + 0.9856474 * d + 1.915 * sin(M) + 0.020 * sin(2 * M)) * degToRad
        
        val phi = acos((sin(L) * sin(ra) + cos(L) * cos(ra) * cos(dec)).coerceIn(-1.0, 1.0))
        val fraction = (1 + cos(phi)) / 2

        val synodicMonth = 29.53058867
        var age = ((L - ra) * radToDeg / 360.0) * synodicMonth
        if (age < 0) age += synodicMonth
        
        // Normalisation de l'âge de 0 à 29.53
        age = age % synodicMonth

        val type = when {
            age < 1.84 -> PhaseType.NEW_MOON
            age < 5.53 -> PhaseType.WAXING_CRESCENT
            age < 9.22 -> PhaseType.FIRST_QUARTER
            age < 12.91 -> PhaseType.WAXING_GIBBOUS
            age < 16.61 -> PhaseType.FULL_MOON
            age < 20.30 -> PhaseType.WANING_GIBBOUS
            age < 23.99 -> PhaseType.LAST_QUARTER
            age < 27.68 -> PhaseType.WANING_CRESCENT
            else -> PhaseType.NEW_MOON
        }

        return MoonPhase(age, fraction, type)
    }

    /**
     * Calcule les événements journaliers (lever, coucher, phase).
     * Cette fonction est lourde (1440 itérations) et ne doit être appelée qu'une fois par jour/lieu.
     */
    fun getDailyEvents(date: LocalDate = LocalDate.now(zoneId)): DailyMoonEvents {
        val startOfDay = date.atStartOfDay()
        
        var moonrise: LocalDateTime? = null
        var moonset: LocalDateTime? = null
        var riseAz: Double? = null
        var setAz: Double? = null

        var previousElevation = getMoonPosition(startOfDay).elevation
        val horizonOffset = 0.125 

        for (minutes in 1..1440) {
            val time = startOfDay.plusMinutes(minutes.toLong())
            val pos = getMoonPosition(time)

            if (previousElevation <= horizonOffset && pos.elevation > horizonOffset) {
                moonrise = time
                riseAz = pos.azimuth
            }
            if (previousElevation >= horizonOffset && pos.elevation < horizonOffset) {
                moonset = time
                setAz = pos.azimuth
            }
            previousElevation = pos.elevation
        }

        return DailyMoonEvents(
            date = date,
            moonrise = moonrise,
            moonriseAzimuth = riseAz,
            moonset = moonset,
            moonsetAzimuth = setAz,
            phase = getMoonPhase(date)
        )
    }
}