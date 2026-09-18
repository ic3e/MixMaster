package com.conwic.mixmaster.data.repository

import com.conwic.mixmaster.data.db.dao.TeamMemberDao
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import kotlinx.coroutines.flow.Flow

class TeamRepository(private val teamMemberDao: TeamMemberDao) {
    fun observeAll(): Flow<List<TeamMemberEntity>> = teamMemberDao.observeAll()
    suspend fun add(member: TeamMemberEntity): Long = teamMemberDao.insert(member)
    suspend fun remove(member: TeamMemberEntity) = teamMemberDao.delete(member)
}
