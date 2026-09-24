package com.conwic.mixmaster.domain

/** One trip to the mixer: what goes in, and whether it is the odd smaller one at the end. */
data class MixingStep(val amounts: List<ComponentAmount>, val isPartBatch: Boolean)

/**
 * The batches of a plan, in the order they get mixed.
 *
 * A leftover too small for its own mixing was folded into the last full batch upstream, so it
 * is added to that batch here too rather than turning up as a trip of its own.
 */
fun mixingSteps(plan: BatchPlan): List<MixingStep> {
    val full = (0 until plan.batches).map { index ->
        val isLast = index == plan.batches - 1
        val extra = plan.lastBatchExtra
        val amounts = if (isLast && extra != null) {
            plan.perBatch.mapIndexed { part, amount ->
                amount.copy(grams = amount.grams + (extra.getOrNull(part)?.grams ?: 0.0))
            }
        } else {
            plan.perBatch
        }
        MixingStep(amounts, isPartBatch = false)
    }
    val remainder = plan.remainderBatch?.let { listOf(MixingStep(it, isPartBatch = true)) }.orEmpty()
    return full + remainder
}
