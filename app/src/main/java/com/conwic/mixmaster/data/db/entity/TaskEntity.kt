package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.conwic.mixmaster.data.model.TaskPriority
import java.time.LocalDate

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index("dueDate")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val title: String,
    val dueDate: LocalDate?,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val isDone: Boolean = false,
)
