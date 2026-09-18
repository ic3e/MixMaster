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

    @Query("SELECT * FROM room_areas WHERE floorId = :floorId ORDER BY sortOrder")
    fun observeForFloor(floorId: Long): Flow<List<RoomAreaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(room: RoomAreaEntity): Long

    @Update
    suspend fun update(room: RoomAreaEntity)

    @Query("UPDATE room_areas SET assignedProductId = :productId WHERE id = :roomId")
    suspend fun assignProduct(roomId: Long, productId: Long?)

    @Delete
    suspend fun delete(room: RoomAreaEntity)
}
