package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.conwic.mixmaster.data.db.entity.MaterialUseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialUseDao {

    /** Newest first: the last thing mixed is the thing being asked about. */
    @Query("SELECT * FROM material_uses WHERE projectId = :projectId ORDER BY mixedAt DESC")
    fun observeForProject(projectId: Long): Flow<List<MaterialUseEntity>>


    @Insert
    suspend fun insert(use: MaterialUseEntity): Long

    @Query("DELETE FROM material_uses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
