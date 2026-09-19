package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp

/**
 * A text field that offers what has been entered before.
 *
 * Type anything — it's a free text field. But everything already in the catalogue is one tap
 * away, which is what stops "Ardex", "ARDEX" and "ardex " becoming three brands that don't
 * filter together. Suggestions narrow as you type.
 */
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val matches = remember(value, suggestions) {
        val typed = value.trim()
        val pool = suggestions.filter { it.isNotBlank() }.distinct()
        // An exact match means there's nothing left to suggest.
        if (pool.any { it.equals(typed, ignoreCase = true) } && pool.size == 1) {
            emptyList()
        } else if (typed.isEmpty()) {
            pool
        } else {
            pool.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier.onSizeChanged { size ->
            with(density) { fieldWidth = size.width.toDp() }
        },
    ) {
        FormTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            hint = hint,
            trailing = if (matches.isEmpty()) {
                null
            } else {
                { expanded = true }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded && matches.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = if (fieldWidth > 0.dp) Modifier.width(fieldWidth) else Modifier,
        ) {
            matches.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
