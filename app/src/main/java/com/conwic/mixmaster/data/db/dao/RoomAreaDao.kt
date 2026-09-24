package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomAreaDao {

    @Query("SELECT * FROM room_areas WHERE projectId = :projectId ORDER BY sortOrder")
    fun observeForProject(projectId: Long): Flow<List<RoomAreaEntity>>

    /** Every room on every project, so the warehouse can see what is spoken for. */
    @Query("SELECT * FROM room_areas ORDER BY projectId, sortOrder")
    fun observeAll(): Flow<List<RoomAreaEntity>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(room: RoomAreaEntity): Long

    @Update
    suspend fun update(room: RoomAreaEntity)


    @Delete
    suspend fun delete(room: RoomAreaEntity)
}
