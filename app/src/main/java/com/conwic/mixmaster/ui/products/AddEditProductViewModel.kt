package com.conwic.mixmaster.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProductComponentEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.domain.densityProblem as domainDensityProblem
import com.conwic.mixmaster.domain.densityWarning as domainDensityWarning
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.isWaterLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stable ids so the list of parts can be keyed — without them every keystroke re-lays out
 * every card on the form, which is a good part of why it dragged. */
private var componentUidCounter = 0L

data class ComponentFormRow(
    val uid: Long = ++componentUidCounter,
    val label: String,
    val ratioText: String,
    val basis: String = "Weight",
    val density: String = "",
    val potLife: String = "",
    val notes: String = "",
    val packSizeText: String = "",
    val packUnit: String = "kg",
    val packType: String = "bag",
    val densityKgPerLText: String = "",
) {
    private val densityValue: Double?
        get() = densityKgPerLText.trim().takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull()

    /** Blocks the save. The rule itself lives in the domain, so the calculator agrees with it. */
    val densityProblem: String?
        get() = if (densityKgPerLText.isBlank()) null else domainDensityProblem(densityValue)

    /** Doesn't block — see the domain rule. */
    val densityWarning: String?
        get() = if (densityKgPerLText.isBlank()) null else domainDensityWarning(densityValue)
}

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
    /** Marks this as a colour or admixture that goes into another product's mix. */
    val isAddOn: Boolean = false,
    val addOnAmountText: String = "",
    /** g, kg, ml or L — what [addOnAmountText] counts. */
    val addOnUnitChoice: String = "g",
    /** …per this many kg of the part it's measured against. */
    val addOnPerKgText: String = "1",
    val components: List<ComponentFormRow> = listOf(ComponentFormRow(label = "Part A", ratioText = "100"), ComponentFormRow(label = "Part B", ratioText = "")),
    val isLoaded: Boolean = false,
) {
    val isValid: Boolean
        get() {
            val min = minDoseText.toDoubleOrNull()
            val max = maxDoseText.toDoubleOrNull()
            return brand.isNotBlank() && name.isNotBlank() && category.isNotBlank() &&
                min != null && max != null && min > 0.0 && max >= min &&
                components.any { it.label.isNotBlank() && it.ratioText.toDoubleOrNull() != null } &&
                components.none { it.densityProblem != null }
        }
}

/** "28 g per 1 kg" becomes 0.028 kg per kg; "1 L per 25 kg" becomes 0.04 L per kg. */
internal fun normaliseAddOnDose(amountText: String, unitChoice: String, perKgText: String): Pair<Double, String> {
    val amount = amountText.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    val perKg = perKgText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
    val inBaseUnit = when (unitChoice) {
        "g", "ml" -> amount / 1000.0
        else -> amount
    }
    val unit = if (unitChoice == "ml" || unitChoice == "L") "L" else "kg"
    return (inBaseUnit / perKg) to unit
}

/** The inverse, for showing a stored dose in whatever unit reads best. */
internal fun describeAddOnDose(amountPerKg: Double, unit: String): Triple<String, String, String> {
    if (amountPerKg <= 0.0) return Triple("", if (unit == "L") "ml" else "g", "1")
    val small = amountPerKg < 1.0
    val shown = if (small) amountPerKg * 1000.0 else amountPerKg
    val shownUnit = when {
        unit == "L" && small -> "ml"
        unit == "L" -> "L"
        small -> "g"
        else -> "kg"
    }
    return Triple(formatDecimal(shown, 3), shownUnit, "1")
}

private fun computeRatioLabel(components: List<ComponentFormRow>): String {
    val valid = components.filter { it.label.isNotBlank() && it.ratioText.toDoubleOrNull() != null }
    if (valid.size <= 1) return "1K"
    return valid.joinToString(":") { (it.ratioText.toDoubleOrNull() ?: 0.0).toInt().toString() }
}

/** Everything already in the catalogue, offered back so the same thing isn't typed two ways. */
data class ProductSuggestions(
    val brands: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val doseUnitLabels: List<String> = emptyList(),
    val componentLabels: List<String> = emptyList(),
)

