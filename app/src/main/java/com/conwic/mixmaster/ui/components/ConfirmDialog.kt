package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R

/**
 * The step between a tap and something that cannot be undone.
 *
 * Everything destructive in the app goes through this: a delete sitting under a thumb on a
 * scrolling form is tapped by accident sooner or later, and there is nothing to put the work
 * back afterwards. The confirming action is the one that says what it does — "Delete", not
 * "OK" — so the answer can be read without reading the question again.
 *
 * For the few things that take everything with them, [typedWord] asks for more than a tap: the
 * confirming button stays dead until that word has been typed. Two taps in a row happen by
 * accident in a pocket; a word does not.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    typedWord: String? = null,
) {
    var typed by remember { mutableStateOf("") }
    // Capitals or not, and a stray space from the keyboard's suggestion bar, are not the point.
    val armed = typedWord == null || typed.trim().equals(typedWord, ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            if (typedWord == null) {
                Text(text = message)
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Text(text = message)
                    FormTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = stringResource(R.string.confirm_type_word, typedWord),
                        capitalization = KeyboardCapitalization.Characters,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = armed) {
                Text(
                    text = confirmText,
                    color = if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}
