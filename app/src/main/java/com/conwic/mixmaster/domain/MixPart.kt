package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineRole

/**
 * One part of a mix, with everything the maths needs in one place: how much of it goes in, and
 * the pack it is bought in.
 *
 * A solution names products; the products carry the packaging. Resolving the two into this
 * means the mix, batch and packing maths never has to know where either came from.
 */
data class MixPart(
    val productId: Long,
    val label: String,
    val ratioParts: Double,
    val packageSize: Double,
    val packageUnit: String,
    val packageType: String,
    val densityKgPerL: Double,
)

/** A colour or admixture, dosed off one named part rather than by ratio. */
data class MixAddOn(
    val productId: Long,
    val label: String,
    val amountPerKg: Double,
    val amountUnit: String,
    val againstLabel: String,
    val againstIndex: Int,
    val packageSize: Double,
    val packageUnit: String,
    val packageType: String,
)

/** A solution with its lines resolved to the products they name. */
data class SolutionMix(
    val solution: SolutionEntity,
    val parts: List<MixPart>,
    val addOns: List<MixAddOn>,
) {
    val ratioLabel: String
        get() = parts.joinToString(":") { formatDecimal(it.ratioParts, 2) }
}

fun solutionMix(
    solution: SolutionEntity,
    lines: List<SolutionLineEntity>,
    productsById: Map<Long, ProductEntity>,
): SolutionMix {
    val ordered = lines.sortedBy { it.sortOrder }
    val base = ordered.filter { it.role == SolutionLineRole.BASE }
    val parts = base.map { line ->
        val product = productsById[line.productId]
        MixPart(
            productId = line.productId,
            label = line.label.ifBlank { product?.name.orEmpty() },
            ratioParts = line.ratioParts,
            packageSize = product?.packageSize ?: 0.0,
            packageUnit = product?.packageUnit ?: "kg",
            packageType = product?.packageType ?: "bag",
            densityKgPerL = product?.densityKgPerL ?: 0.0,
        )
    }
    val addOns = ordered.filter { it.role == SolutionLineRole.ADD_ON }.map { line ->
        val product = productsById[line.productId]
        // An add-on with nothing named falls back to the first part, which is the powder in
        // every mix that has one — better than dosing off a figure nobody chose.
        val againstIndex = base.indexOfFirst { it.id == line.againstLineId }.takeIf { it >= 0 } ?: 0
        MixAddOn(
            productId = line.productId,
            label = line.label.ifBlank { product?.name.orEmpty() },
            amountPerKg = line.amountPerKg,
            amountUnit = line.amountUnit,
            againstLabel = parts.getOrNull(againstIndex)?.label.orEmpty(),
            againstIndex = againstIndex,
            packageSize = product?.packageSize ?: 0.0,
            packageUnit = product?.packageUnit ?: "kg",
            packageType = product?.packageType ?: "bag",
        )
    }
    return SolutionMix(solution, parts, addOns)
}
