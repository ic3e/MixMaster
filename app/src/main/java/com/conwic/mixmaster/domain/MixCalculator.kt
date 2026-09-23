package com.conwic.mixmaster.domain


data class ComponentAmount(
    val label: String,
    val grams: Double,
    /** The bought item this part is, so the warehouse can be asked about it by name. */
    val productId: Long = 0L,
)

data class MixResult(
    val totalGrams: Double,
    val components: List<ComponentAmount>,
)

/**
 * The core "solve the guessing game" calculation: given a product's typical total dose per m²
 * (or per m² per mm/coat) and its component ratio, work out exactly how much of each part is
 * needed for a real area — instead of a contractor eyeballing a bag-to-bucket ratio on site.
 */
object MixCalculator {

    /**
     * @param parts the solution's parts, already resolved to the products they name
     * @param quantity coats, pours or millimetres, depending on the solution's dosing mode
     * @param doseGramsPerM2 the per-m² dose of the mixed material
     */
    fun compute(
        parts: List<MixPart>,
        areaM2: Double,
        quantity: Double,
        doseGramsPerM2: Double,
    ): MixResult {
        val rawTotalGrams = doseGramsPerM2 * areaM2 * quantity
        val ratioSum = parts.sumOf { it.ratioParts }.takeIf { it > 0.0 } ?: 1.0
        val amounts = parts.map { part ->
            ComponentAmount(
                label = part.label,
                grams = toScale(rawTotalGrams * (part.ratioParts / ratioSum)),
                productId = part.productId,
            )
        }
        // Total is the sum of the snapped parts, so what's weighed out always adds up to the
        // total shown rather than drifting from it.
        val totalGrams = if (amounts.isEmpty()) toScale(rawTotalGrams) else amounts.sumOf { it.grams }
        return MixResult(totalGrams = totalGrams, components = amounts)
    }
}
