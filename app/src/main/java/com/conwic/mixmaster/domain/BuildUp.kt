package com.conwic.mixmaster.domain

import kotlin.math.pow

/**
 * A floor is not a product, it is a build-up: primer, mesh, screed, topping, sealer, in that
 * order, each laid at its own rate. This is that build-up as it gets drawn and written down —
 * the same figures behind the picture on the project page and the one in the report.
 */
data class BuildUpCoat(
    /** 1 is the first coat laid, which is the one at the bottom. */
    val number: Int,
    val title: String,
    val gramsPerM2: Double,
    /** How thick it goes on, where the parts carry densities. Null where they do not. */
    val millimetres: Double?,
    val colourName: String?,
    /** How thick this one is drawn against the thickest in the stack, 0..1. */
    val weight: Float,
)

/**
 * The wet density of a mixed coat in kg/L, or null when any part of it has none on file.
 *
 * Volumes add, masses add, densities do not: a 100:24 powder-and-polymer mix is the total mass
 * over the total volume, which is what decides how thick the coat lies.
 */
fun mixDensityKgPerL(parts: List<MixPart>, components: List<ComponentAmount>): Double? {
    if (components.isEmpty()) return null
    var mass = 0.0
    var volume = 0.0
    for (component in components) {
        val density = parts.firstOrNull { it.label == component.label }?.densityKgPerL ?: 0.0
        if (density <= 0.0 || component.grams < 0.0) return null
        mass += component.grams
        volume += component.grams / density
    }
    return if (volume > 0.0 && mass > 0.0) mass / volume else null
}

/**
 * Millimetres from a rate, or null without a density.
 *
 * A litre spread over a square metre is a millimetre deep, so the sum is the coverage in kg/m²
 * divided by the density in kg/L. Wet, not cured — a cementitious topping loses a little water
 * on the way, and nothing here pretends to know how much.
 */
fun coatMillimetres(gramsPerM2: Double, densityKgPerL: Double?): Double? {
    if (densityKgPerL == null || densityKgPerL <= 0.0 || gramsPerM2 <= 0.0) return null
    return gramsPerM2 / 1000.0 / densityKgPerL
}

/** The thinnest a coat is ever drawn, so a primer under a screed is still a line you can see. */
private const val ThinnestShare = 0.18f

/**
 * How thick each coat is drawn.
 *
 * Flattened on purpose: a 150 g/m² primer against a 5 kg/m² screed is one part in thirty, and
 * drawn to scale it is not there at all. The square-ish root keeps the order and the obvious
 * differences while leaving every coat something to see.
 */
fun slabWeights(values: List<Double>): List<Float> {
    val largest = values.maxOrNull() ?: 0.0
    if (largest <= 0.0) return values.map { 0.5f }
    return values.map { value ->
        if (value <= 0.0) ThinnestShare else (value / largest).pow(0.45).toFloat().coerceIn(ThinnestShare, 1f)
    }
}

/**
 * The build-up of one room, bottom coat first.
 *
 * [coats] arrive in laying order. Millimetres are worked out per coat where the recipe knows
 * its densities; where it does not, the drawing falls back to what the coat weighs — which is
 * for the picture only, and never printed as a figure.
 */
fun buildUp(
    coats: List<CoatMix>,
    areaM2: Double,
    titleOf: (CoatMix) -> String,
): List<BuildUpCoat> {
    if (coats.isEmpty() || areaM2 <= 0.0) return emptyList()
    val rates = coats.map { it.totalGrams / areaM2 }
    val millimetres = coats.mapIndexed { index, coat ->
        coatMillimetres(rates[index], mixDensityKgPerL(coat.parts, coat.result.components))
    }
    // A middling 1.5 kg/L for anything with no density on file: enough to draw with, never
    // enough to quote.
    val drawn = rates.mapIndexed { index, rate -> millimetres[index] ?: (rate / 1000.0 / 1.5) }
    val weights = slabWeights(drawn)
    return coats.mapIndexed { index, coat ->
        BuildUpCoat(
            number = index + 1,
            title = titleOf(coat),
            gramsPerM2 = rates[index],
            millimetres = millimetres[index],
            colourName = coat.colour?.name,
            weight = weights[index],
        )
    }
}

/** The whole build-up in millimetres, or null unless every coat in it knows its own. */
fun buildUpMillimetres(coats: List<BuildUpCoat>): Double? {
    if (coats.isEmpty() || coats.any { it.millimetres == null }) return null
    return coats.sumOf { it.millimetres ?: 0.0 }
}
