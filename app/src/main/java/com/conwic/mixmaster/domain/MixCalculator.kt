package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.dao.ProductWithComponents
import com.conwic.mixmaster.data.model.DosingMode

data class ComponentAmount(
    val label: String,
    val grams: Double,
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
     * @param quantity meaning depends on [ProductWithComponents.product]'s dosing mode:
     *  - [DosingMode.COATS] → number of coats (e.g. 2.0 for two coats)
     *  - [DosingMode.POUR] → number of pours (usually 1.0)
     *  - [DosingMode.MM] → thickness in millimetres (e.g. 5.0 for a 5mm self-levelling pour)
     * @param doseGramsPerM2 the per-m² dose to use — defaults to the product's typical (midpoint
     *  of its datasheet range), but the calculator screen lets it be nudged within that range.
     */
    fun compute(
        productWithComponents: ProductWithComponents,
        areaM2: Double,
        quantity: Double,
        doseGramsPerM2: Double = productWithComponents.product.typicalDoseGramsPerM2,
    ): MixResult {
        val rawTotalGrams = doseGramsPerM2 * areaM2 * quantity
        val components = productWithComponents.components
        val ratioSum = components.sumOf { it.ratioParts }.takeIf { it > 0.0 } ?: 1.0
        val amounts = components.map { component ->
            ComponentAmount(
                label = component.label,
                grams = toScale(rawTotalGrams * (component.ratioParts / ratioSum)),
            )
        }
        // Total is the sum of the snapped parts, so what's weighed out always adds up to the
        // total shown rather than drifting from it.
        val totalGrams = if (amounts.isEmpty()) toScale(rawTotalGrams) else amounts.sumOf { it.grams }
        return MixResult(totalGrams = totalGrams, components = amounts)
    }
}
