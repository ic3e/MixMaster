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
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.ui.theme.Charcoal
import com.conwic.mixmaster.ui.theme.ChipShape
import androidx.compose.ui.draw.clip

data class ChipOption(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
fun ChipRow(options: List<ChipOption>, modifier: Modifier = Modifier) {
    val readOnly = LocalReadOnly.current
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(options) { option ->
            val bg = if (option.selected) Charcoal else MaterialTheme.colorScheme.surface
            val fg = if (option.selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = option.label,
                color = fg,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(ChipShape)
                    .background(bg)
                    .then(if (!option.selected) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, ChipShape) else Modifier)
                    .clickable(enabled = !readOnly) { option.onClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