class AddEditProductViewModel(
    private val productRepository: ProductRepository,
    private val productId: Long?,
) : ViewModel() {

    private val _formState = MutableStateFlow(ProductFormState(isLoaded = productId == null))
    val formState: StateFlow<ProductFormState> = _formState.asStateFlow()

    val suggestions: StateFlow<ProductSuggestions> = combine(
        productRepository.observeBrands(),
        productRepository.observeCategories(),
        productRepository.observeDoseUnitLabels(),
        productRepository.observeComponentLabels(),
    ) { brands, categories, doseUnits, labels ->
        ProductSuggestions(brands, categories, doseUnits, labels)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductSuggestions())

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
                            isAddOn = data.product.isAddOn,
                            addOnAmountText = describeAddOnDose(data.product.addOnAmountPerKg, data.product.addOnUnit).first,
                            addOnUnitChoice = describeAddOnDose(data.product.addOnAmountPerKg, data.product.addOnUnit).second,
                            addOnPerKgText = "1",
                            components = data.components.map {
                                ComponentFormRow(
                                    label = it.label,
                                    ratioText = it.ratioParts.toString(),
                                    basis = it.basis,
                                    density = it.density,
                                    potLife = it.potLife,
                                    notes = it.notes,
                                    packSizeText = if (it.packageSize > 0.0) formatDecimal(it.packageSize, 2) else "",
                                    packUnit = it.packageUnit,
                                    packType = it.packageType,
                                    densityKgPerLText = if (it.densityKgPerL > 0.0) formatDecimal(it.densityKgPerL, 3) else "",
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
    fun setComponentPotLife(index: Int, value: String) = updateComponent(index) { it.copy(potLife = value) }
    fun setComponentNotes(index: Int, value: String) = updateComponent(index) { it.copy(notes = value) }
    fun setComponentPackSize(index: Int, value: String) = updateComponent(index) { it.copy(packSizeText = value) }
    fun setComponentPackUnit(index: Int, unit: String) = updateComponent(index) { it.copy(packUnit = unit) }
    fun setComponentPackType(index: Int, type: String) = updateComponent(index) { it.copy(packType = type) }
    fun setComponentDensityKgPerL(index: Int, value: String) = updateComponent(index) { it.copy(densityKgPerLText = value) }

    private fun updateComponent(index: Int, transform: (ComponentFormRow) -> ComponentFormRow) = _formState.update { state ->
        state.copy(components = state.components.mapIndexed { i, row -> if (i == index) transform(row) else row })
    }

    fun setIsAddOn(value: Boolean) = _formState.update { it.copy(isAddOn = value) }
    fun setAddOnAmount(value: String) = _formState.update { it.copy(addOnAmountText = value) }
    fun setAddOnUnitChoice(value: String) = _formState.update { it.copy(addOnUnitChoice = value) }
    fun setAddOnPerKg(value: String) = _formState.update { it.copy(addOnPerKgText = value) }

    fun addComponentRow() = _formState.update { it.copy(components = it.components + ComponentFormRow(label = "", ratioText = "")) }

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
                isAddOn = state.isAddOn,
                addOnAmountPerKg = if (state.isAddOn) {
                    normaliseAddOnDose(state.addOnAmountText, state.addOnUnitChoice, state.addOnPerKgText).first
                } else {
                    0.0
                },
                addOnUnit = normaliseAddOnDose(state.addOnAmountText, state.addOnUnitChoice, state.addOnPerKgText).second,
            )
            val components = state.components
                .filter { it.label.isNotBlank() && it.ratioText.toDoubleOrNull() != null }
                .mapIndexed { index, row ->
                    // Water is 1 kg/L whatever was typed, so nobody has to remember to fill it in.
                    val typedDensity = row.densityKgPerLText.replace(',', '.').toDoubleOrNull() ?: 0.0
                    val densityValue = if (isWaterLabel(row.label)) 1.0 else typedDensity
                    ProductComponentEntity(
                        productId = 0,
                        label = row.label.trim(),
                        ratioParts = row.ratioText.toDouble(),
                        basis = row.basis,
                        // Kept in step with the numeric density so the detail screen's note and
                        // the maths can never disagree.
                        density = if (densityValue > 0.0) "${formatDecimal(densityValue, 3)} kg/L" else row.density.trim(),
                        potLife = row.potLife.trim(),
                        notes = row.notes.trim(),
                        sortOrder = index,
                        packageSize = row.packSizeText.toDoubleOrNull() ?: 0.0,
                        packageUnit = row.packUnit,
                        packageType = row.packType,
                        densityKgPerL = densityValue,
                    )
                }
            productRepository.save(product, components)
            onSaved()
        }
    }
}
