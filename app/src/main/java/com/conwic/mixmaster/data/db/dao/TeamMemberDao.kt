package com.conwic.mixmaster.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamMemberDao {

    @Query("SELECT * FROM team_members ORDER BY role, name")
    fun observeAll(): Flow<List<TeamMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: TeamMemberEntity): Long

    @Delete
    suspend fun delete(member: TeamMemberEntity)
}
