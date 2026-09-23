package com.conwic.mixmaster.ui.warehouse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.ProductStock
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.toNumberOr
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.BrandPill
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.OnAccentCard
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.theme.CardShape

/**
 * What is actually in the shed.
 *
 * Counted in packs, because that is what is on the racks, with the kilos or litres worked out
 * from the pack size the product carries. A job books its material the moment a coat is put on
 * one of its rooms, so what is left over is the figure that decides whether anything has to be
 * ordered.
 */
@Composable
fun WarehouseScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: WarehouseViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                WarehouseViewModel(
                    container.productRepository,
                    container.projectRepository,
                    container.solutionRepository,
                    container.stockRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    var counting by remember { mutableStateOf<ProductStock?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(text = stringResource(R.string.wh_title), style = MaterialTheme.typography.headlineLarge) }

        val toOrder = state.toOrder
        if (toOrder.isNotEmpty()) {
            item {
                CardAccent {
                    Text(
                        text = stringResource(R.string.wh_to_order),
                        style = MaterialTheme.typography.titleLarge,
                        color = OnAccentCard,
                    )
                    Text(
                        text = stringResource(R.string.wh_to_order_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnAccentCard.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    toOrder.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnAccentCard,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            Text(
                                text = orderText(item),
                                style = MaterialTheme.typography.titleMedium,
                                color = OnAccentCard,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }

        item {
            DropdownField(
                label = stringResource(R.string.filter_brand),
                selected = state.brandFilter,
                options = state.brands,
                onSelect = { viewModel.setBrandFilter(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { SectionLabel(text = stringResource(R.string.wh_on_the_shelf)) }

        if (state.items.isEmpty()) {
            item { Text(text = stringResource(R.string.wh_empty), style = MaterialTheme.typography.bodyMedium) }
        }

        items(state.items.size) { index ->
            val item = state.items[index]
            CardFlat(
                modifier = Modifier
                    .clip(CardShape)
                    .clickable { counting = item },
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (item.brand.isNotBlank()) BrandPill(text = item.brand)
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = if (item.brand.isNotBlank()) 10.dp else 0.dp),
                    )
                    Text(text = onHandText(item), style = MaterialTheme.typography.titleMedium)
                }
                if (item.bookings.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.wh_booked_for,
                            item.bookings.joinToString(", ") { it.projectName },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = if (item.short > 0.0) {
                            stringResource(R.string.wh_short_by, amountText(item.short, item.packUnit))
                        } else {
                            stringResource(R.string.wh_in_stock, amountText(item.free, item.packUnit))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.short > 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!item.isKnownPack) {
                    Text(
                        text = stringResource(R.string.wh_no_pack_size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    counting?.let { item ->
        CountDialog(
            item = item,
            onDismiss = { counting = null },
            onSave = { packs, open ->
                viewModel.setStock(item.productId, packs, open)
                counting = null
            },
        )
    }
}

@Composable
private fun CountDialog(item: ProductStock, onDismiss: () -> Unit, onSave: (Int, Double) -> Unit) {
    var packs by remember { mutableStateOf(if (item.fullPacks > 0) item.fullPacks.toString() else "") }
    var open by remember { mutableStateOf(if (item.openAmount > 0.0) formatDecimal(item.openAmount, 2) else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormTextField(
                    value = packs,
                    onValueChange = { packs = it },
                    label = stringResource(R.string.wh_full_packs),
                    keyboardType = KeyboardType.Number,
                    hint = if (item.isKnownPack) {
                        stringResource(
                            R.string.wh_pack_of,
                            formatDecimal(item.packSize, 2),
                            item.packUnit,
                            item.packType,
                        )
                    } else {
                        stringResource(R.string.wh_no_pack_size)
                    },
                )
                FormTextField(
                    value = open,
                    onValueChange = { open = it },
                    label = stringResource(R.string.wh_open_pack, item.packUnit),
                    keyboardType = KeyboardType.Decimal,
                    hint = stringResource(R.string.wh_open_pack_hint),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(packs.toNumberOr(0.0).toInt(), open.toNumberOr(0.0)) }) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

/** "3 bags · 62.5 kg", or just the amount when nobody has said what a pack holds. */
@Composable
private fun onHandText(item: ProductStock): String = when {
    !item.isKnownPack -> amountText(item.onHand, item.packUnit)
    else -> stringResource(
        R.string.wh_packs_and_amount,
        item.fullPacks,
        item.packType,
        amountText(item.onHand, item.packUnit),
    )
}

@Composable
private fun orderText(item: ProductStock): String {
    val packs = item.packsToOrder
    return if (packs != null && packs > 0) {
        stringResource(R.string.wh_order_packs, packs, item.packType)
    } else {
        amountText(item.short, item.packUnit)
    }
}

private fun amountText(amount: Double, unit: String): String = "${formatDecimal(amount, 2)} $unit"
