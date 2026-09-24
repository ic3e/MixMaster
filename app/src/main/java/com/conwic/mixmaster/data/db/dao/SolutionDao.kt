package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
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

    @Query("SELECT * FROM solutions WHERE id = :id")
    fun observeById(id: Long): Flow<SolutionEntity?>

    @Query("SELECT * FROM solution_lines WHERE solutionId = :solutionId ORDER BY sortOrder")
    fun observeLines(solutionId: Long): Flow<List<SolutionLineEntity>>

    /** Every line, so a screen can resolve many solutions without a query each. */
    @Query("SELECT * FROM solution_lines ORDER BY solutionId, sortOrder")
    fun observeAllLines(): Flow<List<SolutionLineEntity>>

    @Query("SELECT * FROM solutions WHERE isArchived = 0 ORDER BY brand, name")
    suspend fun getAll(): List<SolutionEntity>

    @Query("SELECT * FROM solution_lines ORDER BY solutionId, sortOrder")
    suspend fun getAllLines(): List<SolutionLineEntity>

    @Query("SELECT * FROM solutions WHERE id = :id")
    suspend fun getById(id: Long): SolutionEntity?


    @Query("SELECT DISTINCT brand FROM solutions WHERE isArchived = 0 AND brand != '' ORDER BY brand")
    fun observeBrands(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM solutions WHERE isArchived = 0 AND category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM solutions WHERE isArchived = 0")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(solution: SolutionEntity): Long

    @Update
    suspend fun update(solution: SolutionEntity)

    @Delete
    suspend fun delete(solution: SolutionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLine(line: SolutionLineEntity): Long

    @Query("DELETE FROM solution_lines WHERE solutionId = :solutionId")
    suspend fun deleteLines(solutionId: Long)
}
