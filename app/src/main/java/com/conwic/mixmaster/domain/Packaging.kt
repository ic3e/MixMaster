package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import kotlin.math.ceil

/** How a batch is limited: by what the mixer holds, by weight, or by whole packs. */
enum class BatchBasis { MIXER_VOLUME, MAX_WEIGHT, ONE_PACKAGE }

/** What to buy for one component: the amount needed, and how many packs that is. */
data class PackNeed(
    val label: String,
    val amountKg: Double,
    /** Amount expressed in the unit the pack is sold in — litres for L-sold packs. */
    val amountInPackUnit: Double?,
    val packUnit: String,
    val packSize: Double,
    val packType: String,
    /** Whole packs to open, rounded up. Null when the pack size isn't known yet. */
    val packs: Int?,
) {
    val packLabel: String get() = "${formatDecimal(packSize, 2)} $packUnit $packType"
}

data class BatchPlan(
    /** Number of identical full batches. */
    val batches: Int,
    /** Per-batch component amounts in grams — what actually goes in the mixer each time. */
    val perBatch: List<ComponentAmount>,
    val batchSizeLabel: String,
    /** A smaller final batch, when batching by whole packs leaves a part bag over. */
    val remainderBatch: List<ComponentAmount>? = null,
    /** Volume one full batch takes up, when every part has a density. */
    val perBatchLitres: Double? = null,
    /** True when a batch wouldn't leave the requested mixing room — it would slop over. */
    val overflows: Boolean = false,
    /** Set when the plan couldn't be worked out, explaining what's missing. */
    val problem: String? = null,
)

/** What's actually mixable in a drum of [mixerLitres] once [headroomPercent] is left free. */
fun usableLitres(mixerLitres: Double, headroomPercent: Double): Double =
    mixerLitres * (1.0 - headroomPercent.coerceIn(0.0, 90.0) / 100.0)

/** Litres this mix occupies, or null if any part is missing a density. */
fun mixVolumeLitres(result: MixResult, components: List<ProductComponentEntity>): Double? {
    var litres = 0.0
    result.components.forEachIndexed { index, amount ->
        val density = components.getOrNull(index)?.densityKgPerL ?: 0.0
        if (density <= 0.0) return null
        litres += (amount.grams / 1000.0) / density
    }
    return litres
}

fun packNeeds(result: MixResult, components: List<ProductComponentEntity>): List<PackNeed> =
    result.components.mapIndexed { index, amount ->
        val component = components.getOrNull(index)
        val kg = amount.grams / 1000.0
        val packSize = component?.packageSize ?: 0.0
        val packUnit = component?.packageUnit ?: "kg"
        val density = component?.densityKgPerL ?: 0.0
        // An L-sold pack has to be compared in litres, which needs the density.
        val amountInPackUnit = when {
            packUnit == "kg" -> kg
            density > 0.0 -> kg / density
            else -> null
        }
        PackNeed(
            label = amount.label,
            amountKg = kg,
            amountInPackUnit = amountInPackUnit,
            packUnit = packUnit,
            packSize = packSize,
            packType = component?.packageType ?: "bag",
            packs = if (packSize > 0.0 && amountInPackUnit != null) {
                ceil(amountInPackUnit / packSize).toInt().coerceAtLeast(1)
            } else {
                null
            },
        )
    }

/**
 * Splits the mix into batches the mixer can actually take.
 *
 * Every basis divides the whole mix evenly, so each batch keeps the product's ratio — mixing a
 * part batch at the wrong ratio is how a floor ends up cured wrong.
 */
fun planBatches(
    result: MixResult,
    components: List<ProductComponentEntity>,
    basis: BatchBasis,
    mixerLitres: Double,
    headroomPercent: Double,
    maxBatchKg: Double,
): BatchPlan {
    val totalKg = result.totalGrams / 1000.0
    if (totalKg <= 0.0) return BatchPlan(0, emptyList(), "—")

    val totalLitres = mixVolumeLitres(result, components)
    val usable = usableLitres(mixerLitres, headroomPercent)

    /** Flags a plan whose batch wouldn't fit the mixing room left in the drum. */
    fun withFit(plan: BatchPlan): BatchPlan {
        val batchLitres = totalLitres?.takeIf { plan.batches > 0 }?.div(plan.batches) ?: return plan
        return plan.copy(perBatchLitres = batchLitres, overflows = batchLitres > usable)
    }

    // Whole packs can't be split on site, so batch by full packs and leave the odd bit over
    // as a smaller final batch instead of pretending every batch is a fraction of a bag.
    if (basis == BatchBasis.ONE_PACKAGE) {
        val index = components.indexOfFirst { it.packageSize > 0.0 && it.packageUnit == "kg" }
        if (index < 0) {
            return BatchPlan(1, result.components, "—", problem = "Set a kg pack size on one part to batch by the bag.")
        }
        val partKg = (result.components.getOrNull(index)?.grams ?: 0.0) / 1000.0
        val packSize = components[index].packageSize
        if (partKg <= 0.0) return BatchPlan(0, emptyList(), "—")

        val fullBatches = kotlin.math.floor(partKg / packSize).toInt()
        val remainderKg = partKg - fullBatches * packSize
        val fullShare = packSize / partKg
        val packType = components[index].packageType
        val batchLitres = totalLitres?.times(fullShare)
        return BatchPlan(
            batches = fullBatches,
            perBatch = result.components.map { ComponentAmount(it.label, it.grams * fullShare) },
            batchSizeLabel = "1 × ${formatDecimal(packSize, 2)} kg $packType per batch",
            remainderBatch = if (remainderKg > 0.001) {
                result.components.map { ComponentAmount(it.label, it.grams * (remainderKg / partKg)) }
            } else {
                null
            },
            perBatchLitres = batchLitres,
            overflows = batchLitres != null && batchLitres > usable,
        )
    }

    val batches: Int
    val label: String
    when (basis) {
        BatchBasis.MIXER_VOLUME -> {
            val litres = totalLitres
                ?: return BatchPlan(
                    batches = 1,
                    perBatch = result.components,
                    batchSizeLabel = "—",
                    problem = "Add a density (kg/L) to every part of this product to work in litres.",
                )
            if (usable <= 0.0) return BatchPlan(1, result.components, "—", problem = "Choose a mixer size.")
            batches = ceil(litres / usable).toInt().coerceAtLeast(1)
            label = "${formatDecimal(litres / batches, 1)} L per batch · ${formatDecimal(usable, 1)} L usable"
        }
        BatchBasis.MAX_WEIGHT -> {
            if (maxBatchKg <= 0.0) return BatchPlan(1, result.components, "—", problem = "Set a maximum batch weight.")
            batches = ceil(totalKg / maxBatchKg).toInt().coerceAtLeast(1)
            label = "${formatKg(result.totalGrams / batches)} kg per batch"
        }
        BatchBasis.ONE_PACKAGE -> error("handled above")
    }

    val perBatch = result.components.map { ComponentAmount(it.label, it.grams / batches) }
    return withFit(BatchPlan(batches = batches, perBatch = perBatch, batchSizeLabel = label))
}
