package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.StockDao
import com.conwic.mixmaster.data.db.entity.StockEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.floor

class StockRepository(private val stockDao: StockDao) {

    fun observeAll(): Flow<List<StockEntity>> = stockDao.observeAll()

    /** Records a count: so many unopened packs, and so much left in the open one. */
    suspend fun set(productId: Long, fullPacks: Int, openAmount: Double) {
        val existing = stockDao.getForProduct(productId)
        val row = StockEntity(
            id = existing?.id ?: 0L,
            productId = productId,
            fullPacks = fullPacks.coerceAtLeast(0),
            openAmount = openAmount.coerceAtLeast(0.0),
            updatedAt = System.currentTimeMillis(),
        )
        if (existing == null) stockDao.insert(row) else stockDao.update(row)
    }

    /**
     * A quick correction — two bags taken to site, one found behind the door. Not a count, so the
     * date the shelf was last counted is left alone.
     */
    suspend fun adjustPacks(productId: Long, delta: Int) {
        val existing = stockDao.getForProduct(productId)
        val packs = ((existing?.fullPacks ?: 0) + delta).coerceAtLeast(0)
        if (existing == null) {
            stockDao.insert(StockEntity(productId = productId, fullPacks = packs))
        } else {
            stockDao.update(existing.copy(fullPacks = packs))
        }
    }

    /**
     * Puts a delivery on the shelf.
     *
     * Loose amounts are rolled up into whole packs where they make one, so a shed that takes in
     * two 25 kg canisters twice reads as four canisters rather than 100 kg of "open pack".
     */
    suspend fun add(productId: Long, packs: Int, amount: Double, packSize: Double) {
        val existing = stockDao.getForProduct(productId)
        val open = (existing?.openAmount ?: 0.0) + amount.coerceAtLeast(0.0)
        val rolled = if (packSize > 0.0) floor(open / packSize).toInt() else 0
        val row = StockEntity(
            id = existing?.id ?: 0L,
            productId = productId,
            fullPacks = (existing?.fullPacks ?: 0) + packs.coerceAtLeast(0) + rolled,
            openAmount = open - rolled * packSize,
            updatedAt = System.currentTimeMillis(),
        )
        if (existing == null) stockDao.insert(row) else stockDao.update(row)
    }

    /**
     * Takes [amount] off the shelf, in the pack's own unit.
     *
     * Worked out as one total and split back into packs afterwards, rather than tracking which
     * bag got opened: what is left over is what matters, and the shelf never goes below empty.
     */
    suspend fun take(productId: Long, amount: Double, packSize: Double) {
        val existing = stockDao.getForProduct(productId) ?: return
        val remaining = (existing.fullPacks * packSize + existing.openAmount - amount).coerceAtLeast(0.0)
        val packs = if (packSize > 0.0) floor(remaining / packSize).toInt() else 0
        stockDao.update(
            existing.copy(
                fullPacks = packs,
                openAmount = remaining - packs * packSize,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
