package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    /**
     * Newest first, with a job that has no start date yet at the top rather than the bottom:
     * ordering on the date alone buried every project the moment it was created, because the
     * date is filled in later if at all.
     */
    @Query(
        "SELECT * FROM projects WHERE isArchived = 0 " +
            "ORDER BY COALESCE(startDate, 9999999) DESC, id DESC",
    )
    fun observeAll(): Flow<List<ProjectEntity>>

    /** What has been put away. Nothing listed it, so archiving was a one-way door. */
    @Query("SELECT * FROM projects WHERE isArchived = 1 ORDER BY id DESC")
    fun observeArchived(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: Long): Flow<ProjectEntity?>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

}
