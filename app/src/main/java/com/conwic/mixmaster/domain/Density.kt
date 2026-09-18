package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.entity.ProductComponentEntity

/**
 * Solid quartz is 2.65 kg/L and set concrete about 2.4, so nothing that comes out of a bag, a
 * bucket or a can to go on a floor is denser than this. A figure above it is almost always the
 * pack weight typed into the density box.
 */
const val MAX_PLAUSIBLE_DENSITY_KG_PER_L = 3.0

/** Below this is lightweight-filler territory: unusual, but real, so only worth a second look. */
const val MIN_PLAUSIBLE_DENSITY_KG_PER_L = 0.2

/** Why this density can't be right, or null. The one place that rule lives. */
fun densityProblem(value: Double?): String? = when {
    value == null -> "Needs to be a number, like 1.35"
    value <= 0.0 -> "Has to be more than zero"
    value > MAX_PLAUSIBLE_DENSITY_KG_PER_L ->
        "Denser than concrete — is this the pack weight rather than what a litre weighs?"
    else -> null
}

/** Worth double-checking but not refused — see [MIN_PLAUSIBLE_DENSITY_KG_PER_L]. */
fun densityWarning(value: Double?): String? =
    if (value != null && densityProblem(value) == null && value < MIN_PLAUSIBLE_DENSITY_KG_PER_L) {
        "Lighter than most fillers — worth double-checking"
    } else {
        null
    }

/**
 * A warning about densities already saved on a product, for the calculator to show.
 *
 * The form refuses a bad density on the way in, but that only helps products saved since. A
 * figure entered earlier — or restored from a backup — sits in the database and quietly throws
 * off every litre the calculator works out, which is exactly the kind of wrong that gets
 * believed. The weights stay correct either way, so this warns rather than blocks.
 */
fun storedDensityWarning(components: List<ProductComponentEntity>): String? {
    val wrong = components.filter { effectiveDensityKgPerL(it) > MAX_PLAUSIBLE_DENSITY_KG_PER_L }
    if (wrong.isEmpty()) return null
    val named = wrong.joinToString(", ") {
        "${it.label} at ${formatDecimal(it.densityKgPerL, 2)} kg/L"
    }
    val subject = if (wrong.size == 1) "This density can't be right" else "These densities can't be right"
    return "$subject — $named. Nothing that goes on a floor is denser than concrete, so it's " +
        "probably the pack weight. The kilos below are still right; the litres and the mixer " +
        "fit aren't, until it's corrected in Products → edit."
}
