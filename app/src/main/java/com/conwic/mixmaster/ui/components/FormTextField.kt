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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // [value] normally only seeds the field — see above. But a change that came from somewhere
    // other than typing, like a suggestion picked from the dropdown, has to reach the field or
    // it lands in the form and never appears on screen.
    //
    // Keyed on [value] changing rather than on it differing from the text: after a keystroke
    // this recomposes with a [value] that hasn't caught up yet, and syncing on the difference
    // would undo what was just typed.
    var lastValue by remember { mutableStateOf(value) }
    if (value != lastValue) {
        lastValue = value
        if (value != field.text) {
            field = TextFieldValue(value, TextRange(value.length))
        }
    }

    val supporting = problem ?: hint
    Column(modifier = modifier) {
        FieldLabel(text = label, error = problem != null)
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
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
            // Filled, not just outlined. On the cream page a hairline outline made every field
            // blend into the background and into the text around it; a solid fill separates them.
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                errorContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The word above a field, rather than the one Material floats across its top border.
 *
 * The floating one is what made the fields look like they had notes stuck to them. Material
 * hangs the label half in and half out of the field and cuts the border away behind it, so on
 * this app's cream page a small square of page colour sits inside the white fill — a pale patch
 * with hard edges, which is exactly what a sticker looks like. Set above the field, the field
 * stays one unbroken rounded shape and the word plainly belongs to it.
 *
 * Shared by every field in the app so that a form, a sheet and a picker all name their fields
 * the same way and at the same height.
 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        // A shade over the small end of the scale: this is read in a van, in daylight, by
        // somebody who is not looking for it.
        fontSize = 11.5.sp,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        // Off the very edge, so it stands over the field's own text rather than over its corner.
        modifier = modifier.padding(start = 4.dp, bottom = 5.dp),
    )
}

/**
 * The one split every "value + its unit" row in the app uses — a wide field and the short one
 * that qualifies it.
 *
 * A form had three of these stacked in a single card at 2:1, 1:1 and 1.2:1, so the break moved
 * by a few percent on every line. Near-misses read as sloppier than an obviously different
 * layout would, so the split is named once and shared. Rows of two equal fields (Min/Max,
 * Brand/Type) stay 1f/1f — those are genuinely symmetric and belong on the centre line.
 */
const val FieldWeightWide = 1.6f
const val FieldWeightNarrow = 1f
