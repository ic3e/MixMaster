package com.conwic.mixmaster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.repository.ProductRepository
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

/** A task as the home list shows it — the row needs the whole task to open the editor. */
data class HomeTaskUi(val task: TaskEntity, val projectName: String) {
    val subtitle: String get() = projectName.ifBlank { "No project" }
}

data class WeekDayUi(
    val date: LocalDate,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val isSelected: Boolean,
    val taskCount: Int,
)

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
    val projects: List<ProjectOption> = emptyList(),
    val recentProducts: List<ProductEntity> = emptyList(),
)

class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

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
        val week = (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            WeekDayUi(
                date = date,
                dayOfMonth = date.dayOfMonth,
                isToday = date == today,
                isSelected = date == selected,
                taskCount = tasks.count { it.dueDate == date && !it.isDone },
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
            projects = projects.map { ProjectOption(it.id, it.name) },
            recentProducts = products.takeLast(3).reversed(),
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
