package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.FieldWeightNarrow
import com.conwic.mixmaster.ui.components.FieldWeightWide
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.GhostButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.conwic.mixmaster.ui.components.SuggestField
import com.conwic.mixmaster.ui.theme.CardShape
import androidx.compose.material3.Switch
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.toNumberOrNull

@Composable
fun AddEditProductScreen(navController: NavHostController, productId: Long?) {
    val container = LocalAppContainer.current
    val viewModel: AddEditProductViewModel = viewModel(
        factory = viewModelFactory { initializer { AddEditProductViewModel(container.productRepository, productId) } },
    )
    val state by viewModel.formState.collectAsState()
    if (!state.isLoaded) return

    val suggestions by viewModel.suggestions.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MixMasterTopBar(
                title = stringResource(if (productId == null) R.string.product_add else R.string.product_edit),
                onBack = { navController.popBackStack() },
            )
        }

        item { SectionLabel(text = stringResource(R.string.product_what_it_is)) }
        item {
            CardFlat {
                SuggestField(
                    value = state.brand,
                    onValueChange = viewModel::setBrand,
                    label = stringResource(R.string.product_brand),
                    suggestions = suggestions.brands,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = stringResource(R.string.product_name),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                SuggestField(
                    value = state.category,
                    onValueChange = viewModel::setCategory,
                    label = stringResource(R.string.product_type),
                    suggestions = suggestions.categories,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        item { SectionLabel(text = stringResource(R.string.product_coverage)) }
        item {
            CardFlat {
                DropdownField(
                    label = stringResource(R.string.product_measured_per),
                    selected = state.dosingMode.pickerLabel,
                    options = DosingMode.entries.map { it.pickerLabel },
                    onSelect = { chosen ->
                        DosingMode.entries.firstOrNull { it.pickerLabel == chosen }
                            ?.let(viewModel::setDosingMode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = state.dosingMode.explanation + " " + stringResource(R.string.product_range_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    FormTextField(
                        value = state.minDoseText,
                        onValueChange = viewModel::setMinDose,
                        label = stringResource(R.string.product_min),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    FormTextField(
                        value = state.maxDoseText,
                        onValueChange = viewModel::setMaxDose,
                        label = stringResource(R.string.product_max),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                SuggestField(
                    value = state.doseUnitLabel,
                    onValueChange = viewModel::setDoseUnitLabel,
                    label = stringResource(R.string.product_per_what),
                    suggestions = suggestions.doseUnitLabels,
                    hint = stringResource(R.string.product_per_what_hint),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        item { SectionLabel(text = stringResource(R.string.product_notes)) }
        item {
            CardFlat {
                FormTextField(
                    value = state.rangeNote,
                    onValueChange = viewModel::setRangeNote,
                    label = stringResource(R.string.product_card_note),
                    modifier = Modifier.fillMaxWidth(),
                )
                FormTextField(
                    value = state.sourceNote,
                    onValueChange = viewModel::setSourceNote,
                    label = stringResource(R.string.product_source_note),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                FormTextField(
                    value = state.datasheetUrl,
                    onValueChange = viewModel::setDatasheetUrl,
                    label = stringResource(R.string.product_datasheet_link),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        item { SectionLabel(text = stringResource(R.string.product_addon_section)) }
        item {
            CardFlat {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = stringResource(R.string.product_addon_toggle), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.product_addon_toggle_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.isAddOn, onCheckedChange = viewModel::setIsAddOn)
                }
                if (state.isAddOn) {
                    Text(
                        text = stringResource(R.string.product_addon_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        FormTextField(
                            value = state.addOnAmountText,
                            onValueChange = viewModel::setAddOnAmount,
                            label = stringResource(R.string.product_amount),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(FieldWeightWide),
                        )
                        DropdownField(
                            label = stringResource(R.string.product_unit),
                            selected = state.addOnUnitChoice,
                            options = listOf("g", "kg", "ml", "L"),
                            onSelect = viewModel::setAddOnUnitChoice,
                            modifier = Modifier.weight(FieldWeightNarrow),
                        )
                    }
                    FormTextField(
                        value = state.addOnPerKgText,
                        onValueChange = viewModel::setAddOnPerKg,
                        label = stringResource(R.string.product_per_this_many_kg),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                    Text(
                        text = addOnDoseSummary(state),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.product_mix_section)) }
        item {
            // Reads back the ratio as it's typed, rather than explaining ratios in the abstract.
            val named = state.components.filter {
                it.label.isNotBlank() && (it.ratioText.toNumberOrNull() ?: 0.0) > 0.0
            }
            CardFlat {
                if (named.isEmpty()) {
                    Text(
                        text = stringResource(R.string.product_mix_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.product_mixed_at),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = named.joinToString("  ·  ") { "${it.ratioText.trim()} ${it.label.trim()}" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = stringResource(R.string.product_mix_optional_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        items(state.components.size, key = { state.components[it].uid }) { index ->
            val row = state.components[index]
            ComponentCard(
                row = row,
                number = index + 1,
                labelSuggestions = suggestions.componentLabels,
                onLabel = { viewModel.setComponentLabel(index, it) },
                onRatio = { viewModel.setComponentRatio(index, it) },
                onBasis = { viewModel.setComponentBasis(index, it) },
                onDensity = { viewModel.setComponentDensityKgPerL(index, it) },
                onPotLife = { viewModel.setComponentPotLife(index, it) },
                onPackSize = { viewModel.setComponentPackSize(index, it) },
                onPackUnit = { viewModel.setComponentPackUnit(index, it) },
                onPackType = { viewModel.setComponentPackType(index, it) },
                onNotes = { viewModel.setComponentNotes(index, it) },
                onRemove = { viewModel.removeComponentRow(index) },
            )
        }

        item {
            GhostButton(
                text = stringResource(R.string.product_add_part),
                onClick = { viewModel.addComponentRow() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            if (state.saveBlockers.isNotEmpty()) {
                CardFlat(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.save_blockers_heading,
                            state.saveBlockers.size,
                            state.saveBlockers.size,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.saveBlockers.forEach { blocker ->
                        val message = when (blocker) {
                            is SaveBlocker.Simple -> stringResource(blocker.messageRes)
                            is SaveBlocker.PartDensity -> stringResource(
                                R.string.blocker_part_density,
                                blocker.partNumber,
                                stringResource(blocker.problemRes),
                            )
                        }
                        Text(
                            text = "· $message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            PrimaryButton(
                text = stringResource(R.string.product_save),
                onClick = { viewModel.save { navController.popBackStack() } },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/**
 * One part of the mix.
 *
 * The ratio is the part that matters and is always visible; density, pack size and notes are
 * folded away behind a summary. Nine text fields per part, three parts deep, was both slow to
 * draw and impossible to read.
 */
@Composable
private fun ComponentCard(
    row: ComponentFormRow,
    number: Int,
    labelSuggestions: List<String>,
    onLabel: (String) -> Unit,
    onRatio: (String) -> Unit,
    onBasis: (String) -> Unit,
    onDensity: (String) -> Unit,
    onPotLife: (String) -> Unit,
    onPackSize: (String) -> Unit,
    onPackUnit: (String) -> Unit,
    onPackType: (String) -> Unit,
    onNotes: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by rememberSaveable(row.uid) { mutableStateOf(false) }

    val summary = buildList {
        if (row.densityKgPerLText.isNotBlank()) add("${row.densityKgPerLText} kg/L")
        if (row.packSizeText.isNotBlank()) add("${row.packSizeText} ${row.packUnit} ${row.packType}")
        if (row.potLife.isNotBlank()) add(row.potLife)
    }.joinToString(" · ").ifBlank { stringResource(R.string.product_no_density_yet) }

    CardFlat(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.product_part_n, number),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.product_remove_part_n, number))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SuggestField(
                value = row.label,
                onValueChange = onLabel,
                label = stringResource(R.string.product_what_it_is),
                suggestions = labelSuggestions,
                modifier = Modifier.weight(FieldWeightWide),
            )
            FormTextField(
                value = row.ratioText,
                onValueChange = onRatio,
                label = stringResource(R.string.product_parts),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(FieldWeightNarrow),
            )
        }

        DropdownField(
            label = stringResource(R.string.product_measured_by),
            selected = row.basis,
            options = listOf("Weight", "Volume"),
            onSelect = onBasis,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(text = stringResource(R.string.product_density_packaging), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (expanded) "▴" else "▾",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FormTextField(
                    value = row.densityKgPerLText,
                    onValueChange = onDensity,
                    label = stringResource(R.string.product_density),
                    keyboardType = KeyboardType.Decimal,
                    problem = row.densityProblem?.let { stringResource(it) },
                    hint = stringResource(row.densityWarning ?: R.string.product_density_hint),
                    modifier = Modifier.weight(FieldWeightWide),
                )
                FormTextField(
                    value = row.potLife,
                    onValueChange = onPotLife,
                    label = stringResource(R.string.product_pot_life),
                    modifier = Modifier.weight(FieldWeightNarrow),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                FormTextField(
                    value = row.packSizeText,
                    onValueChange = onPackSize,
                    label = stringResource(R.string.product_pack_size),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(FieldWeightWide),
                )
                DropdownField(
                    label = stringResource(R.string.product_unit),
                    selected = row.packUnit,
                    options = listOf("kg", "L"),
                    onSelect = onPackUnit,
                    modifier = Modifier.weight(FieldWeightNarrow),
                )
            }
            DropdownField(
                label = stringResource(R.string.product_container),
                selected = row.packType,
                options = listOf("bag", "bucket", "canister", "bottle", "drum", "tub"),
                onSelect = onPackType,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            FormTextField(
                value = row.notes,
                onValueChange = onNotes,
                label = stringResource(R.string.product_notes),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/** Reads the add-on dose back in the form the datasheet states it. */
@Composable
private fun addOnDoseSummary(state: ProductFormState): String {
    val amount = state.addOnAmountText.toNumberOrNull()
    val per = state.addOnPerKgText.toNumberOrNull()
    if (amount == null || amount <= 0.0 || per == null || per <= 0.0) {
        return stringResource(R.string.product_addon_summary_unset)
    }
    val perLabel = stringResource(R.string.product_kg_amount, if (per == 1.0) "1" else formatDecimal(per, 2))
    return stringResource(
        R.string.product_addon_summary,
        formatDecimal(amount, 2),
        state.addOnUnitChoice,
        perLabel,
    )
}

/**
 * What each dosing mode is called on screen. The picker used to show the enum constant, so the
 * choice read as "MM" — which says nothing to whoever is holding the datasheet.
 */
private val DosingMode.pickerLabelRes: Int
    get() = when (this) {
        DosingMode.COATS -> R.string.dosing_per_coat
        DosingMode.POUR -> R.string.dosing_per_pour
        DosingMode.MM -> R.string.dosing_per_mm
    }

private val DosingMode.pickerLabel: String
    @Composable get() = stringResource(pickerLabelRes)

/** What the figures below the picker actually mean in that mode. */
private val DosingMode.explanation: String
    @Composable get() = stringResource(
        when (this) {
            DosingMode.COATS -> R.string.dosing_explain_coats
            DosingMode.POUR -> R.string.dosing_explain_pour
            DosingMode.MM -> R.string.dosing_explain_mm
        },
    )
