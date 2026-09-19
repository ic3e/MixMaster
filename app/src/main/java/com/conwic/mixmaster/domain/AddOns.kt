package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.entity.ProductEntity
import kotlin.math.ceil

/**
 * A colour or admixture the crew is adding to a base mix on this job, and which part of that
 * mix it doses off.
 *
 * Colour is dosed off the liquid, not the whole batch — "28 g per 1 kg of polymer" on a
 * pigment, "1 litre per 25 kg of colour-mix" on an admixture. The part is chosen per job
 * because the same pigment goes into different products against different parts, and the base
 * and the add-on are frequently different brands.
 */
data class AddOnChoice(val productId: Long, val partIndex: Int)

data class AddOnNeed(
    val productId: Long,
    val name: String,
    val brand: String,
    /** The part of the base mix this is dosed against. */
    val againstLabel: String,
    val againstKg: Double,
    /** How much add-on is needed, in [unit]. */
    val amount: Double,
    val unit: String,
    val rateLabel: String,
    /** Containers to take, when the add-on's pack size is known. */
    val packs: Int?,
    val packLabel: String?,
    val problem: String?,
)

/**
 * Works out how much of each chosen add-on the job needs.
 *
 * [packSizeOf] gives the add-on's own container size, so this doesn't need to know how
 * packaging is stored.
 */
fun addOnNeeds(
    result: MixResult,
    choices: List<AddOnChoice>,
    addOns: List<ProductEntity>,
    packSizeOf: (Long) -> Pair<Double, String>?,
): List<AddOnNeed> = choices.mapNotNull { choice ->
    val product = addOns.firstOrNull { it.id == choice.productId } ?: return@mapNotNull null
    val part = result.components.getOrNull(choice.partIndex)
    val againstKg = (part?.grams ?: 0.0) / 1000.0
    val amount = againstKg * product.addOnAmountPerKg
    val pack = packSizeOf(product.id)
    val packs = if (pack != null && pack.first > 0.0 && pack.second == product.addOnUnit && amount > 0.0) {
        ceil(amount / pack.first).toInt()
    } else {
        null
    }
    AddOnNeed(
        productId = product.id,
        name = product.name,
        brand = product.brand,
        againstLabel = part?.label ?: "—",
        againstKg = againstKg,
        amount = amount,
        unit = product.addOnUnit,
        rateLabel = "${formatDecimal(product.addOnAmountPerKg * 1000, 1)} " +
            "${if (product.addOnUnit == "L") "ml" else "g"} per kg of ${part?.label ?: "—"}",
        packs = packs,
        packLabel = pack?.let { "${formatDecimal(it.first, 2)} ${it.second}" },
        problem = when {
            product.addOnAmountPerKg <= 0.0 ->
                "No dose set on ${product.name} — set it on the product under \"Added to another mix\"."
            part == null -> "Pick which part of the mix this is measured against."
            else -> null
        },
    )
}
