package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.conwic.mixmaster.data.db.entity.BlueprintEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlueprintDao {

    /** In the order they were added, so "Blueprint 2" stays the second one. */
    @Query("SELECT * FROM blueprints WHERE projectId = :projectId ORDER BY addedAt, id")
    fun observeForProject(projectId: Long): Flow<List<BlueprintEntity>>

    @Insert
    suspend fun insert(blueprint: BlueprintEntity): Long

    @Delete
    suspend fun delete(blueprint: BlueprintEntity)
}
