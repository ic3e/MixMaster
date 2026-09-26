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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    /** Coats that are a copy of another one — same name, same recipe — and can go. */
    val duplicateCoatIds: List<Long> = emptyList(),
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

/** A value in the list of changes: typed text, or a word the screen puts in its own language. */
data class Shown(val text: String = "", @StringRes val res: Int? = null)

/** One thing on the form that is not what was last saved: what it was, and what it is now. */
data class FormChange(
    @StringRes val label: Int,
    /** The part's number, for a change to one of the parts; the label is then ignored. */
    val part: Int? = null,
    val before: Shown,
    val after: Shown,
)

class SolutionEditorViewModel(
    private val solutionRepository: SolutionRepository,
    private val productRepository: ProductRepository,
    private val solutionId: Long?,
) : ViewModel() {

    private val _formState = MutableStateFlow(SolutionFormState())
    val formState: StateFlow<SolutionFormState> = _formState.asStateFlow()

    /**
     * What a coat's figures come to once written down. Saving and the list of changes both work
     * from this, so the list never calls something a change that would be saved the same.
     */
    private data class Written(
        val kept: List<LineDraft>,
        /** Each kept line's parts, shares worked out, as they go into the recipe. */
        val parts: List<Double>,
        val min: Double,
        val max: Double,
        val mixSeconds: Int,
        val potLifeMinutes: Int,
    )

    /** The form as it was opened, or as it was last saved — what "unsaved" is measured from. */
    private val baseline = MutableStateFlow<SolutionFormState?>(null)

    /**
     * Everything on the form that would be lost by leaving now, in the order it is on screen.
     *
     * A change typed and never saved was gone without a word the moment back was pressed or
     * another coat was opened, and nothing on screen said the recipe differed from the saved one.
     */
    val changes: StateFlow<List<FormChange>> = combine(_formState, baseline) { now, saved ->
        if (saved == null || !now.isLoaded) emptyList() else changesBetween(saved, now)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every recipe, as last seen: what this one's coats are picked out of. */
    @Volatile private var allSolutions: List<SolutionEntity> = emptyList()

    /**
     * The coats of the mix [state] belongs to, worked out again whenever either side changes.
     *
     * It used to be worked out only when the list of recipes changed. A coat opened before its
     * own row had loaded then saw no coats at all until something else was saved — so it offered
     * "Coat 2" as the next name every time, and in a company, where the other phones' changes
     * arrive every half minute, the list came and went under the thumb.
     */
    private fun withCoats(state: SolutionFormState): SolutionFormState {
        if (state.solutionId == 0L) return state.copy(coats = emptyList(), duplicateCoatIds = emptyList())
        val family = if (state.parentId > 0L) state.parentId else state.solutionId
        val coats = allSolutions.filter { it.familyId == family }.sortedBy { it.id }
        return state.copy(coats = coats, duplicateCoatIds = copiesIn(coats, state.solutionId) { coatKey(it) })
    }

    /** What makes two coats the same coat: the name, and everything the recipe says. */
    private fun coatKey(coat: SolutionEntity): String = listOf(
        coat.coatName.trim().lowercase(), coat.ratioLabel, coat.dosingMode, coat.minDoseGramsPerM2,
        coat.maxDoseGramsPerM2, coat.doseUnitLabel, coat.mixSeconds, coat.potLifeMinutes,
    ).joinToString("|")

    /**
     * Every coat beyond the first of each set of identical ones. The one kept is the mix's first
     * coat when it is among them — the others hang off it — then the one open on screen, then
     * the oldest.
     */
    private fun copiesIn(coats: List<SolutionEntity>, openId: Long, key: (SolutionEntity) -> String): List<Long> =
        coats.groupBy(key).values.flatMap { same ->
            if (same.size < 2) {
                emptyList()
            } else {
                val kept = same.firstOrNull { it.parentId == 0L }
                    ?: same.firstOrNull { it.id == openId }
                    ?: same.minBy { it.id }
                same.filter { it.id != kept.id }.map { it.id }
            }
        }

    /**
     * Takes one coat off the mix. Archived like a deleted recipe rather than wiped, so a room
     * already laid with it keeps what it was laid with. The first coat is the mix itself, and
     * goes with the bin at the top.
     */
    fun removeCoat(id: Long) {
        val coat = _formState.value.coats.firstOrNull { it.id == id } ?: return
        if (coat.parentId == 0L) return
        viewModelScope.launch { runCatching { solutionRepository.archive(coat) } }
    }

    /**
     * Takes away every copy, keeping one of each. Checked again against the lines themselves
     * before anything goes, so two coats that only look alike on the list are both kept.
     */
    fun removeDuplicateCoats() {
        val state = _formState.value
        viewModelScope.launch {
            runCatching {
                val lines = solutionRepository.getAllWithLines().associate { withLines ->
                    withLines.solution.id to withLines.lines.sortedBy { it.sortOrder }.joinToString(";") {
                        "${it.productId}:${it.role}:${it.ratioParts}:${it.percentOfRest}:${it.amountPerKg}:${it.amountUnit}"
                    }
                }
                val copies = copiesIn(state.coats, state.solutionId) { coatKey(it) + "|" + lines[it.id].orEmpty() }
                state.coats.filter { it.id in copies }.forEach { solutionRepository.archive(it) }
            }
        }
    }

    init {
        viewModelScope.launch {
            val existing = solutionId?.takeIf { it > 0L }?.let { id ->
                solutionRepository.getAllWithLines().firstOrNull { it.solution.id == id }
            }
            _formState.update { state ->
                withCoats(if (existing == null) {
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
                })
            }
            baseline.value = _formState.value
        }
        viewModelScope.launch {
            productRepository.observeAll().collect { rows -> _formState.update { it.copy(products = rows) } }
        }
        // The other coats of this mix, kept live so one saved next door shows up here.
        viewModelScope.launch {
            solutionRepository.observeAll().collect { all ->
                allSolutions = all
                _formState.update { withCoats(it) }
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
            // A product in the recipe taken away on another phone a moment ago fails the write;
            // the form stays open with what was typed rather than the app closing under it.
            val id = runCatching { persist(state) }.getOrNull() ?: return@launch
            // Saved from the list of changes, the form stays open: what was saved is now what
            // the next change is measured from, and a new recipe has an id to be saved under.
            _formState.update { withCoats(it.copy(solutionId = id)) }
            baseline.value = state.copy(solutionId = id)
            onSaved()
        }
    }

    /** Puts the form back to how it was opened, or last saved. */
    fun undoChanges() {
        val saved = baseline.value ?: return
        _formState.update {
            it.copy(
                brand = saved.brand,
                name = saved.name,
                category = saved.category,
                dosingMode = saved.dosingMode,
                minDoseText = saved.minDoseText,
                maxDoseText = saved.maxDoseText,
                doseUnitLabel = saved.doseUnitLabel,
                mixMinutesText = saved.mixMinutesText,
                potLifeText = saved.potLifeText,
                datasheetUrl = saved.datasheetUrl,
                lines = saved.lines,
                entry = saved.entry,
                coatName = saved.coatName,
            )
        }
    }

    private fun changesBetween(saved: SolutionFormState, now: SolutionFormState): List<FormChange> {
        val savedAs = written(saved)
        val nowAs = written(now)
        val found = mutableListOf<FormChange>()
        fun typed(@StringRes label: Int, before: String, after: String) {
            if (before.trim() != after.trim()) found += FormChange(label, before = Shown(before.trim()), after = Shown(after.trim()))
        }
        typed(R.string.solution_name, saved.name, now.name)
        typed(R.string.product_brand, saved.brand, now.brand)
        typed(R.string.product_type, saved.category, now.category)
        typed(R.string.solution_coat_name, saved.coatName, now.coatName)

        // Typed the same way on both sides, a part is compared as it was typed, so a rate changed
        // shows as that rate. Switched between ratio and kg/m², every box is rewritten without the
        // recipe changing, and only the ratio it comes to says whether anything did.
        val sameEntry = saved.entry == now.entry
        fun amount(written: Written, index: Int): Double? {
            val line = written.kept.getOrNull(index) ?: return null
            return if (line.percentOfRest || sameEntry) line.partsText.toNumberOr(0.0) else written.parts.getOrElse(index) { 0.0 }
        }
        fun shown(written: Written, index: Int): Shown {
            val line = written.kept.getOrNull(index) ?: return Shown()
            val product = now.products.firstOrNull { it.id == line.productId }
                ?.let { if (it.brand.isBlank()) it.name else "${it.brand} — ${it.name}" }
                .orEmpty()
            val figure = formatDecimal(amount(written, index) ?: 0.0, 4)
            return Shown(
                listOf(product, line.label.trim(), if (line.percentOfRest) "$figure%" else figure)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
            )
        }
        for (index in 0 until maxOf(savedAs.kept.size, nowAs.kept.size)) {
            val before = savedAs.kept.getOrNull(index)
            val after = nowAs.kept.getOrNull(index)
            val same = before != null && after != null &&
                before.productId == after.productId &&
                before.label.trim() == after.label.trim() &&
                before.percentOfRest == after.percentOfRest &&
                alike(amount(savedAs, index) ?: 0.0, amount(nowAs, index) ?: 0.0)
            if (!same) found += FormChange(R.string.product_parts, part = index + 1, before = shown(savedAs, index), after = shown(nowAs, index))
        }

        if (!alike(savedAs.min, nowAs.min) || !alike(savedAs.max, nowAs.max)) {
            found += FormChange(R.string.product_coverage, before = Shown(range(savedAs)), after = Shown(range(nowAs)))
        }
        if (saved.dosingMode != now.dosingMode) {
            found += FormChange(
                R.string.product_measured_per,
                before = Shown(res = dosingLabel(saved.dosingMode)),
                after = Shown(res = dosingLabel(now.dosingMode)),
            )
        }
        typed(R.string.product_per_what, saved.doseUnitLabel, now.doseUnitLabel)
        if (savedAs.mixSeconds != nowAs.mixSeconds) {
            found += FormChange(
                R.string.solution_mix_time,
                before = Shown(if (savedAs.mixSeconds > 0) formatDecimal(savedAs.mixSeconds / 60.0, 2) else ""),
                after = Shown(if (nowAs.mixSeconds > 0) formatDecimal(nowAs.mixSeconds / 60.0, 2) else ""),
            )
        }
        if (savedAs.potLifeMinutes != nowAs.potLifeMinutes) {
            found += FormChange(
                R.string.solution_pot_life,
                before = Shown(if (savedAs.potLifeMinutes > 0) "${savedAs.potLifeMinutes}" else ""),
                after = Shown(if (nowAs.potLifeMinutes > 0) "${nowAs.potLifeMinutes}" else ""),
            )
        }
        typed(R.string.product_datasheet_link, saved.datasheetUrl, now.datasheetUrl)
        return found
    }

    /**
     * Near enough to be the same figure. A ratio taken to kg/m² and back is rounded to four
     * decimals on the way, which moves a 2 to 2.002 — not a change anybody made.
     */
    private fun alike(a: Double, b: Double): Boolean = abs(a - b) <= maxOf(0.005, 0.001 * maxOf(abs(a), abs(b)))

    private fun range(written: Written): String = when {
        written.max <= 0.0 -> ""
        alike(written.min, written.max) -> formatDecimal(written.max, 1)
        else -> "${formatDecimal(written.min, 1)}–${formatDecimal(written.max, 1)}"
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
            val next = runCatching {
                val savedId = persist(named)
                val family = if (state.parentId > 0L) state.parentId else savedId
                val made = persist(
                    named.copy(
                        solutionId = 0L,
                        parentId = family,
                        coatName = nextCoatName,
                    ),
                )
                _formState.update { withCoats(it.copy(coatName = named.coatName, solutionId = savedId)) }
                made
            }.getOrNull() ?: return@launch
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
        val written = written(state)
        val kept = written.kept
        val parts = written.parts
        val min = written.min
        val max = written.max
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
                mixSeconds = written.mixSeconds,
                potLifeMinutes = written.potLifeMinutes,
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

    private fun written(state: SolutionFormState): Written {
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
        val typedMin = state.minDoseText.toNumberOr(0.0)
        val typedMax = state.maxDoseText.toNumberOr(typedMin).takeIf { it > 0.0 } ?: typedMin
        return Written(
            kept = kept,
            parts = if (perArea && top > 0.0) typed.map { it / top * 100.0 } else typed,
            min = if (perArea) typed.sum() * 1000.0 else minOf(typedMin, typedMax),
            max = if (perArea) typed.sum() * 1000.0 else maxOf(typedMin, typedMax),
            // Capped where the timer's own arrows stop: a mistyped 900 minutes is a
            // countdown nobody can sit through and a screen held awake all afternoon.
            mixSeconds = (state.mixMinutesText.toNumberOr(0.0) * 60.0).roundToInt().coerceIn(0, MaxMixSeconds),
            // Capped at a working day: a pot life is minutes, and a mistyped 9000 would
            // read as three days of workable material.
            potLifeMinutes = state.potLifeText.toNumberOr(0.0).roundToInt().coerceIn(0, 600),
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
