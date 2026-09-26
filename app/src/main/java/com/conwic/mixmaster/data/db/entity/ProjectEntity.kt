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
    /**
     * The one blueprint a project could have before they got a table of their own
     * ([BlueprintEntity]). Moved across by the 18→19 migration and no longer read; the column
     * stays because dropping one in SQLite means rebuilding a shared table.
     */
    val blueprintUri: String? = null,
    val isArchived: Boolean = false,
    /**
     * When this job's material was taken off the warehouse shelf. Until then the job books
     * what it needs; afterwards it has its material and books nothing.
     */
    val materialsIssuedAt: Long? = null,
)
