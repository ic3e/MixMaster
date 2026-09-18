package com.conwic.mixmaster.ui.calendarscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class CalendarCell(
    val date: LocalDate?,
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val taskCount: Int = 0,
)

data class CalendarTaskUi(val id: Long, val title: String, val projectName: String, val done: Boolean)

data class CalendarUiState(
    val monthLabel: String = "",
    val cells: List<CalendarCell> = emptyList(),
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
        val firstOfMonth = currentMonth.atDay(1)
        val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val cells = (0 until 42).map { offset ->
            val date = gridStart.plusDays(offset.toLong())
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
        }
        val dayTasks = tasks.filter { it.dueDate == selected }.map {
            CalendarTaskUi(id = it.id, title = it.title, projectName = projectNameById[it.projectId] ?: "", done = it.isDone)
        }
        CalendarUiState(
            monthLabel = "${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${currentMonth.year}",
            cells = cells,
            selectedDateLabel = selected.toString(),
            selectedDayTasks = dayTasks,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    fun selectDate(date: LocalDate) = selectedDate.update { date }

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { it.plusMonths(1) }
}
