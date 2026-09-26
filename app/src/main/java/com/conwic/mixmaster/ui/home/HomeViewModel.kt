package com.conwic.mixmaster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.DeliveryEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.repository.DeliveryRepository
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.StockRepository
import com.conwic.mixmaster.domain.solutionMix
import com.conwic.mixmaster.domain.productStock
import com.conwic.mixmaster.domain.bookingsByProduct
import com.conwic.mixmaster.data.repository.SolutionRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.domain.formatWeek
import com.conwic.mixmaster.ui.tasks.ProjectOption
import com.conwic.mixmaster.ui.tasks.TaskDraft
import com.conwic.mixmaster.ui.tasks.toEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import com.conwic.mixmaster.ui.calendarscreen.CalendarProject
import com.conwic.mixmaster.ui.calendarscreen.DayBar
import com.conwic.mixmaster.ui.calendarscreen.barsOn
import com.conwic.mixmaster.ui.calendarscreen.projectSpans

/** A task as the home list shows it — the row needs the whole task to open the editor. */
data class HomeTaskUi(val task: TaskEntity, val projectName: String) {
    /** Blank when the task has no project — the screen supplies the wording. */
    val subtitle: String get() = projectName
}

data class WeekDayUi(
    val date: LocalDate,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
    val taskCount: Int,
    /** The projects running that day, as bars — the same as on the calendar. */
    val bars: List<DayBar?> = emptyList(),
)

/**
 * One product the shelf cannot cover.
 *
 * Carries enough to write an order straight from the home screen, because the moment you find
 * out you are short is the moment to do something about it.
 */
data class ShortItem(
    val productId: Long,
    val name: String,
    val short: Double,
    val unit: String,
    val packType: String,
    val isKnownPack: Boolean,
    /** Packs still to order once anything already on its way is counted. */
    val packsToOrder: Int,
    val stillToOrder: Double,
    val onOrder: Double,
    /** The soonest thing already ordered is due, if anything is. */
    val dueOn: LocalDate?,
)

/** The warehouse's answer to "is anything going to stop work this week?" */
data class MaterialAlert(
    val projects: List<String> = emptyList(),
    val items: List<ShortItem> = emptyList(),
)

/** An order whose day has come, and which nobody has ticked off yet. */
data class DueDelivery(
    val delivery: DeliveryEntity,
    val productName: String,
    val packType: String,
    val packUnit: String,
    val packSize: Double,
) {
    val amount: Double get() = delivery.packs * packSize + delivery.amount
}

data class HomeUiState(
    val activeProjectCount: Int = 0,
    val productCount: Int = 0,
    val todayTaskCount: Int = 0,
    val selectedDate: LocalDate = LocalDate.now(),
    val weekLabel: String = "",
    val dayTasks: List<HomeTaskUi> = emptyList(),
    val overdueTasks: List<HomeTaskUi> = emptyList(),
    val undatedTasks: List<HomeTaskUi> = emptyList(),
    val week: List<WeekDayUi> = emptyList(),
    /** Every project with a day in the week on show, for the key under it. */
    val weekProjects: List<CalendarProject> = emptyList(),
    val projects: List<ProjectOption> = emptyList(),
)

