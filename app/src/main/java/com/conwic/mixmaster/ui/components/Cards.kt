package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.Charcoal
import com.conwic.mixmaster.ui.theme.TextOnDark

/** The flat, warm-surface card used throughout the design for grouped rows of info. */
@Composable
fun CardFlat(
    modifier: Modifier = Modifier,
    /** Drop this when the rows inside do their own insetting, so everything still lines up. */
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Text colour for anything inside [CardAccent].
 *
 * The card's background is always charcoal, so its content colour has to be fixed too.
 * colorScheme.onPrimary flips to a dark tone in the dark theme, which turned every one of these
 * cards into dark-on-dark.
 */
val OnAccentCard: Color = TextOnDark

/** The solid dark "hero" card (quick actions, rollup stats) — matches the design's charcoal card-accent. */
@Composable
fun CardAccent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Charcoal, CardShape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StatCard(modifier: Modifier = Modifier, label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    CardFlat(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.headlineMedium, color = valueColor, fontWeight = FontWeight.ExtraBold)
    }
}
