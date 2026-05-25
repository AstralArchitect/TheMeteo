/*
TheMeteo - A modern weather app.
Copyright (C) 2026  AstralArchitect
 */
package fr.matthstudio.themeteo.utilClasses

import fr.matthstudio.themeteo.data.TemperatureUnit
import fr.matthstudio.themeteo.data.WindUnit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Utilitaire pour la conversion et le formatage des unités.
 */
object UnitConverter {

    /**
     * Convertit une température de Celsius vers l'unité cible.
     */
    fun convertTemperature(celsius: Double, to: TemperatureUnit): Double {
        return when (to) {
            TemperatureUnit.CELSIUS -> celsius
            TemperatureUnit.FAHRENHEIT -> (celsius * 9 / 5) + 32
            TemperatureUnit.KELVIN -> celsius + 273.15
        }
    }

    /**
     * Retourne le symbole correspondant à l'unité de température.
     */
    fun getSymbol(unit: TemperatureUnit): String {
        return when (unit) {
            TemperatureUnit.CELSIUS -> "C"
            TemperatureUnit.FAHRENHEIT -> "F"
            TemperatureUnit.KELVIN -> "K"
        }
    }

    /**
     * Retourne le symbole correspondant à l'unité de température avec le ° si nécéssaire
     */
    fun getSymbolWithDegree(unit: TemperatureUnit): String {
        return when (unit) {
            TemperatureUnit.CELSIUS -> "°C"
            TemperatureUnit.FAHRENHEIT -> "°F"
            TemperatureUnit.KELVIN -> "K"
        }
    }

    /**
     * Formate une valeur numérique en chaîne de caractères lisible.
     */
    fun formatValue(value: Number): String {
        return if (value is Double || value is Float) {
            val df = DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.getDefault()))
            df.format(value.toDouble())
        } else {
            value.toString()
        }
    }

    /**
     * Convertit, arrondit éventuellement et formate une température avec son symbole.
     */
    fun formatTemperature(
        celsius: Double?,
        unit: TemperatureUnit,
        roundToInt: Boolean,
        showUnitSymbol: Boolean = true,
        showDegreeSymbol: Boolean = true
    ): String {
        if (celsius == null) return "--"
        
        val converted = convertTemperature(celsius, unit)
        val formatted = if (roundToInt) {
            converted.roundToInt().toString()
        } else {
            formatValue(converted)
        }

        val symbol = getSymbol(unit)

        return if (showDegreeSymbol && symbol != "K") {
            if (showUnitSymbol) {
                "$formatted°${getSymbol(unit)}"
            } else {
                "$formatted°"
            }
        } else {
            if (showUnitSymbol) {
                "$formatted${getSymbol(unit)}"
            } else {
                formatted
            }
        }
    }

    /**
     * Convertit une vitesse de vent de km/h vers l'unité cible.
     */
    fun convertWind(kph: Double, to: WindUnit): Double {
        return when (to) {
            WindUnit.KPH -> kph
            WindUnit.MPH -> kph * 0.621371
        }
    }

    /**
     * Formate une vitesse de vent avec son symbole.
     */
    fun formatWind(kph: Double?, unit: WindUnit): String {
        if (kph == null) return "--"
        val converted = convertWind(kph, unit)
        val formatted = formatValue(converted)
        val symbol = when (unit) {
            WindUnit.KPH -> "km/h"
            WindUnit.MPH -> "mph"
        }
        return "$formatted $symbol"
    }
}

/**
 * Extension pour simplifier l'appel depuis les types Number.
 */
fun Number.toSmartString(): String = UnitConverter.formatValue(this)
