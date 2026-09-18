package com.conwic.mixmaster.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.data.repository.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectListItem(val project: ProjectEntity, val progressPercent: Int)

data class ProjectsUiState(
    val filter: String = "All",
    val visibleProjects: List<ProjectListItem> = emptyList(),
)

class ProjectsViewModel(private val projectRepository: ProjectRepository) : ViewModel() {

    private val filter = MutableStateFlow("All")
    val filterOptions = listOf("All", "Active", "Planning", "On hold", "Completed")

    val uiState: StateFlow<ProjectsUiState> = combine(
        projectRepository.observeAll(),
        projectRepository.observeAllTasks(),
        filter,
    ) { projects, tasks, currentFilter ->
        val tasksByProject = tasks.groupBy { it.projectId }
        val items = projects
            .filter { currentFilter == "All" || it.status.label() == currentFilter }
            .map { project ->
                val projectTasks = tasksByProject[project.id].orEmpty()
                val progress = if (projectTasks.isEmpty()) 0 else (projectTasks.count { it.isDone } * 100) / projectTasks.size
                ProjectListItem(project, progress)
            }
        ProjectsUiState(filter = currentFilter, visibleProjects = items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectsUiState())

    fun setFilter(value: String) = filter.update { value }

    fun createDraftProject(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = projectRepository.save(
                ProjectEntity(
                    name = "New project",
                    clientName = "",
                    address = "",
                    status = ProjectStatus.PLANNING,
                    startDate = null,
                    targetFinishDate = null,
                ),
            )
            onCreated(id)
        }
    }
}

fun ProjectStatus.label(): String = when (this) {
    ProjectStatus.ACTIVE -> "Active"
    ProjectStatus.PLANNING -> "Planning"
    ProjectStatus.ON_HOLD -> "On hold"
    ProjectStatus.COMPLETED -> "Completed"
}
