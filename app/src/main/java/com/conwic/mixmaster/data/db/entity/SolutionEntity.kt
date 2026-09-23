package com.conwic.mixmaster.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.conwic.mixmaster.data.model.DosingMode

/**
 * A mix as it goes on the floor, built out of products.
 *
 * A product is a thing you buy — a bag of powder, a canister of polymer, water, a pigment. A
 * solution is what you make of them: which products, in what ratio, and how much of the result
 * goes down per square metre. The two were one thing until now, which meant water sat in the
 * catalogue as a product with a mix ratio, and a floor that takes a primer, a coat and a sealer
 * had nowhere to say so.
 */
@Entity(tableName = "solutions")
data class SolutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val name: String,
    val category: String,
    val dosingMode: DosingMode,
    /** The datasheet's real range for the mixed material, per m². */
    val minDoseGramsPerM2: Double,
    val maxDoseGramsPerM2: Double,
    /** Midpoint of the range — where the calculator's slider starts. */
    val typicalDoseGramsPerM2: Double,
    val doseUnitLabel: String,
    val rangeNote: String,
    val sourceNote: String = "",
    val datasheetUrl: String = "",
    /** Denormalised "100:35", so a list card needs no join. */
    val ratioLabel: String = "",
    val isArchived: Boolean = false,
)

/** What a line of a solution is for. */
object SolutionLineRole {
    /** Part of the ratio: powder to water, A to B. */
    const val BASE = "BASE"
    /**
     * Dosed off one named part rather than by ratio — a pigment at 28 g per kg of polymer.
     * How much powder is in the batch does not come into it.
     */
    const val ADD_ON = "ADD_ON"
}

@Entity(
    tableName = "solution_lines",
    foreignKeys = [
        ForeignKey(
            entity = SolutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["solutionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("solutionId"), Index("productId")],
)
data class SolutionLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val solutionId: Long,
    val productId: Long,
    /** What this part is called in this mix — "Powder", "Part B" — which need not be the
     * product's own name. Blank falls back to the product name. */
    val label: String = "",
    val role: String = SolutionLineRole.BASE,
    /** Relative ratio part for a [SolutionLineRole.BASE] line. Units cancel out. */
    val ratioParts: Double = 0.0,
    /** For an [SolutionLineRole.ADD_ON]: how much per 1 kg of the part it is dosed against. */
    @ColumnInfo(defaultValue = "0") val amountPerKg: Double = 0.0,
    /** "kg" or "L" — what [amountPerKg] counts. */
    @ColumnInfo(defaultValue = "kg") val amountUnit: String = "kg",
    /** The base line an add-on doses against. 0 means the whole mix. */
    @ColumnInfo(defaultValue = "0") val againstLineId: Long = 0L,
    val sortOrder: Int = 0,
)
