package com.conwic.mixmaster.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R

/**
 * The step between a tap and something that cannot be undone.
 *
 * Everything destructive in the app goes through this: a delete sitting under a thumb on a
 * scrolling form is tapped by accident sooner or later, and there is nothing to put the work
 * back afterwards. The confirming action is the one that says what it does — "Delete", not
 * "OK" — so the answer can be read without reading the question again.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}
