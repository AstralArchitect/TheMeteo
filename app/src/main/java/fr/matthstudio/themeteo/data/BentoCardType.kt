/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.data

import kotlinx.serialization.Serializable

@Serializable
enum class BentoCardType {
    VIGILANCE,
    HOURLY_FORECAST,
    DAILY_FORECAST,
    AIR_QUALITY,
    POLLEN,
    SUN_DETAILS,
    RAIN_RADAR,
    RAIN_WITHIN_HOUR,
    ADDITIONAL_INFOS
}
