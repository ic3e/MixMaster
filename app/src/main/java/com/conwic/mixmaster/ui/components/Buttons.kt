package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.ui.theme.Charcoal
import com.conwic.mixmaster.ui.theme.ChipShape
import com.conwic.mixmaster.ui.theme.AppFontFamily

private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

@Composable
private fun ButtonLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        letterSpacing = 0.4.sp,
    )
}

/** The design's primary call to action — solid charcoal. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ChipShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Charcoal,
            contentColor = Color.White,
            // Material's defaults derive from the scheme and vanish against a dark background.
            disabledContainerColor = Charcoal.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        // The design's buttons are flat: no drop shadow, resting or pressed.
        elevation = null,
        contentPadding = ButtonPadding,
        modifier = modifier,
    ) {
        ButtonLabel(text)
    }
}

/** The bordered, surface-coloured secondary action. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ChipShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        elevation = null,
        contentPadding = ButtonPadding,
        modifier = modifier,
    ) {
        ButtonLabel(text)
    }
}

/**
 * An action worded as a link — "Mix", "Edit", "+ Add coat", "Attach a PDF".
 *
 * Sized for a thumb rather than for the label beside it. These were set at labelSmall with the
 * tap area ending exactly where the letters did, which is a miss on a phone held in one hand
 * with gloves on. The padding sits inside the clickable, which is what makes the target bigger
 * than the word: outside it, it only pushes the neighbours away.
 */
@Composable
fun ActionLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val ink = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = ink,
        modifier = modifier
            .clip(ChipShape)
            .tappableText(enabled = enabled, onClick = onClick)
            // An outline round it, so a word you can press does not read as a word you cannot.
            // Brown text on a card said "link" to whoever wrote it and "label" to everybody
            // else — the Layout tab has "Mix", "Edit" and "Remove" sitting under a coat's rate
            // and its colour, all four in the same ink. The pill is the one the app already
            // uses for its badges and its ghost buttons, so it belongs here without inventing
            // a shape; it takes the link's own colour, which puts a red ring round Remove.
            .border(1.dp, ink.copy(alpha = 0.55f), ChipShape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    )
}
