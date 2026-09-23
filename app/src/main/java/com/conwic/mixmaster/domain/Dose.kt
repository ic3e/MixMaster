package com.conwic.mixmaster.domain

import kotlin.math.round

/** The finest a site scale reads. Amounts are snapped to it, not to the whole gram. */
const val ScaleGrams = 0.1

/**
 * An amount as it can actually be weighed out.
 *
 * Rounding to the whole gram is fine on a bagged job and ruinous on a small one: two square
 * metres of a 100:25 mix came out as "9 g and 2 g", which is neither the right ratio nor a
 * total anyone can hit. A tenth of a gram is what the scale on the van reads.
 */
fun toScale(grams: Double): Double {
    val snapped = round(grams / ScaleGrams) * ScaleGrams
    // Never snap a real amount away to nothing: under a tenth of a gram the figure is the only
    // thing there is to go on, even where the scale can't resolve it.
    return if (snapped == 0.0 && grams > 0.0) grams else snapped
}

/**
 * The same step, for an amount held in a product's own unit — kilos or litres.
 *
 * A colour is dosed in grams against kilos of base, so its figure lands in the same place as
 * the mix it goes into: a tenth of a gram, or a tenth of a millilitre.
 */
fun toScaleInBaseUnit(amount: Double): Double = toScale(amount * 1000.0) / 1000.0

/**
 * How finely the dose rate can be set: tenths for the low-dose products, whole grams for the
 * ones measured in hundreds.
 *
 * The slider is snapped to this and shown to match, so the rate on screen is the rate the mix
 * was worked out from — "6 g/m²" used to be anything from 5.5 up, which is how two square
 * metres at "6" came to 11 g instead of 12.
 */
fun doseStep(minDose: Double, maxDose: Double): Double =
    if (maxOf(minDose, maxDose) <= 20.0) 0.1 else 1.0

fun snapDose(dose: Double, step: Double): Double = round(dose / step) * step

/** Decimal places that match [doseStep], so the figure shown is the figure held. */
fun doseDecimals(step: Double): Int = if (step < 1.0) 1 else 0
