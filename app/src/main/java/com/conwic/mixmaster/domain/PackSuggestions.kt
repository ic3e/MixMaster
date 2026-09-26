package com.conwic.mixmaster.domain

/** One way a product is sold: 23 kg in a bag, 17 L in a bucket. */
data class PackOption(val size: Double, val unit: String, val type: String)

/**
 * Packs worth offering for a product nobody has given one yet — offered, never written on their
 * own: the same product comes in different packs from one country to the next, and what is on
 * this shed's racks is the only answer that counts.
 *
 * Two places to look, the shed's own first:
 *  - the same brand's products that end in the same word, which have a pack already. Somebody
 *    who has set Microtopping Base Coat Polymer as a 25 kg canister has, near enough, told the
 *    app what the Finish Coat Polymer comes in too;
 *  - what the manufacturer's datasheet lists, where it lists anything.
 */
fun packSuggestions(name: String, brand: String, shelf: List<ProductStock>): List<PackOption> {
    val lastWord = name.trim().substringAfterLast(' ').lowercase()
    val fromSiblings = shelf
        .filter { it.isKnownPack && it.brand.equals(brand, ignoreCase = true) && it.name != name }
        .filter { it.name.trim().substringAfterLast(' ').lowercase() == lastWord }
        .map { PackOption(it.packSize, it.packUnit, it.packType) }
    return (fromSiblings + datasheetPacks(name)).distinct().take(4)
}

/**
 * The packs the datasheets give, for the products the app was first set up with. Only where a
 * sheet names them; Nuvolato Architop's does not, so it gets nothing rather than a guess.
 */
private fun datasheetPacks(name: String): List<PackOption> {
    val n = name.lowercase()
    return when {
        // Mapei: 23 kg in Europe, 20 kg where the 23 is not sold.
        "ultraplan eco" in n -> listOf(PackOption(23.0, "kg", "bag"), PackOption(20.0, "kg", "bag"))
        "primer g" in n -> listOf(
            PackOption(25.0, "kg", "drum"),
            PackOption(10.0, "kg", "drum"),
            PackOption(5.0, "kg", "drum"),
            PackOption(1.0, "kg", "bottle"),
        )
        "ideal sealer" in n -> listOf(PackOption(20.0, "L", "bucket"))
        "microtopping" in n && "polymer" in n -> listOf(PackOption(17.0, "L", "bucket"))
        "microtopping" in n && "powder" in n -> listOf(PackOption(25.0, "kg", "bucket"))
        else -> emptyList()
    }
}
