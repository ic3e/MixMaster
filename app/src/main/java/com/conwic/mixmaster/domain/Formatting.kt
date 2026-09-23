package com.conwic.mixmaster.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Formats to at most [maxDecimals] places with trailing zeros trimmed: 84.0 -> "84",
 * 29.40 -> "29.4". Builds the string from integer maths so binary floating point can't leak
 * artefacts like "34.073800000000006" into the UI.
 */
fun formatDecimal(value: Double, maxDecimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    var factor = 1L
    repeat(maxDecimals) { factor *= 10L }
    val scaled = (value * factor).roundToLong()
    val sign = if (scaled < 0L) "-" else ""
    val magnitude = abs(scaled)
    val whole = magnitude / factor
    val fraction = (magnitude % factor).toString().padStart(maxDecimals, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

/** Material quantities are shown to the nearest gram — a finer figure is noise on site. */
fun formatKg(grams: Double): String = formatDecimal(grams / 1000.0, 3)

fun formatArea(squareMetres: Double): String = formatDecimal(squareMetres, 2)

/**
 * An amount together with the unit it reads best in.
 *
 * One calculator screen can hold 18 kg of resin and 0.14 g of pigment, and a single fixed unit
 * shows one of them as "0" — the pigment being the one you can't guess at. So anything under a
 * kilo is given in grams, and anything under a litre in millilitres.
 */
data class Quantity(val amount: String, val unit: String) {
    /** For lines that read as a sentence, rather than a figure with its unit set beside it. */
    val text: String get() = "$amount $unit"
}

/** Under a gram the figure is a pigment dose, where every decimal is still worth something. */
private fun smallDecimals(value: Double): Int = if (abs(value) < 1.0) 3 else 2

/** Rounds the way [formatDecimal] will, so the unit is picked from the figure actually shown. */
private fun roundedTo(value: Double, decimals: Int): Double {
    if (!value.isFinite()) return 0.0
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    return (value * factor).roundToLong() / factor
}

fun quantityFromGrams(grams: Double): Quantity {
    val decimals = smallDecimals(grams)
    return if (abs(roundedTo(grams, decimals)) < 1000.0) {
        Quantity(formatDecimal(grams, decimals), "g")
    } else {
        // Kilos stay at gram precision — that's what a site scale reads out.
        Quantity(formatKg(grams), "kg")
    }
}

fun quantityFromLitres(litres: Double): Quantity {
    val millilitres = litres * 1000.0
    val decimals = smallDecimals(millilitres)
    return if (abs(roundedTo(millilitres, decimals)) < 1000.0) {
        Quantity(formatDecimal(millilitres, decimals), "ml")
    } else {
        Quantity(formatDecimal(litres, 2), "L")
    }
}

/** An amount held against a product in its own unit — "kg" or "L". */
fun quantityOf(amount: Double, unit: String): Quantity =
    if (unit.equals("L", ignoreCase = true)) quantityFromLitres(amount) else quantityFromGrams(amount * 1000.0)
