package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conwic.mixmaster.data.db.entity.UsageLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageLogDao {

    @Query("SELECT * FROM usage_logs WHERE productId = :productId ORDER BY loggedAt DESC")
    fun observeForProduct(productId: Long): Flow<List<UsageLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: UsageLogEntity): Long
}
