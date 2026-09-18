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

data class ComponentFormRow(
    val label: String,
    val ratioText: String,
    val basis: String = "Weight",
    val density: String = "",
    val potLife: String = "",
    val notes: String = "",
)

data class ProductFormState(
    val productId: Long = 0L,
    val brand: String = "",
    val name: String = "",
    val category: String = "",
    val dosingMode: DosingMode = DosingMode.COATS,
    val minDoseText: String = "",
    val maxDoseText: String = "",
    val doseUnitLabel: String = "",
    val rangeNote: String = "",
    val sourceNote: String = "",
    val datasheetUrl: String = "",
    val components: List<ComponentFormRow> = listOf(ComponentFormRow("Part A", "100"), ComponentFormRow("Part B", "")),
    val isLoaded: Boolean = false,
) {
    val isValid: Boolean
        get() {
            val min = minDoseText.toDoubleOrNull()
            val max = maxDoseText.toDoubleOrNull()
            return brand.isNotBlank() && name.isNotBlank() && category.isNotBlank() &&
                min != null && max != null && min > 0.0 && max >= min &&
                components.any { it.label.isNotBlank() && it.ratioText.toDoubleOrNull() != null }
        }
}

private fun computeRatioLabel(components: List<ComponentFormRow>): String {
    val valid = components.filter { it.label.isNotBlank() && it.ratioText.toDoubleOrNull() != null }
    if (valid.size <= 1) return "1K"
    return valid.joinToString(":") { (it.ratioText.toDoubleOrNull() ?: 0.0).toInt().toString() }
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
                            minDoseText = data.product.minDoseGramsPerM2.toString(),
                            maxDoseText = data.product.maxDoseGramsPerM2.toString(),
                            doseUnitLabel = data.product.doseUnitLabel,
                            rangeNote = data.product.rangeNote,
                            sourceNote = data.product.sourceNote,
                            datasheetUrl = data.product.datasheetUrl,
                            components = data.components.map {
                                ComponentFormRow(
                                    label = it.label,
                                    ratioText = it.ratioParts.toString(),
                                    basis = it.basis,
                                    density = it.density,
                                    potLife = it.potLife,
                                    notes = it.notes,
                                )
                            },
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
    fun setMinDose(value: String) = _formState.update { it.copy(minDoseText = value) }
    fun setMaxDose(value: String) = _formState.update { it.copy(maxDoseText = value) }
    fun setDoseUnitLabel(value: String) = _formState.update { it.copy(doseUnitLabel = value) }
    fun setRangeNote(value: String) = _formState.update { it.copy(rangeNote = value) }
    fun setSourceNote(value: String) = _formState.update { it.copy(sourceNote = value) }
    fun setDatasheetUrl(value: String) = _formState.update { it.copy(datasheetUrl = value) }

    fun setComponentLabel(index: Int, value: String) = updateComponent(index) { it.copy(label = value) }
    fun setComponentRatio(index: Int, value: String) = updateComponent(index) { it.copy(ratioText = value) }
    fun setComponentBasis(index: Int, basis: String) = updateComponent(index) { it.copy(basis = basis) }
    fun setComponentDensity(index: Int, value: String) = updateComponent(index) { it.copy(density = value) }
    fun setComponentPotLife(index: Int, value: String) = updateComponent(index) { it.copy(potLife = value) }
    fun setComponentNotes(index: Int, value: String) = updateComponent(index) { it.copy(notes = value) }

    private fun updateComponent(index: Int, transform: (ComponentFormRow) -> ComponentFormRow) = _formState.update { state ->
        state.copy(components = state.components.mapIndexed { i, row -> if (i == index) transform(row) else row })
    }

    fun addComponentRow() = _formState.update { it.copy(components = it.components + ComponentFormRow("", "")) }

    fun removeComponentRow(index: Int) = _formState.update { state ->
        state.copy(components = state.components.filterIndexed { i, _ -> i != index })
    }

    fun save(onSaved: () -> Unit) {
        val state = _formState.value
        if (!state.isValid) return
        viewModelScope.launch {
            val min = state.minDoseText.toDoubleOrNull() ?: 0.0
            val max = state.maxDoseText.toDoubleOrNull() ?: min
            val product = ProductEntity(
                id = state.productId,
                brand = state.brand.trim(),
                name = state.name.trim(),
                category = state.category.trim(),
                dosingMode = state.dosingMode,
                minDoseGramsPerM2 = min,
                maxDoseGramsPerM2 = max,
                typicalDoseGramsPerM2 = (min + max) / 2.0,
                doseUnitLabel = state.doseUnitLabel.trim(),
                rangeNote = state.rangeNote.trim(),
                sourceNote = state.sourceNote.trim(),
                datasheetUrl = state.datasheetUrl.trim(),
                ratioLabel = computeRatioLabel(state.components),
            )
            val components = state.components
                .filter { it.label.isNotBlank() && it.ratioText.toDoubleOrNull() != null }
                .mapIndexed { index, row ->
                    ProductComponentEntity(
                        productId = 0,
                        label = row.label.trim(),
                        ratioParts = row.ratioText.toDouble(),
                        basis = row.basis,
                        density = row.density.trim(),
                        potLife = row.potLife.trim(),
                        notes = row.notes.trim(),
                        sortOrder = index,
                    )
                }
            productRepository.save(product, components)
            onSaved()
        }
    }
}
