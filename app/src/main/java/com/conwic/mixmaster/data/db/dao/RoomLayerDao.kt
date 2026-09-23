package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.conwic.mixmaster.data.db.entity.RoomLayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomLayerDao {

    @Query("SELECT * FROM room_layers WHERE roomId = :roomId ORDER BY sortOrder")
    fun observeForRoom(roomId: Long): Flow<List<RoomLayerEntity>>

    /** Every coat on every room, for the warehouse's bookings. */
    @Query("SELECT * FROM room_layers ORDER BY roomId, sortOrder")
    fun observeAll(): Flow<List<RoomLayerEntity>>

    @Query(
        "SELECT rl.* FROM room_layers rl " +
            "JOIN room_areas ra ON ra.id = rl.roomId " +
            "WHERE ra.projectId = :projectId ORDER BY rl.roomId, rl.sortOrder",
    )
    fun observeForProject(projectId: Long): Flow<List<RoomLayerEntity>>

    @Query("SELECT COUNT(*) FROM room_layers WHERE roomId = :roomId")
    suspend fun countForRoom(roomId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(layer: RoomLayerEntity): Long

    @Update
    suspend fun update(layer: RoomLayerEntity)

    @Delete
    suspend fun delete(layer: RoomLayerEntity)
}
