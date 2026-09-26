package com.conwic.mixmaster.ui.warehouse

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.prefs.ShelfFigure
import com.conwic.mixmaster.data.prefs.StockCountState
import com.conwic.mixmaster.data.prefs.StockCountStore
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.StockRepository
import com.conwic.mixmaster.domain.ProductStock
import com.conwic.mixmaster.domain.productStock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StockCountViewModel(
    private val app: Context,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
) : ViewModel() {

    /** Every product the warehouse lists, brand by brand — the order you walk the racks in. */
    val rows: StateFlow<List<ProductStock>?> = combine(
        productRepository.observeAll(),
        stockRepository.observeAll(),
    ) { products, stock ->
        val byProduct = stock.associateBy { it.productId }
        products
            .map { productStock(product = it, stock = byProduct[it.id], bookings = emptyList()) }
            .sortedWith(compareBy({ it.brand.isBlank() }, { it.brand.lowercase() }, { it.name.lowercase() }))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val count: StateFlow<StockCountState> = StockCountStore.state(app)

    /**
     * One write at a time, in the order they were made. Finishing reads the shelf back, and it
     * has to see the figure typed a moment before the button was pressed, not the one before it.
     */
    private val writes = Mutex()

    init {
        // The shelf as it stood before anything was touched, for the summary at the end. Taken
        // once; opening the screen again on a count under way carries on with that one.
        viewModelScope.launch {
            val shelf = rows.filterNotNull().first()
            StockCountStore.start(app, shelf.associate { it.productId to ShelfFigure(it.fullPacks, it.openAmount) })
        }
    }

    fun save(productId: Long, packs: Int, open: Double) {
        StockCountStore.markCounted(app, productId)
        viewModelScope.launch { writes.withLock { stockRepository.set(productId, packs, open) } }
    }

    /** As the app had it: written back all the same, which dates the count. */
    fun same(item: ProductStock) = save(item.productId, item.fullPacks, item.openAmount)

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            val shelf = writes.withLock {
                val products = productRepository.observeAll().first()
                val stock = stockRepository.observeAll().first().associateBy { it.productId }
                products.associate { product ->
                    val row = stock[product.id]
                    product.id to ShelfFigure(row?.fullPacks ?: 0, row?.openAmount ?: 0.0)
                }
            }
            StockCountStore.finish(app, shelf)
            StockCountReminder.dismiss(app)
            StockCountReminder.schedule(app)
            onDone()
        }
    }
}
