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
    companion object {
        /**
         * For [save]: a line measured against the line at this place in the list being saved,
         * which has no id yet. Written as a negative number so it can't be mistaken for an id.
         */
        fun againstPosition(position: Int): Long = -(position + 1L)
    }


    fun observeAll(): Flow<List<SolutionEntity>> = solutionDao.observeAll()

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
            // Gone since the form was opened — taken away on another phone, or put back by the
            // company's server: written again under its own id, so its lines have it to belong to.
            if (solutionDao.update(solution) == 0) solutionDao.insert(solution)
            solution.id
        }
        solutionDao.deleteLines(id)
        // The lines are written afresh under new ids, so a line measured against another one is
        // pointed at it again here. It used to keep the old id, which was gone: the mix maths
        // fell back to the first part, and a colour given per kg of the catalyst was weighed off
        // the powder from the first save on.
        val written = mutableListOf<Long>()
        val renamed = mutableMapOf<Long, Long>()
        lines.forEachIndexed { index, line ->
            val against = when {
                line.againstLineId < 0L -> written.getOrElse((-line.againstLineId - 1L).toInt()) { 0L }
                line.againstLineId > 0L -> renamed[line.againstLineId] ?: line.againstLineId
                else -> 0L
            }
            val newId = solutionDao.insertLine(line.copy(id = 0L, solutionId = id, sortOrder = index, againstLineId = against))
            written += newId
            if (line.id != 0L) renamed[line.id] = newId
        }
        return id
    }

    suspend fun archive(solution: SolutionEntity) = solutionDao.update(solution.copy(isArchived = true))

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
