/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.utilClasses

import kotlinx.serialization.Serializable

@Serializable
data class GovernmentInvertedGeocodingAPIResponse (
    val features: List<Feature>
)

@Serializable
data class Feature (
    val properties: Properties
)

@Serializable
data class Properties (
    val postcode: String
)