class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val solutionRepository: SolutionRepository,
    private val deliveryRepository: DeliveryRepository,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    /**
     * Jobs whose material the shelf can't cover.
     *
     * Kept apart from the rest of the home state because it asks the warehouse a question, and
     * a job that is short is worth knowing about before the van is loaded, not after.
     */
    private val bookings = combine(
        projectRepository.observeAll(),
        projectRepository.observeAllRooms(),
        projectRepository.observeAllLayers(),
        solutionRepository.observeAllWithLines(),
        productRepository.observeAll(),
    ) { projects, rooms, layers, solutions, products ->
        val productsById = products.associateBy { it.id }
        val mixes = solutions.associate { it.solution.id to solutionMix(it.solution, it.lines, productsById) }
        bookingsByProduct(projects, rooms, layers.groupBy { it.roomId }, mixes, productsById)
    }

    val shortOfMaterial: StateFlow<MaterialAlert> = combine(
        bookings,
        stockRepository.observeAll(),
        productRepository.observeAll(),
        deliveryRepository.observeAll(),
    ) { booked, stock, products, deliveries ->
        val stockByProduct = stock.associateBy { it.productId }
        val coming = deliveries.filter { it.arrivedOn == null }.groupBy { it.productId }
        val short = products.mapNotNull { product ->
            val held = productStock(
                product = product,
                stock = stockByProduct[product.id],
                bookings = booked[product.id].orEmpty(),
                deliveries = coming[product.id].orEmpty(),
            )
            held.takeIf { it.short > 0.0 }
        }
        MaterialAlert(
            projects = short.flatMap { it.bookings }.map { it.projectName }.distinct().sorted(),
            // Worst first: the one that will stop work soonest is the one to read.
            items = short.sortedByDescending { it.short }.map { held ->
                ShortItem(
                    productId = held.productId,
                    name = held.name,
                    short = held.short,
                    unit = held.packUnit,
                    packType = held.packType,
                    isKnownPack = held.isKnownPack,
                    packsToOrder = held.packsStillToOrder ?: 0,
                    stillToOrder = held.stillToOrder,
                    onOrder = held.onOrder,
                    dueOn = coming[held.productId].orEmpty().minOfOrNull { it.expectedOn },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MaterialAlert())

    /**
     * Orders that were due today or earlier and are still open.
     *
     * The app asks rather than assumes: a lorry that did not turn up would otherwise put stock
     * on the shelf that nobody can find.
     */
    val dueDeliveries: StateFlow<List<DueDelivery>> = combine(
        deliveryRepository.observeAll(),
        productRepository.observeAll(),
    ) { deliveries, products ->
        val productsById = products.associateBy { it.id }
        val today = LocalDate.now()
        deliveries
            .filter { it.arrivedOn == null && !it.expectedOn.isAfter(today) }
            .mapNotNull { delivery ->
                productsById[delivery.productId]?.let { product ->
                    DueDelivery(
                        delivery = delivery,
                        productName = product.name,
                        packType = product.packageType,
                        packUnit = product.packageUnit,
                        packSize = product.packageSize,
                    )
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun order(productId: Long, packs: Int, amount: Double, expectedOn: LocalDate, note: String) {
        viewModelScope.launch { deliveryRepository.order(productId, packs, amount, expectedOn, note) }
    }

    /** It turned up: onto the shelf, and the question stops being asked. */
    fun receive(due: DueDelivery) {
        viewModelScope.launch { deliveryRepository.receive(due.delivery, due.packSize) }
    }

    /** Not here yet — ask again tomorrow rather than every time the app is opened. */
    fun postpone(due: DueDelivery) {
        viewModelScope.launch { deliveryRepository.postpone(due.delivery, LocalDate.now().plusDays(1)) }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        projectRepository.observeAll(),
        projectRepository.observeAllTasks(),
        productRepository.observeAll(),
        selectedDate,
    ) { projects, tasks, products, selected ->
        val today = LocalDate.now()
        val projectNameById = projects.associate { it.id to it.name }
        fun List<TaskEntity>.toUi() = map { task ->
            HomeTaskUi(task, task.projectId?.let { projectNameById[it] }.orEmpty())
        }

        val dayTasks = tasks
            .filter { it.dueDate == selected }
            .sortedWith(compareBy({ it.isDone }, { it.priority.ordinal }))
            .toUi()

        // Anything already past its date and still open follows you around until it's closed —
        // a task list that quietly hides what's late is worse than no list.
        val overdue = if (selected == today) {
            tasks.filter { !it.isDone && it.dueDate?.isBefore(today) == true }
                .sortedBy { it.dueDate }
                .toUi()
        } else {
            emptyList()
        }

        // A task saved without a date belongs to no day, so it would vanish from a day list.
        // It gets its own section rather than being quietly lost.
        val undated = if (selected == today) {
            tasks.filter { !it.isDone && it.dueDate == null }
                .sortedBy { it.priority.ordinal }
                .toUi()
        } else {
            emptyList()
        }

        val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekProjects = projectSpans(projects, monday, monday.plusDays(6))
        val week = (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            WeekDayUi(
                date = date,
                dayOfMonth = date.dayOfMonth,
                isToday = date == today,
                isSelected = date == selected,
                taskCount = tasks.count { it.dueDate == date && !it.isDone },
                bars = barsOn(date, weekProjects),
            )
        }

        HomeUiState(
            activeProjectCount = projects.count { it.status == ProjectStatus.ACTIVE },
            productCount = products.size,
            todayTaskCount = tasks.count { it.dueDate == today && !it.isDone },
            selectedDate = selected,
            weekLabel = formatWeek(monday),
            dayTasks = dayTasks,
            overdueTasks = overdue,
            undatedTasks = undated,
            week = week,
            weekProjects = weekProjects,
            projects = projects.map { ProjectOption(it.id, it.name) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun selectDate(date: LocalDate) = selectedDate.update { date }

    fun shiftWeek(weeks: Long) = selectedDate.update { it.plusWeeks(weeks) }

    fun saveTask(draft: TaskDraft) {
        val entity = draft.toEntity()
        viewModelScope.launch {
            if (draft.id == null) projectRepository.addTask(entity) else projectRepository.updateTask(entity)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { projectRepository.deleteTask(taskId) }
    }
}
