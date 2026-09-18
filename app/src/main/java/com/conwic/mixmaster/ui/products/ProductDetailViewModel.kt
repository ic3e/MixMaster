package com.conwic.mixmaster.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.dao.ProductWithComponents
import com.conwic.mixmaster.data.db.entity.UsageLogEntity
import com.conwic.mixmaster.data.repository.ProductRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductDetailViewModel(
    private val productRepository: ProductRepository,
    private val productId: Long,
) : ViewModel() {

    val productWithComponents: StateFlow<ProductWithComponents?> =
        productRepository.observeWithComponents(productId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val usageLogs: StateFlow<List<UsageLogEntity>> =
        productRepository.observeUsageLogs(productId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            productWithComponents.value?.product?.let { productRepository.delete(it) }
            onDeleted()
        }
    }
}
