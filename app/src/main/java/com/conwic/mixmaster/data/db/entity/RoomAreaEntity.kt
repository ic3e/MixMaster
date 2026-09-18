package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single room/bay/zone inside a [FloorEntity]. Named RoomAreaEntity (not RoomEntity) to avoid
 * clashing with androidx.room.Room, the database class.
 */
@Entity(
    tableName = "room_areas",
    foreignKeys = [
        ForeignKey(
            entity = FloorEntity::class,
            parentColumns = ["id"],
            childColumns = ["floorId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["assignedProductId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("floorId"), Index("projectId"), Index("assignedProductId")],
)
data class RoomAreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val floorId: Long,
    val projectId: Long,
    val name: String,
    val areaM2: Double,
    val assignedProductId: Long? = null,
    val sortOrder: Int = 0,
)
