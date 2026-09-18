package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RoomAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("projectId"), Index("roomId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val roomId: Long? = null,
    /** content:// URI from the camera or gallery picker. */
    val uri: String,
    val caption: String = "",
    val takenAt: Instant,
)
