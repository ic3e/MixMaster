package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conwic.mixmaster.data.db.entity.FloorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FloorDao {

    @Query("SELECT * FROM floors WHERE projectId = :projectId ORDER BY sortOrder")
    fun observeForProject(projectId: Long): Flow<List<FloorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(floor: FloorEntity): Long

    @Delete
    suspend fun delete(floor: FloorEntity)
}
