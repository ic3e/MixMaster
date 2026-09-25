package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
    /**
     * A coloured band down the left edge, for a card whose state has to be seen from a scroll
     * rather than read. Null for the ordinary card, which is most of them — a page where every
     * card is marked is a page where none of them are.
     */
    edge: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Clipped first, so the band takes the card's own rounded corners at top and bottom
            // instead of squaring them off.
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .then(
                if (edge == null) {
                    Modifier
                } else {
                    Modifier.drawBehind {
                        drawRect(color = edge, size = Size(5.dp.toPx(), size.height))
                    }
                },
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(contentPadding)
            .then(if (edge == null) Modifier else Modifier.padding(start = 5.dp)),
        content = content,
    )
}

/**
 * The same card, set back into the page: a soft fill and no outline.
 *
 * For what has already happened. On the materials page every card was the same white, so a
 * record of what went in the drum last Tuesday and a line saying to order nineteen canisters
 * read as the same kind of thing, and the only way to tell them apart was to read them.
 */
@Composable
fun CardSoft(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, CardShape)
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

/**
 * A headline figure with its label.
 *
 * Given an [onClick] it becomes the way to go and look at what the figure counts — clipped
 * first so the press stays inside the card's corners rather than flashing a square behind it.
 */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    CardFlat(
        modifier = if (onClick == null) modifier else modifier.clip(CardShape).clickable(onClick = onClick),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.headlineMedium, color = valueColor, fontWeight = FontWeight.ExtraBold)
    }
}
