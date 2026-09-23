package com.conwic.mixmaster.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A site-logged coverage reading for a product — "I actually used N g/m² on this job" — the
 * data behind Product Detail's "your site average" field, separate from the datasheet range.
 */
@Entity(
    tableName = "usage_logs",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("productId")],
)
data class UsageLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val doseGramsPerM2: Double,
    val loggedAt: Instant,
    /** The mix this reading was taken on. [productId] is the shape it was logged in before
     * mixes and products were separate things, and is kept only so the rows survive. */
    @ColumnInfo(defaultValue = "0") val solutionId: Long = 0L,
)
