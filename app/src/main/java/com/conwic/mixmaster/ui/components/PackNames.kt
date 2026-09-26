package com.conwic.mixmaster.ui.components

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.formatDecimal
import kotlin.math.floor

/**
 * What a pack is called, in the app's language.
 *
 * The kind of pack is stored as an English word — "bag", "canister" — because it is a key the
 * forms pick from and the company's server passes along, not text anybody reads. It used to be
 * shown as it was stored, so an Estonian stock count asked for "Avamata (bottle)" and the mixer
 * was told to add "0.56 canister".
 *
 * Three forms, because the languages need them: the word alone ("pudel"), the word over a count
 * of them ("Avamata (pudelid)"), and the word after a number, which Estonian and Finnish put in
 * another case ("3 pudelit", "3 pulloa").
 */
private class PackWords(@StringRes val one: Int, @StringRes val many: Int, @PluralsRes val counted: Int)

private val Words = mapOf(
    "bag" to PackWords(R.string.pack_bag, R.string.pack_bags, R.plurals.pack_bag_count),
    "bucket" to PackWords(R.string.pack_bucket, R.string.pack_buckets, R.plurals.pack_bucket_count),
    "canister" to PackWords(R.string.pack_canister, R.string.pack_canisters, R.plurals.pack_canister_count),
    "bottle" to PackWords(R.string.pack_bottle, R.string.pack_bottles, R.plurals.pack_bottle_count),
    "drum" to PackWords(R.string.pack_drum, R.string.pack_drums, R.plurals.pack_drum_count),
    "tub" to PackWords(R.string.pack_tub, R.string.pack_tubs, R.plurals.pack_tub_count),
)

private fun wordsFor(type: String): PackWords? = Words[type.trim().lowercase()]

/**
 * Which plural a figure takes. Android counts in whole numbers, and 1.5 bags is not one bag, so
 * anything with a fraction is asked for as the "many" form.
 */
private fun quantityOf(count: Double): Int = if (count == floor(count)) count.toInt() else 2

/** "bottle" → "pudel". A kind the app does not know is shown as it was typed. */
fun packName(context: Context, type: String): String {
    val words = wordsFor(type) ?: return type
    return context.getString(words.one)
}

/** "3 bottles", "0.56 canisters" — a number of packs, with the word in the case it takes. */
fun packCount(context: Context, count: Double, type: String): String {
    val figure = formatDecimal(count, 2)
    val words = wordsFor(type) ?: return "$figure $type"
    return context.resources.getQuantityString(words.counted, quantityOf(count), figure)
}

@Composable
fun packName(type: String): String {
    val words = wordsFor(type) ?: return type
    return stringResource(words.one)
}

/** The kind of pack over a count of them — "Unopened (bottles)", "Packs ordered (buckets)". */
@Composable
fun packsName(type: String): String {
    val words = wordsFor(type) ?: return type
    return stringResource(words.many)
}

@Composable
fun packCount(count: Double, type: String): String = packCount(LocalContext.current, count, type)

@Composable
fun packCount(count: Int, type: String): String = packCount(LocalContext.current, count.toDouble(), type)

/** "25 kg bucket" — the way a pack is said at the rack. */
@Composable
fun packLabel(size: Double, unit: String, type: String): String = "${formatDecimal(size, 2)} $unit ${packName(type)}"
