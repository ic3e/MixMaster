package com.conwic.mixmaster.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.gramsIfTypedInKilos
import com.conwic.mixmaster.domain.toNumberOrNull

/**
 * Said under a "g per kg" box when the figure in it reads like kilos: a colour typed as the
 * datasheet's 0.028 went into the mix at 0.028 g per kg — a fifth of a gram where 160 g were due —
 * and the calculator showed it as "0 g per kg". Not refused: it is said, with the figure to type.
 */
@Composable
fun kilosTyped(text: String): String? {
    val typed = text.toNumberOrNull()
    val grams = gramsIfTypedInKilos(typed) ?: return null
    return stringResource(R.string.addon_rate_kg_typed, text.trim(), formatDecimal(grams, 1))
}
