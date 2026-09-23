@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.warehouse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.db.entity.DeliveryEntity
import com.conwic.mixmaster.domain.ProductStock
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.domain.toNumberOr
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.BrandPill
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.OnAccentCard
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.Ok
import java.time.Instant
import java.time.ZoneId

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
                    container.deliveryRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val onTheWay by viewModel.onTheWay.collectAsState()
    // Held as an id rather than a copy of the row, so the sheet follows the shelf while it is
    // open — a count saved elsewhere, or a room resized on a job, shows up straight away.
    var detailId by remember { mutableStateOf<Long?>(null) }
    var counting by remember { mutableStateOf<ProductStock?>(null) }
    var ordering by remember { mutableStateOf<ProductStock?>(null) }
    var askingAbout by remember { mutableStateOf<DeliveryEntity?>(null) }

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
                    .clickable { detailId = item.productId },
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

    val detail = detailId?.let { id -> state.items.firstOrNull { it.productId == id } }
    detail?.let { item ->
        StockSheet(
            item = item,
            deliveries = onTheWay[item.productId].orEmpty(),
            onDismiss = { detailId = null },
            onCount = {
                detailId = null
                counting = item
            },
            onOrder = {
                detailId = null
                ordering = item
            },
            onDelivery = { delivery ->
                detailId = null
                askingAbout = delivery
            },
            onOpenProject = { projectId ->
                detailId = null
                navController.navigate(Routes.projectDetail(projectId))
            },
            onOpenProduct = {
                detailId = null
                navController.navigate(Routes.productDetail(item.productId))
            },
        )
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

    ordering?.let { item ->
        OrderDialog(
            productName = item.name,
            packType = item.packType,
            packUnit = item.packUnit,
            isKnownPack = item.isKnownPack,
            suggestedPacks = item.packsStillToOrder ?: 0,
            suggestedAmount = item.stillToOrder,
            onDismiss = { ordering = null },
            onOrder = { packs, amount, due, note ->
                viewModel.order(item.productId, packs, amount, due, note)
                ordering = null
            },
        )
    }

    // Tapped from the shelf rather than asked on the day, so "not yet" only closes it — the
    // date it is due is the app's business, not something to move by looking at it.
    askingAbout?.let { delivery ->
        state.items.firstOrNull { it.productId == delivery.productId }?.let { item ->
            ArrivalDialog(
                productName = item.name,
                line = deliveryText(delivery, item) + " · " + formatDueDate(delivery.expectedOn),
                onNotYet = { askingAbout = null },
                onArrived = {
                    viewModel.receive(delivery, item.packSize)
                    askingAbout = null
                },
                onCancelOrder = {
                    viewModel.cancelOrder(delivery.id)
                    askingAbout = null
                },
            )
        }
    }
}

/**
 * Everything the shed knows about one bought item.
 *
 * The list card can only fit a total and the names of the jobs behind it, which is the wrong way
 * round when something is short: what you need standing in front of the rack is how that total
 * is made up, and which job — and which bay of it — is waiting on the stock.
 */
