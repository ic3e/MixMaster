package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.conwic.mixmaster.data.model.ProjectStatus
import java.time.LocalDate

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val clientName: String,
    val address: String,
    val status: ProjectStatus,
    val startDate: LocalDate?,
    val targetFinishDate: LocalDate?,
    val scopeNotes: String = "",
    /** content:// URI to an attached blueprint image or PDF, set by the Employer. */
    val blueprintUri: String? = null,
    val isArchived: Boolean = false,
    /**
     * When this job's material was taken off the warehouse shelf. Until then the job books
     * what it needs; afterwards it has its material and books nothing.
     */
    val materialsIssuedAt: Long? = null,
)
