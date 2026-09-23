package com.conwic.mixmaster.ui.solutions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.FieldWeightNarrow
import com.conwic.mixmaster.ui.components.FieldWeightWide
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.SuggestField
import com.conwic.mixmaster.ui.components.tappableText

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
    if (!state.isLoaded) return

    val productLabels = state.products.map { product ->
        if (product.brand.isBlank()) product.name else "${product.brand} — ${product.name}"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MixMasterTopBar(
                title = stringResource(if (state.solutionId == 0L) R.string.solution_add else R.string.solution_edit),
                onBack = { navController.popBackStack() },
                actions = {
                    if (state.solutionId != 0L) {
                        IconButton(onClick = { viewModel.archive { navController.popBackStack() } }) {
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

        item { SectionLabel(text = stringResource(R.string.solution_whats_in_it)) }

        item {
            CardFlat {
                Text(
                    text = stringResource(R.string.solution_parts_explain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                state.lines.forEachIndexed { index, line ->
                    Column(modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = stringResource(R.string.product_part_n, index + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.lines.size > 1) {
                                Text(
                                    text = stringResource(R.string.action_remove),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.tappableText { viewModel.removeLine(index) },
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
                                label = stringResource(R.string.product_parts),
                                keyboardType = KeyboardType.Decimal,
                                modifier = Modifier.weight(FieldWeightNarrow),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.product_add_part),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 14.dp).tappableText { viewModel.addLine() },
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
            PrimaryButton(
                text = stringResource(R.string.solution_save),
                onClick = { viewModel.save { navController.popBackStack() } },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun dosingLabel(mode: DosingMode): Int = when (mode) {
    DosingMode.COATS -> R.string.dosing_per_coat
    DosingMode.POUR -> R.string.dosing_per_pour
    DosingMode.MM -> R.string.dosing_per_mm
}
