package com.conwic.mixmaster.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.prefs.UserPrefs
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.domain.AddOnNeed
import com.conwic.mixmaster.domain.BatchBasis
import com.conwic.mixmaster.domain.BatchPlan
import com.conwic.mixmaster.domain.ImplausibleDensity
import com.conwic.mixmaster.domain.MixCalculator
import com.conwic.mixmaster.domain.MixResult
import com.conwic.mixmaster.domain.PackNeed
import com.conwic.mixmaster.domain.SolutionMix
import com.conwic.mixmaster.domain.addOnNeeds
import com.conwic.mixmaster.domain.implausibleStoredDensities
import com.conwic.mixmaster.domain.packNeeds
import com.conwic.mixmaster.domain.planBatches
import com.conwic.mixmaster.domain.solutionMix
import com.conwic.mixmaster.domain.toNumberOrNull
import com.conwic.mixmaster.domain.usableLitres
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalculatorUiState(
    val selectedSolution: SolutionMix? = null,
    val areaInput: String = "",
    val quantityInput: String = "1",
    /** The dose in effect — the datasheet typical until the slider is moved. */
    val coverageValue: Double = 0.0,
    val result: MixResult? = null,
    /** Average of what has actually been logged on site for this mix, if anything has. */
    val siteAverageDose: Double? = null,
    val loggedJobCount: Int = 0,
    val packNeeds: List<PackNeed> = emptyList(),
    val batchBasis: BatchBasis = BatchBasis.ONE_PACKAGE,
    val mixerLitres: Double = 65.0,
    /** How much of the drum is deliberately left empty so the mix has room to turn over. */
    val headroomPercent: Double = 40.0,
    val usableLitres: Double = 39.0,
    val maxBatchKg: Double = 25.0,
    val batchPlan: BatchPlan? = null,
    /** Densities saved on these products that can't be right; the screen words the warning. */
    val implausibleDensities: List<ImplausibleDensity> = emptyList(),
    /** The colours and admixtures this recipe already carries. */
    val addOnNeeds: List<AddOnNeed> = emptyList(),
)

private data class CalculatorInputs(
    val areaText: String = "",
    val quantityText: String = "1",
    /** null = not yet overridden by the user; falls back to the recipe's typical dose. */
    val coverageOverride: Double? = null,
    val batchBasis: BatchBasis = BatchBasis.ONE_PACKAGE,
    val mixerLitres: Double = 65.0,
    val headroomPercent: Double = 40.0,
    val maxBatchKg: Double = 25.0,
)

class CalculatorViewModel(
    private val solutionRepository: SolutionRepository,
    private val productRepository: ProductRepository,
    private val userPrefs: UserPrefs,
    /** The mix this screen was opened for, or 0 when opened on its own. */
    initialSolutionId: Long = 0L,
) : ViewModel() {

    /** Settings can turn the "leave the drum room" nudge off for people who've heard it. */
    val showMixingReminders: StateFlow<Boolean> = userPrefs.mixingRemindersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val solutions: StateFlow<List<SolutionEntity>> = solutionRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Kept apart from the typed inputs: folding them together would re-run the queries on
    // every keystroke and every pixel of slider travel, which is what made dragging stutter.
    private val selectedSolutionId = MutableStateFlow<Long?>(null)
    private val inputs = MutableStateFlow(CalculatorInputs())

    init {
        if (initialSolutionId > 0L) {
            // Asked for by name. Nothing else gets to change it afterwards.
            selectSolution(initialSolutionId)
        } else {
            // Opened on its own, so carry on with whatever was last worked on. Read once, not
            // followed: an echo of the stored id after the screen is up would pull it off
            // whatever has since been chosen.
            viewModelScope.launch {
                val remembered = userPrefs.lastSolutionId.first()
                if (remembered > 0L && selectedSolutionId.value == null) selectSolution(remembered)
            }
        }
    }

    /** Every recipe with its lines resolved to the products they name. */
    private val mixes = combine(
        solutionRepository.observeAllWithLines(),
        productRepository.observeAll(),
    ) { solutions, products ->
        val productsById = products.associateBy { it.id }
        solutions.associate { it.solution.id to solutionMix(it.solution, it.lines, productsById) }
    }

    val uiState: StateFlow<CalculatorUiState> =
        combine(selectedSolutionId, mixes, inputs, solutionRepository.observeUsageLogs()) { id, byId, input, logs ->
            val mix = id?.let { byId[it] }
            val area = input.areaText.toNumberOrNull()
            val quantity = input.quantityText.toNumberOrNull() ?: 1.0
            val coverage = input.coverageOverride ?: mix?.solution?.typicalDoseGramsPerM2 ?: 0.0
            val parts = mix?.parts.orEmpty()
            val result = if (mix != null && area != null && area > 0.0) {
                MixCalculator.compute(parts, area, quantity, coverage)
            } else {
                null
            }
            val mine = logs.filter { it.solutionId == id }
            CalculatorUiState(
                selectedSolution = mix,
                areaInput = input.areaText,
                quantityInput = input.quantityText,
                coverageValue = coverage,
                result = result,
                siteAverageDose = if (mine.isEmpty()) null else mine.map { it.doseGramsPerM2 }.average(),
                loggedJobCount = mine.size,
                packNeeds = result?.let { packNeeds(it, parts) }.orEmpty(),
                batchBasis = input.batchBasis,
                mixerLitres = input.mixerLitres,
                headroomPercent = input.headroomPercent,
                usableLitres = usableLitres(input.mixerLitres, input.headroomPercent),
                maxBatchKg = input.maxBatchKg,
                implausibleDensities = implausibleStoredDensities(parts),
                addOnNeeds = result?.let { addOnNeeds(it, mix?.addOns.orEmpty()) }.orEmpty(),
                batchPlan = result?.let {
                    planBatches(
                        it,
                        parts,
                        input.batchBasis,
                        input.mixerLitres,
                        input.headroomPercent,
                        input.maxBatchKg,
                    )
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalculatorUiState())

    fun selectSolution(solutionId: Long) {
        if (selectedSolutionId.value == solutionId) return
        selectedSolutionId.value = solutionId
        inputs.update { it.copy(coverageOverride = null) }
        viewModelScope.launch { userPrefs.setLastSolutionId(solutionId) }
    }

    fun setArea(text: String) = inputs.update { it.copy(areaText = text) }

    fun setQuantity(text: String) = inputs.update { it.copy(quantityText = text) }

    fun setCoverage(value: Double) = inputs.update { it.copy(coverageOverride = value) }

    fun setBatchBasis(basis: BatchBasis) = inputs.update { it.copy(batchBasis = basis) }

    fun setMixerLitres(litres: Double) = inputs.update { it.copy(mixerLitres = litres) }

    fun setHeadroomPercent(percent: Double) =
        inputs.update { it.copy(headroomPercent = percent.coerceIn(0.0, 80.0)) }

    fun setMaxBatchKg(kg: Double) = inputs.update { it.copy(maxBatchKg = kg) }

    /**
     * Logs the current coverage as a real site reading — what feeds "your site average",
     * separate from the fixed datasheet figure.
     */
    fun logUsage(onLogged: () -> Unit) {
        val state = uiState.value
        val solutionId = state.selectedSolution?.solution?.id ?: return
        viewModelScope.launch {
            solutionRepository.logUsage(solutionId, state.coverageValue)
            onLogged()
        }
    }
}
