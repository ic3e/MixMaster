package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.StockDao
import com.conwic.mixmaster.data.db.entity.StockEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.floor

/**
 * The shelf. A product's stock row always has the product's own id, so every phone in a company
 * writes the same row for it rather than each starting its own (see AppDatabase, 15 → 16).
 */
class StockRepository(private val stockDao: StockDao) {

    fun observeAll(): Flow<List<StockEntity>> = stockDao.observeAll()

    /** Records a count: so many unopened packs, and so much left in the open one. */
    suspend fun set(productId: Long, fullPacks: Int, openAmount: Double) {
        val existing = stockDao.getForProduct(productId)
        val row = StockEntity(
            id = existing?.id ?: productId,
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
    suspend fun setPacks(productId: Long, fullPacks: Int) {
        val existing = stockDao.getForProduct(productId)
        val packs = fullPacks.coerceAtLeast(0)
        if (existing == null) {
            stockDao.insert(StockEntity(id = productId, productId = productId, fullPacks = packs))
        } else {
            stockDao.update(existing.copy(fullPacks = packs))
        }
    }

    /**
     * Reads the shelf again in packs, once a product has been given a pack size.
     *
     * Until then everything of it was counted as one loose amount; 130 kg of a 25 kg bag is five
     * bags and 5 kg open, and that is how the next count should find it. The same amount either
     * way, so the date it was counted stays.
     */
    suspend fun rollUp(productId: Long, packSize: Double) {
        if (packSize <= 0.0) return
        val existing = stockDao.getForProduct(productId) ?: return
        val whole = floor(existing.openAmount / packSize).toInt()
        if (whole <= 0) return
        stockDao.update(
            existing.copy(
                fullPacks = existing.fullPacks + whole,
                openAmount = existing.openAmount - whole * packSize,
            ),
        )
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
            id = existing?.id ?: productId,
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
