package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.SolutionDao
import com.conwic.mixmaster.data.db.dao.UsageLogDao
import com.conwic.mixmaster.data.db.dao.SolutionWithLines
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.SolutionLineEntity
import com.conwic.mixmaster.data.db.entity.UsageLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant

class SolutionRepository(
    private val solutionDao: SolutionDao,
    private val usageLogDao: UsageLogDao,
) {

    fun observeAll(): Flow<List<SolutionEntity>> = solutionDao.observeAll()

    fun observeById(id: Long): Flow<SolutionEntity?> = solutionDao.observeById(id)

    fun observeAllLines(): Flow<List<SolutionLineEntity>> = solutionDao.observeAllLines()

    fun observeAllWithLines(): Flow<List<SolutionWithLines>> =
        combine(solutionDao.observeAll(), solutionDao.observeAllLines()) { solutions, lines ->
            val bySolution = lines.groupBy { it.solutionId }
            solutions.map { SolutionWithLines(it, bySolution[it.id].orEmpty()) }
        }

    suspend fun getAllWithLines(): List<SolutionWithLines> {
        val lines = solutionDao.getAllLines().groupBy { it.solutionId }
        return solutionDao.getAll().map { SolutionWithLines(it, lines[it.id].orEmpty()) }
    }

    fun observeBrands(): Flow<List<String>> = solutionDao.observeBrands()

    fun observeCategories(): Flow<List<String>> = solutionDao.observeCategories()

    fun observeCount(): Flow<Int> = solutionDao.observeCount()

    fun observeSolutionsUsing(productId: Long): Flow<List<SolutionEntity>> =
        observeAllWithLines().map { all ->
            all.filter { withLines -> withLines.lines.any { it.productId == productId } }
                .map { it.solution }
        }

    /** Saves a solution and its lines together — the lines are the recipe, not an afterthought. */
    suspend fun save(solution: SolutionEntity, lines: List<SolutionLineEntity>): Long {
        val id = if (solution.id == 0L) {
            solutionDao.insert(solution)
        } else {
            solutionDao.update(solution)
            solution.id
        }
        solutionDao.deleteLines(id)
        lines.forEachIndexed { index, line ->
            solutionDao.insertLine(line.copy(id = 0L, solutionId = id, sortOrder = index))
        }
        return id
    }

    suspend fun archive(solution: SolutionEntity) = solutionDao.update(solution.copy(isArchived = true))

    suspend fun delete(solution: SolutionEntity) = solutionDao.delete(solution)

    fun observeUsageLogs(): Flow<List<UsageLogEntity>> = usageLogDao.observeAll()

    fun observeUsageLogs(solutionId: Long): Flow<List<UsageLogEntity>> =
        usageLogDao.observeForSolution(solutionId)

    /** Records what was actually laid, which is what "your site average" is made of. */
    suspend fun logUsage(solutionId: Long, doseGramsPerM2: Double) {
        usageLogDao.insert(
            UsageLogEntity(solutionId = solutionId, doseGramsPerM2 = doseGramsPerM2, loggedAt = Instant.now()),
        )
    }
}
