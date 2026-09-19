package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A simple "pick one from a list" field: a [PickerField] that opens a dropdown.
 * Deliberately avoids Material3's ExposedDropdownMenuBox/ExposedDropdownMenu — that API surface
 * has shifted across versions and this plain Box + DropdownMenu combination is long-stable and
 * behaves predictably everywhere.
 */
@Composable
fun DropdownField(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // The menu is as wide as the field. Measured with onSizeChanged rather than
    // BoxWithConstraints: that subcomposes, and with nine of these on the product form it was a
    // noticeable part of why the screen dragged.
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(
        modifier = modifier.onSizeChanged { size ->
            with(density) { fieldWidth = size.width.toDp() }
        },
    ) {
        PickerField(label = label, value = selected, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = if (fieldWidth > 0.dp) Modifier.width(fieldWidth) else Modifier,
        ) {
            options.forEach { option ->
                // The open menu marks the one already in force. Without it the list gives no
                // clue what the field currently holds, so picking felt like it hadn't landed.
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isSelected) "✓  $option" else option,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
