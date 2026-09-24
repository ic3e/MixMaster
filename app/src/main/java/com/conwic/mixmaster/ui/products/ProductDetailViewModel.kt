package com.conwic.mixmaster.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.data.repository.StockRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDetailViewModel(
    private val productRepository: ProductRepository,
    private val solutionRepository: SolutionRepository,
    private val stockRepository: StockRepository,
    private val productId: Long,
) : ViewModel() {

    val product: StateFlow<ProductEntity?> = productRepository.observeById(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The recipes that call for this, so it is clear what a change here would touch. */
    val usedIn: StateFlow<List<SolutionEntity>> = solutionRepository.observeSolutionsUsing(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * What is on the shelf for this, in its own units.
     *
     * The warehouse knew it and this page didn't, so "have we got any?" meant leaving the
     * product you were looking at and finding it again in another list.
     */
    val onShelf: StateFlow<Double?> = combine(
        productRepository.observeById(productId),
        stockRepository.observeAll(),
    ) { product, stock ->
        if (product == null) {
            null
        } else {
            val row = stock.firstOrNull { it.productId == productId }
            (row?.fullPacks ?: 0) * product.packageSize + (row?.openAmount ?: 0.0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            product.value?.let { productRepository.delete(it) }
            onDeleted()
        }
    }
}