@Composable
private fun StockSheet(
    item: ProductStock,
    deliveries: List<DeliveryEntity>,
    onDismiss: () -> Unit,
    onCount: () -> Unit,
    onOrder: () -> Unit,
    onDelivery: (DeliveryEntity) -> Unit,
    onOpenProject: (Long) -> Unit,
    onOpenProduct: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (item.brand.isNotBlank()) BrandPill(text = item.brand)
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).padding(start = if (item.brand.isNotBlank()) 10.dp else 0.dp),
                )
            }
            Text(
                text = if (item.isKnownPack) {
                    stringResource(
                        R.string.wh_pack_of,
                        formatDecimal(item.packSize, 2),
                        item.packUnit,
                        item.packType,
                    )
                } else {
                    stringResource(R.string.wh_no_pack_size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel(text = stringResource(R.string.wh_on_the_shelf))
            CardFlat {
                if (item.isKnownPack) {
                    StockRow(
                        label = stringResource(R.string.wh_full_packs),
                        value = stringResource(
                            R.string.wh_packs_and_amount,
                            item.fullPacks,
                            item.packType,
                            amountText(item.packedAmount, item.packUnit),
                        ),
                    )
                    StockRow(
                        label = stringResource(R.string.wh_open_pack_row),
                        value = amountText(item.openAmount, item.packUnit),
                    )
                }
                StockRow(
                    label = stringResource(R.string.wh_total_on_hand),
                    value = amountText(item.onHand, item.packUnit),
                    strong = true,
                )
                Text(
                    text = if (item.countedAt > 0L) {
                        stringResource(
                            R.string.wh_counted_on,
                            formatDueDate(
                                Instant.ofEpochMilli(item.countedAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                            ),
                        )
                    } else {
                        stringResource(R.string.wh_never_counted)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SectionLabel(text = stringResource(R.string.wh_booked_title))
            if (item.bookings.isEmpty()) {
                Text(
                    text = stringResource(R.string.wh_not_booked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.bookings.sortedByDescending { it.amount }.forEach { booking ->
                CardFlat(
                    modifier = Modifier
                        .clip(CardShape)
                        .clickable { onOpenProject(booking.projectId) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = booking.projectName,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Text(
                            text = amountText(booking.amount, item.packUnit),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    booking.rooms.forEach { room ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = room.roomName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            Text(
                                text = amountText(room.amount, item.packUnit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (deliveries.isNotEmpty()) {
                SectionLabel(text = stringResource(R.string.wh_on_its_way))
                deliveries.forEach { delivery ->
                    CardFlat(
                        modifier = Modifier
                            .clip(CardShape)
                            .clickable { onDelivery(delivery) },
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = deliveryText(delivery, item),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                            )
                            Text(
                                text = formatDueDate(delivery.expectedOn),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (delivery.note.isNotBlank()) {
                            Text(
                                text = delivery.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            CardFlat {
                StockRow(
                    label = stringResource(R.string.wh_booked_total),
                    value = amountText(item.booked, item.packUnit),
                )
                if (item.short > 0.0) {
                    StockRow(
                        label = stringResource(R.string.wh_short_label),
                        value = amountText(item.short, item.packUnit),
                        strong = true,
                    )
                } else {
                    StockRow(
                        label = stringResource(R.string.wh_free),
                        value = amountText(item.free, item.packUnit),
                        valueColor = Ok,
                    )
                }
                if (item.onOrder > 0.0) {
                    StockRow(
                        label = stringResource(R.string.wh_on_order_label),
                        value = amountText(item.onOrder, item.packUnit),
                        valueColor = MaterialTheme.colorScheme.secondary,
                    )
                }
                StockRow(
                    label = stringResource(R.string.wh_to_order),
                    value = if (item.stillToOrder > 0.0) {
                        orderText(item)
                    } else {
                        stringResource(R.string.prj_nothing_to_order)
                    },
                    strong = item.stillToOrder > 0.0,
                )
            }

            PrimaryButton(
                text = stringResource(R.string.wh_count_stock),
                onClick = onCount,
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton(
                text = stringResource(R.string.wh_mark_ordered),
                onClick = onOrder,
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton(
                text = stringResource(R.string.wh_open_product),
                onClick = onOpenProduct,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A label and its figure, the way the materials tab sets them out. */
@Composable
private fun StockRow(
    label: String,
    value: String,
    strong: Boolean = false,
    valueColor: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor ?: if (strong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Bold,
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
    val packs = item.packsStillToOrder
    return if (packs != null && packs > 0) {
        stringResource(R.string.wh_order_packs, packs, item.packType)
    } else {
        amountText(item.stillToOrder, item.packUnit)
    }
}

/** "2 canister · 50 kg" for one order, or just the amount when the pack size is unknown. */
@Composable
private fun deliveryText(delivery: DeliveryEntity, item: ProductStock): String {
    val total = delivery.packs * item.packSize + delivery.amount
    return if (delivery.packs > 0) {
        stringResource(
            R.string.wh_packs_and_amount,
            delivery.packs,
            item.packType,
            amountText(total, item.packUnit),
        )
    } else {
        amountText(total, item.packUnit)
    }
}

private fun amountText(amount: Double, unit: String): String = "${formatDecimal(amount, 2)} $unit"
