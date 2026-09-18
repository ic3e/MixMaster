package com.conwic.mixmaster.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.repository.ProductRepository
import com.conwic.mixmaster.data.repository.ProjectRepository
import com.conwic.mixmaster.domain.MixCalculator
import com.conwic.mixmaster.domain.MixResult
import com.conwic.mixmaster.ui.tasks.TaskDraft
import com.conwic.mixmaster.ui.tasks.toEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

data class ProjectDetailData(
    val project: ProjectEntity? = null,
    val floors: List<FloorEntity> = emptyList(),
    val rooms: List<RoomAreaEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val photos: List<PhotoEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
) {
    val totalAreaM2: Double get() = rooms.sumOf { it.areaM2 }
    val progressPercent: Int get() = if (tasks.isEmpty()) 0 else (tasks.count { it.isDone } * 100) / tasks.size
}

class ProjectDetailViewModel(
    private val projectRepository: ProjectRepository,
    private val productRepository: ProductRepository,
    private val projectId: Long,
) : ViewModel() {

    val data: StateFlow<ProjectDetailData> = combine(
        projectRepository.observeById(projectId),
        projectRepository.observeFloors(projectId),
        projectRepository.observeRooms(projectId),
        projectRepository.observeTasks(projectId),
        projectRepository.observeNotes(projectId),
    ) { project, floors, rooms, tasks, notes ->
        ProjectDetailData(project, floors, rooms, tasks, notes, emptyList())
    }.combine(projectRepository.observePhotos(projectId)) { partial, photos ->
        partial.copy(photos = photos)
    }.combine(productRepository.observeAll()) { partial, products ->
        partial.copy(products = products)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectDetailData())

    private val _roomMixes = MutableStateFlow<Map<Long, MixResult?>>(emptyMap())
    val roomMixes: StateFlow<Map<Long, MixResult?>> = _roomMixes.asStateFlow()

    init {
        viewModelScope.launch {
            data.collect { current ->
                val productsWithComponents = productRepository.getAllWithComponents().associateBy { it.product.id }
                _roomMixes.value = current.rooms.associate { room ->
                    val mix = room.assignedProductId
                        ?.let { productsWithComponents[it] }
                        ?.let { MixCalculator.compute(it, room.areaM2, quantity = 1.0) }
                    room.id to mix
                }
            }
        }
    }

    fun addFloor(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val sortOrder = data.value.floors.size
            projectRepository.addFloor(FloorEntity(projectId = projectId, name = name.trim(), sortOrder = sortOrder))
        }
    }

    fun addRoom(floorId: Long, name: String, areaM2: Double) {
        if (name.isBlank() || areaM2 <= 0.0) return
        viewModelScope.launch {
            val sortOrder = data.value.rooms.count { it.floorId == floorId }
            projectRepository.addRoom(
                RoomAreaEntity(floorId = floorId, projectId = projectId, name = name.trim(), areaM2 = areaM2, sortOrder = sortOrder),
            )
        }
    }

    fun assignProduct(roomId: Long, productId: Long?) {
        viewModelScope.launch { projectRepository.assignProduct(roomId, productId) }
    }

    /** Saves a task from the shared editor. New tasks are pinned to this project. */
    fun saveTask(draft: TaskDraft) {
        if (draft.title.isBlank()) return
        val entity = draft.copy(projectId = projectId).toEntity()
        viewModelScope.launch {
            if (draft.id == null) projectRepository.addTask(entity) else projectRepository.updateTask(entity)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { projectRepository.deleteTask(taskId) }
    }

    fun setTaskDone(taskId: Long, done: Boolean) {
        viewModelScope.launch { projectRepository.setTaskDone(taskId, done) }
    }

    fun addNote(text: String, authorName: String, role: Role) {
        if (text.isBlank()) return
        viewModelScope.launch {
            projectRepository.addNote(
                NoteEntity(projectId = projectId, authorName = authorName, authorRole = role, text = text.trim(), createdAt = Instant.now()),
            )
        }
    }

    fun addPhoto(uri: String, roomId: Long?, caption: String) {
        viewModelScope.launch {
            projectRepository.addPhoto(PhotoEntity(projectId = projectId, roomId = roomId, uri = uri, caption = caption, takenAt = Instant.now()))
        }
    }

    fun setBlueprintUri(uri: String) {
        viewModelScope.launch {
            data.value.project?.let { projectRepository.save(it.copy(blueprintUri = uri)) }
        }
    }

    fun updateDetails(name: String, clientName: String, address: String, scopeNotes: String, startDate: LocalDate?, targetFinishDate: LocalDate?) {
        viewModelScope.launch {
            data.value.project?.let {
                projectRepository.save(
                    it.copy(
                        name = name.trim(),
                        clientName = clientName.trim(),
                        address = address.trim(),
                        scopeNotes = scopeNotes.trim(),
                        startDate = startDate,
                        targetFinishDate = targetFinishDate,
                    ),
                )
            }
        }
    }

    fun archive(onArchived: () -> Unit) {
        viewModelScope.launch {
            data.value.project?.let { projectRepository.archive(it) }
            onArchived()
        }
    }
}
