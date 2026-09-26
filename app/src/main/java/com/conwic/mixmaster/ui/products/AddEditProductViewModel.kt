package com.conwic.mixmaster.ui.products

import androidx.annotation.StringRes
import android.content.Context
import android.net.Uri
import com.conwic.mixmaster.data.docs.SheetStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.domain.densityProblem
import com.conwic.mixmaster.domain.densityWarning
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.toNumberOr
import com.conwic.mixmaster.domain.toNumberOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The units a product is bought in. */
val PackUnits = listOf("kg", "L")

/** What it comes in. Free text would give four spellings of "bucket" inside a week. */
val PackTypes = listOf("bag", "bucket", "canister", "bottle", "drum", "tub")

data class ProductFormState(
    val productId: Long = 0L,
    val isLoaded: Boolean = false,
    val brand: String = "",
    val name: String = "",
    val category: String = "",
    val packSizeText: String = "",
    val packUnit: String = "kg",
    val packType: String = "bag",
    val densityText: String = "",
    val datasheetUrl: String = "",
    /** A link the manufacturer publishes, or a file:// URI of a PDF copied into the app. */
    val safetySheet: String = "",
    val technicalSheet: String = "",
    /** Found on site rather than bought — water from the tap. Kept off the shelf and off orders. */
    val suppliedOnSite: Boolean = false,
    val brands: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
) {
    /** A product with no name is a row nobody can pick out of a list. */
    @get:StringRes
    val nameProblem: Int? get() = if (name.isBlank()) R.string.product_problem_no_name else null

    @get:StringRes
    val densityProblemRes: Int?
        get() = densityText.takeIf { it.isNotBlank() }?.let { densityProblem(it.toNumberOrNull()) }

    @get:StringRes
    val densityWarningRes: Int?
        get() = densityText.takeIf { it.isNotBlank() }?.let { densityWarning(it.toNumberOrNull()) }

    val isValid: Boolean get() = nameProblem == null && densityProblemRes == null
}

class AddEditProductViewModel(
    private val productRepository: ProductRepository,
    private val productId: Long?,
) : ViewModel() {

    private val _formState = MutableStateFlow(ProductFormState())
    val formState: StateFlow<ProductFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = productId?.takeIf { it > 0L }?.let { productRepository.getById(it) }
            _formState.update {
                if (existing == null) {
                    it.copy(isLoaded = true)
                } else {
                    it.copy(
                        isLoaded = true,
                        productId = existing.id,
                        brand = existing.brand,
                        name = existing.name,
                        category = existing.category,
                        packSizeText = if (existing.packageSize > 0.0) formatDecimal(existing.packageSize, 2) else "",
                        packUnit = existing.packageUnit,
                        packType = existing.packageType,
                        densityText = if (existing.densityKgPerL > 0.0) formatDecimal(existing.densityKgPerL, 3) else "",
                        datasheetUrl = existing.datasheetUrl,
                        safetySheet = existing.safetySheetUrl,
                        technicalSheet = existing.technicalSheetUrl,
                        suppliedOnSite = existing.suppliedOnSite,
                    )
                }
            }
        }
        viewModelScope.launch {
            productRepository.observeBrands().collect { brands -> _formState.update { it.copy(brands = brands) } }
        }
        viewModelScope.launch {
            productRepository.observeCategories().collect { rows -> _formState.update { it.copy(categories = rows) } }
        }
    }

    fun setBrand(value: String) = _formState.update { it.copy(brand = value) }
    fun setName(value: String) = _formState.update { it.copy(name = value) }
    fun setCategory(value: String) = _formState.update { it.copy(category = value) }
    fun setPackSize(value: String) = _formState.update { it.copy(packSizeText = value) }
    fun setPackUnit(value: String) = _formState.update { it.copy(packUnit = value) }
    fun setPackType(value: String) = _formState.update { it.copy(packType = value) }
    fun setDensity(value: String) = _formState.update { it.copy(densityText = value) }
    fun setSuppliedOnSite(value: Boolean) = _formState.update { it.copy(suppliedOnSite = value) }
    fun setDatasheetUrl(value: String) = _formState.update { it.copy(datasheetUrl = value) }

    fun setSafetySheet(value: String) = _formState.update { it.copy(safetySheet = value) }

    fun setTechnicalSheet(value: String) = _formState.update { it.copy(technicalSheet = value) }

    /**
     * Takes a sheet off, and the copy the app made of it with it.
     *
     * Done here rather than on save: a file dropped and then never saved would otherwise sit in
     * the app's folder for the life of the install.
     */
    fun clearSheet(context: Context, safety: Boolean) {
        val current = if (safety) _formState.value.safetySheet else _formState.value.technicalSheet
        _formState.update { if (safety) it.copy(safetySheet = "") else it.copy(technicalSheet = "") }
        if (SheetStore.isStored(current)) {
            viewModelScope.launch { SheetStore.forget(context, current) }
        }
    }

    /** Copies a picked PDF in and hangs it on the product. */
    fun attachSheet(context: Context, source: Uri, name: String, safety: Boolean, onFailed: () -> Unit) {
        viewModelScope.launch {
            val stored = SheetStore.keep(context, source, name)
            if (stored == null) {
                onFailed()
            } else {
                _formState.update {
                    if (safety) it.copy(safetySheet = stored) else it.copy(technicalSheet = stored)
                }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _formState.value
        if (!state.isValid) return
        viewModelScope.launch {
            productRepository.saveProduct(
                ProductEntity(
                    id = state.productId,
                    brand = state.brand.trim(),
                    name = state.name.trim(),
                    category = state.category.trim(),
                    // A bought item has no coverage and no ratio — those belong to the solution
                    // it goes into. The columns are still on the row, left at nothing.
                    dosingMode = DosingMode.COATS,
                    minDoseGramsPerM2 = 0.0,
                    maxDoseGramsPerM2 = 0.0,
                    typicalDoseGramsPerM2 = 0.0,
                    doseUnitLabel = "",
                    rangeNote = "",
                    sourceNote = "",
                    datasheetUrl = state.datasheetUrl.trim(),
                    ratioLabel = "",
                    packageSize = state.packSizeText.toNumberOr(0.0),
                    packageUnit = state.packUnit,
                    packageType = state.packType,
                    densityKgPerL = state.densityText.toNumberOr(0.0),
                    safetySheetUrl = state.safetySheet.trim(),
                    technicalSheetUrl = state.technicalSheet.trim(),
                    // Carried through by hand: this row is built fresh from the form, and anything
                    // the form does not know about would be reset to its default on every save.
                    suppliedOnSite = state.suppliedOnSite,
                ),
            )
            onSaved()
        }
    }
}
