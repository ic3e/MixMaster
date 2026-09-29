package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The heading over a group — TODAY, ON ORDER, LANGUAGE.
 *
 * In the text colour, not a faded grey: the client reads these on site in daylight, and a pale
 * grey heading on the cream page all but disappeared.
 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(bottom = 8.dp),
    )
}
