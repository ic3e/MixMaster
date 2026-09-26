package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One drawing attached to a project — a floor plan, a section, a revision.
 *
 * A project used to hold a single blueprint in a column of its own, which could be swapped but
 * never taken off, and a job with a plan per floor had nowhere to put the second one.
 *
 * Kept on this phone only, like photos: what is stored is a link to a file on this phone, which
 * would mean nothing on another one.
 */
@Entity(
    tableName = "blueprints",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class BlueprintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    /** content:// URI the document picker granted for good. */
    val uri: String,
    /** The file's own name, as the picker gave it. Blank for the one carried over from before. */
    val name: String,
    /** image/…, application/pdf — blank when it wasn't known. */
    val mimeType: String,
    val addedAt: Instant,
)
