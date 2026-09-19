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

/** Why a batch plan couldn't be worked out. Worded by the screen, which knows the language. */
sealed interface BatchProblem {
    data object NoPackWeight : BatchProblem
    data class NeedsDensity(val missingLabels: List<String>) : BatchProblem
    data object NoMixerSize : BatchProblem
    data object NoMaxWeight : BatchProblem
}

/** How big one batch is, in the terms the chosen basis works in. */
sealed interface BatchSize {
    data object Unknown : BatchSize
    data class WholePack(val packSizeKg: String, val packType: String) : BatchSize
    data class Litres(val litres: String) : BatchSize
    data class Kilos(val kilos: String) : BatchSize
}

data class BatchPlan(
    /** Number of identical full batches. */
    val batches: Int,
    /** Per-batch component amounts in grams — what actually goes in the mixer each time. */
    val perBatch: List<ComponentAmount>,
    val batchSize: BatchSize,
    /** A smaller final batch, when batching by whole packs leaves a part bag over. */
    val remainderBatch: List<ComponentAmount>? = null,
    /** Volume one full batch takes up, when every part has a density. */
    val perBatchLitres: Double? = null,
    /** True when a batch wouldn't leave the requested mixing room — it would slop over. */
    val overflows: Boolean = false,
    /** Set when the plan couldn't be worked out, explaining what's missing. */
    val problem: BatchProblem? = null,
) {
    /** Times the mixer actually gets loaded, counting the part batch at the end. */
    val totalMixes: Int get() = batches + if (remainderBatch != null) 1 else 0
}

/** What's actually mixable in a drum of [mixerLitres] once [headroomPercent] is left free. */
fun usableLitres(mixerLitres: Double, headroomPercent: Double): Double =
    mixerLitres * (1.0 - headroomPercent.coerceIn(0.0, 90.0) / 100.0)

/**
 * Water is 1 kg per litre and always will be, so it never has to be filled in — and is
 * resolved here rather than stored, which also covers products saved before the field existed.
 * Matched narrowly enough that a "Waterproofer" additive isn't mistaken for it.
 */
fun isWaterLabel(label: String): Boolean {
    val normalized = label.trim().lowercase()
    return normalized == "water" ||
        normalized.startsWith("water ") ||
        normalized.startsWith("water(") ||
        normalized.endsWith(" water")
}

fun effectiveDensityKgPerL(component: ProductComponentEntity): Double =
    if (isWaterLabel(component.label)) 1.0 else component.densityKgPerL

/**
 * The parts with no usable density, for a message that says what to go and fix.
 *
 * Returns the names rather than a phrase: joining them with "and" is a decision that belongs
 * in whichever language the app is set to.
 */
fun missingDensityLabels(result: MixResult, components: List<ProductComponentEntity>): List<String> =
    result.components.filterIndexed { index, _ ->
        (components.getOrNull(index)?.let { effectiveDensityKgPerL(it) } ?: 0.0) <= 0.0
    }.map { it.label }

/** Litres this mix occupies, or null if any part is missing a density. */
fun mixVolumeLitres(result: MixResult, components: List<ProductComponentEntity>): Double? {
    var litres = 0.0
    result.components.forEachIndexed { index, amount ->
        val density = components.getOrNull(index)?.let { effectiveDensityKgPerL(it) } ?: 0.0
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
        val density = component?.let { effectiveDensityKgPerL(it) } ?: 0.0
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
    if (totalKg <= 0.0) return BatchPlan(0, emptyList(), BatchSize.Unknown)

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
        // Batch on the biggest part by ratio — that's the powder that comes in bags, not an
        // additive that happens to be listed first.
        val index = components.indices
            .filter { components[it].packageSize > 0.0 && components[it].packageUnit == "kg" }
            .maxByOrNull { components[it].ratioParts }
            ?: return BatchPlan(
                1,
                result.components,
                BatchSize.Unknown,
                problem = BatchProblem.NoPackWeight,
            )
        val partKg = (result.components.getOrNull(index)?.grams ?: 0.0) / 1000.0
        val packSize = components[index].packageSize
        if (partKg <= 0.0) return BatchPlan(0, emptyList(), BatchSize.Unknown)

        val fullBatches = kotlin.math.floor(partKg / packSize).toInt()
        val remainderKg = partKg - fullBatches * packSize
        val fullShare = packSize / partKg
        val packType = components[index].packageType
        val batchLitres = totalLitres?.times(fullShare)
        return BatchPlan(
            batches = fullBatches,
            perBatch = result.components.map { ComponentAmount(it.label, it.grams * fullShare) },
            batchSize = BatchSize.WholePack(formatDecimal(packSize, 2), packType),
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
    val size: BatchSize
    when (basis) {
        BatchBasis.MIXER_VOLUME -> {
            val litres = totalLitres
                ?: return BatchPlan(
                    batches = 1,
                    perBatch = result.components,
                    batchSize = BatchSize.Unknown,
                    problem = BatchProblem.NeedsDensity(missingDensityLabels(result, components)),
                )
            if (usable <= 0.0) return BatchPlan(1, result.components, BatchSize.Unknown, problem = BatchProblem.NoMixerSize)
            batches = ceil(litres / usable).toInt().coerceAtLeast(1)
            // Just the batch size. How it sits in the drum is the next line's job — saying it
            // twice in two different phrasings only reads as a discrepancy.
            size = BatchSize.Litres(formatDecimal(litres / batches, 1))
        }
        BatchBasis.MAX_WEIGHT -> {
            if (maxBatchKg <= 0.0) return BatchPlan(1, result.components, BatchSize.Unknown, problem = BatchProblem.NoMaxWeight)
            batches = ceil(totalKg / maxBatchKg).toInt().coerceAtLeast(1)
            size = BatchSize.Kilos(formatKg(result.totalGrams / batches))
        }
        BatchBasis.ONE_PACKAGE -> error("handled above")
    }

    val perBatch = result.components.map { ComponentAmount(it.label, it.grams / batches) }
    return withFit(BatchPlan(batches = batches, perBatch = perBatch, batchSize = size))
}
