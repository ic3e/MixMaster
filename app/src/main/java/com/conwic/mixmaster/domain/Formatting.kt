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

/**
 * Kilos to a tenth.
 *
 * They were shown to the gram, which on a total is three digits of noise: nobody orders
 * 376.381 kg, and nobody can read it off a card at a glance either. A tenth of a kilo is the
 * finest figure worth printing on a number that size, and anything under a kilo is given in
 * grams to a tenth of a gram, where the precision belongs.
 */
fun formatKg(grams: Double): String = formatDecimal(grams / 1000.0, 1)

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

/**
 * Grams as the scale on the van reads them: a tenth, never finer.
 *
 * A batch that says "500.04 g" is asking for a figure no site scale can hit, and reading it off
 * leaves you working out which digits matter. Anything that would round away to nothing is
 * shown as the smallest the scale reads instead, because "0 g" of a pigment is wrong in a way
 * that a tenth of a gram is not.
 */
private fun onScale(grams: Double): Double {
    val snapped = toScale(grams)
    return when {
        snapped == 0.0 -> 0.0
        abs(snapped) < ScaleGrams -> if (snapped < 0.0) -ScaleGrams else ScaleGrams
        else -> snapped
    }
}

fun quantityFromGrams(grams: Double): Quantity {
    val scaled = onScale(grams)
    return if (abs(scaled) < 1000.0) {
        Quantity(formatDecimal(scaled, 1), "g")
    } else {
        Quantity(formatKg(grams), "kg")
    }
}

fun quantityFromLitres(litres: Double): Quantity {
    val millilitres = onScale(litres * 1000.0)
    return if (abs(millilitres) < 1000.0) {
        Quantity(formatDecimal(millilitres, 1), "ml")
    } else {
        Quantity(formatDecimal(litres, 2), "L")
    }
}

/** An amount held against a product in its own unit — "kg" or "L". */
fun quantityOf(amount: Double, unit: String): Quantity =
    if (unit.equals("L", ignoreCase = true)) quantityFromLitres(amount) else quantityFromGrams(amount * 1000.0)
