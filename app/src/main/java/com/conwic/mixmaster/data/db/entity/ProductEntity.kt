package com.conwic.mixmaster.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.conwic.mixmaster.data.model.DosingMode

/**
 * A sellable product, e.g. "Microtopping® Base Coat" from Ideal Work.
 * The per-m² dose always refers to the TOTAL mixed product weight; [ProductComponentEntity]
 * rows describe how that total splits across parts (A/B, powder/polymer, cement/water/
 * additive/gravel, ...) by ratio.
 *
 * Datasheets give a coverage RANGE, not a single number — [minDoseGramsPerM2] and
 * [maxDoseGramsPerM2] are that real range, and [typicalDoseGramsPerM2] (their midpoint,
 * computed on save) is just the default starting point for the calculator's slider. Where no
 * published range exists, min/max/typical are all set equal rather than a range being invented.
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val name: String,
    val category: String,
    val dosingMode: DosingMode,
    val minDoseGramsPerM2: Double,
    val maxDoseGramsPerM2: Double,
    /** Midpoint of min/max — the calculator slider's default position. */
    val typicalDoseGramsPerM2: Double,
    /** Short human label shown next to the dose, e.g. "per coat", "per pour", "per mm". */
    val doseUnitLabel: String,
    /** Free-text note shown on the product card, e.g. "~1.35 kg/m² per coat". */
    val rangeNote: String,
    /** Free-text source reference, e.g. "Ideal Work technical datasheet". */
    val sourceNote: String = "",
    /** Link to the manufacturer's published datasheet, if known. */
    val datasheetUrl: String = "",
    /** Denormalized display label, e.g. "100:35" — computed from components on save so product
     * list cards can show it without a join. */
    val ratioLabel: String = "",
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "product_components",
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
data class ProductComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    /** e.g. "Powder", "Polymer", "Part A", "Part B", "Cement", "Water", "Additive", "Gravel". */
    val label: String,
    /** Relative ratio part, e.g. 100 and 35 for a 100:35 mix. Units cancel out. */
    val ratioParts: Double,
    /** "Weight" or "Volume" — informational only; doesn't affect the mix math (matching how
     * the original design itself treated it — display classification, not unit conversion). */
    val basis: String = "Weight",
    val density: String = "",
    val potLife: String = "",
    val notes: String = "",
    val sortOrder: Int,
    /** How this part is sold, e.g. 20.0 for a 20 kg bag or 10.0 for a 10 L canister.
     * 0 means nobody has told the app the pack size yet. */
    @ColumnInfo(defaultValue = "0") val packageSize: Double = 0.0,
    /** "kg" or "L" — the unit the pack is sold in. */
    @ColumnInfo(defaultValue = "kg") val packageUnit: String = "kg",
    /** "bag", "bucket", "canister", "bottle", "drum" or "tub". */
    @ColumnInfo(defaultValue = "bag") val packageType: String = "bag",
    /** Needed to convert this part's weight into litres, both for L-sold packs and for
     * working out whether a batch fits the mixer. 0 means unknown. */
    @ColumnInfo(defaultValue = "0") val densityKgPerL: Double = 0.0,
)
