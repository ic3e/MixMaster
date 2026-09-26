package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineEntity
import kotlinx.coroutines.flow.Flow

data class SolutionWithLines(
    val solution: SolutionEntity,
    val lines: List<SolutionLineEntity>,
)

@Dao
interface SolutionDao {

    @Query("SELECT * FROM solutions WHERE isArchived = 0 ORDER BY brand, name")
    fun observeAll(): Flow<List<SolutionEntity>>

    /** Every line, so a screen can resolve many solutions without a query each. */
    @Query("SELECT * FROM solution_lines ORDER BY solutionId, sortOrder")
    fun observeAllLines(): Flow<List<SolutionLineEntity>>

    @Query("SELECT * FROM solutions WHERE isArchived = 0 ORDER BY brand, name")
    suspend fun getAll(): List<SolutionEntity>

    @Query("SELECT * FROM solution_lines ORDER BY solutionId, sortOrder")
    suspend fun getAllLines(): List<SolutionLineEntity>



    @Query("SELECT DISTINCT brand FROM solutions WHERE isArchived = 0 AND brand != '' ORDER BY brand")
    fun observeBrands(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM solutions WHERE isArchived = 0 AND category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(solution: SolutionEntity): Long

    /** How many rows it changed: none when the recipe is no longer there. */
    @Update
    suspend fun update(solution: SolutionEntity): Int


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLine(line: SolutionLineEntity): Long

    @Query("DELETE FROM solution_lines WHERE solutionId = :solutionId")
    suspend fun deleteLines(solutionId: Long)
}
