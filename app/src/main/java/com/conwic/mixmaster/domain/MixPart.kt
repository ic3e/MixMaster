package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.RoomLayerEntity
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
    /**
     * What the datasheet actually said, where this part is a share of the others rather than a
     * ratio part of its own: "water at 10% of (A+B)". Zero for an ordinary part.
     *
     * [ratioParts] already carries what that comes to, so nothing in the maths reads this — it
     * is here so the screen can say the recipe back in the words it was written in.
     */
    val percentOfRest: Double = 0.0,
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
            percentOfRest = line.percentOfRest,
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

/**
 * One coat on one room, with its mix worked out.
 *
 * A coat laid straight out of its container is a mix of one part, so both kinds go through the
 * same maths and the same figures come out.
 */
data class CoatMix(
    val layer: RoomLayerEntity,
    val title: String,
    /** Whose system this coat is part of: a change of brand is a change of system. */
    val brand: String = "",
    val doseGramsPerM2: Double,
    val doseUnitLabel: String,
    val parts: List<MixPart>,
    val result: MixResult,
    /** What this coat is tinted with, worked out for the room. */
    val colour: AddOnNeed? = null,
) {
    val totalGrams: Double get() = result.totalGrams
}

/**
 * The colour a coat is tinted with, as the mix maths sees it.
 *
 * Held on the room rather than on the recipe: the same topping goes down ocra in one bay and
 * grey in the next, so the choice — and the rate it is mixed at — belongs where it is made.
 */
fun colourAddOn(
    layer: RoomLayerEntity,
    parts: List<MixPart>,
    productsById: Map<Long, ProductEntity>,
): MixAddOn? {
    if (layer.colourProductId <= 0L) return null
    val colour = productsById[layer.colourProductId] ?: return null
    return colourAddOn(colour, layer.colourAmountPerKg, layer.colourUnit, layer.colourAgainstIndex, parts)
}

/**
 * The same colour from loose figures rather than from the room's row.
 *
 * The calculator is handed a coat to mix, not the row it came from, and a tinted topping worked
 * out with no pigment in it is a batch of the wrong colour.
 */
fun colourAddOn(
    colour: ProductEntity,
    amountPerKg: Double,
    amountUnit: String,
    againstIndex: Int,
    parts: List<MixPart>,
): MixAddOn {
    val index = againstIndex.coerceIn(0, (parts.size - 1).coerceAtLeast(0))
    return MixAddOn(
        productId = colour.id,
        label = colour.name,
        amountPerKg = amountPerKg,
        amountUnit = amountUnit,
        againstLabel = parts.getOrNull(index)?.label.orEmpty(),
        againstIndex = index,
        packageSize = colour.packageSize,
        packageUnit = colour.packageUnit,
        packageType = colour.packageType,
    )
}
