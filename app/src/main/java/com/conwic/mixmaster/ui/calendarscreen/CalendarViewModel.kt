package com.conwic.mixmaster.ui.calendarscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.domain.formatDayWithWeek
import com.conwic.mixmaster.domain.isoWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.conwic.mixmaster.ui.tasks.ProjectOption
import com.conwic.mixmaster.ui.tasks.TaskDraft
import com.conwic.mixmaster.ui.tasks.toEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

data class CalendarCell(
    val date: LocalDate?,
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val taskCount: Int = 0,
)

/** One row of the month grid, labelled with its ISO week number. */
data class CalendarWeek(val weekNumber: Int, val days: List<CalendarCell>)

data class CalendarTaskUi(val task: TaskEntity, val projectName: String) {
    val subtitle: String get() = projectName.ifBlank { "No project" }
}

data class CalendarUiState(
    val monthLabel: String = "",
    val weeks: List<CalendarWeek> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateLabel: String = "",
    val selectedDayTasks: List<CalendarTaskUi> = emptyList(),
    val projects: List<ProjectOption> = emptyList(),
)

class CalendarViewModel(private val projectRepository: ProjectRepository) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        selectedDate,
        projectRepository.observeAllTasks(),
        projectRepository.observeAll(),
    ) { currentMonth, selected, tasks, projects ->
        val projectNameById = projects.associate { it.id to it.name }
        val today = LocalDate.now()
        val gridStart = currentMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val gridEnd = currentMonth.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        // Only as many rows as the month actually spans, so nothing is left hanging off the grid.
        val weekCount = ChronoUnit.WEEKS.between(gridStart, gridEnd.plusDays(1)).toInt()
        val weeks = (0 until weekCount).map { weekIndex ->
            val weekStart = gridStart.plusWeeks(weekIndex.toLong())
            CalendarWeek(
                weekNumber = isoWeek(weekStart),
                days = (0 until 7).map { dayIndex ->
                    val date = weekStart.plusDays(dayIndex.toLong())
                    if (date.month != currentMonth.month) {
                        CalendarCell(date = null)
                    } else {
                        CalendarCell(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selected,
                            taskCount = tasks.count { it.dueDate == date && !it.isDone },
                        )
                    }
                },
            )
        }
        val dayTasks = tasks
            .filter { it.dueDate == selected }
            .sortedWith(compareBy({ it.isDone }, { it.priority.ordinal }))
            .map { task -> CalendarTaskUi(task, task.projectId?.let { projectNameById[it] }.orEmpty()) }
        CalendarUiState(
            monthLabel = currentMonth.format(monthFormatter),
            weeks = weeks,
            selectedDate = selected,
            selectedDateLabel = formatDayWithWeek(selected),
            selectedDayTasks = dayTasks,
            projects = projects.map { ProjectOption(it.id, it.name) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun selectDate(date: LocalDate) = selectedDate.update { date }

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }

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
