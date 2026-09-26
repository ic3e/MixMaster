package com.conwic.mixmaster.ui.solutions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.DosingMode
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.FieldWeightNarrow
import com.conwic.mixmaster.ui.components.FieldWeightWide
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.company.rememberAccess
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.SuggestField
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.ui.navigation.Routes

/**
 * A recipe: which products go in, in what ratio, and how much of the result covers a square
 * metre.
 *
 * Everything the calculator needs lives here now. The products it names bring their own packs
 * and densities, so a recipe never repeats what a bag already knows.
 */
@Composable
fun SolutionEditorScreen(navController: NavHostController, solutionId: Long?) {
    val container = LocalAppContainer.current
    val viewModel: SolutionEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SolutionEditorViewModel(container.solutionRepository, container.productRepository, solutionId)
            }
        },
    )
    val state by viewModel.formState.collectAsState()
    // In a company, somebody not allowed to change recipes can still read one here; the buttons
    // that would change it are not offered, since the server would only put the recipe back.
    val access = rememberAccess()
    val canChange = access.catalogue
    if (!state.isLoaded) return

    var confirmArchive by remember { mutableStateOf(false) }
    // The part number waiting on an answer, so a mis-tap on a scrolling form does not
    // quietly take a component out of the mix.
    var confirmRemoveLine by remember { mutableStateOf<Int?>(null) }

    val productLabels = state.products.map { product ->
        if (product.brand.isBlank()) product.name else "${product.brand} — ${product.name}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MixMasterTopBar(
                title = stringResource(if (state.solutionId == 0L) R.string.solution_add else R.string.solution_edit),
                onBack = { navController.popBackStack() },
                actions = {
                    if (state.solutionId != 0L && canChange) {
                        IconButton(onClick = { confirmArchive = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        }

        item { SectionLabel(text = stringResource(R.string.product_what_it_is)) }

        item {
            CardFlat {
                FormTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = stringResource(R.string.solution_name),
                    problem = state.nameProblem?.let { stringResource(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SuggestField(
                        value = state.brand,
                        onValueChange = viewModel::setBrand,
                        label = stringResource(R.string.product_brand),
                        suggestions = state.brands,
                        modifier = Modifier.weight(FieldWeightWide),
                    )
                    SuggestField(
                        value = state.category,
                        onValueChange = viewModel::setCategory,
                        label = stringResource(R.string.product_type),
                        suggestions = state.categories,
                        modifier = Modifier.weight(FieldWeightNarrow),
                    )
                }
            }
        }

        // A datasheet that gives one product two recipes — architop lays its first coat at
        // 2.0 kg/m² of hardener and its second at 1.5 — needs a coat for each. Only shown once
        // the mix exists, because a coat has to belong to something.
        if (state.solutionId != 0L) {
            item { SectionLabel(text = stringResource(R.string.solution_coats)) }

            item {
                CardFlat {
                    Text(
                        text = stringResource(R.string.solution_coats_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    if (state.coats.size > 1) {
                        state.coats.forEach { coat ->
                            val isThisOne = coat.id == state.solutionId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .tappableText {
                                        // In place of this one, not on top of it: back goes to where the
                                        // recipe was opened from, not through every coat looked at.
                                        if (!isThisOne) navController.navigate(Routes.solutionEdit(coat.id)) {
                                            popUpTo(Routes.SOLUTION_EDIT) { inclusive = true }
                                        }
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    modifier = Modifier.weight(1f, fill = false),
                                    text = coat.coatName.ifBlank { coat.name },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isThisOne) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                                Text(
                                    text = coat.ratioLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (state.coats.size > 1 || state.coatName.isNotBlank()) {
                        FormTextField(
                            value = state.coatName,
                            onValueChange = viewModel::setCoatName,
                            label = stringResource(R.string.solution_coat_name),
                            hint = stringResource(R.string.solution_coat_name_hint),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        )
                    }
                    // Named here, where the language is known — the view model only stores them.
                    val firstName = stringResource(R.string.solution_coat_number, 1)
                    val nextName = stringResource(R.string.solution_coat_number, state.coats.size.coerceAtLeast(1) + 1)
                    if (canChange) {
                        ActionLink(
                            text = stringResource(R.string.solution_add_coat),
                            onClick = {
                                viewModel.addCoat(firstName, nextName) { id ->
                                    navController.navigate(Routes.solutionEdit(id)) {
                                        popUpTo(Routes.SOLUTION_EDIT) { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.solution_whats_in_it)) }

        item {
            CardFlat {
                Text(
                    text = stringResource(R.string.solution_parts_explain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                // Some datasheets never give a ratio at all — architop lists each part's own
                // rate per square metre. Typed that way, the ratio and the coverage both fall
                // out of the figures instead of being worked out by hand.
                ChipRow(
                    options = listOf(EntryMode.RATIO, EntryMode.PER_AREA).map { mode ->
                        ChipOption(
                            label = stringResource(
                                if (mode == EntryMode.RATIO) R.string.solution_by_ratio else R.string.solution_by_area,
                            ),
                            selected = mode == state.entry,
                            onClick = { viewModel.setEntry(mode) },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                if (state.entry == EntryMode.PER_AREA) {
                    Text(
                        text = stringResource(R.string.solution_by_area_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                state.lines.forEachIndexed { index, line ->
                    Column(modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                modifier = Modifier.weight(1f, fill = false),
                                text = stringResource(R.string.product_part_n, index + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.lines.size > 1) {
                                ActionLink(
                                    text = stringResource(R.string.action_remove),
                                    onClick = { confirmRemoveLine = index },
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        DropdownField(
                            label = stringResource(R.string.solution_line_product),
                            selected = state.products.firstOrNull { it.id == line.productId }
                                ?.let { product ->
                                    if (product.brand.isBlank()) product.name else "${product.brand} — ${product.name}"
                                }
                                ?: stringResource(R.string.solution_line_choose),
                            options = productLabels,
                            onSelect = { label ->
                                val picked = productLabels.indexOf(label)
                                state.products.getOrNull(picked)?.let { viewModel.setLineProduct(index, it.id) }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FormTextField(
                                value = line.label,
                                onValueChange = { viewModel.setLineLabel(index, it) },
                                label = stringResource(R.string.solution_line_label),
                                hint = stringResource(R.string.solution_line_label_hint),
                                modifier = Modifier.weight(FieldWeightWide),
                            )
                            FormTextField(
                                value = line.partsText,
                                onValueChange = { viewModel.setLineParts(index, it) },
                                label = stringResource(
                                    when {
                                        line.percentOfRest -> R.string.solution_line_percent
                                        state.entry == EntryMode.PER_AREA -> R.string.solution_line_rate
                                        else -> R.string.product_parts
                                    },
                                ),
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(FieldWeightNarrow),
                            )
                        }
                        // "10 parts A + 2 parts B + water at 10% of (A+B)" is how a fair few
                        // datasheets word the water and the thinner, and there was no way to
                        // write it down: the 1.2 parts it comes to had to be worked out by hand,
                        // and worked out again the day somebody corrected A or B.
                        ActionLink(
                            text = stringResource(
                                if (line.percentOfRest) {
                                    R.string.solution_line_as_parts
                                } else {
                                    R.string.solution_line_as_percent
                                },
                            ),
                            onClick = { viewModel.setLinePercentOfRest(index, !line.percentOfRest) },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (line.percentOfRest) {
                            Text(
                                text = stringResource(R.string.solution_line_percent_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                ActionLink(
                    text = stringResource(R.string.product_add_part),
                    onClick = { viewModel.addLine() },
                    modifier = Modifier.padding(top = 10.dp),
                )
                state.linesProblem?.let { problem ->
                    Text(
                        text = stringResource(problem),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.product_coverage)) }

        item {
            CardFlat {
                Text(
                    text = stringResource(R.string.product_range_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (state.entry == EntryMode.PER_AREA) {
                    Text(
                        text = stringResource(
                            R.string.solution_coverage_computed,
                            formatDecimal(state.perAreaTotal, 1),
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                // Typed by area, the coverage is the sum of the rates — asking for it as
                // well would be asking the same question twice, in a way that can disagree.
                if (state.entry == EntryMode.RATIO) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormTextField(
                            value = state.minDoseText,
                            onValueChange = viewModel::setMinDose,
                            label = stringResource(R.string.product_min),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(FieldWeightNarrow),
                        )
                        FormTextField(
                            value = state.maxDoseText,
                            onValueChange = viewModel::setMaxDose,
                            label = stringResource(R.string.product_max),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(FieldWeightNarrow),
                        )
                    }
                }
                // Resolved above the callback — onSelect is never composable.
                val modeLabels = DosingMode.entries.map { stringResource(dosingLabel(it)) }
                DropdownField(
                    label = stringResource(R.string.product_measured_per),
                    selected = stringResource(dosingLabel(state.dosingMode)),
                    options = modeLabels,
                    onSelect = { label ->
                        val picked = modeLabels.indexOf(label)
                        DosingMode.entries.getOrNull(picked)?.let { viewModel.setDosingMode(it) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                FormTextField(
                    value = state.doseUnitLabel,
                    onValueChange = viewModel::setDoseUnitLabel,
                    label = stringResource(R.string.product_per_what),
                    hint = stringResource(R.string.product_per_what_hint),
                    modifier = Modifier.padding(top = 10.dp),
                )
                // The line every datasheet carries and nobody times. Put it here and the mixer
                // can be counted down instead of stopped when the lumps go.
                FormTextField(
                    value = state.mixMinutesText,
                    onValueChange = viewModel::setMixMinutes,
                    label = stringResource(R.string.solution_mix_time),
                    hint = stringResource(R.string.solution_mix_time_hint),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.padding(top = 10.dp),
                )
                // How long it stays workable once it is mixed. Every datasheet gives it, and
                // it is what decides whether a batch should be half the size.
                FormTextField(
                    value = state.potLifeText,
                    onValueChange = viewModel::setPotLife,
                    label = stringResource(R.string.solution_pot_life),
                    hint = stringResource(R.string.solution_pot_life_hint),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.padding(top = 10.dp),
                )
                state.doseProblem?.let { problem ->
                    Text(
                        text = stringResource(problem),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.product_notes)) }

        item {
            CardFlat {
                FormTextField(
                    value = state.datasheetUrl,
                    onValueChange = viewModel::setDatasheetUrl,
                    label = stringResource(R.string.product_datasheet_link),
                )
            }
        }

        item {
            if (canChange) {
                PrimaryButton(
                    text = stringResource(R.string.solution_save),
                    onClick = { viewModel.save { navController.popBackStack() } },
                    enabled = state.isValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CardFlat {
                    Text(
                        text = stringResource(R.string.solution_read_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.solution_delete_confirm),
            message = stringResource(R.string.solution_delete_confirm_body, state.name),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                confirmArchive = false
                viewModel.archive { navController.popBackStack() }
            },
            onDismiss = { confirmArchive = false },
        )
    }

    confirmRemoveLine?.let { index ->
        ConfirmDialog(
            title = stringResource(R.string.part_remove_confirm),
            message = stringResource(R.string.part_remove_confirm_body, index + 1),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                confirmRemoveLine = null
                viewModel.removeLine(index)
            },
            onDismiss = { confirmRemoveLine = null },
        )
    }

}

private fun dosingLabel(mode: DosingMode): Int = when (mode) {
    DosingMode.COATS -> R.string.dosing_per_coat
    DosingMode.POUR -> R.string.dosing_per_pour
    DosingMode.MM -> R.string.dosing_per_mm
}
