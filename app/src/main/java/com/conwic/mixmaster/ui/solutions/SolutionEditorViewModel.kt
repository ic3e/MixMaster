package com.conwic.mixmaster.ui.solutions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.familyId
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

/** How the parts of a coat are typed in. */
enum class EntryMode {
    /** Parts of a ratio — 100 powder to 35 water. */
    RATIO,

    /**
     * Each part's own rate off the datasheet, in kg/m².
     *
     * Some datasheets never give a ratio: architop's first coat is 2.0 kg/m² of hardener,
     * 0.48 of catalyst and 0.04 of the additive. Typed that way, the ratio and the coverage
     * both fall out of the figures instead of being worked out by hand on site.
     */
    PER_AREA,
}

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
    val entry: EntryMode = EntryMode.RATIO,
    /** The first coat of this mix, or 0 when this is it. */
    val parentId: Long = 0L,
    val coatName: String = "",
    /** Every coat of this mix, first coat first — this one included. */
    val coats: List<SolutionEntity> = emptyList(),
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
        get() = when {
            entry == EntryMode.PER_AREA -> null
            (minDoseText.toNumberOrNull() ?: 0.0) > 0.0 -> null
            (maxDoseText.toNumberOrNull() ?: 0.0) > 0.0 -> null
            else -> R.string.solution_problem_no_dose
        }

    /** What the typed rates come to, in g/m² — the coverage, when the parts carry their own. */
    val perAreaTotal: Double
        get() = lines.filter { it.productId > 0L }.sumOf { it.partsText.toNumberOrNull() ?: 0.0 } * 1000.0

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
                        parentId = solution.parentId,
                        coatName = solution.coatName,
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
        // The other coats of this mix, kept live so one saved next door shows up here.
        viewModelScope.launch {
            solutionRepository.observeAll().collect { all ->
                _formState.update { state ->
                    val family = if (state.solutionId == 0L) {
                        emptyList()
                    } else {
                        val id = if (state.parentId > 0L) state.parentId else state.solutionId
                        all.filter { it.familyId == id }.sortedBy { it.id }
                    }
                    state.copy(coats = family)
                }
            }
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
    fun setCoatName(value: String) = _formState.update { it.copy(coatName = value) }
    fun setEntry(mode: EntryMode) = _formState.update { it.copy(entry = mode) }

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
            persist(state)
            onSaved()
        }
    }

    /**
     * Saves this coat and opens another one of the same mix, started from it.
     *
     * The names are passed in because they are shown to someone: the screen knows which
     * language the app is in, and this does not.
     */
    fun addCoat(thisCoatName: String, nextCoatName: String, onOpen: (Long) -> Unit) {
        val state = _formState.value
        if (!state.isValid) return
        viewModelScope.launch {
            // The first coat is named too, or the list reads "architop" and "2nd coat".
            val named = state.copy(coatName = state.coatName.ifBlank { thisCoatName })
            val savedId = persist(named)
            val family = if (state.parentId > 0L) state.parentId else savedId
            val next = persist(
                named.copy(
                    solutionId = 0L,
                    parentId = family,
                    coatName = nextCoatName,
                ),
            )
            _formState.update { it.copy(coatName = named.coatName, solutionId = savedId) }
            onOpen(next)
        }
    }

    /** Writes one coat, and answers with the id it was written under. */
    private suspend fun persist(state: SolutionFormState): Long {
        val kept = state.lines.filter { it.productId > 0L }
        val typed = kept.map { it.partsText.toNumberOr(0.0) }
        // Typed by area, the figures are rates: the ratio is what they are to each other and
        // the coverage is what they come to, so neither has to be worked out by hand.
        val perArea = state.entry == EntryMode.PER_AREA
        val top = typed.maxOrNull() ?: 0.0
        val parts = if (perArea && top > 0.0) typed.map { it / top * 100.0 } else typed
        val typedMin = state.minDoseText.toNumberOr(0.0)
        val typedMax = state.maxDoseText.toNumberOr(typedMin).takeIf { it > 0.0 } ?: typedMin
        val min = if (perArea) typed.sum() * 1000.0 else minOf(typedMin, typedMax)
        val max = if (perArea) typed.sum() * 1000.0 else maxOf(typedMin, typedMax)
        return solutionRepository.save(
            SolutionEntity(
                id = state.solutionId,
                brand = state.brand.trim(),
                name = state.name.trim(),
                category = state.category.trim(),
                dosingMode = state.dosingMode,
                minDoseGramsPerM2 = min,
                maxDoseGramsPerM2 = max,
                // The slider starts in the middle of the datasheet's range.
                typicalDoseGramsPerM2 = (min + max) / 2.0,
                doseUnitLabel = state.doseUnitLabel.trim(),
                rangeNote = "",
                sourceNote = "",
                datasheetUrl = state.datasheetUrl.trim(),
                ratioLabel = parts.joinToString(":") { formatDecimal(it, 2) },
                parentId = state.parentId,
                coatName = state.coatName.trim(),
            ),
            kept.mapIndexed { index, line ->
                SolutionLineEntity(
                    solutionId = state.solutionId,
                    productId = line.productId,
                    label = line.label.trim(),
                    role = SolutionLineRole.BASE,
                    ratioParts = parts.getOrElse(index) { 0.0 },
                )
            },
        )
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
