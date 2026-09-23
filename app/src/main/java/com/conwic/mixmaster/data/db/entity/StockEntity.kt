package com.conwic.mixmaster.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What is on the shelf for one product.
 *
 * [fullPacks] is what you count walking the racks; [openAmount] is what is left in the one that
 * has been started, in that pack's own unit.
 */
@Entity(
    tableName = "stock",
    indices = [Index(value = ["productId"], unique = true)],
)
data class StockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    @ColumnInfo(defaultValue = "0") val fullPacks: Int = 0,
    @ColumnInfo(defaultValue = "0") val openAmount: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)
