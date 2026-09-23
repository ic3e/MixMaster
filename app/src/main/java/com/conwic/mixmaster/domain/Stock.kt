package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.entity.DeliveryEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.RoomLayerEntity
import com.conwic.mixmaster.data.db.entity.StockEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import kotlin.math.ceil

/** One room's share of a booking, so "booked for Riverside" can say which bays and how much. */
data class BookingRoom(val roomId: Long, val roomName: String, val amount: Double)

/** A job with this product spoken for, and how much of it. */
data class Booking(
    val projectId: Long,
    val projectName: String,
    val amount: Double,
    /** The rooms behind [amount], in the order they are laid out. */
    val rooms: List<BookingRoom> = emptyList(),
)

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
    /** When the shelf was last counted, as epoch millis. Zero when nobody has counted it yet. */
    val countedAt: Long = 0L,
    /** Ordered and on its way, in [packUnit]. Not stock: it cannot be mixed or booked yet. */
    val onOrder: Double = 0.0,
) {
    /** Everything on the shelf, opened packs included. */
    val onHand: Double get() = fullPacks * packSize + openAmount

    /** What the unopened packs alone come to. */
    val packedAmount: Double get() = fullPacks * packSize

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

    /** What is short that nobody has ordered yet — the figure a new order is written from. */
    val stillToOrder: Double get() = (short - onOrder).coerceAtLeast(0.0)

    val packsStillToOrder: Int?
        get() = when {
            stillToOrder <= 0.0 -> 0
            packSize > 0.0 -> ceil(stillToOrder / packSize).toInt()
            else -> null
        }

    val isKnownPack: Boolean get() = packSize > 0.0
}

/** What one product has coming, in its own pack unit. Arrived lines are stock, so they are out. */
fun onOrderAmount(deliveries: List<DeliveryEntity>, packSize: Double): Double =
    deliveries.filter { it.arrivedOn == null }.sumOf { it.packs * packSize + it.amount }

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
 * How much of each product one room needs, in each product's own pack unit.
 *
 * A part whose litres can't be worked out — an L-sold pack with no density — is left out
 * rather than guessed at, so it shows as "pack size not set" instead of a made-up figure.
 */
fun needsForRoom(
    room: RoomAreaEntity,
    layers: List<RoomLayerEntity>,
    mixes: Map<Long, SolutionMix>,
    products: Map<Long, ProductEntity>,
): Map<Long, Double> {
    if (room.areaM2 <= 0.0) return emptyMap()
    val needs = mutableMapOf<Long, Double>()
    fun add(productId: Long, amount: Double) {
        if (amount > 0.0) needs[productId] = (needs[productId] ?: 0.0) + amount
    }

    layers.sortedBy { it.sortOrder }.forEach { layer ->
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
    return needs
}

/** How much of each product a set of rooms needs, in each product's own pack unit. */
fun needsByProduct(
    rooms: List<RoomAreaEntity>,
    layersByRoom: Map<Long, List<RoomLayerEntity>>,
    mixes: Map<Long, SolutionMix>,
    products: Map<Long, ProductEntity>,
): Map<Long, Double> {
    val needs = mutableMapOf<Long, Double>()
    rooms.forEach { room ->
        needsForRoom(room, layersByRoom[room.id].orEmpty(), mixes, products).forEach { (productId, amount) ->
            needs[productId] = (needs[productId] ?: 0.0) + amount
        }
    }
    return needs
}

/**
 * Every booking against every product, keyed by product.
 *
 * The room behind each figure is kept, not just the job total: standing in the shed, "1446 kg
 * for Riverside" is only half the answer — the other half is which bays it is going on.
 */
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
        val totals = mutableMapOf<Long, Double>()
        val byRoom = mutableMapOf<Long, MutableList<BookingRoom>>()
        roomsByProject[project.id].orEmpty().sortedBy { it.sortOrder }.forEach { room ->
            needsForRoom(room, layersByRoom[room.id].orEmpty(), mixes, products)
                .forEach { (productId, amount) ->
                    if (amount > 0.0) {
                        totals[productId] = (totals[productId] ?: 0.0) + amount
                        byRoom.getOrPut(productId) { mutableListOf() }
                            .add(BookingRoom(room.id, room.name, amount))
                    }
                }
        }
        totals.forEach { (productId, amount) ->
            out.getOrPut(productId) { mutableListOf() }
                .add(Booking(project.id, project.name, amount, byRoom[productId].orEmpty()))
        }
    }
    return out
}

/** The shelf for one bought item. */
fun productStock(
    product: ProductEntity,
    stock: StockEntity?,
    bookings: List<Booking>,
    deliveries: List<DeliveryEntity> = emptyList(),
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
    countedAt = stock?.updatedAt ?: 0L,
    onOrder = onOrderAmount(deliveries, product.packageSize),
)
