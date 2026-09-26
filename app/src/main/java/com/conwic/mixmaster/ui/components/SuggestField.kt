package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.nameMatches

/**
 * A text field that offers what has been entered before.
 *
 * Type anything — it's a free text field. But the names already in the catalogue that fit what
 * is being typed come up under it on their own, which is what stops "Ardex", "ARDEX" and
 * "ardex " becoming three brands that don't filter together. The ▾ lists them all.
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
    // Opened by the ▾, for looking through the whole list.
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // The text the list was last closed or picked on, so it doesn't spring back for the same one.
    var settledOn by remember { mutableStateOf<String?>(null) }
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val pool = remember(suggestions) { suggestions.filter { it.isNotBlank() }.distinct() }
    val typing = value.isNotBlank()
    val matches = remember(value, pool) { if (typing) nameMatches(value, pool) else emptyList() }
    // While typing it opens by itself with what fits. Only then, though: an empty field with the
    // list hanging off it would cover the next field on the way past.
    val offering = !expanded && focused && typing && matches.isNotEmpty() && value != settledOn
    // The ▾ shows everything, whatever is typed — a new name has no matches to narrow it to.
    val shown = if (offering) matches else pool
    val open = (expanded || offering) && shown.isNotEmpty()

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
            trailing = if (pool.isEmpty()) {
                null
            } else {
                { expanded = true }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = {
                expanded = false
                settledOn = value
            },
            // Not focusable, or it takes the keyboard off the field the moment it appears and
            // the next letter goes nowhere.
            properties = PopupProperties(focusable = false),
            // At least wide enough for a name: the Type field is the narrow half of a row.
            modifier = Modifier.width(maxOf(fieldWidth, 220.dp)),
        ) {
            if (offering) {
                Text(
                    text = stringResource(R.string.suggest_already_in),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            shown.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        settledOn = option
                        expanded = false
                    },
                )
            }
        }
    }
}
