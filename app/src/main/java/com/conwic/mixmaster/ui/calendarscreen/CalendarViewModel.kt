package com.conwic.mixmaster.ui.calendarscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.domain.formatDayWithWeek
import com.conwic.mixmaster.domain.isoWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

data class CalendarTaskUi(val id: Long, val title: String, val projectName: String, val done: Boolean)

data class CalendarUiState(
    val monthLabel: String = "",
    val weeks: List<CalendarWeek> = emptyList(),
    val selectedDateLabel: String = "",
    val selectedDayTasks: List<CalendarTaskUi> = emptyList(),
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
                            taskCount = tasks.count { it.dueDate == date },
                        )
                    }
                },
            )
        }
        val dayTasks = tasks.filter { it.dueDate == selected }.map {
            CalendarTaskUi(id = it.id, title = it.title, projectName = projectNameById[it.projectId] ?: "", done = it.isDone)
        }
        CalendarUiState(
            monthLabel = currentMonth.format(monthFormatter),
            weeks = weeks,
            selectedDateLabel = formatDayWithWeek(selected),
            selectedDayTasks = dayTasks,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun selectDate(date: LocalDate) = selectedDate.update { date }

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }
}
