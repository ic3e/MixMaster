package com.conwic.mixmaster.ui.calendarscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.repository.ProjectRepository
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
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class CalendarCell(
    val date: LocalDate?,
    val isToday: Boolean = false,
    val isSelected: Boolean = false,
    val taskCount: Int = 0,
    /** One slot per row of project bars in the month, null where no project runs that day. */
    val bars: List<DayBar?> = emptyList(),
)

/**
 * A project on the calendar: the days it runs, the colour it is drawn in, and the row of bars it
 * keeps to all month, so a job reads as one line across the weeks rather than hopping about.
 */
data class CalendarProject(
    val id: Long,
    val name: String,
    val start: LocalDate,
    val end: LocalDate,
    /** Which of the calendar's colours, the same every month for the same project. */
    val colour: Int,
    val lane: Int,
    val status: ProjectStatus,
)

/** A project's bar through one day, rounded off on the days it starts and ends. */
data class DayBar(val colour: Int, val startsHere: Boolean, val endsHere: Boolean, val finished: Boolean)

/** How many colours the calendar has for projects; the screen holds the colours themselves. */
const val ProjectColourCount = 6

/** Rows of bars under a day before it would crowd the date out. The rest are in the list. */
private const val MaxLanes = 3

/** One row of the month grid, labelled with its ISO week number. */
data class CalendarWeek(val weekNumber: Int, val days: List<CalendarCell>)

fun CalendarProject.runsOn(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

data class CalendarTaskUi(val task: TaskEntity, val projectName: String) {
    /** Blank when the task has no project — the screen supplies the wording. */
    val subtitle: String get() = projectName
}

data class CalendarUiState(
    /** The month on show. The heading is built in the UI, where the language is known. */
    val currentMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val weeks: List<CalendarWeek> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDayTasks: List<CalendarTaskUi> = emptyList(),
    /** Projects running on the selected day. */
    val selectedDayProjects: List<CalendarProject> = emptyList(),
    /** Every project with a day in the month on show, first to start first. */
    val monthProjects: List<CalendarProject> = emptyList(),
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
        val monthProjects = projectsIn(currentMonth, projects)
        val lanes = minOf(MaxLanes, (monthProjects.maxOfOrNull { it.lane } ?: -1) + 1)
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
                            bars = (0 until lanes).map { lane ->
                                monthProjects
                                    .firstOrNull { it.lane == lane && it.runsOn(date) }
                                    ?.let {
                                        DayBar(
                                            colour = it.colour,
                                            startsHere = date == it.start,
                                            endsHere = date == it.end,
                                            finished = it.status == ProjectStatus.COMPLETED,
                                        )
                                    }
                            },
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
            currentMonth = currentMonth.atDay(1),
            weeks = weeks,
            selectedDate = selected,
            selectedDayTasks = dayTasks,
            selectedDayProjects = monthProjects.filter { it.runsOn(selected) },
            monthProjects = monthProjects,
            projects = projects.map { ProjectOption(it.id, it.name) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState())

    /**
     * The projects with a day in [month], each given a row of bars to keep to.
     *
     * A project with only one of its dates filled in is shown on that day alone; one with neither
     * has nothing to put on a calendar. The rows are handed out first come, first served — a
     * project takes the first row free by the day it starts — so two jobs that overlap sit one
     * above the other instead of on top of each other.
     */
    private fun projectsIn(month: YearMonth, projects: List<ProjectEntity>): List<CalendarProject> {
        val dated = projects.mapNotNull { project ->
            val first = project.startDate ?: project.targetFinishDate ?: return@mapNotNull null
            val last = project.targetFinishDate ?: first
            // A finish typed before the start is read the other way round rather than dropped.
            if (last.isBefore(first)) Triple(project, last, first) else Triple(project, first, last)
        }
        // By id, so a project keeps its colour from month to month whatever else starts.
        val colourOf = dated.map { it.first.id }.sorted()
            .withIndex()
            .associate { (index, id) -> id to index % ProjectColourCount }
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val laneEnds = mutableListOf<LocalDate>()
        return dated
            .filter { (_, first, last) -> !last.isBefore(monthStart) && !first.isAfter(monthEnd) }
            .sortedWith(compareBy({ it.second }, { it.first.id }))
            .map { (project, first, last) ->
                var lane = laneEnds.indexOfFirst { it.isBefore(first) }
                if (lane < 0) {
                    laneEnds += last
                    lane = laneEnds.lastIndex
                } else {
                    laneEnds[lane] = last
                }
                CalendarProject(
                    id = project.id,
                    name = project.name,
                    start = first,
                    end = last,
                    colour = colourOf.getValue(project.id),
                    lane = lane,
                    status = project.status,
                )
            }
    }

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
