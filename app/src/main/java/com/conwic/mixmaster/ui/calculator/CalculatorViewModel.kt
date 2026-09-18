package com.conwic.mixmaster.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.dao.ProductWithComponents
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.domain.MixCalculator
import com.conwic.mixmaster.domain.MixResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CalculatorUiState(
    val selectedProduct: ProductWithComponents? = null,
    val areaInput: String = "",
    val quantityInput: String = "1",
    val result: MixResult? = null,
)

private data class CalculatorInputs(
    val productId: Long? = null,
    val areaText: String = "",
    val quantityText: String = "1",
)

class CalculatorViewModel(private val productRepository: ProductRepository) : ViewModel() {

    val products: StateFlow<List<ProductEntity>> =
        productRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val inputs = MutableStateFlow(CalculatorInputs())

    val uiState: StateFlow<CalculatorUiState> = inputs
        .flatMapLatest { input ->
            val productFlow = input.productId?.let { productRepository.observeWithComponents(it) } ?: flowOf(null)
            productFlow.map { productWithComponents ->
                val area = input.areaText.toDoubleOrNull()
                val quantity = input.quantityText.toDoubleOrNull() ?: 1.0
                val result = if (productWithComponents != null && area != null && area > 0.0) {
                    MixCalculator.compute(productWithComponents, area, quantity)
                } else {
                    null
                }
                CalculatorUiState(
                    selectedProduct = productWithComponents,
                    areaInput = input.areaText,
                    quantityInput = input.quantityText,
                    result = result,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalculatorUiState())

    fun selectProduct(productId: Long) {
        inputs.update { it.copy(productId = productId) }
    }

    fun setArea(text: String) {
        inputs.update { it.copy(areaText = text) }
    }

    fun setQuantity(text: String) {
        inputs.update { it.copy(quantityText = text) }
    }
}
