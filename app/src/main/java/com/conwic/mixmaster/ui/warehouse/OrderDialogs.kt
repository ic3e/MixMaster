@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.domain.toNumberOr
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.PickerField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Writing down an order that has been placed.
 *
 * Shared by the warehouse and the home screen, because the two moments you decide to order
 * something are standing in front of the rack and seeing the morning's shortage.
 */
@Composable
fun OrderDialog(
    productName: String,
    packType: String,
    packUnit: String,
    isKnownPack: Boolean,
    suggestedPacks: Int,
    suggestedAmount: Double,
    onDismiss: () -> Unit,
    onOrder: (packs: Int, amount: Double, expectedOn: LocalDate, note: String) -> Unit,
) {
    var packs by remember { mutableStateOf(if (suggestedPacks > 0) suggestedPacks.toString() else "") }
    var amount by remember {
        mutableStateOf(if (!isKnownPack && suggestedAmount > 0.0) formatDecimal(suggestedAmount, 2) else "")
    }
    // A week out: an order placed today is not on the rack this afternoon, and a date in the
    // past would have the app asking whether it arrived before anyone has left the yard.
    var due by remember { mutableStateOf(LocalDate.now().plusWeeks(1)) }
    var note by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = productName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isKnownPack) {
                    FormTextField(
                        value = packs,
                        onValueChange = { packs = it },
                        label = stringResource(R.string.wh_order_packs_field, packType),
                        keyboardType = KeyboardType.Number,
                    )
                } else {
                    FormTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = stringResource(R.string.wh_order_amount_field, packUnit),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                PickerField(
                    label = stringResource(R.string.wh_order_due),
                    value = formatDueDate(due),
                    onClick = { pickerOpen = true },
                )
                FormTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(R.string.wh_order_note),
                )
            }
        },
        confirmButton = {
            TextButton(
                // Nothing to record until there is a figure: an order of zero would only sit
                // on the shelf's "on the way" line saying nothing.
                enabled = packs.toNumberOr(0.0) > 0.0 || amount.toNumberOr(0.0) > 0.0,
                onClick = {
                    onOrder(
                        packs.toNumberOr(0.0).toInt(),
                        amount.toNumberOr(0.0),
                        due,
                        note,
                    )
                },
            ) { Text(text = stringResource(R.string.wh_order_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )

    if (pickerOpen) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            due = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        pickerOpen = false
                    },
                ) { Text(text = stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text(text = stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * The question the app asks on the day something was due.
 *
 * One answer puts it on the shelf, because the two halves — "it came" and "the count went up" —
 * getting separated is how a shed ends up counted twice.
 */
@Composable
fun ArrivalDialog(
    productName: String,
    line: String,
    onNotYet: () -> Unit,
    onArrived: () -> Unit,
    onCancelOrder: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onNotYet,
        title = { Text(text = stringResource(R.string.wh_arrived_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "$productName · $line", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.wh_arrived_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                onCancelOrder?.let { cancel ->
                    TextButton(onClick = cancel) {
                        Text(
                            text = stringResource(R.string.wh_cancel_order),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onArrived) { Text(text = stringResource(R.string.wh_arrived_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onNotYet) { Text(text = stringResource(R.string.wh_arrived_not_yet)) }
        },
    )
}
