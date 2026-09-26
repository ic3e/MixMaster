package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.ui.theme.Charcoal
import com.conwic.mixmaster.ui.theme.ChipShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow

/** The rounded, pill-segmented tab bar used throughout the design (e.g. Project Detail's 5 tabs). */
@Composable
fun SegmentedTabs(titles: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, ChipShape)
            .padding(4.dp),
        content = {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .clip(ChipShape)
                        .background(if (selected) Charcoal else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = byHeight(tight = 6.dp, roomy = 9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    // One line: five of these share a phone's width, and a long word ("Yleiskatsaus")
                    // broke in the middle of itself rather than wrap anywhere sensible.
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val gap = 2.dp.roundToPx()
        val gaps = gap * (measurables.size - 1).coerceAtLeast(0)
        val words = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else words.sum() + gaps
        val widths = shareOut(words, (width - gaps).coerceAtLeast(0))
        val height = measurables.zip(widths).maxOfOrNull { (tab, w) -> tab.maxIntrinsicHeight(w) } ?: 0
        val tabs = measurables.zip(widths).map { (tab, w) -> tab.measure(Constraints.fixed(w, height)) }
        layout(width, height) {
            var x = 0
            tabs.forEach { tab ->
                tab.placeRelative(x, 0)
                x += tab.width + gap
            }
        }
    }
}

/**
 * Each tab gets its own word plus an equal share of the room left over, so the space around every
 * word is the same. Shared out by the number of letters instead, "Materials" and "Calendar" sat in
 * wide gaps while "Layout" and "Notes" were pressed together — letters are not all one width.
 * If the words do not fit at all, each gives up the same fraction of itself.
 */
private fun shareOut(words: List<Int>, room: Int): List<Int> {
    if (words.isEmpty()) return emptyList()
    val sum = words.sum()
    if (sum <= room) {
        val spare = room - sum
        val each = spare / words.size
        val odd = spare - each * words.size
        return words.mapIndexed { i, w -> w + each + if (i < odd) 1 else 0 }
    }
    var used = 0
    return words.mapIndexed { i, w ->
        if (i == words.lastIndex) {
            (room - used).coerceAtLeast(0)
        } else {
            (w.toLong() * room / sum).toInt().also { used += it }
        }
    }
}
