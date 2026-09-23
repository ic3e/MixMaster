package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Something ordered that is not on the shelf yet.
 *
 * Kept apart from [StockEntity] because it is not stock: it cannot be mixed, carried or booked
 * against a job until someone says it turned up. Until then it only answers the question the
 * warehouse screen keeps raising — "this is short, is anything being done about it?"
 */
@Entity(
    tableName = "deliveries",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("productId"), Index("expectedOn")],
)
data class DeliveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    /** Whole packs ordered — the way an order is actually placed. */
    val packs: Int = 0,
    /** Anything ordered loose, in the product's own pack unit. */
    val amount: Double = 0.0,
    val expectedOn: LocalDate,
    val orderedOn: LocalDate,
    /** Supplier, order number, whatever will make sense when it turns up. */
    val note: String = "",
    /** Set when it arrived and went on the shelf. Null while it is still on its way. */
    val arrivedOn: LocalDate? = null,
)
