package com.conwic.mixmaster.domain

import kotlin.math.ceil

/** How a batch is limited: by what the mixer holds, by weight, or by whole packs. */
enum class BatchBasis { MIXER_VOLUME, MAX_WEIGHT, ONE_PACKAGE }

/** What to buy for one component: the amount needed, and how many packs that is. */
data class PackNeed(
    val label: String,
    /** The bought item this is, so an order and the shelf can be matched up. */
    val productId: Long = 0L,
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
    /** A batch limited by weight — in grams when that's under a kilo. */
    data class Weight(val weight: String) : BatchSize
}

data class BatchPlan(
    /** Number of identical full batches. */
    val batches: Int,
    /** Per-batch component amounts in grams — what actually goes in the mixer each time. */
    val perBatch: List<ComponentAmount>,
    val batchSize: BatchSize,
    /** A smaller final batch, when batching by whole packs leaves a part bag over. */
    val remainderBatch: List<ComponentAmount>? = null,
    /** A leftover too small to be worth its own mixing — it goes in with the last full batch. */
    val lastBatchExtra: List<ComponentAmount>? = null,
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

/**
 * How much of a pack can be left over before it is worth a mixing of its own.
 *
 * Two per cent: half a kilo out of a 25 kg bag makes about a litre of material, which is a
 * patch rather than a batch, and adding it to the last drum changes that batch by a fortieth —
 * less than the tolerance the dose itself is set to.
 */
const val SmallLeftover = 0.02

/** What's actually mixable in a drum of [mixerLitres] once [headroomPercent] is left free. */
fun usableLitres(mixerLitres: Double, headroomPercent: Double): Double =
    mixerLitres * (1.0 - headroomPercent.coerceIn(0.0, 90.0) / 100.0)

/**
 * The word for water in each language the app is written in, so a part typed as "Vesi" is
 * recognised the same way "Water" is. Estonian and Finnish share the nominative; the rest are
 * the cases a part actually gets labelled in.
 */
private val WATER_WORDS = setOf("water", "vesi", "vett", "vee", "vettä", "veden")

/**
 * Splits a label into words on whitespace and punctuation, but *not* on hyphens — those bind a
 * compound together, and "Water-proofer" is a waterproofer.
 */
private val WORD_SEPARATORS = Regex("""[\s()\[\]{}/,;:+&.]+""")

/**
 * Water is 1 kg per litre and always will be, so it never has to be filled in — and is
 * resolved here rather than stored, which also covers products saved before the field existed.
 *
 * Matched a whole word at a time, so "Waterproofer", "Veekindel" and "Vesiohenteinen" — all
 * additives, none of them water — are left alone. Getting this wrong is expensive in one
 * direction only: a false match silently assumes a density of 1.0 and throws off every litre
 * the calculator works out.
 */
fun isWaterLabel(label: String): Boolean =
    WORD_SEPARATORS.split(label.lowercase(java.util.Locale.ROOT))
        .any { it.isNotEmpty() && it in WATER_WORDS }

fun effectiveDensityKgPerL(part: MixPart): Double =
    if (isWaterLabel(part.label)) 1.0 else part.densityKgPerL

/**
 * The parts with no usable density, for a message that says what to go and fix.
 *
 * Returns the names rather than a phrase: joining them with "and" is a decision that belongs
 * in whichever language the app is set to.
 */
fun missingDensityLabels(result: MixResult, parts: List<MixPart>): List<String> =
    result.components.filterIndexed { index, _ ->
        (parts.getOrNull(index)?.let { effectiveDensityKgPerL(it) } ?: 0.0) <= 0.0
    }.map { it.label }

/** Litres this mix occupies, or null if any part is missing a density. */
fun mixVolumeLitres(result: MixResult, parts: List<MixPart>): Double? {
    var litres = 0.0
    result.components.forEachIndexed { index, amount ->
        val density = parts.getOrNull(index)?.let { effectiveDensityKgPerL(it) } ?: 0.0
        if (density <= 0.0) return null
        litres += (amount.grams / 1000.0) / density
    }
    return litres
}

fun packNeeds(result: MixResult, parts: List<MixPart>): List<PackNeed> =
    result.components.mapIndexed { index, amount ->
        val part = parts.getOrNull(index)
        val kg = amount.grams / 1000.0
        val packSize = part?.packageSize ?: 0.0
        val packUnit = part?.packageUnit ?: "kg"
        val density = part?.let { effectiveDensityKgPerL(it) } ?: 0.0
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
            packType = part?.packageType ?: "bag",
            productId = part?.productId ?: 0L,
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
    parts: List<MixPart>,
    basis: BatchBasis,
    mixerLitres: Double,
    headroomPercent: Double,
    maxBatchKg: Double,
): BatchPlan {
    val totalKg = result.totalGrams / 1000.0
    if (totalKg <= 0.0) return BatchPlan(0, emptyList(), BatchSize.Unknown)

    val totalLitres = mixVolumeLitres(result, parts)
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
        val index = parts.indices
            .filter { parts[it].packageSize > 0.0 && parts[it].packageUnit == "kg" }
            .maxByOrNull { parts[it].ratioParts }
            ?: return BatchPlan(
                1,
                result.components,
                BatchSize.Unknown,
                problem = BatchProblem.NoPackWeight,
            )
        val partKg = (result.components.getOrNull(index)?.grams ?: 0.0) / 1000.0
        val packSize = parts[index].packageSize
        if (partKg <= 0.0) return BatchPlan(0, emptyList(), BatchSize.Unknown)

        val fullBatches = kotlin.math.floor(partKg / packSize).toInt()
        val remainderKg = partKg - fullBatches * packSize
        val fullShare = packSize / partKg
        val remainderShare = remainderKg / partKg
        val packType = parts[index].packageType
        val leftover = if (remainderKg > 0.001) {
            result.components.map { ComponentAmount(it.label, it.grams * remainderShare) }
        } else {
            null
        }
        // Four grams over a whole bag is not a mixing. Anything under [SmallLeftover] of a pack
        // goes in with the last full batch instead, where it disappears into the drum — sending
        // someone back to the mixer to weigh out a few grams is how a batch gets skipped.
        val foldsIn = leftover != null && fullBatches > 0 && remainderKg < packSize * SmallLeftover
        // There is no whole pack in this job at all: a patch of 11 m² wants 400 g of a part
        // that comes in 5 kg canisters. Nothing goes in the drum but the part batch, so the
        // part batch is the batch — and it is the one the drum has to hold. Measured against a
        // whole canister instead, the app worked out the volume of a batch twelve times the
        // size of the whole job and told somebody their half-litre would not fit a 2.6 L drum.
        val noFullBatch = fullBatches == 0
        // Whichever batch is the biggest is the one the drum has to hold: the last one when a
        // leftover is folded into it, the part batch when there is nothing else.
        val biggestShare = when {
            noFullBatch -> remainderShare
            foldsIn -> fullShare + remainderShare
            else -> fullShare
        }
        val biggestLitres = totalLitres?.times(biggestShare)
        return BatchPlan(
            batches = fullBatches,
            perBatch = if (noFullBatch) {
                emptyList()
            } else {
                result.components.map { ComponentAmount(it.label, it.grams * fullShare) }
            },
            // And it is not a whole canister per batch either, whatever the pack holds.
            batchSize = if (noFullBatch) {
                BatchSize.Weight(quantityFromGrams(result.totalGrams * remainderShare).text)
            } else {
                BatchSize.WholePack(formatDecimal(packSize, 2), packType)
            },
            remainderBatch = if (foldsIn) null else leftover,
            lastBatchExtra = if (foldsIn) leftover else null,
            perBatchLitres = biggestLitres,
            overflows = biggestLitres != null && biggestLitres > usable,
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
                    problem = BatchProblem.NeedsDensity(missingDensityLabels(result, parts)),
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
            size = BatchSize.Weight(quantityFromGrams(result.totalGrams / batches).text)
        }
        BatchBasis.ONE_PACKAGE -> error("handled above")
    }

    val perBatch = result.components.map { ComponentAmount(it.label, it.grams / batches) }
    return withFit(BatchPlan(batches = batches, perBatch = perBatch, batchSize = size))
}
