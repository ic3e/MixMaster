package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.conwic.mixmaster.data.model.Role

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val role: Role,
)
