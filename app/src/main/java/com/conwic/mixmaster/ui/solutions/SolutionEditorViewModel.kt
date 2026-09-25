package com.conwic.mixmaster.ui.solutions

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.ui.calculator.MaxMixSeconds
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
import kotlin.math.abs
import kotlin.math.roundToInt

/** One line of the recipe as it is being edited. */
data class LineDraft(
    val productId: Long = 0L,
    val label: String = "",
    val partsText: String = "",
    /**
     * Whether [partsText] is a percentage of everything else in the drum rather than a ratio
     * part or a rate of its own — "water at 10% of (A+B)", which is how a good few datasheets
     * word the water and the thinner.
     */
    val percentOfRest: Boolean = false,
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
    /** Minutes, as it is typed — the datasheet says "at least 2 minutes", not "120". */
    val mixMinutesText: String = "",
    val potLifeText: String = "",
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
            // A share needs something to be a share of, so a recipe that is nothing but
            // percentages has no ratio either.
            lines.filter { it.productId > 0L && !it.percentOfRest }
                .none { (it.partsText.toNumberOrNull() ?: 0.0) > 0.0 } ->
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

    /**
     * What the typed rates come to, in g/m² — the coverage, when the parts carry their own.
     *
     * A share line's box holds a percentage rather than a rate, so it is worked out off the
     * others instead of being added in as if it were kilos.
     */
    val perAreaTotal: Double
        get() {
            val used = lines.filter { it.productId > 0L }
            val fixed = used.filterNot { it.percentOfRest }.sumOf { it.partsText.toNumberOrNull() ?: 0.0 }
            val shares = used.filter { it.percentOfRest }
                .sumOf { fixed * (it.partsText.toNumberOrNull() ?: 0.0) / 100.0 }
            return (fixed + shares) * 1000.0
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
                        mixMinutesText = if (solution.mixSeconds > 0) formatDecimal(solution.mixSeconds / 60.0, 2) else "",
                        potLifeText = if (solution.potLifeMinutes > 0) "${solution.potLifeMinutes}" else "",
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
                                    // The percentage is what was typed; the parts it came to
                                    // are worked out again on the way back out.
                                    partsText = if (it.percentOfRest > 0.0) {
                                        formatDecimal(it.percentOfRest, 2)
                                    } else {
                                        formatDecimal(it.ratioParts, 2)
                                    },
                                    percentOfRest = it.percentOfRest > 0.0,
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
    fun setMixMinutes(value: String) = _formState.update { it.copy(mixMinutesText = value) }

    fun setPotLife(value: String) = _formState.update { it.copy(potLifeText = value) }
    /**
     * Switches how the parts are typed, carrying the figures over.
     *
     * The two are the same recipe said differently — 100:2:24 at 2700 g/m² is 2.1429 kg/m² of
     * powder, 0.0429 of finish and 0.5143 of polymer — so switching works them out rather than
     * leaving numbers on screen that quietly mean something else. Four decimals on a rate is
     * a tenth of a gram per square metre, which is enough to come back the other way unchanged.
     *
     * Where there is nothing to work from — a ratio with no coverage typed yet — the figures
     * are left alone rather than thrown away, and the coverage line under the parts shows what
     * they would come to.
     */
    fun setEntry(mode: EntryMode) = _formState.update { state ->
        if (mode == state.entry) return@update state
        // The share lines already worked out, so the ones that are not shares are divided by a
        // total that has the water in it rather than by each other alone.
        val typed = resolvedParts(state.lines)
        when (mode) {
            EntryMode.PER_AREA -> {
                val parts = typed.sum()
                val min = state.minDoseText.toNumberOrNull() ?: 0.0
                val max = state.maxDoseText.toNumberOrNull() ?: min
                // The middle of the range is where the calculator starts, so it is the figure
                // the rates are worked out from.
                val coverageKg = ((min + max) / 2.0) / 1000.0
                if (parts <= 0.0 || coverageKg <= 0.0) {
                    state.copy(entry = mode)
                } else {
                    state.copy(
                        entry = mode,
                        lines = state.lines.mapIndexed { index, line ->
                            // A percentage is a percentage whichever way the parts are typed:
                            // 10% of the rest is 10% of the rest in parts and in kg/m² alike.
                            if (line.percentOfRest) return@mapIndexed line
                            val rate = typed.getOrElse(index) { 0.0 } / parts * coverageKg
                            line.copy(partsText = if (rate > 0.0) formatDecimal(rate, 4) else "")
                        },
                    )
                }
            }
            EntryMode.RATIO -> {
                // Off a fixed line, never off a share: scaling the ratio to a figure that is
                // itself a tenth of the others would put the whole recipe out.
                val top = state.lines.indices
                    .filterNot { state.lines[it].percentOfRest }
                    .mapNotNull { typed.getOrNull(it) }
                    .maxOrNull() ?: 0.0
                if (top <= 0.0) {
                    state.copy(entry = mode)
                } else {
                    val coverage = typed.sum() * 1000.0
                    val min = state.minDoseText.toNumberOrNull() ?: 0.0
                    val max = state.maxDoseText.toNumberOrNull() ?: min
                    val middle = (min + max) / 2.0
                    // A range that already agrees with these rates is left as it is. Otherwise
                    // a look at the m² figures and back would flatten 2400–3000 to a single
                    // 2700, which is the range the slider is there for.
                    val agrees = middle > 0.0 && abs(middle - coverage) <= coverage * 0.005
                    state.copy(
                        entry = mode,
                        lines = state.lines.mapIndexed { index, line ->
                            if (line.percentOfRest) return@mapIndexed line
                            val part = typed.getOrElse(index) { 0.0 } / top * 100.0
                            line.copy(partsText = if (part > 0.0) formatDecimal(part, 2) else "")
                        },
                        minDoseText = if (agrees) state.minDoseText else formatDecimal(coverage, 1),
                        maxDoseText = if (agrees) state.maxDoseText else formatDecimal(coverage, 1),
                    )
                }
            }
        }
    }

    fun setLineProduct(index: Int, productId: Long) = updateLine(index) { it.copy(productId = productId) }

    fun setLineLabel(index: Int, label: String) = updateLine(index) { it.copy(label = label) }

    fun setLineParts(index: Int, parts: String) = updateLine(index) { it.copy(partsText = parts) }

    /**
     * What each line comes to, with any "a share of the others" line worked out.
     *
     * The fixed lines are settled first and the share is taken off their total, because that is
     * what the datasheet says: ten parts A plus two parts B plus ten per cent *of those two*.
     * Taking it off the total including itself would be nine per cent, and on a thousand-kilo
     * job that is nine kilos of water.
     *
     * Works the same whichever way the parts are typed — parts of a ratio or kg/m² each.
     */
    private fun resolvedParts(lines: List<LineDraft>): List<Double> {
        val typed = lines.map { it.partsText.toNumberOr(0.0) }
        val fixed = lines.mapIndexed { index, line -> if (line.percentOfRest) 0.0 else typed[index] }.sum()
        return lines.mapIndexed { index, line ->
            if (line.percentOfRest) fixed * typed[index] / 100.0 else typed[index]
        }
    }

    /**
     * Switches one part between a figure of its own and a share of the others.
     *
     * The box is cleared on the way through: 10 parts and 10% of the rest are different
     * amounts, and a figure left standing under a changed label is how somebody mixes the
     * wrong thing.
     */
    fun setLinePercentOfRest(index: Int, percent: Boolean) = updateLine(index) {
        if (it.percentOfRest == percent) it else it.copy(percentOfRest = percent, partsText = "")
    }

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
        // Saving rewrites the recipe's lines from what the form holds, and the form only holds
        // the ratio parts. Anything else on the recipe — a colour dosed off one of them — is
        // carried across untouched rather than being wiped by a save that never showed it.
        val keptOther = if (state.solutionId > 0L) {
            solutionRepository.getAllWithLines()
                .firstOrNull { it.solution.id == state.solutionId }
                ?.lines
                ?.filter { it.role != SolutionLineRole.BASE }
                .orEmpty()
        } else {
            emptyList()
        }
        val kept = state.lines.filter { it.productId > 0L }
        // Shares worked out here rather than on the way back out, so everything downstream —
        // the batches, the packing, the film on the floor — goes on reading one plain number.
        val typed = resolvedParts(kept)
        // Typed by area, the figures are rates: the ratio is what they are to each other and
        // the coverage is what they come to, so neither has to be worked out by hand.
        val perArea = state.entry == EntryMode.PER_AREA
        val top = kept.indices.filterNot { kept[it].percentOfRest }
            .mapNotNull { typed.getOrNull(it) }
            .maxOrNull() ?: 0.0
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
                // Capped where the timer's own arrows stop: a mistyped 900 minutes is a
                // countdown nobody can sit through and a screen held awake all afternoon.
                mixSeconds = (state.mixMinutesText.toNumberOr(0.0) * 60.0).roundToInt()
                    .coerceIn(0, MaxMixSeconds),
                // Capped at a working day: a pot life is minutes, and a mistyped 9000 would
                // read as three days of workable material.
                potLifeMinutes = state.potLifeText.toNumberOr(0.0).roundToInt().coerceIn(0, 600),
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
                    percentOfRest = if (line.percentOfRest) line.partsText.toNumberOr(0.0) else 0.0,
                )
            } + keptOther,
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
