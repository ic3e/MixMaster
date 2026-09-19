package com.conwic.mixmaster.domain

/**
 * Reads a number the way it actually gets typed on site.
 *
 * The decimal key on an Estonian keyboard types a comma, so "0,1" is what lands in the field —
 * and Kotlin's own [String.toDoubleOrNull] returns null for it. Half the form already replaced
 * the comma and half didn't, so a density of "2,6" was accepted while a min dose of "0,1" was
 * read as no number at all, which silently disabled Save with nothing on screen to explain it.
 *
 * Every field the user types a number into goes through here, so that can't drift apart again.
 * Spaces are dropped so a pasted "1 200" reads as 1200.
 */
fun String.toNumberOrNull(): Double? = trim()
    .replace(',', '.')
    .replace(" ", "")
    .replace(" ", "")
    .takeIf { it.isNotEmpty() }
    ?.toDoubleOrNull()

/** The same reading, with anything unparseable treated as [fallback]. */
fun String.toNumberOr(fallback: Double): Double = toNumberOrNull() ?: fallback
