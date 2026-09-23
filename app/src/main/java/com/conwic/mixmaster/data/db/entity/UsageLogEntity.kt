package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A site-logged coverage reading for a mix — "I actually used N g/m² on this job" — the data
 * behind "your site average", separate from the datasheet range.
 *
 * Against the solution, because coverage is a property of what goes on the floor, not of a bag
 * of powder.
 */
@Entity(
    tableName = "usage_logs",
    foreignKeys = [
        ForeignKey(
            entity = SolutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["solutionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("solutionId")],
)
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val solutionId: Long,
    val doseGramsPerM2: Double,
    val loggedAt: Instant,
)
