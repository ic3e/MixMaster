package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.conwic.mixmaster.ui.theme.DisplayFontFamily

private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)

@Composable
private fun ButtonLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = DisplayFontFamily,
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
