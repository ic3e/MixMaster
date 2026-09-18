package com.conwic.mixmaster.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.data.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComponentFormRow(val label: String, val ratioText: String)

data class ProductFormState(
    val productId: Long = 0L,
    val brand: String = "",
    val name: String = "",
    val category: String = "",
    val dosingMode: DosingMode = DosingMode.COATS,
    val doseGramsPerM2Text: String = "",
    val doseUnitLabel: String = "",
    val rangeNote: String = "",
    val sourceNote: String = "",
    val components: List<ComponentFormRow> = listOf(ComponentFormRow("Part A", "100"), ComponentFormRow("Part B", "")),
    val isLoaded: Boolean = false,
) {
    val isValid: Boolean
        get() = brand.isNotBlank() && name.isNotBlank() && category.isNotBlank() &&
            doseGramsPerM2Text.toDoubleOrNull() != null &&
            components.any { it.label.isNotBlank() && it.ratioText.toDoubleOrNull() != null }
}

class AddEditProductViewModel(
    private val productRepository: ProductRepository,
    private val productId: Long?,
) : ViewModel() {

    private val _formState = MutableStateFlow(ProductFormState(isLoaded = productId == null))
    val formState: StateFlow<ProductFormState> = _formState.asStateFlow()

    init {
        if (productId != null) {
            viewModelScope.launch {
                productRepository.getWithComponents(productId)?.let { data ->
                    _formState.update {
                        ProductFormState(
                            productId = data.product.id,
                            brand = data.product.brand,
                            name = data.product.name,
                            category = data.product.category,
                            dosingMode = data.product.dosingMode,
                            doseGramsPerM2Text = data.product.typicalDoseGramsPerM2.toString(),
                            doseUnitLabel = data.product.doseUnitLabel,
                            rangeNote = data.product.rangeNote,
                            sourceNote = data.product.sourceNote,
                            components = data.components.map { ComponentFormRow(it.label, it.ratioParts.toString()) },
                            isLoaded = true,
                        )
                    }
                }
            }
        }
    }

    fun setBrand(value: String) = _formState.update { it.copy(brand = value) }
    fun setName(value: String) = _formState.update { it.copy(name = value) }
    fun setCategory(value: String) = _formState.update { it.copy(category = value) }
    fun setDosingMode(value: DosingMode) = _formState.update { it.copy(dosingMode = value) }
    fun setDose(value: String) = _formState.update { it.copy(doseGramsPerM2Text = value) }
    fun setDoseUnitLabel(value: String) = _formState.update { it.copy(doseUnitLabel = value) }
    fun setRangeNote(value: String) = _formState.update { it.copy(rangeNote = value) }
    fun setSourceNote(value: String) = _formState.update { it.copy(sourceNote = value) }

    fun setComponentLabel(index: Int, value: String) = _formState.update { state ->
        state.copy(components = state.components.mapIndexed { i, row -> if (i == index) row.copy(label = value) else row })
    }

    fun setComponentRatio(index: Int, value: String) = _formState.update { state ->
        state.copy(components = state.components.mapIndexed { i, row -> if (i == index) row.copy(ratioText = value) else row })
    }

    fun addComponentRow() = _formState.update { it.copy(components = it.components + ComponentFormRow("", "")) }

    fun removeComponentRow(index: Int) = _formState.update { state ->
        state.copy(components = state.components.filterIndexed { i, _ -> i != index })
    }

    fun save(onSaved: () -> Unit) {
        val state = _formState.value
        if (!state.isValid) return
        viewModelScope.launch {
            val product = ProductEntity(
                id = state.productId,
                brand = state.brand.trim(),
                name = state.name.trim(),
                category = state.category.trim(),
                dosingMode = state.dosingMode,
                typicalDoseGramsPerM2 = state.doseGramsPerM2Text.toDoubleOrNull() ?: 0.0,
                doseUnitLabel = state.doseUnitLabel.trim(),
                rangeNote = state.rangeNote.trim(),
                sourceNote = state.sourceNote.trim(),
            )
            val components = state.components
                .filter { it.label.isNotBlank() && it.ratioText.toDoubleOrNull() != null }
                .mapIndexed { index, row ->
                    ProductComponentEntity(productId = 0, label = row.label.trim(), ratioParts = row.ratioText.toDouble(), sortOrder = index)
                }
            productRepository.save(product, components)
            onSaved()
        }
    }
}
