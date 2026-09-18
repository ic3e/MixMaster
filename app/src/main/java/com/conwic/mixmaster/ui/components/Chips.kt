package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.ui.theme.ChipShape

data class ChipOption(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
fun ChipRow(options: List<ChipOption>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(options) { option ->
            val bg = if (option.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (option.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = option.label,
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .background(bg, ChipShape)
                    .clickable { option.onClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Status/role pills, e.g. "Active", "Employer", "Worker". */
@Composable
fun StatusPill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = foreground,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(background, ChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
