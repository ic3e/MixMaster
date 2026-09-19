package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.conwic.mixmaster.ui.theme.FieldShape

/**
 * A text field for forms whose state is held in a ViewModel.
 *
 * The caret is owned here instead of being re-derived from [value] on every recomposition.
 * Form state round-trips through a StateFlow, so the typed text comes back a frame later —
 * long enough for a value-derived field to reset the caret to the start and turn "12" into
 * "21". [value] seeds the field; edits flow outwards only.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    /** Shown under the field. Red when [problem] is set, otherwise a plain hint. */
    problem: String? = null,
    hint: String? = null,
    /** When set, a ▾ appears that calls this — used to offer values already in the catalogue. */
    trailing: (() -> Unit)? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val supporting = problem ?: hint
    OutlinedTextField(
        value = field,
        onValueChange = {
            field = it
            onValueChange(it.text)
        },
        label = { Text(label) },
        singleLine = singleLine,
        isError = problem != null,
        trailingIcon = if (trailing != null) {
            {
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 12.dp).tappableText(onClick = trailing),
                )
            }
        } else {
            null
        },
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = FieldShape,
        // Filled, not just outlined. On the cream page a hairline outline made every field blend
        // into the background and into the text around it; a solid fill separates them.
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier,
    )
}
