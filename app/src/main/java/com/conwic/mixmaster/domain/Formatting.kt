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
