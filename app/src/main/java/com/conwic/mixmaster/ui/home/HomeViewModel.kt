package com.conwic.mixmaster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class HomeTaskUi(
    val id: Long,
    val title: String,
    val projectName: String,
    val done: Boolean,
    val dotColorHex: String,
)

data class WeekDayUi(
    val label: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val taskCount: Int,
)

data class HomeUiState(
    val activeProjectCount: Int = 0,
    val productCount: Int = 0,
    val todayTasks: List<HomeTaskUi> = emptyList(),
    val week: List<WeekDayUi> = emptyList(),
    val recentProducts: List<ProductEntity> = emptyList(),
)

class HomeViewModel(
    private val projectRepository: ProjectRepository,
    private val productRepository: ProductRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        projectRepository.observeAll(),
        projectRepository.observeAllTasks(),
        productRepository.observeAll(),
    ) { projects, tasks, products ->
        val today = LocalDate.now()
        val projectNameById = projects.associate { it.id to it.name }
        val todayTasks = tasks
            .filter { it.dueDate == today }
            .map { it.toUi(projectNameById[it.projectId] ?: "") }

        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong())
            WeekDayUi(
                label = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                dayOfMonth = date.dayOfMonth,
                isToday = date == today,
                taskCount = tasks.count { it.dueDate == date },
            )
        }

        HomeUiState(
            activeProjectCount = projects.count { it.status == ProjectStatus.ACTIVE },
            productCount = products.size,
            todayTasks = todayTasks,
            week = week,
            recentProducts = products.takeLast(3).reversed(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun toggleTask(task: HomeTaskUi) {
        viewModelScope.launch { projectRepository.setTaskDone(task.id, !task.done) }
    }
}

private fun TaskEntity.toUi(projectName: String): HomeTaskUi = HomeTaskUi(
    id = id,
    title = title,
    projectName = projectName,
    done = isDone,
    dotColorHex = when (priority) {
        com.conwic.mixmaster.data.model.TaskPriority.HIGH -> "#B3423A"
        com.conwic.mixmaster.data.model.TaskPriority.MEDIUM -> "#B98A3E"
        com.conwic.mixmaster.data.model.TaskPriority.LOW -> "#8A5A2E"
        com.conwic.mixmaster.data.model.TaskPriority.DONE -> "#4B7A52"
    },
)
