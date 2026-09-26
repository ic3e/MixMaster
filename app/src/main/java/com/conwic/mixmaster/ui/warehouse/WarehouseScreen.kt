@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.warehouse

import com.conwic.mixmaster.ui.components.LocalBarInset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.conwic.mixmaster.ui.company.rememberAccess
import androidx.compose.ui.text.style.TextAlign
import com.conwic.mixmaster.data.prefs.StockCountStore
import com.conwic.mixmaster.ui.components.ActionLink
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
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
import com.conwic.mixmaster.domain.packSuggestions
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.domain.toNumberOr
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.OnAccentCard
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.ProductIdentity
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.Ok
import java.time.Instant
import java.time.ZoneId
import com.conwic.mixmaster.ui.components.packCount
import com.conwic.mixmaster.ui.components.packName
import com.conwic.mixmaster.ui.components.FilterField
import java.time.LocalDate
import androidx.compose.material3.HorizontalDivider

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
    var settingPack by remember { mutableStateOf<ProductStock?>(null) }
    val count = rememberStockCount()
    val context = LocalContext.current
    // In a company the employer decides who changes the shelf. Whoever may not sees the shelf
    // as it stands, with nothing to press that would change it: buttons that only answered
    // "not yours" were options that were never there.
    val access = rememberAccess()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The same rhythm as the Products and Projects lists — this screen sat at its own
        // spacing and its own title size, which is what made moving between them feel like
        // moving between two apps. The room at the bottom keeps the last card off the nav bar.
        contentPadding = pagePadding(bottom = 40.dp + LocalBarInset.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(text = stringResource(R.string.wh_title), style = MaterialTheme.typography.headlineMedium) }

        // The count, and its reminder, are for whoever looks after the shelf.
        if (access.warehouse) {
            item {
                StockCountCard(
                    count = count,
                    shelf = state.shelf,
                    onStart = { navController.navigate(Routes.STOCK_COUNT) },
                )
            }
        }

        val summary = count.summary
        if (summary != null) {
            item {
                CountSummaryCard(
                    summary = summary,
                    shelf = state.shelf,
                    onDismiss = { StockCountStore.dismissSummary(context) },
                )
            }
        }

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
                            verticalAlignment = Alignment.CenterVertically,
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
                            // Straight from the list to the order, with the amount filled in — it
                            // used to be the product's sheet first, then the button at its foot.
                            if (access.warehouse) ActionLink(
                                text = stringResource(R.string.wh_order_short),
                                onClick = { ordering = item },
                                color = OnAccentCard,
                                modifier = Modifier.padding(start = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        // Everything marked ordered that has not come in, in one place. It was only to be found
        // product by product, so an order marked and never actually placed went unseen until
        // the day it did not turn up. Each says who marked it and whether it has an order number,
        // which is what there is to check it against.
        val openOrders = onTheWay.values.flatten().sortedBy { it.expectedOn }
        if (openOrders.isNotEmpty()) {
            item { SectionLabel(text = stringResource(R.string.wh_on_order_title, openOrders.size)) }
            item {
                CardFlat(contentPadding = 12.dp) {
                    Text(
                        text = stringResource(R.string.wh_on_order_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                    )
                    openOrders.forEachIndexed { index, delivery ->
                        val item = state.shelf.firstOrNull { it.productId == delivery.productId }
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        OpenOrderRow(
                            delivery = delivery,
                            item = item,
                            onClick = if (access.warehouse) {
                                { askingAbout = delivery }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        item {
            FilterField(
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
                // Laid out like a product on the Products screen — brand over the name, the
                // figure on the right. It used to carry a dark brand pill beside the name at a
                // size no other list uses, which read as a different app; it also left a long
                // name and the amount fighting for the same line, and they met in the middle.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ProductIdentity(
                        name = item.name,
                        brand = item.brand,
                        modifier = Modifier.weight(1f).padding(end = 10.dp),
                    )
                    // Packs over kilos, as on the project's material list: on one line they came
                    // to more than twenty characters and left "Microtopping® Base Coat Polymer"
                    // four lines tall beside them.
                    Column(horizontalAlignment = Alignment.End) {
                        if (item.isKnownPack) {
                            Text(
                                text = packCount(item.fullPacks, item.packType),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = amountText(item.onHand, item.packUnit),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = amountText(item.onHand, item.packUnit),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.pack_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        if (access.catalogue) {
                            ActionLink(text = stringResource(R.string.pack_set), onClick = { settingPack = item })
                        }
                    }
                }
            }
        }
    }

    val detail = detailId?.let { id -> state.items.firstOrNull { it.productId == id } }
    detail?.let { item ->
        StockSheet(
            item = item,
            deliveries = onTheWay[item.productId].orEmpty(),
            canChange = access.warehouse,
            onDismiss = { detailId = null },
            onCount = {
                detailId = null
                counting = item
            },
            onSetPacks = { packs -> viewModel.setPacks(item.productId, packs) },
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

    val packFor = settingPack
    if (packFor != null) {
        PackSizeDialog(
            item = packFor,
            suggestions = packSuggestions(packFor.name, packFor.brand, state.shelf),
            onDismiss = { settingPack = null },
            onSave = { pack ->
                viewModel.setPack(packFor.productId, pack)
                settingPack = null
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
        state.shelf.firstOrNull { it.productId == delivery.productId }?.let { item ->
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
    /** Looks after the shelf: may count, nudge, order and answer for deliveries. Otherwise it is read. */
    canChange: Boolean,
    onDismiss: () -> Unit,
    onCount: () -> Unit,
    /** Puts the shelf at this many unopened packs — a figure, not a step, so a second Save is harmless. */
    onSetPacks: (Int) -> Unit,
    onOrder: () -> Unit,
    onDelivery: (DeliveryEntity) -> Unit,
    onOpenProject: (Long) -> Unit,
    onOpenProduct: () -> Unit,
) {
    // The − / + only moves the figure; the shelf changes on Save. Each tap used to be written at
    // once, and a sheet brushed on the way into a pocket left the stock two bags out with nothing
    // to say so.
    var packs by remember(item.productId) { mutableStateOf<Int?>(null) }
    val shownPacks = packs ?: item.fullPacks
    val unsaved = shownPacks != item.fullPacks
    // Once the saved figure catches up with the one being shown, there is nothing held any more.
    LaunchedEffect(item.fullPacks) { if (packs == item.fullPacks) { packs = null } }

    // Where the sheet was going when it was stopped to ask about the change.
    var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }
    val leaveThen: (() -> Unit) -> Unit = { go -> if (unsaved) { leaving = go } else { go() } }
    val save = { onSetPacks(shownPacks) }

    // Read by the sheet state, which is made once: the figures it needs are whatever they are now.
    val unsavedNow by rememberUpdatedState(unsaved)
    val dismissNow by rememberUpdatedState(onDismiss)
    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && unsavedNow) {
                leaving = dismissNow
                false
            } else {
                true
            }
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProductIdentity(
                name = item.name,
                brand = item.brand,
                large = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (item.isKnownPack) {
                    stringResource(
                        R.string.wh_pack_of,
                        formatDecimal(item.packSize, 2),
                        item.packUnit,
                        packName(item.packType),
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
                    // − / + for the everyday change — a bag taken to site, one found behind the
                    // door — without typing out a whole count for it.
                    val fullPacks = stringResource(
                        R.string.wh_packs_and_amount,
                        packCount(shownPacks, item.packType),
                        amountText(shownPacks * item.packSize, item.packUnit),
                    )
                    if (canChange) {
                        PackAdjustRow(
                            label = stringResource(R.string.wh_full_packs),
                            value = fullPacks,
                            canLess = shownPacks > 0,
                            onLess = { packs = (shownPacks - 1).coerceAtLeast(0) },
                            onMore = { packs = shownPacks + 1 },
                        )
                        if (unsaved) {
                            UnsavedPacks(
                                was = packCount(item.fullPacks, item.packType),
                                onUndo = { packs = null },
                                onSave = save,
                            )
                        }
                    } else {
                        StockRow(label = stringResource(R.string.wh_full_packs), value = fullPacks)
                    }
                    StockRow(
                        label = stringResource(R.string.wh_open_pack_row),
                        value = amountText(item.openAmount, item.packUnit),
                    )
                }
                StockRow(
                    label = stringResource(R.string.wh_total_on_hand),
                    value = amountText(item.onHand + (shownPacks - item.fullPacks) * item.packSize, item.packUnit),
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
                        .clickable { leaveThen { onOpenProject(booking.projectId) } },
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
                            .clickable(enabled = canChange) { leaveThen { onDelivery(delivery) } },
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

            if (canChange) {
                PrimaryButton(
                    text = stringResource(R.string.wh_count_stock),
                    onClick = { leaveThen(onCount) },
                    modifier = Modifier.fillMaxWidth(),
                )
                GhostButton(
                    text = stringResource(R.string.wh_mark_ordered),
                    onClick = { leaveThen(onOrder) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            GhostButton(
                text = stringResource(R.string.wh_open_product),
                onClick = { leaveThen(onOpenProduct) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val going = leaving
    if (going != null) {
        AlertDialog(
            // Tapped past: back to the sheet, the change still held.
            onDismissRequest = { leaving = null },
            title = { Text(text = stringResource(R.string.wh_adjust_leave_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.wh_adjust_leave_body,
                        stringResource(R.string.wh_full_packs),
                        packCount(item.fullPacks, item.packType),
                        packCount(shownPacks, item.packType),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        save()
                        leaving = null
                        going()
                    },
                ) {
                    Text(text = stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        packs = null
                        leaving = null
                        going()
                    },
                ) {
                    Text(text = stringResource(R.string.wh_adjust_undo), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

/** Under the − / +: the figure moved but the shelf has not, with the two ways to settle it. */
@Composable
private fun UnsavedPacks(was: String, onUndo: () -> Unit, onSave: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(CardShape)
            .background(scheme.secondaryContainer)
            .border(1.dp, scheme.secondary, CardShape)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.wh_adjust_unsaved, was),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSecondaryContainer,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
        )
        TextButton(onClick = onUndo) {
            Text(text = stringResource(R.string.wh_adjust_undo), color = scheme.error)
        }
        TextButton(onClick = onSave) {
            Text(text = stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
        }
    }
}

/** A figure with a − and a + either side of it, each one pack. */
@Composable
private fun PackAdjustRow(
    label: String,
    value: String,
    canLess: Boolean,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundStep(icon = Icons.Filled.Remove, label = stringResource(R.string.sc_less), enabled = canLess, onClick = onLess)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            RoundStep(icon = Icons.Filled.Add, label = stringResource(R.string.sc_more), enabled = true, onClick = onMore)
        }
    }
}

@Composable
private fun RoundStep(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val ink = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, ink.copy(alpha = 0.6f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = ink)
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
        Text(modifier = Modifier.weight(1f, fill = false), text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                            packName(item.packType),
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

@Composable
private fun orderText(item: ProductStock): String {
    val packs = item.packsStillToOrder
    return if (packs != null && packs > 0) {
        packCount(packs, item.packType)
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
            packCount(delivery.packs, item.packType),
            amountText(total, item.packUnit),
        )
    } else {
        amountText(total, item.packUnit)
    }
}

private fun amountText(amount: Double, unit: String): String = "${formatDecimal(amount, 2)} $unit"

/**
 * One order on its way: what, how much, when it was marked and by whom, when it is due, and what
 * it was ordered under. One with no order number says so — that is the one to check was placed.
 */
@Composable
private fun OpenOrderRow(delivery: DeliveryEntity, item: ProductStock?, onClick: (() -> Unit)?) {
    val today = remember { LocalDate.now() }
    val late = delivery.expectedOn.isBefore(today)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = item?.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            if (item != null) {
                Text(
                    text = deliveryText(delivery, item),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = if (delivery.orderedBy.isBlank()) {
                stringResource(R.string.wh_order_marked, formatDueDate(delivery.orderedOn))
            } else {
                stringResource(R.string.wh_order_marked_by, formatDueDate(delivery.orderedOn), delivery.orderedBy)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stringResource(
                if (late) R.string.wh_order_late else R.string.wh_order_expected,
                formatDueDate(delivery.expectedOn),
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (late) FontWeight.Bold else FontWeight.Normal,
            color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = delivery.note.ifBlank { stringResource(R.string.wh_order_no_number) },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (delivery.note.isBlank()) FontWeight.Bold else FontWeight.Normal,
            color = if (delivery.note.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
