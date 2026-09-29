package com.conwic.mixmaster.data.db.entity

import androidx.room.ColumnInfo
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
    /**
     * The app's own copy of the photo (see PhotoStore). Blank on a phone the photo was shared to,
     * until its file has come down from the company's server.
     */
    val uri: String,
    val caption: String = "",
    val takenAt: Instant,
    /** What the company's server keeps the file under; blank on a phone that has never shared it. */
    @ColumnInfo(defaultValue = "") val fileKey: String = "",
)
