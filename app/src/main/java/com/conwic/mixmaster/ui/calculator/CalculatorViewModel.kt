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
import kotlinx.coroutines.launch

data class CalculatorUiState(
    val selectedProduct: ProductWithComponents? = null,
    val areaInput: String = "",
    val quantityInput: String = "1",
    /** The dose currently in effect — the product's datasheet typical until the slider is
     * moved, then whatever the slider is set to. */
    val coverageValue: Double = 0.0,
    val result: MixResult? = null,
)

private data class CalculatorInputs(
    val productId: Long? = null,
    val areaText: String = "",
    val quantityText: String = "1",
    /** null = not yet overridden by the user; falls back to the product's typical dose. */
    val coverageOverride: Double? = null,
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
                val coverage = input.coverageOverride ?: productWithComponents?.product?.typicalDoseGramsPerM2 ?: 0.0
                val result = if (productWithComponents != null && area != null && area > 0.0) {
                    MixCalculator.compute(productWithComponents, area, quantity, doseGramsPerM2 = coverage)
                } else {
                    null
                }
                CalculatorUiState(
                    selectedProduct = productWithComponents,
                    areaInput = input.areaText,
                    quantityInput = input.quantityText,
                    coverageValue = coverage,
                    result = result,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalculatorUiState())

    fun selectProduct(productId: Long) {
        inputs.update { it.copy(productId = productId, coverageOverride = null) }
    }

    fun setArea(text: String) {
        inputs.update { it.copy(areaText = text) }
    }

    fun setQuantity(text: String) {
        inputs.update { it.copy(quantityText = text) }
    }

    fun setCoverage(value: Double) {
        inputs.update { it.copy(coverageOverride = value) }
    }

    /** Logs the current coverage value as a real site reading — feeds Product Detail's
     * "your site average", separate from the fixed datasheet figure. */
    fun logUsage(onLogged: () -> Unit) {
        val state = uiState.value
        val productId = state.selectedProduct?.product?.id ?: return
        viewModelScope.launch {
            productRepository.logUsage(productId, state.coverageValue)
            onLogged()
        }
    }
}
