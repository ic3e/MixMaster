package com.conwic.mixmaster.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDetailViewModel(
    private val productRepository: ProductRepository,
    private val solutionRepository: SolutionRepository,
    private val productId: Long,
) : ViewModel() {

    val product: StateFlow<ProductEntity?> = productRepository.observeById(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The recipes that call for this, so it is clear what a change here would touch. */
    val usedIn: StateFlow<List<SolutionEntity>> = solutionRepository.observeSolutionsUsing(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            product.value?.let { productRepository.delete(it) }
            onDeleted()
        }
    }
}
