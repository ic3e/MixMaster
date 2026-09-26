package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.DeliveryDao
import com.conwic.mixmaster.data.db.entity.DeliveryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import android.content.Context
import com.conwic.mixmaster.data.company.CompanyStore

/**
 * Orders placed against the warehouse.
 *
 * Receiving one is the only thing here that touches the shelf, and it does both halves at once
 * — the count goes up and the line is closed off — so nothing can end up counted twice by
 * someone ticking it off and then counting the bags in as well.
 */
class DeliveryRepository(
    private val deliveryDao: DeliveryDao,
    private val stockRepository: StockRepository,
    private val context: Context,
) {

    fun observeAll(): Flow<List<DeliveryEntity>> = deliveryDao.observeAll()

    suspend fun order(
        productId: Long,
        packs: Int,
        amount: Double,
        expectedOn: LocalDate,
        note: String,
    ) {
        deliveryDao.insert(
            DeliveryEntity(
                productId = productId,
                packs = packs.coerceAtLeast(0),
                amount = amount.coerceAtLeast(0.0),
                expectedOn = expectedOn,
                orderedOn = LocalDate.now(),
                note = note.trim(),
                // By the name the company knows this phone by; nobody to name on a phone of its own.
                orderedBy = CompanyStore.current(context)?.name.orEmpty(),
            ),
        )
    }

    /** It turned up: onto the shelf it goes, and the line is closed. */
    suspend fun receive(delivery: DeliveryEntity, packSize: Double) {
        if (delivery.arrivedOn != null) return
        stockRepository.add(
            productId = delivery.productId,
            packs = delivery.packs,
            amount = delivery.amount,
            packSize = packSize,
        )
        deliveryDao.update(delivery.copy(arrivedOn = LocalDate.now()))
    }

    /** Not here yet — ask again on [expectedOn]. */
    suspend fun postpone(delivery: DeliveryEntity, expectedOn: LocalDate) {
        deliveryDao.update(delivery.copy(expectedOn = expectedOn))
    }

    suspend fun cancel(id: Long) = deliveryDao.delete(id)
}
