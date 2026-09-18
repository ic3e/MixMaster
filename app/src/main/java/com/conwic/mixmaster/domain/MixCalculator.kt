package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.dao.ProductWithComponents
import com.conwic.mixmaster.data.model.DosingMode
import kotlin.math.round

data class ComponentAmount(
    val label: String,
    val grams: Double,
)

data class MixResult(
    val totalGrams: Double,
    val components: List<ComponentAmount>,
) {
    val totalKg: Double get() = totalGrams / 1000.0
}

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
     */
    fun compute(productWithComponents: ProductWithComponents, areaM2: Double, quantity: Double): MixResult {
        val product = productWithComponents.product
        val totalGrams = product.typicalDoseGramsPerM2 * areaM2 * quantity
        val components = productWithComponents.components
        val ratioSum = components.sumOf { it.ratioParts }.takeIf { it > 0.0 } ?: 1.0
        val amounts = components.map { component ->
            ComponentAmount(
                label = component.label,
                grams = roundTo1Decimal(totalGrams * (component.ratioParts / ratioSum)),
            )
        }
        return MixResult(totalGrams = roundTo1Decimal(totalGrams), components = amounts)
    }

    private fun roundTo1Decimal(value: Double): Double = round(value * 10.0) / 10.0
}
