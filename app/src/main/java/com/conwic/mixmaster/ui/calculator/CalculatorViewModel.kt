package com.conwic.mixmaster.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.coatLabel
import com.conwic.mixmaster.data.prefs.UserPrefs
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.domain.AddOnNeed
import com.conwic.mixmaster.domain.BatchBasis
import com.conwic.mixmaster.domain.BatchPlan
import com.conwic.mixmaster.domain.ImplausibleDensity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.domain.MixAddOn
import com.conwic.mixmaster.domain.MixPart
import com.conwic.mixmaster.domain.colourAddOn
import com.conwic.mixmaster.domain.MixCalculator
import com.conwic.mixmaster.domain.MixResult
import com.conwic.mixmaster.domain.PackNeed
import com.conwic.mixmaster.domain.SolutionMix
import com.conwic.mixmaster.domain.addOnNeeds
import com.conwic.mixmaster.domain.formatDecimal
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

/**
 * A colour as the coat's row records it, before it is worked out against a mix.
 *
 * Which part it is measured against only means something once there is a recipe on screen, so
 * it is resolved late — [against] is what turns it into something the mix maths can use.
 */
data class ColourSpec(
    val product: ProductEntity,
    val amountPerKg: Double,
    val unit: String,
    val againstIndex: Int,
) {
    fun against(parts: List<MixPart>): MixAddOn =
        colourAddOn(product, amountPerKg, unit, againstIndex, parts)
}

/** One room on a live project, offered to the calculator as a job it can be pointed at. */
data class JobRoom(
    val projectId: Long,
    val projectName: String,
    val roomId: Long,
    val roomName: String,
    val areaM2: Double,
    val coats: List<JobCoat>,
)

/** One coat of a [JobRoom]: the recipe the project specified, at the rate it specified. */
data class JobCoat(
    val number: Int,
    val title: String,
    val solutionId: Long,
    /** The coat's row, so the colour set on it comes across with the recipe. */
    val layerId: Long,
    val doseGramsPerM2: Double,
    val quantity: Double,
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
    /** The coat's row on a room, when this was opened for one — what its colour hangs off. */
    val layerId: Long = 0L,
)

class CalculatorViewModel(
    private val solutionRepository: SolutionRepository,
    private val productRepository: ProductRepository,
    private val projectRepository: ProjectRepository,
    private val userPrefs: UserPrefs,
    /** The mix this screen was opened for, or 0 when opened on its own. */
    initialSolutionId: Long = 0L,
    /** The room's coat it was opened for, when it was opened from a project. */
    private val handover: CoatHandover = CoatHandover.None,
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
            // Opened from a room's build-up, so the job's own figures stand rather than the
            // datasheet's. Applied after the mix is chosen: choosing one clears the rate.
            applyHandover()
        } else {
            // Opened on its own, so carry on with whatever was last worked on. Read once, not
            // followed: an echo of the stored id after the screen is up would pull it off
            // whatever has since been chosen.
            viewModelScope.launch {
                val remembered = userPrefs.lastSolutionId.first()
                if (remembered > 0L && selectedSolutionId.value == null) selectSolution(remembered)
                applyHandover()
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

    /**
     * The colour each coat on a project is tinted with.
     *
     * Kept by the coat's own row rather than by the recipe: the same topping goes down ocra in
     * one bay and grey in the next. Opened for a coat, this screen used to work the mix out with
     * no pigment in it at all, while the project's own build-up showed it — so a tinted batch
     * came out of the calculator the wrong colour.
     */
    private val layerColours: StateFlow<Map<Long, ColourSpec>> = combine(
        projectRepository.observeAllLayers(),
        productRepository.observeAll(),
    ) { layers, products ->
        val productsById = products.associateBy { it.id }
        layers.mapNotNull { layer ->
            val colour = productsById[layer.colourProductId] ?: return@mapNotNull null
            layer.id to ColourSpec(colour, layer.colourAmountPerKg, layer.colourUnit, layer.colourAgainstIndex)
        }.toMap()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiState: StateFlow<CalculatorUiState> =
        combine(
            selectedSolutionId,
            mixes,
            inputs,
            solutionRepository.observeUsageLogs(),
            layerColours,
        ) { id, byId, input, logs, colours ->
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
                addOnNeeds = result?.let {
                    // What the recipe carries, plus what this coat is tinted with on the job.
                    val colour = colours[input.layerId]?.against(parts)
                    addOnNeeds(it, mix?.addOns.orEmpty() + listOfNotNull(colour))
                }.orEmpty(),
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

    /**
     * Every room on a live project, with what the project says goes on it.
     *
     * There was no way back to a project from here: the area was read off the Layout tab and
     * typed in again, and the coat's rate along with it, which is how the figure on the drum
     * ends up disagreeing with the figure in the report.
     */
    val jobRooms: StateFlow<List<JobRoom>> = combine(
        projectRepository.observeAll(),
        projectRepository.observeAllRooms(),
        projectRepository.observeAllLayers(),
        solutions,
    ) { projects, rooms, layers, recipes ->
        val live = projects.filterNot { it.isArchived }.associateBy { it.id }
        val recipesById = recipes.associateBy { it.id }
        val layersByRoom = layers.groupBy { it.roomId }
        rooms.mapNotNull { room ->
            val project = live[room.projectId] ?: return@mapNotNull null
            JobRoom(
                projectId = project.id,
                projectName = project.name,
                roomId = room.id,
                roomName = room.name,
                areaM2 = room.areaM2,
                // Already in the order they are laid — the query sorts by sortOrder, same as
                // the Layout tab reads them. Numbered over every coat, skipped ones included,
                // so the numbers are the ones on that tab. A coat laid as a single ready
                // product is skipped: it has no recipe for this screen to work out.
                coats = layersByRoom[room.id].orEmpty()
                    .mapIndexedNotNull { index, layer ->
                        val mix = recipesById[layer.solutionId] ?: return@mapIndexedNotNull null
                        JobCoat(
                            number = index + 1,
                            title = mix.coatLabel,
                            solutionId = mix.id,
                            layerId = layer.id,
                            doseGramsPerM2 = layer.doseGramsPerM2.takeIf { it > 0.0 }
                                ?: mix.typicalDoseGramsPerM2,
                            quantity = layer.quantity,
                        )
                    },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Fills in what the room already knows. A figure that wasn't handed over is left alone, so
     * a coat with no rate of its own still comes up on the datasheet typical.
     */
    private fun applyHandover() {
        if (handover.isEmpty) return
        inputs.update { current ->
            current.copy(
                areaText = handover.areaM2?.let { formatDecimal(it, 2) } ?: current.areaText,
                quantityText = handover.quantity?.let { formatDecimal(it, 2) } ?: current.quantityText,
                coverageOverride = handover.doseGramsPerM2 ?: current.coverageOverride,
                layerId = handover.layerId,
            )
        }
    }

    /**
     * Takes a room from a project: its area, and where a coat was picked, that coat's recipe,
     * rate and number of passes. The project is not written back — the spec lives there, the
     * batch is worked out here.
     */
    fun applyJob(room: JobRoom, coat: JobCoat?) {
        // Choosing a mix clears the rate, so the rate goes in after it.
        coat?.let { selectSolution(it.solutionId) }
        inputs.update { current ->
            current.copy(
                areaText = formatDecimal(room.areaM2, 2),
                quantityText = coat?.let { formatDecimal(it.quantity, 2) } ?: current.quantityText,
                coverageOverride = coat?.doseGramsPerM2 ?: current.coverageOverride,
                layerId = coat?.layerId ?: 0L,
            )
        }
    }

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
