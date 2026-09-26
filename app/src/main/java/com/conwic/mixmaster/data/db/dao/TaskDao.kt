package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.conwic.mixmaster.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY dueDate")
    fun observeForProject(projectId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY dueDate")
    fun observeAll(): Flow<List<TaskEntity>>



    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET isDone = :done WHERE id = :taskId")
    suspend fun setDone(taskId: Long, done: Boolean)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: Long)

}
