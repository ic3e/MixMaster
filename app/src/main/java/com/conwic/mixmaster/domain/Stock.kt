package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.dao.ProductWithComponents
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.StockEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import kotlin.math.ceil

/** A job with this part spoken for, and how much of it. */
data class Booking(val projectId: Long, val projectName: String, val amount: Double)

/**
 * One part of one product, as the warehouse sees it. Every amount is in [packUnit] — the unit
 * that part is bought in — because that is the unit an order is placed in.
 */
data class PartStock(
    val productId: Long,
    val componentId: Long,
    val label: String,
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
 * A job books its material from the moment a product is put on a room, and stops the moment
 * someone takes that material off the shelf — or the job is finished or archived.
 *
 * Booking is worked out from the rooms every time rather than written down, so it cannot drift
 * away from what the job actually needs when a room is resized or a product swapped.
 */
val ProjectEntity.booksMaterial: Boolean
    get() = !isArchived && status != ProjectStatus.COMPLETED && materialsIssuedAt == null

/**
 * How much of each part a set of rooms needs, in each part's own pack unit, keyed by component.
 *
 * A part whose litres can't be worked out — an L-sold pack with no density — is left out
 * rather than guessed at, so it shows as "pack size not set" instead of a made-up figure.
 */
fun needsByComponent(
    rooms: List<RoomAreaEntity>,
    productsById: Map<Long, ProductWithComponents>,
): Map<Long, Double> {
    val needs = mutableMapOf<Long, Double>()
    rooms.forEach { room ->
        val product = room.assignedProductId?.let { productsById[it] } ?: return@forEach
        if (room.areaM2 <= 0.0) return@forEach
        val mix = MixCalculator.compute(product, room.areaM2, quantity = 1.0)
        packNeeds(mix, product.components).forEachIndexed { index, need ->
            val component = product.components.getOrNull(index) ?: return@forEachIndexed
            val amount = need.amountInPackUnit ?: return@forEachIndexed
            needs[component.id] = (needs[component.id] ?: 0.0) + amount
        }
    }
    return needs
}

/** Every booking against every part, keyed by component. */
fun bookingsByComponent(
    projects: List<ProjectEntity>,
    roomsByProject: Map<Long, List<RoomAreaEntity>>,
    productsById: Map<Long, ProductWithComponents>,
): Map<Long, List<Booking>> {
    val out = mutableMapOf<Long, MutableList<Booking>>()
    projects.filter { it.booksMaterial }.forEach { project ->
        needsByComponent(roomsByProject[project.id].orEmpty(), productsById).forEach { (componentId, amount) ->
            if (amount > 0.0) {
                out.getOrPut(componentId) { mutableListOf() }.add(Booking(project.id, project.name, amount))
            }
        }
    }
    return out
}

/** The shelf for one product, part by part. */
fun partStock(
    component: ProductComponentEntity,
    stock: StockEntity?,
    bookings: List<Booking>,
): PartStock = PartStock(
    productId = component.productId,
    componentId = component.id,
    label = component.label,
    packSize = component.packageSize,
    packUnit = component.packageUnit,
    packType = component.packageType,
    fullPacks = stock?.fullPacks ?: 0,
    openAmount = stock?.openAmount ?: 0.0,
    bookings = bookings,
)
