package com.conwic.mixmaster.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * One mix that was actually made, written against the project it was made for.
 *
 * Everything else about a project is a plan: the coats say what the floor is to be built of and
 * the warehouse says what to carry. Nothing said what had gone down already, so "how much of it
 * is used" was a question for whoever remembered. This is the receipt, written when the mixing
 * screen is finished with, and it is a receipt in the strict sense — the amounts are the ones
 * that went in the drum, kept apart from the recipe, which gets edited afterwards.
 *
 * Only the project is a foreign key. The room's name is copied in rather than followed: a bay
 * gets renamed, a coat comes off the layout, and what was mixed that morning still happened.
 */
@Entity(
    tableName = "material_uses",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index("roomId")],
)
data class MaterialUseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    /** The room it went on, or 0 where the mix was made for the project as a whole. */
    val roomId: Long = 0L,
    val solutionId: Long = 0L,
    /** What was mixed, in the words the mixing screen used. */
    val title: String = "",
    /** Where it went, as it read on screen: the project and the room. */
    val jobLabel: String = "",
    /** How many trips to the mixer this covers. */
    val batches: Int = 0,
    val totalGrams: Double = 0.0,
    /** What went in, per part: a JSON array of {productId, label, grams}. */
    val parts: String = "[]",
    val mixedAt: Long = 0L,
)

/** One part of a mix, as much of it as actually went in. */
data class UsedAmount(val productId: Long, val label: String, val grams: Double)

/**
 * The parts of this receipt.
 *
 * Held as JSON in one column rather than in a table of its own because nothing queries inside
 * it: it is read whole, for one project at a time, and it must never be recomputed from a
 * recipe that has moved on since.
 */
fun MaterialUseEntity.usedAmounts(): List<UsedAmount> = runCatching {
    val array = JSONArray(parts)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        UsedAmount(
            productId = item.optLong("productId"),
            label = item.optString("label"),
            grams = item.optDouble("grams", 0.0),
        )
    }
}.getOrDefault(emptyList())

/** The same list on its way in. */
fun usedAmountsJson(amounts: List<UsedAmount>): String = JSONArray().apply {
    amounts.forEach { amount ->
        put(
            JSONObject().apply {
                put("productId", amount.productId)
                put("label", amount.label)
                put("grams", amount.grams)
            },
        )
    }
}.toString()
