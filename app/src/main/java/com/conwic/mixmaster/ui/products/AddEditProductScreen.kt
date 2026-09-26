package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.conwic.mixmaster.data.docs.SheetStore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
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
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.FieldWeightNarrow
import com.conwic.mixmaster.ui.components.FieldWeightWide
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.SuggestField
import com.conwic.mixmaster.ui.components.packName

/**
 * A bought item: what it is and how it is sold.
 *
 * No coverage, no ratio and no parts — a bag of powder has none of those. They belong to the
 * solution the product goes into, which is where they are now entered.
 */
@Composable
fun AddEditProductScreen(navController: NavHostController, productId: Long?) {
    val container = LocalAppContainer.current
    val viewModel: AddEditProductViewModel = viewModel(
        factory = viewModelFactory { initializer { AddEditProductViewModel(container.productRepository, productId) } },
    )
    val state by viewModel.formState.collectAsState()
    val context = LocalContext.current
    // Which sheet the picker was opened for — the same picker serves both.
    var pickingSafety by remember { mutableStateOf(true) }
    var attachFailed by remember { mutableStateOf(false) }
    val failedMessage = stringResource(R.string.product_sheet_failed)
    val sheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.attachSheet(
                context = context,
                source = uri,
                name = uri.lastPathSegment?.substringAfterLast('/').orEmpty(),
                safety = pickingSafety,
                onFailed = { attachFailed = true },
            )
        }
    }
    if (!state.isLoaded) return

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MixMasterTopBar(
                title = stringResource(if (state.productId == 0L) R.string.product_add else R.string.product_edit),
                onBack = { navController.popBackStack() },
            )
        }

        item { SectionLabel(text = stringResource(R.string.product_what_it_is)) }

        item {
            CardFlat {
                FormTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = stringResource(R.string.product_name),
                    problem = state.nameProblem?.let { stringResource(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Offered back rather than retyped: four spellings of one brand is how a
                    // catalogue stops being searchable.
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

        item { SectionLabel(text = stringResource(R.string.product_how_sold)) }

        item {
            CardFlat {
                Text(
                    text = stringResource(R.string.product_how_sold_explain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FormTextField(
                        value = state.packSizeText,
                        onValueChange = viewModel::setPackSize,
                        label = stringResource(R.string.product_pack_size),
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(FieldWeightNarrow),
                    )
                    DropdownField(
                        label = stringResource(R.string.product_unit),
                        selected = state.packUnit,
                        options = PackUnits,
                        onSelect = viewModel::setPackUnit,
                        modifier = Modifier.weight(FieldWeightNarrow),
                    )
                    // Picked by the name in the app's language; kept as the key it is stored under.
                    val typeNames = PackTypes.map { packName(it) }
                    DropdownField(
                        label = stringResource(R.string.product_container),
                        selected = packName(state.packType),
                        options = typeNames,
                        onSelect = { name -> PackTypes.getOrNull(typeNames.indexOf(name))?.let(viewModel::setPackType) },
                        modifier = Modifier.weight(FieldWeightWide),
                    )
                }
                FormTextField(
                    value = state.densityText,
                    onValueChange = viewModel::setDensity,
                    label = stringResource(R.string.product_density),
                    keyboardType = KeyboardType.Decimal,
                    problem = state.densityProblemRes?.let { stringResource(it) },
                    hint = state.densityWarningRes?.let { stringResource(it) }
                        ?: stringResource(R.string.product_density_hint),
                    modifier = Modifier.padding(top = 10.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Weighted, so a long line in Finnish wraps instead of pushing the switch off.
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = stringResource(R.string.product_on_site), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(R.string.product_on_site_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.suppliedOnSite, onCheckedChange = viewModel::setSuppliedOnSite)
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.product_sheets)) }

        item {
            CardFlat {
                Text(
                    text = stringResource(R.string.product_sheets_explain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SheetField(
                    label = stringResource(R.string.product_safety_sheet),
                    value = state.safetySheet,
                    onValueChange = viewModel::setSafetySheet,
                    onAttach = {
                        pickingSafety = true
                        attachFailed = false
                        sheetPicker.launch(arrayOf("application/pdf", "*/*"))
                    },
                    onClear = { viewModel.clearSheet(context, safety = true) },
                )
                SheetField(
                    label = stringResource(R.string.product_technical_sheet),
                    value = state.technicalSheet,
                    onValueChange = viewModel::setTechnicalSheet,
                    onAttach = {
                        pickingSafety = false
                        attachFailed = false
                        sheetPicker.launch(arrayOf("application/pdf", "*/*"))
                    },
                    onClear = { viewModel.clearSheet(context, safety = false) },
                    modifier = Modifier.padding(top = 14.dp),
                )
                if (attachFailed) {
                    Text(
                        text = failedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            PrimaryButton(
                text = stringResource(R.string.product_save),
                onClick = { viewModel.save { navController.popBackStack() } },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One sheet: a link typed in, or a PDF taken off the phone.
 *
 * Both in one row because they are the same thing to whoever is looking for it later — the
 * difference only matters when there is no signal, which is when the file wins.
 */
@Composable
private fun SheetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stored = SheetStore.isStored(value)
    Column(modifier = modifier.fillMaxWidth()) {
        if (stored) {
            // A file has no address worth typing over, so it reads as what it is.
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = SheetStore.label(value),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        } else {
            FormTextField(value = value, onValueChange = onValueChange, label = label)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ActionLink(
                text = stringResource(if (stored) R.string.product_sheet_replace else R.string.product_sheet_attach),
                onClick = onAttach,
            )
            if (value.isNotBlank()) {
                ActionLink(
                    text = stringResource(R.string.action_clear),
                    onClick = onClear,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
