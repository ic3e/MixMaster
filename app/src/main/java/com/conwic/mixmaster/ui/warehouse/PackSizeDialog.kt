package com.conwic.mixmaster.ui.warehouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.PackOption
import com.conwic.mixmaster.domain.ProductStock
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.toNumberOrNull
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.products.PackTypes
import com.conwic.mixmaster.ui.products.PackUnits

/** "23 kg bag" — the way a pack is said at the rack. */
internal fun packLabel(option: PackOption): String =
    "${formatDecimal(option.size, 2)} ${option.unit} ${option.type}"

/**
 * The pack a product comes in, set where the question comes up — in the middle of a count, or
 * on the warehouse list — rather than by leaving for the product's own form and finding the
 * way back. Suggestions fill the fields; nothing is saved until Save.
 */
@Composable
internal fun PackSizeDialog(
    item: ProductStock,
    suggestions: List<PackOption>,
    onDismiss: () -> Unit,
    onSave: (PackOption) -> Unit,
) {
    var size by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(item.packUnit.takeIf { it in PackUnits } ?: PackUnits.first()) }
    var type by remember { mutableStateOf(item.packType.takeIf { it in PackTypes } ?: PackTypes.first()) }
    val parsed = size.toNumberOrNull()?.takeIf { it > 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = item.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (suggestions.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.pack_suggested),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChipRow(
                        options = suggestions.map { option ->
                            ChipOption(
                                label = packLabel(option),
                                selected = parsed == option.size && unit == option.unit && type == option.type,
                                onClick = {
                                    size = formatDecimal(option.size, 2)
                                    unit = option.unit
                                    type = option.type
                                },
                            )
                        },
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormTextField(
                        value = size,
                        onValueChange = { size = it },
                        label = stringResource(R.string.product_pack_size),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    DropdownField(
                        label = stringResource(R.string.product_unit),
                        selected = unit,
                        options = PackUnits,
                        onSelect = { unit = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                DropdownField(
                    label = stringResource(R.string.product_container),
                    selected = type,
                    options = PackTypes,
                    onSelect = { type = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.pack_rollup_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onSave(PackOption(it, unit, type)) } },
                enabled = parsed != null,
            ) { Text(text = stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}
