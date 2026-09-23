package com.conwic.mixmaster.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
    if (!state.isLoaded) return

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(20.dp),
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
                    DropdownField(
                        label = stringResource(R.string.product_container),
                        selected = state.packType,
                        options = PackTypes,
                        onSelect = viewModel::setPackType,
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
                text = stringResource(R.string.product_save),
                onClick = { viewModel.save { navController.popBackStack() } },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
