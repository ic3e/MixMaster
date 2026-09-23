package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.FloorDao
import com.conwic.mixmaster.data.db.dao.NoteDao
import com.conwic.mixmaster.data.db.dao.PhotoDao
import com.conwic.mixmaster.data.db.dao.ProjectDao
import com.conwic.mixmaster.data.db.dao.RoomAreaDao
import com.conwic.mixmaster.data.db.dao.TaskDao
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val floorDao: FloorDao,
    private val roomAreaDao: RoomAreaDao,
    private val taskDao: TaskDao,
    private val noteDao: NoteDao,
    private val photoDao: PhotoDao,
) {
    fun observeAll(): Flow<List<ProjectEntity>> = projectDao.observeAll()

    fun observeById(id: Long): Flow<ProjectEntity?> = projectDao.observeById(id)

    suspend fun getById(id: Long): ProjectEntity? = projectDao.getById(id)

    fun observeActiveCount(): Flow<Int> = projectDao.observeActiveCount()

    suspend fun save(project: ProjectEntity): Long =
        if (project.id == 0L) projectDao.insert(project) else { projectDao.update(project); project.id }

    suspend fun archive(project: ProjectEntity) = projectDao.update(project.copy(isArchived = true))

    /** Stamps a job as having its material, which is what stops it booking any more. */
    suspend fun setMaterialsIssued(project: ProjectEntity, at: Long?) =
        projectDao.update(project.copy(materialsIssuedAt = at))

    /** Every room on every project, for working out what the warehouse has spoken for. */
    fun observeAllRooms(): Flow<List<RoomAreaEntity>> = roomAreaDao.observeAll()

    fun observeFloors(projectId: Long): Flow<List<FloorEntity>> = floorDao.observeForProject(projectId)

    suspend fun addFloor(floor: FloorEntity): Long = floorDao.insert(floor)

    fun observeRooms(projectId: Long): Flow<List<RoomAreaEntity>> = roomAreaDao.observeForProject(projectId)

    suspend fun addRoom(room: RoomAreaEntity): Long = roomAreaDao.insert(room)

    suspend fun assignProduct(roomId: Long, productId: Long?) = roomAreaDao.assignProduct(roomId, productId)

    fun observeTasks(projectId: Long): Flow<List<TaskEntity>> = taskDao.observeForProject(projectId)

    fun observeAllTasks(): Flow<List<TaskEntity>> = taskDao.observeAll()

    fun observeTasksForDay(epochDay: Long): Flow<List<TaskEntity>> = taskDao.observeForDay(epochDay)

    suspend fun addTask(task: TaskEntity): Long = taskDao.insert(task)

    suspend fun setTaskDone(taskId: Long, done: Boolean) = taskDao.setDone(taskId, done)

    suspend fun updateTask(task: TaskEntity) = taskDao.update(task)

    suspend fun deleteTask(taskId: Long) = taskDao.deleteById(taskId)

    fun observeNotes(projectId: Long): Flow<List<NoteEntity>> = noteDao.observeForProject(projectId)

    suspend fun addNote(note: NoteEntity): Long = noteDao.insert(note)

    fun observePhotos(projectId: Long): Flow<List<PhotoEntity>> = photoDao.observeForProject(projectId)

    suspend fun addPhoto(photo: PhotoEntity): Long = photoDao.insert(photo)
}
