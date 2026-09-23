package com.conwic.mixmaster.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One coat on one room: the primer, then the mix, then the sealer.
 *
 * A room used to hold a single product, which is not how a floor is built. Each layer names
 * either a solution or a plain product — a ready-to-use sealer needs no recipe — and carries
 * its own coverage, because a primer and a topping are not laid at the same rate.
 */
@Entity(
    tableName = "room_layers",
    foreignKeys = [
        ForeignKey(
            entity = RoomAreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("roomId"), Index("solutionId"), Index("productId")],
)
data class RoomLayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    /** The mix laid in this coat, or 0 when the coat is a single product. */
    @ColumnInfo(defaultValue = "0") val solutionId: Long = 0L,
    /** A product laid as it comes, or 0 when the coat is a solution. */
    @ColumnInfo(defaultValue = "0") val productId: Long = 0L,
    /** Coverage for this coat, in g/m². 0 means take the solution's typical. */
    @ColumnInfo(defaultValue = "0") val doseGramsPerM2: Double = 0.0,
    /** Coats, pours or millimetres, depending on the solution's dosing mode. */
    @ColumnInfo(defaultValue = "1") val quantity: Double = 1.0,
    val sortOrder: Int = 0,
)
