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
    SUN_DETAILS,
    DAILY_FORECAST,
    AIR_QUALITY,
    POLLEN,
    ADDITIONAL_INFOS
}
