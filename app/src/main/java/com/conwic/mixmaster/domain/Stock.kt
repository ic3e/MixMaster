package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.RoomLayerEntity
import com.conwic.mixmaster.data.db.entity.StockEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import kotlin.math.ceil

/** A job with this product spoken for, and how much of it. */
data class Booking(val projectId: Long, val projectName: String, val amount: Double)

/**
 * One bought item as the warehouse sees it. Every amount is in [packUnit] — the unit it is
 * bought in — because that is the unit an order is placed in.
 */
data class ProductStock(
    val productId: Long,
    val name: String,
    val brand: String,
    val packSize: Double,
    val packUnit: String,
    val packType: String,
    val fullPacks: Int,
    val openAmount: Double,
    val bookings: List<Booking>,
) {
    /** Everything on the shelf, opened packs included. */
    val onHand: Double get() = fullPacks * packSize + openAmount

    val booked: Double get() = bookings.sumOf { it.amount }

    /** What is left once every booked job has taken its share. Negative means oversold. */
    val free: Double get() = onHand - booked

    /** What has to come in to cover the bookings. */
    val short: Double get() = (booked - onHand).coerceAtLeast(0.0)

    /** Whole packs to order — nobody sells a third of a bag. Null when the pack size is unknown. */
    val packsToOrder: Int?
        get() = when {
            short <= 0.0 -> 0
            packSize > 0.0 -> ceil(short / packSize).toInt()
            else -> null
        }

    val isKnownPack: Boolean get() = packSize > 0.0
}

/**
 * A job books its material from the moment a coat is put on one of its rooms, and stops the
 * moment someone takes that material off the shelf — or the job is finished or archived.
 *
 * Booking is worked out from the rooms every time rather than written down, so it cannot drift
 * away from what the job actually needs when a room is resized or a coat changed.
 */
val ProjectEntity.booksMaterial: Boolean
    get() = !isArchived && status != ProjectStatus.COMPLETED && materialsIssuedAt == null

/**
 * How much of each product a set of rooms needs, in each product's own pack unit.
 *
 * A part whose litres can't be worked out — an L-sold pack with no density — is left out
 * rather than guessed at, so it shows as "pack size not set" instead of a made-up figure.
 */
fun needsByProduct(
    rooms: List<RoomAreaEntity>,
    layersByRoom: Map<Long, List<RoomLayerEntity>>,
    mixes: Map<Long, SolutionMix>,
    products: Map<Long, ProductEntity>,
): Map<Long, Double> {
    val needs = mutableMapOf<Long, Double>()
    fun add(productId: Long, amount: Double) {
        if (amount > 0.0) needs[productId] = (needs[productId] ?: 0.0) + amount
    }

    rooms.forEach { room ->
        if (room.areaM2 <= 0.0) return@forEach
        layersByRoom[room.id].orEmpty().sortedBy { it.sortOrder }.forEach { layer ->
            val mix = mixes[layer.solutionId]
            if (mix != null) {
                val dose = layer.doseGramsPerM2.takeIf { it > 0.0 }
                    ?: mix.solution.typicalDoseGramsPerM2
                val result = MixCalculator.compute(mix.parts, room.areaM2, layer.quantity, dose)
                packNeeds(result, mix.parts).forEach { need ->
                    need.amountInPackUnit?.let { add(need.productId, it) }
                }
                addOnNeeds(result, mix.addOns).forEach { add(it.productId, it.amount) }
                // The colour the room asked for, which no recipe knows about.
                colourAddOn(layer, mix.parts, products)?.let { colour ->
                    addOnNeeds(result, listOf(colour)).forEach { add(it.productId, it.amount) }
                }
                return@forEach
            }
            // A coat laid straight out of its own container, with no recipe behind it.
            val product = products[layer.productId] ?: return@forEach
            val kg = layer.doseGramsPerM2 * room.areaM2 * layer.quantity / 1000.0
            val amount = when {
                product.packageUnit == "kg" -> kg
                product.densityKgPerL > 0.0 -> kg / product.densityKgPerL
                else -> 0.0
            }
            add(product.id, amount)
        }
    }
    return needs
}

/** Every booking against every product, keyed by product. */
fun bookingsByProduct(
    projects: List<ProjectEntity>,
    rooms: List<RoomAreaEntity>,
    layersByRoom: Map<Long, List<RoomLayerEntity>>,
    mixes: Map<Long, SolutionMix>,
    products: Map<Long, ProductEntity>,
): Map<Long, List<Booking>> {
    val roomsByProject = rooms.groupBy { it.projectId }
    val out = mutableMapOf<Long, MutableList<Booking>>()
    projects.filter { it.booksMaterial }.forEach { project ->
        val needs = needsByProduct(roomsByProject[project.id].orEmpty(), layersByRoom, mixes, products)
        needs.forEach { (productId, amount) ->
            if (amount > 0.0) {
                out.getOrPut(productId) { mutableListOf() }.add(Booking(project.id, project.name, amount))
            }
        }
    }
    return out
}

/** The shelf for one bought item. */
fun productStock(
    product: ProductEntity,
    stock: StockEntity?,
    bookings: List<Booking>,
): ProductStock = ProductStock(
    productId = product.id,
    name = product.name,
    brand = product.brand,
    packSize = product.packageSize,
    packUnit = product.packageUnit,
    packType = product.packageType,
    fullPacks = stock?.fullPacks ?: 0,
    openAmount = stock?.openAmount ?: 0.0,
    bookings = bookings,
)
