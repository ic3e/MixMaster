package com.conwic.mixmaster.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.dao.ProductWithComponents
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.domain.BatchBasis
import com.conwic.mixmaster.domain.BatchPlan
import com.conwic.mixmaster.domain.MixCalculator
import com.conwic.mixmaster.domain.MixResult
import com.conwic.mixmaster.domain.PackNeed
import com.conwic.mixmaster.domain.packNeeds
import com.conwic.mixmaster.domain.planBatches
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    /** Average of what's actually been logged on site for this product, if anything has. */
    val siteAverageDose: Double? = null,
    val loggedJobCount: Int = 0,
    val packNeeds: List<PackNeed> = emptyList(),
    val batchBasis: BatchBasis = BatchBasis.MIXER_VOLUME,
    val mixerLitres: Double = 65.0,
    val maxBatchKg: Double = 25.0,
    val batchPlan: BatchPlan? = null,
)

private data class CalculatorInputs(
    val areaText: String = "",
    val quantityText: String = "1",
    /** null = not yet overridden by the user; falls back to the product's typical dose. */
    val coverageOverride: Double? = null,
    val batchBasis: BatchBasis = BatchBasis.MIXER_VOLUME,
    /** Mixer or bucket capacity, chosen in 5 L steps. */
    val mixerLitres: Double = 65.0,
    val maxBatchKg: Double = 25.0,
)

class CalculatorViewModel(private val productRepository: ProductRepository) : ViewModel() {

    val products: StateFlow<List<ProductEntity>> =
        productRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // The database flows are keyed on the product alone, kept separate from the typed inputs.
    // Folding them together would re-subscribe both queries on every keystroke and every pixel
    // of slider travel, which is what made dragging stutter.
    private val selectedProductId = MutableStateFlow<Long?>(null)
    private val inputs = MutableStateFlow(CalculatorInputs())

    private val selectedProductFlow = selectedProductId
        .flatMapLatest { id -> if (id == null) flowOf(null) else productRepository.observeWithComponents(id) }

    private val usageLogsFlow = selectedProductId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else productRepository.observeUsageLogs(id) }

    val uiState: StateFlow<CalculatorUiState> =
        combine(selectedProductFlow, usageLogsFlow, inputs) { productWithComponents, logs, input ->
            val area = input.areaText.toDoubleOrNull()
            val quantity = input.quantityText.toDoubleOrNull() ?: 1.0
            val coverage = input.coverageOverride ?: productWithComponents?.product?.typicalDoseGramsPerM2 ?: 0.0
            val result = if (productWithComponents != null && area != null && area > 0.0) {
                MixCalculator.compute(productWithComponents, area, quantity, doseGramsPerM2 = coverage)
            } else {
                null
            }
            val components = productWithComponents?.components.orEmpty()
            CalculatorUiState(
                selectedProduct = productWithComponents,
                areaInput = input.areaText,
                quantityInput = input.quantityText,
                coverageValue = coverage,
                result = result,
                siteAverageDose = if (logs.isEmpty()) null else logs.map { it.doseGramsPerM2 }.average(),
                loggedJobCount = logs.size,
                packNeeds = result?.let { packNeeds(it, components) }.orEmpty(),
                batchBasis = input.batchBasis,
                mixerLitres = input.mixerLitres,
                maxBatchKg = input.maxBatchKg,
                batchPlan = result?.let {
                    planBatches(it, components, input.batchBasis, input.mixerLitres, input.maxBatchKg)
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalculatorUiState())

    fun selectProduct(productId: Long) {
        selectedProductId.value = productId
        inputs.update { it.copy(coverageOverride = null) }
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

    fun setBatchBasis(basis: BatchBasis) {
        inputs.update { it.copy(batchBasis = basis) }
    }

    fun setMixerLitres(litres: Double) {
        inputs.update { it.copy(mixerLitres = litres) }
    }

    fun setMaxBatchKg(kg: Double) {
        inputs.update { it.copy(maxBatchKg = kg) }
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
