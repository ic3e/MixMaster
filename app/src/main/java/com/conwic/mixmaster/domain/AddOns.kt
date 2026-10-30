package com.conwic.mixmaster.domain

import kotlin.math.ceil

/** What's wrong with an add-on line. Worded by the screen, which knows the language. */
sealed interface AddOnProblem {
    /** The line has no dose on it at all. */
    data object NoDose : AddOnProblem
    /** Nothing has been named for it to be measured against. */
    data object NoPart : AddOnProblem
}

data class AddOnNeed(
    val productId: Long,
    val name: String,
    /** The part of the mix this is dosed against. */
    val againstLabel: String,
    val againstKg: Double,
    /** How much is needed, in [unit]. */
    val amount: Double,
    val unit: String,
    /** The dose as figures, worded by the screen: "28.0 g per kg of Polymer". */
    val rateAmount: String,
    val rateUnit: String,
    /** Containers to take, when the pack size is known. */
    val packs: Int?,
    val packLabel: String?,
    val problem: AddOnProblem?,
)

/**
 * A rate held in kg per kg, as the grams (or ml) per kg it is typed and read in.
 *
 * To a tenth of a gram, except under one gram: there a tenth rounded 0.028 to "0", and a colour
 * typed in the wrong unit showed as "0 g per kg" while still weighing out its fraction of a gram.
 */
fun gramsPerKgText(perKg: Double): String {
    val grams = perKg * 1000.0
    return formatDecimal(grams, if (grams > 0.0 && grams < 1.0) 3 else 1)
}

/**
 * A colour rate typed in grams per kg that looks like the datasheet's kilos per kilo instead:
 * under a gram per kilo is a trace no pigment is dosed at, and "28 g per kg" written in kilos is
 * 0.028. Answers the grams it would then be, or null when the figure looks like grams.
 */
fun gramsIfTypedInKilos(typed: Double?): Double? = typed?.takeIf { it > 0.0 && it < 1.0 }?.let { it * 1000.0 }

/**
 * How much of each colour or admixture this mix needs.
 *
 * Dosed off one named part, not off the total: a pigment is "28 g per 1 kg of polymer", and how
 * much powder is in the batch does not come into it. The lines belong to the solution now, so
 * there is nothing to remember on site — the colour is part of the recipe.
 */
fun addOnNeeds(result: MixResult, addOns: List<MixAddOn>): List<AddOnNeed> = addOns.map { addOn ->
    val part = result.components.getOrNull(addOn.againstIndex)
    val againstKg = (part?.grams ?: 0.0) / 1000.0
    val amount = toScaleInBaseUnit(againstKg * addOn.amountPerKg)
    val packs = if (addOn.packageSize > 0.0 && addOn.packageUnit == addOn.amountUnit && amount > 0.0) {
        ceil(amount / addOn.packageSize).toInt()
    } else {
        null
    }
    AddOnNeed(
        productId = addOn.productId,
        name = addOn.label,
        againstLabel = addOn.againstLabel.ifBlank { part?.label.orEmpty() },
        againstKg = againstKg,
        amount = amount,
        unit = addOn.amountUnit,
        rateAmount = gramsPerKgText(addOn.amountPerKg),
        rateUnit = if (addOn.amountUnit == "L") "ml" else "g",
        packs = packs,
        packLabel = if (addOn.packageSize > 0.0) {
            "${formatDecimal(addOn.packageSize, 2)} ${addOn.packageUnit}"
        } else {
            null
        },
        problem = when {
            addOn.amountPerKg <= 0.0 -> AddOnProblem.NoDose
            part == null -> AddOnProblem.NoPart
            else -> null
        },
    )
}
