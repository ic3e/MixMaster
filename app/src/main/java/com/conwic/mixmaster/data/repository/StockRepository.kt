package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.StockDao
import com.conwic.mixmaster.data.db.entity.StockEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.floor

class StockRepository(private val stockDao: StockDao) {

    fun observeAll(): Flow<List<StockEntity>> = stockDao.observeAll()

    /** Records a count: so many unopened packs, and so much left in the open one. */
    suspend fun set(productId: Long, componentId: Long, fullPacks: Int, openAmount: Double) {
        val existing = stockDao.getForComponent(componentId)
        val row = StockEntity(
            id = existing?.id ?: 0L,
            productId = productId,
            componentId = componentId,
            fullPacks = fullPacks.coerceAtLeast(0),
            openAmount = openAmount.coerceAtLeast(0.0),
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
    suspend fun take(componentId: Long, amount: Double, packSize: Double) {
        val existing = stockDao.getForComponent(componentId) ?: return
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
