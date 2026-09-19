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
                title = if (productId == null) "Add product" else "Edit product",
                onBack = { navController.popBackStack() },
            )
        }

        item { SectionLabel(text = "What it is") }
        item {
            CardFlat {
                SuggestField(
                    value = state.brand,
                    onValueChange = viewModel::setBrand,
                    label = "Brand",
                    suggestions = suggestions.brands,
                    modifier = Modifier.fillMaxWidth(),
                )
                FormTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = "Product name",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                SuggestField(
                    value = state.category,
                    onValueChange = viewModel::setCategory,
                    label = "Type",
                    suggestions = suggestions.categories,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        item { SectionLabel(text = "How much it covers") }
        item {
            CardFlat {
                DropdownField(
                    label = "Measured per",
                    selected = state.dosingMode.pickerLabel,
                    options = DosingMode.entries.map { it.pickerLabel },
                    onSelect = { chosen ->
                        DosingMode.entries.firstOrNull { it.pickerLabel == chosen }
                            ?.let(viewModel::setDosingMode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = state.dosingMode.explanation +
                        " If the datasheet gives one figure rather than a range, put it in both boxes.",
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
                        label = "Min",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    FormTextField(
                        value = state.maxDoseText,
                        onValueChange = viewModel::setMaxDose,
                        label = "Max",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
                SuggestField(
                    value = state.doseUnitLabel,
                    onValueChange = viewModel::setDoseUnitLabel,
                    label = "Per what",
                    suggestions = suggestions.doseUnitLabels,
                    hint = "e.g. per coat, per mm",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        item { SectionLabel(text = "Notes") }
        item {
            CardFlat {
                FormTextField(
                    value = state.rangeNote,
                    onValueChange = viewModel::setRangeNote,
                    label = "Shown on the product card",
                    modifier = Modifier.fillMaxWidth(),
                )
                FormTextField(
                    value = state.sourceNote,
                    onValueChange = viewModel::setSourceNote,
                    label = "Where these figures came from",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                FormTextField(
                    value = state.datasheetUrl,
                    onValueChange = viewModel::setDatasheetUrl,
                    label = "Datasheet link (optional)",
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }

        item { SectionLabel(text = "Added to another mix") }
        item {
            CardFlat {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = "A colour or additive", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Goes into another product's mix rather than being mixed on its own.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.isAddOn, onCheckedChange = viewModel::setIsAddOn)
                }
                if (state.isAddOn) {
                    Text(
                        text = "How much goes in, and per how much of the part it's measured " +
                            "against — colour is normally dosed off the liquid, not the whole batch. " +
                            "Off the datasheet: 28 g per 1 kg, or 1 L per 25 kg.",
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
                            label = "Amount",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(FieldWeightWide),
                        )
                        DropdownField(
                            label = "Unit",
                            selected = state.addOnUnitChoice,
                            options = listOf("g", "kg", "ml", "L"),
                            onSelect = viewModel::setAddOnUnitChoice,
                            modifier = Modifier.weight(FieldWeightNarrow),
                        )
                    }
                    FormTextField(
                        value = state.addOnPerKgText,
                        onValueChange = viewModel::setAddOnPerKg,
                        label = "Per this many kg",
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

        item { SectionLabel(text = "What goes in the mix") }
        item {
            // Reads back the ratio as it's typed, rather than explaining ratios in the abstract.
            val named = state.components.filter {
                it.label.isNotBlank() && (it.ratioText.toNumberOrNull() ?: 0.0) > 0.0
            }
            CardFlat {
                if (named.isEmpty()) {
                    Text(
                        text = "Add each part below and how many parts of it go in. " +
                            "100 powder to 21 water means exactly that, in whatever you weigh in.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Mixed at",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = named.joinToString("  ·  ") { "${it.ratioText.trim()} ${it.label.trim()}" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "Density and pack size are optional, but they're what let the " +
                            "calculator count bags and check a batch fits the mixer.",
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
                text = "+ Add another part",
                onClick = { viewModel.addComponentRow() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            if (state.saveBlockers.isNotEmpty()) {
                CardFlat(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        text = if (state.saveBlockers.size == 1) {
                            "One thing left before this saves"
                        } else {
                            "${state.saveBlockers.size} things left before this saves"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.saveBlockers.forEach { blocker ->
                        Text(
                            text = "· $blocker",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            PrimaryButton(
                text = "Save product",
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
    }.joinToString(" · ").ifBlank { "No density or pack size yet" }

    CardFlat(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Part $number",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove part $number")
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
                label = "What it is",
                suggestions = labelSuggestions,
                modifier = Modifier.weight(FieldWeightWide),
            )
            FormTextField(
                value = row.ratioText,
                onValueChange = onRatio,
                label = "Parts",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(FieldWeightNarrow),
            )
        }

        DropdownField(
            label = "Measured by",
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
                Text(text = "Density & packaging", style = MaterialTheme.typography.titleMedium)
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
                    label = "Density (kg/L)",
                    keyboardType = KeyboardType.Decimal,
                    problem = row.densityProblem,
                    hint = row.densityWarning ?: "What one litre weighs",
                    modifier = Modifier.weight(FieldWeightWide),
                )
                FormTextField(
                    value = row.potLife,
                    onValueChange = onPotLife,
                    label = "Pot life",
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
                    label = "Pack size",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(FieldWeightWide),
                )
                DropdownField(
                    label = "Unit",
                    selected = row.packUnit,
                    options = listOf("kg", "L"),
                    onSelect = onPackUnit,
                    modifier = Modifier.weight(FieldWeightNarrow),
                )
            }
            DropdownField(
                label = "Container",
                selected = row.packType,
                options = listOf("bag", "bucket", "canister", "bottle", "drum", "tub"),
                onSelect = onPackType,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            FormTextField(
                value = row.notes,
                onValueChange = onNotes,
                label = "Notes",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/** Reads the add-on dose back in the form the datasheet states it. */
private fun addOnDoseSummary(state: ProductFormState): String {
    val amount = state.addOnAmountText.toNumberOrNull()
    val per = state.addOnPerKgText.toNumberOrNull()
    if (amount == null || amount <= 0.0 || per == null || per <= 0.0) {
        return "Set an amount to use this as an add-on."
    }
    val perLabel = if (per == 1.0) "1 kg" else "${formatDecimal(per, 2)} kg"
    return "${formatDecimal(amount, 2)} ${state.addOnUnitChoice} per $perLabel of whatever it goes into"
}

/**
 * What each dosing mode is called on screen. The picker used to show the enum constant, so the
 * choice read as "MM" — which says nothing to whoever is holding the datasheet.
 */
private val DosingMode.pickerLabel: String
    get() = when (this) {
        DosingMode.COATS -> "Per coat"
        DosingMode.POUR -> "Per pour"
        DosingMode.MM -> "Per mm of thickness"
    }

/** What the figures below the picker actually mean in that mode. */
private val DosingMode.explanation: String
    get() = when (this) {
        DosingMode.COATS ->
            "Grams per m² for each coat — two coats uses twice these figures."
        DosingMode.POUR ->
            "Grams per m² for the whole pour, laid in one go."
        DosingMode.MM ->
            "Grams per m² for every millimetre of thickness — 10 mm uses ten times these figures."
    }
