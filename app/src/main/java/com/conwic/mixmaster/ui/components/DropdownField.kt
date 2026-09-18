package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
    // BoxWithConstraints so the menu can be told how wide the field is — a dropdown that opens
    // a third of the width of the thing it belongs to reads as a different control entirely.
    BoxWithConstraints(modifier = modifier) {
        // Unbounded width would mean an infinite menu; nothing in the app does that, but a
        // crash would be a poor way to find out.
        val fieldWidth = if (maxWidth == Dp.Infinity) 260.dp else maxWidth
        PickerField(label = label, value = selected, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(fieldWidth),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option, modifier = Modifier.fillMaxWidth()) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
