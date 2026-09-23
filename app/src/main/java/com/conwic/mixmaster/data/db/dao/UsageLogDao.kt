package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conwic.mixmaster.data.db.entity.UsageLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageLogDao {

    @Query("SELECT * FROM usage_logs WHERE solutionId = :solutionId ORDER BY loggedAt DESC")
    fun observeForSolution(solutionId: Long): Flow<List<UsageLogEntity>>

    /** Every reading, so one screen can show several mixes without a query each. */
    @Query("SELECT * FROM usage_logs ORDER BY loggedAt DESC")
    fun observeAll(): Flow<List<UsageLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: UsageLogEntity): Long
}
