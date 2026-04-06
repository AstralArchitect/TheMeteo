package fr.matthstudio.themeteo.utilClasses

import java.time.*
import kotlin.math.*

data class SunPosition(
    val azimuth: Double,
    val elevation: Double
)

data class DailySunData(
    val date: LocalDate,
    val sunrise: LocalDateTime,
    val sunriseAzimuth: Double,
    val sunset: LocalDateTime,
    val sunsetAzimuth: Double,
    val zenithElevation: Double,
    val dayLength: Duration,
    val goldenHourMorning: Pair<LocalDateTime, LocalDateTime>,
    val goldenHourEvening: Pair<LocalDateTime, LocalDateTime>
)

data class FullSunData(
    val dailyData: List<DailySunData>,
    val currentPosition: SunPosition
)

class FullSunCalculator(val lat: Double, val lon: Double, val zoneId: ZoneId = ZoneId.systemDefault()) {

    private val degToRad = PI / 180.0
    private val radToDeg = 180.0 / PI

    fun getCompleteSunData(referenceDate: LocalDate = LocalDate.now()): FullSunData {
        val days = listOf(
            referenceDate.minusDays(1),
            referenceDate,
            referenceDate.plusDays(1)
        )

        val dailyDataList = days.map { calculateDailyData(it) }

        // Pour la position actuelle, on utilise la déclinaison et le midi solaire du jour de référence (aujourd'hui)
        val today = referenceDate
        val dayOfYear = today.dayOfYear
        val declination = 23.45 * sin(degToRad * (360.0 / 365.0 * (dayOfYear + 284)))
        val b = degToRad * (360.0 / 364.0 * (dayOfYear - 81))
        val eqTime = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)
        val zonedDateTime = today.atStartOfDay(zoneId)
        val utcOffset = zonedDateTime.offset.totalSeconds / 3600.0
        val solarNoonDecimal = 12.0 - (lon - (utcOffset * 15.0)) / 15.0 - (eqTime / 60.0)

        return FullSunData(
            dailyData = dailyDataList,
            currentPosition = calculateCurrentPosition(LocalDateTime.now(zoneId), declination, solarNoonDecimal)
        )
    }

    private fun calculateDailyData(date: LocalDate): DailySunData {
        val dayOfYear = date.dayOfYear
        val declination = 23.45 * sin(degToRad * (360.0 / 365.0 * (dayOfYear + 284)))
        
        val b = degToRad * (360.0 / 364.0 * (dayOfYear - 81))
        val eqTime = 9.87 * sin(2 * b) - 7.53 * cos(b) - 1.5 * sin(b)

        val zonedDateTime = date.atStartOfDay(zoneId)
        val utcOffset = zonedDateTime.offset.totalSeconds / 3600.0
        val solarNoonDecimal = 12.0 - (lon - (utcOffset * 15.0)) / 15.0 - (eqTime / 60.0)

        val hSunrise = calculateHourAngle(-0.83, declination)
        val hGoldenHour = calculateHourAngle(6.0, declination)
        val hGoldenHourEnd = calculateHourAngle(-4.0, declination)

        fun adjustToWindow(decimalHour: Double): LocalDateTime {
            val totalSeconds = (decimalHour * 3600).toLong()
            val secondsInDay = (totalSeconds % 86400 + 86400) % 86400
            return date.atStartOfDay().plusSeconds(secondsInDay)
        }

        val sunrise = adjustToWindow(solarNoonDecimal - (hSunrise / 15.0))
        val sunset = adjustToWindow(solarNoonDecimal + (hSunrise / 15.0))
        
        val goldenStartMorn = adjustToWindow(solarNoonDecimal - (hGoldenHourEnd / 15.0))
        val goldenEndMorn = adjustToWindow(solarNoonDecimal - (hGoldenHour / 15.0))
        
        val goldenStartEve = adjustToWindow(solarNoonDecimal + (hGoldenHour / 15.0))
        val goldenEndEve = adjustToWindow(solarNoonDecimal + (hGoldenHourEnd / 15.0))

        val azRise = calculateAzimuth(declination, -0.83, true)
        val azSet = 360.0 - azRise

        val zenithPos = calculatePositionAtHour(solarNoonDecimal, declination)

        return DailySunData(
            date = date,
            sunrise = sunrise,
            sunriseAzimuth = azRise,
            sunset = sunset,
            sunsetAzimuth = azSet,
            zenithElevation = zenithPos.elevation,
            dayLength = Duration.ofMinutes(((hSunrise / 15.0) * 2.0 * 60.0).toLong()),
            goldenHourMorning = Pair(goldenStartMorn, goldenEndMorn),
            goldenHourEvening = Pair(goldenStartEve, goldenEndEve)
        )
    }

    private fun calculateHourAngle(targetElevation: Double, declination: Double): Double {
        val cosH = (sin(targetElevation * degToRad) - sin(lat * degToRad) * sin(declination * degToRad)) /
                   (cos(lat * degToRad) * cos(declination * degToRad))
        return if (cosH >= 1.0) 0.0 else if (cosH <= -1.0) 180.0 else acos(cosH) * radToDeg
    }

    private fun calculateAzimuth(declination: Double, elevation: Double, morning: Boolean): Double {
        val cosAz = (sin(declination * degToRad) - sin(elevation * degToRad) * sin(lat * degToRad)) /
                    (cos(elevation * degToRad) * cos(lat * degToRad))
        val az = acos(cosAz.coerceIn(-1.0, 1.0)) * radToDeg
        return if (morning) az else 360.0 - az
    }

    private fun calculateCurrentPosition(now: LocalDateTime, declination: Double, solarNoon: Double): SunPosition {
        val currentHourDecimal = now.hour + now.minute / 60.0 + now.second / 3600.0
        return calculatePositionAtHour(currentHourDecimal, declination, solarNoon)
    }

    private fun calculatePositionAtHour(hourDecimal: Double, declination: Double, solarNoon: Double? = null): SunPosition {
        // Si solarNoon est null, on calcule pour midi solaire (hourAngle = 0)
        val hourAngle = if (solarNoon != null) 15.0 * (hourDecimal - solarNoon) else 0.0
        
        val sinElevation = sin(lat * degToRad) * sin(declination * degToRad) +
                           cos(lat * degToRad) * cos(declination * degToRad) * cos(hourAngle * degToRad)
        val hRad = asin(sinElevation.coerceIn(-1.0, 1.0))
        
        val elevationDeg = hRad * radToDeg
        val azimuth = calculateAzimuth(declination, elevationDeg, hourAngle < 0)
        
        return SunPosition(azimuth, elevationDeg)
    }

    private fun decimalToDateTime(date: LocalDate, decimalHour: Double): LocalDateTime {
        // Gestion des bords (0-24h)
        val normalizedHour = (decimalHour % 24 + 24) % 24
        val totalSeconds = (normalizedHour * 3600).toLong()
        return date.atStartOfDay().plusSeconds(totalSeconds)
    }
}
