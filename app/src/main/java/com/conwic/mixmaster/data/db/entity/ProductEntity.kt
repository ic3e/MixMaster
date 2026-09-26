package com.conwic.mixmaster.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.conwic.mixmaster.data.model.DosingMode

/**
 * A sellable product, e.g. "Microtopping® Base Coat" from Ideal Work.
 * The per-m² dose always refers to the TOTAL mixed product weight. How a mix splits across its
 * parts (A/B, powder/polymer, cement/water) is a recipe: see [SolutionEntity].
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
    /**
     * A product that goes into another product's mix rather than being mixed on its own — a
     * colour pack, an admixture. Dosed off one named part of the base mix, not off the total:
     * a pigment is "28 g per 1 kg of polymer", and how much powder is in the batch doesn't
     * come into it.
     */
    @ColumnInfo(defaultValue = "0") val isAddOn: Boolean = false,
    /** How much of this add-on per 1 kg of the part it doses off, in [addOnUnit]. */
    @ColumnInfo(defaultValue = "0") val addOnAmountPerKg: Double = 0.0,
    /** "kg" or "L" — what [addOnAmountPerKg] counts. */
    @ColumnInfo(defaultValue = "kg") val addOnUnit: String = "kg",
    val isArchived: Boolean = false,
    /**
     * How this product is sold — 20.0 for a 20 kg bag, 10.0 for a 10 L canister. 0 means
     * nobody has told the app yet. Packaging belongs to the product, not to a mix: it is the
     * thing that is ordered, carried and counted on the shelf.
     */
    @ColumnInfo(defaultValue = "0") val packageSize: Double = 0.0,
    @ColumnInfo(defaultValue = "kg") val packageUnit: String = "kg",
    @ColumnInfo(defaultValue = "bag") val packageType: String = "bag",
    /** kg per litre, for turning a weight into the litres a canister is sold in. 0 = unknown. */
    @ColumnInfo(defaultValue = "0") val densityKgPerL: Double = 0.0,
    /**
     * The safety data sheet and the technical data sheet for this item.
     *
     * Each holds either a link the manufacturer publishes or a `file://` URI of a PDF copied
     * into the app — a site has no signal half the time, and a client asking for the safety
     * sheets is not going to wait for a supplier's website to come up.
     */
    @ColumnInfo(defaultValue = "") val safetySheetUrl: String = "",
    @ColumnInfo(defaultValue = "") val technicalSheetUrl: String = "",
    /**
     * Not bought or kept: found on site. Water, above all — it comes out of the client's tap,
     * so it belongs in every recipe and batch but never on the shelf, in a count or on an order.
     */
    @ColumnInfo(defaultValue = "0") val suppliedOnSite: Boolean = false,
)

/**
 * The product's technical sheet: its own, or the datasheet link from before there was one.
 *
 * The product form asked for a "Datasheet link" in its notes as well as the technical sheet above
 * it — the same document twice — and the link was saved and never shown anywhere. The box is gone;
 * a link already typed into it, and the ones the catalogue came with, turn up here instead.
 */
val ProductEntity.technicalSheet: String get() = technicalSheetUrl.ifBlank { datasheetUrl }
