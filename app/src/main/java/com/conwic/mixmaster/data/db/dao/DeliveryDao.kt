package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.conwic.mixmaster.data.db.entity.DeliveryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeliveryDao {

    /** Everything ordered, soonest first — arrived lines included, so a shelf count can be traced back. */
    @Query("SELECT * FROM deliveries ORDER BY expectedOn")
    fun observeAll(): Flow<List<DeliveryEntity>>

    @Insert
    suspend fun insert(row: DeliveryEntity): Long

    @Update
    suspend fun update(row: DeliveryEntity)

    @Query("DELETE FROM deliveries WHERE id = :id")
    suspend fun delete(id: Long)
}
