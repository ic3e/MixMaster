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
    /**
     * The first coat of the mix this one belongs to, or 0 when it is that first coat.
     *
     * A datasheet can give one product two recipes: architop's first coat is 2.0 kg/m² of
     * hardener to 0.48 of catalyst, its second 1.5 to 0.24 — different ratio, different
     * coverage, same tin. Each coat is a recipe of its own, and this is what ties them
     * together so the calculator can offer them side by side.
     */
    @ColumnInfo(defaultValue = "0") val parentId: Long = 0L,
    /** What this coat is called — "1st coat". Blank when the mix has only the one. */
    @ColumnInfo(defaultValue = "") val coatName: String = "",
    /**
     * How long the datasheet says to mix it for, in seconds. Zero when nobody has said.
     *
     * "Mix thoroughly for at least 2 minutes" is the line every datasheet carries and the one
     * nobody times — the drill comes out when the lumps go, which is usually early.
     */
    @ColumnInfo(defaultValue = "0") val mixSeconds: Int = 0,
    /**
     * How long the mixed material stays workable, in minutes. 0 means the datasheet does not
     * say — or nobody has typed it in yet.
     */
    @ColumnInfo(defaultValue = "0") val potLifeMinutes: Int = 0,
)

/** The mix a coat belongs to: itself, when it is the first coat. */
val SolutionEntity.familyId: Long get() = if (parentId > 0L) parentId else id

/** "architop · 2nd coat", or just the name when there is only one coat. */
val SolutionEntity.coatLabel: String
    get() = if (coatName.isBlank()) name else "$name · $coatName"

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
    /**
     * When set, this part is that percentage of everything else in the drum rather than a ratio
     * part of its own — "10 parts A + 2 parts B + water at 10% of (A+B)", which is how a fair
     * few datasheets word the water and the thinner.
     *
     * [ratioParts] still carries what that comes to, worked out when the recipe is saved, so
     * everything downstream — the batches, the packing, the film thickness — goes on reading
     * one number. This is what the recipe was written as, so the editor can show it back the
     * way the datasheet says it and work it out again if A or B changes.
     */
    @ColumnInfo(defaultValue = "0") val percentOfRest: Double = 0.0,
    /** For an [SolutionLineRole.ADD_ON]: how much per 1 kg of the part it is dosed against. */
    @ColumnInfo(defaultValue = "0") val amountPerKg: Double = 0.0,
    /** "kg" or "L" — what [amountPerKg] counts. */
    @ColumnInfo(defaultValue = "kg") val amountUnit: String = "kg",
    /** The base line an add-on doses against. 0 means the whole mix. */
    @ColumnInfo(defaultValue = "0") val againstLineId: Long = 0L,
    val sortOrder: Int = 0,
)
