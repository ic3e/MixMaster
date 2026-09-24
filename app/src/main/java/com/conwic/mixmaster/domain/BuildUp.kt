package com.conwic.mixmaster.domain

import kotlin.math.abs
import kotlin.math.pow

/**
 * A floor is not a product, it is a build-up: mesh, primer, sand, screed, topping, sealer, in
 * that order, each laid at its own rate. This is that build-up as it gets drawn and written
 * down — the same figures behind the picture on the project page and the one in the report.
 */
data class BuildUpCoat(
    /** 1 is the first coat laid, which is the one at the bottom. */
    val number: Int,
    val title: String,
    /** Whose system this coat belongs to. A change of brand is a change of system. */
    val brand: String,
    val gramsPerM2: Double,
    /**
     * How thick it goes on, where the parts carry densities.
     *
     * Null for a coat that has no thickness the app can stand behind — a mesh laid dry, sand
     * broadcast into a wet primer, or simply a product whose density is not on file. Those are
     * drawn as a line across the strip rather than a band in it, and no figure is printed.
     */
    val millimetres: Double?,
    val colourName: String?,
    /** How thick this one is drawn against the thickest in the stack, 0..1. */
    val weight: Float,
) {
    val hasThickness: Boolean get() = millimetres != null
}

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
 * How dark each coat is drawn.
 *
 * Flattened on purpose: a 150 g/m² primer against a 5 kg/m² screed is one part in thirty, and
 * to scale it is not there at all. The square-ish root keeps the order and the obvious
 * differences. Thickness on the strip is true to life; this only decides the tone.
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
 * its densities; where it does not, the coat carries none and is drawn as a line.
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
    val weights = slabWeights(rates)
    return coats.mapIndexed { index, coat ->
        BuildUpCoat(
            number = index + 1,
            title = titleOf(coat),
            brand = coat.brand,
            gramsPerM2 = rates[index],
            millimetres = millimetres[index],
            colourName = coat.colour?.name,
            weight = weights[index],
        )
    }
}

/**
 * The film of the build-up in millimetres: every coat that has a thickness, added up.
 *
 * Null when not one of them knows its own. A coat without a thickness is not counted and not
 * missed — a mesh and a broadcast sand are laid into the coats around them.
 */
fun buildUpMillimetres(coats: List<BuildUpCoat>): Double? {
    val known = coats.mapNotNull { it.millimetres }
    return if (known.isEmpty()) null else known.sum()
}

/**
 * How far apart to put the labelled marks on the millimetre scale beside the strip.
 *
 * Four or five labels is what a strip this size can hold, and the step has to be a figure
 * somebody would actually write down — 0.5 mm, not 0.4064.
 */
fun rulerStep(totalMm: Double): Double {
    if (totalMm <= 0.0) return 1.0
    val steps = doubleArrayOf(0.05, 0.1, 0.2, 0.25, 0.5, 1.0, 2.0, 2.5, 5.0, 10.0, 20.0, 50.0)
    val wanted = totalMm / 4.0
    return steps.firstOrNull { it >= wanted } ?: steps.last()
}

/** True when two figures are the same to within a hair, used to place the marks. */
fun sameMillimetre(a: Double, b: Double): Boolean = abs(a - b) < 0.0005
