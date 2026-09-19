package com.conwic.mixmaster.domain

import androidx.annotation.StringRes
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity

/**
 * Solid quartz is 2.65 kg/L and set concrete about 2.4, so nothing that comes out of a bag, a
 * bucket or a can to go on a floor is denser than this. A figure above it is almost always the
 * pack weight typed into the density box.
 */
const val MAX_PLAUSIBLE_DENSITY_KG_PER_L = 3.0

/** Below this is lightweight-filler territory: unusual, but real, so only worth a second look. */
const val MIN_PLAUSIBLE_DENSITY_KG_PER_L = 0.2

/**
 * Why this density can't be right, or null. The one place that rule lives.
 *
 * A resource id rather than a sentence: this is called from view models and from the form's
 * state, neither of which knows the language. The screen that shows it resolves it.
 */
@StringRes
fun densityProblem(value: Double?): Int? = when {
    value == null -> R.string.density_problem_nan
    value <= 0.0 -> R.string.density_problem_zero
    value > MAX_PLAUSIBLE_DENSITY_KG_PER_L -> R.string.density_problem_too_dense
    else -> null
}

/** Worth double-checking but not refused — see [MIN_PLAUSIBLE_DENSITY_KG_PER_L]. */
@StringRes
fun densityWarning(value: Double?): Int? =
    if (value != null && densityProblem(value) == null && value < MIN_PLAUSIBLE_DENSITY_KG_PER_L) {
        R.string.density_warning_light
    } else {
        null
    }

/** One stored component whose density can't be right, ready for the screen to word. */
data class ImplausibleDensity(val label: String, val densityKgPerL: String)

/**
 * Densities already saved on a product that can't be right, for the calculator to warn about.
 *
 * The form refuses a bad density on the way in, but that only helps products saved since. A
 * figure entered earlier — or restored from a backup — sits in the database and quietly throws
 * off every litre the calculator works out, which is exactly the kind of wrong that gets
 * believed. The weights stay correct either way, so this warns rather than blocks.
 *
 * Returns the offenders rather than a sentence, because the sentence has to be built in the
 * language the app is set to.
 */
fun implausibleStoredDensities(components: List<ProductComponentEntity>): List<ImplausibleDensity> =
    components
        .filter { effectiveDensityKgPerL(it) > MAX_PLAUSIBLE_DENSITY_KG_PER_L }
        .map { ImplausibleDensity(it.label, formatDecimal(it.densityKgPerL, 2)) }
