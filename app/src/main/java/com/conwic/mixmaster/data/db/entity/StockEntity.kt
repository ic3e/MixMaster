package com.conwic.mixmaster.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What is on the shelf for one part of one product.
 *
 * Counted per part, not per product: a two-part system runs out one part at a time, and the
 * shed holds bags of one and canisters of the other. [fullPacks] is what you count walking the
 * racks; [openAmount] is what is left in the one that has been started, in that pack's own unit.
 */
@Entity(
    tableName = "stock",
    indices = [Index(value = ["componentId"], unique = true), Index(value = ["productId"])],
)
data class StockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val componentId: Long,
    @ColumnInfo(defaultValue = "0") val fullPacks: Int = 0,
    @ColumnInfo(defaultValue = "0") val openAmount: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0L,
)
