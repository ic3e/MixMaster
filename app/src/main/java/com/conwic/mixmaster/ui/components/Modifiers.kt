package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * A tappable inline text link with no ripple.
 *
 * A plain clickable draws its ripple across the whole layout box, so a short coloured link ends
 * up flashing a grey rectangle the size of its text. The colour already marks these as tappable.
 */
@Composable
fun Modifier.tappableText(enabled: Boolean = true, onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    enabled = enabled,
    onClick = onClick,
)
