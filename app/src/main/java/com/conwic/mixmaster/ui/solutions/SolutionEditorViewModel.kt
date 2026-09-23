package com.conwic.mixmaster.ui.solutions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineRole
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.toNumberOr
import com.conwic.mixmaster.domain.toNumberOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line of the recipe as it is being edited. */
data class LineDraft(
    val productId: Long = 0L,
    val label: String = "",
    val partsText: String = "",
)

data class SolutionFormState(
    val solutionId: Long = 0L,
    val isLoaded: Boolean = false,
    val brand: String = "",
    val name: String = "",
    val category: String = "",
    val dosingMode: DosingMode = DosingMode.COATS,
    val minDoseText: String = "",
    val maxDoseText: String = "",
    val doseUnitLabel: String = "",
    val datasheetUrl: String = "",
    val lines: List<LineDraft> = listOf(LineDraft(), LineDraft()),
    val products: List<ProductEntity> = emptyList(),
    val brands: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
) {
    @get:StringRes
    val nameProblem: Int? get() = if (name.isBlank()) R.string.solution_problem_no_name else null

    /** A recipe with nothing in it cannot be mixed. */
    @get:StringRes
    val linesProblem: Int?
        get() = when {
            lines.none { it.productId > 0L } -> R.string.solution_problem_no_parts
            lines.filter { it.productId > 0L }.none { (it.partsText.toNumberOrNull() ?: 0.0) > 0.0 } ->
                R.string.solution_problem_no_ratio
            else -> null
        }

    /** A coverage of nothing would work every mix out as nothing. */
    @get:StringRes
    val doseProblem: Int?
        get() = if ((minDoseText.toNumberOrNull() ?: 0.0) <= 0.0 && (maxDoseText.toNumberOrNull() ?: 0.0) <= 0.0) {
            R.string.solution_problem_no_dose
        } else {
            null
        }

    val isValid: Boolean get() = nameProblem == null && linesProblem == null && doseProblem == null
}

class SolutionEditorViewModel(
    private val solutionRepository: SolutionRepository,
    private val productRepository: ProductRepository,
    private val solutionId: Long?,
) : ViewModel() {

    private val _formState = MutableStateFlow(SolutionFormState())
    val formState: StateFlow<SolutionFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = solutionId?.takeIf { it > 0L }?.let { id ->
                solutionRepository.getAllWithLines().firstOrNull { it.solution.id == id }
            }
            _formState.update { state ->
                if (existing == null) {
                    state.copy(isLoaded = true)
                } else {
                    val solution = existing.solution
                    state.copy(
                        isLoaded = true,
                        solutionId = solution.id,
                        brand = solution.brand,
                        name = solution.name,
                        category = solution.category,
                        dosingMode = solution.dosingMode,
                        minDoseText = if (solution.minDoseGramsPerM2 > 0.0) formatDecimal(solution.minDoseGramsPerM2, 1) else "",
                        maxDoseText = if (solution.maxDoseGramsPerM2 > 0.0) formatDecimal(solution.maxDoseGramsPerM2, 1) else "",
                        doseUnitLabel = solution.doseUnitLabel,
                        datasheetUrl = solution.datasheetUrl,
                        lines = existing.lines
                            .filter { it.role == SolutionLineRole.BASE }
                            .sortedBy { it.sortOrder }
                            .map {
                                LineDraft(
                                    productId = it.productId,
                                    label = it.label,
                                    partsText = formatDecimal(it.ratioParts, 2),
                                )
                            }
                            .ifEmpty { listOf(LineDraft(), LineDraft()) },
                    )
                }
            }
        }
        viewModelScope.launch {
            productRepository.observeAll().collect { rows -> _formState.update { it.copy(products = rows) } }
        }
        viewModelScope.launch {
            solutionRepository.observeBrands().collect { rows -> _formState.update { it.copy(brands = rows) } }
        }
        viewModelScope.launch {
            solutionRepository.observeCategories().collect { rows -> _formState.update { it.copy(categories = rows) } }
        }
    }

    fun setBrand(value: String) = _formState.update { it.copy(brand = value) }
    fun setName(value: String) = _formState.update { it.copy(name = value) }
    fun setCategory(value: String) = _formState.update { it.copy(category = value) }
    fun setDosingMode(mode: DosingMode) = _formState.update { it.copy(dosingMode = mode) }
    fun setMinDose(value: String) = _formState.update { it.copy(minDoseText = value) }
    fun setMaxDose(value: String) = _formState.update { it.copy(maxDoseText = value) }
    fun setDoseUnitLabel(value: String) = _formState.update { it.copy(doseUnitLabel = value) }
    fun setDatasheetUrl(value: String) = _formState.update { it.copy(datasheetUrl = value) }

    fun setLineProduct(index: Int, productId: Long) = updateLine(index) { it.copy(productId = productId) }

    fun setLineLabel(index: Int, label: String) = updateLine(index) { it.copy(label = label) }

    fun setLineParts(index: Int, parts: String) = updateLine(index) { it.copy(partsText = parts) }

    fun addLine() = _formState.update { it.copy(lines = it.lines + LineDraft()) }

    fun removeLine(index: Int) = _formState.update { state ->
        state.copy(lines = state.lines.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(LineDraft()) })
    }

    private fun updateLine(index: Int, change: (LineDraft) -> LineDraft) = _formState.update { state ->
        state.copy(lines = state.lines.mapIndexed { i, line -> if (i == index) change(line) else line })
    }

    fun save(onSaved: () -> Unit) {
        val state = _formState.value
        if (!state.isValid) return
        viewModelScope.launch {
            val min = state.minDoseText.toNumberOr(0.0)
            val max = state.maxDoseText.toNumberOr(min).takeIf { it > 0.0 } ?: min
            val kept = state.lines.filter { it.productId > 0L }
            solutionRepository.save(
                SolutionEntity(
                    id = state.solutionId,
                    brand = state.brand.trim(),
                    name = state.name.trim(),
                    category = state.category.trim(),
                    dosingMode = state.dosingMode,
                    minDoseGramsPerM2 = minOf(min, max),
                    maxDoseGramsPerM2 = maxOf(min, max),
                    // The slider starts in the middle of the datasheet's range.
                    typicalDoseGramsPerM2 = (minOf(min, max) + maxOf(min, max)) / 2.0,
                    doseUnitLabel = state.doseUnitLabel.trim(),
                    rangeNote = "",
                    sourceNote = "",
                    datasheetUrl = state.datasheetUrl.trim(),
                    ratioLabel = kept.joinToString(":") { formatDecimal(it.partsText.toNumberOr(0.0), 2) },
                ),
                kept.map { line ->
                    SolutionLineEntity(
                        solutionId = state.solutionId,
                        productId = line.productId,
                        label = line.label.trim(),
                        role = SolutionLineRole.BASE,
                        ratioParts = line.partsText.toNumberOr(0.0),
                    )
                },
            )
            onSaved()
        }
    }

    fun archive(onArchived: () -> Unit) {
        val id = _formState.value.solutionId
        if (id == 0L) return
        viewModelScope.launch {
            solutionRepository.getAllWithLines().firstOrNull { it.solution.id == id }?.let {
                solutionRepository.archive(it.solution)
            }
            onArchived()
        }
    }
}
