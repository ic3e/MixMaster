package com.conwic.mixmaster.ui.warehouse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.ProductStock
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.packSuggestions
import com.conwic.mixmaster.domain.toNumberOrNull
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.ProductIdentity
import com.conwic.mixmaster.ui.components.ProgressBarRow
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.Stepper
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.components.pageSide
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.theme.Ok
import com.conwic.mixmaster.ui.components.packName
import com.conwic.mixmaster.ui.components.packsName
import com.conwic.mixmaster.ui.company.rememberAccess

/** What is typed into one row, kept as text: "12," on its way to "12,5" is not a number yet. */
private data class CountDraft(val packs: String, val open: String)

/**
 * The shelf, one product after another, to be walked with the phone in one hand.
 *
 * Every row starts at what the app already has, so the common case — the same as last time — is
 * one tap on the tick. A figure changed with the steppers or typed in is saved there and then:
 * a count broken off for a phone call, or a delivery at the door, loses nothing, and picks up
 * where it stopped the next time the screen is opened.
 */
@Composable
fun StockCountScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: StockCountViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StockCountViewModel(context.applicationContext, container.productRepository, container.stockRepository)
            }
        },
    )
    val rows by viewModel.rows.collectAsState()
    val count by viewModel.count.collectAsState()
    // Held here rather than read back from the database: a figure round-trips through Room a
    // frame late, and feeding it back into the field would eat the comma being typed.
    val drafts = remember { mutableStateMapOf<Long, CountDraft>() }
    var confirmFinish by remember { mutableStateOf(false) }
    var settingPack by remember { mutableStateOf<ProductStock?>(null) }
    val access = rememberAccess()

    val list = rows.orEmpty()
    val done = list.count { it.productId in count.counted }
    val otherBrand = stringResource(R.string.sc_no_brand)

    fun draftOf(item: ProductStock): CountDraft = drafts[item.productId] ?: CountDraft(
        packs = item.fullPacks.toString(),
        open = formatDecimal(item.openAmount, 1),
    )

    fun saveDraft(item: ProductStock, draft: CountDraft) {
        drafts[item.productId] = draft
        viewModel.save(
            productId = item.productId,
            packs = (draft.packs.toNumberOrNull() ?: 0.0).toInt().coerceAtLeast(0),
            open = (draft.open.toNumberOrNull() ?: 0.0).coerceAtLeast(0.0),
        )
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = pagePadding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                MixMasterTopBar(title = stringResource(R.string.sc_title), onBack = { navController.popBackStack() })
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.sc_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.sc_progress, done, list.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ProgressBarRow(progressPercent = if (list.isEmpty()) 0 else done * 100 / list.size)
                }
            }

            list.groupBy { it.brand.ifBlank { otherBrand } }.forEach { (brand, products) ->
                item(key = "brand:$brand") {
                    // In full ink, not the faint grey of other section labels: here the brand is
                    // the signpost for finding your place along the racks.
                    SectionLabel(
                        text = brand,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(products, key = { it.productId }) { item ->
                    val draft = draftOf(item)
                    CountRow(
                        item = item,
                        counted = item.productId in count.counted,
                        draft = draft,
                        onPacks = { saveDraft(item, draft.copy(packs = it)) },
                        onOpen = { saveDraft(item, draft.copy(open = it)) },
                        // The pack belongs to the product, which is the catalogue's to change.
                        onSetPack = if (access.catalogue) {
                            { settingPack = item }
                        } else {
                            null
                        },
                        onTick = {
                            // Both ways: a green tick tapped again comes off, so a slip of the
                            // thumb while scrolling is undone by the same tap that made it.
                            if (item.productId in count.counted) {
                                viewModel.uncount(item.productId)
                            } else {
                                drafts.remove(item.productId)
                                viewModel.same(item)
                            }
                        },
                    )
                }
            }

            item {
                // Something on the rack the app has never heard of: add it, and it joins the list.
                ActionLink(
                    text = stringResource(R.string.sc_add_product),
                    onClick = { navController.navigate(Routes.PRODUCT_ADD) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // Kept in reach at the bottom, whatever row the count has got to.
        Surface(color = MaterialTheme.colorScheme.background) {
            PrimaryButton(
                text = stringResource(R.string.sc_finish),
                onClick = {
                    if (done < list.size) {
                        confirmFinish = true
                    } else {
                        viewModel.finish { navController.popBackStack() }
                    }
                },
                enabled = rows != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pageSide(), vertical = 10.dp),
            )
        }
    }

    val packFor = settingPack
    if (packFor != null) {
        PackSizeDialog(
            item = packFor,
            suggestions = packSuggestions(packFor.name, packFor.brand, list),
            onDismiss = { settingPack = null },
            onSave = { pack ->
                // What was typed was a loose amount; the row is packs and an open one from now on.
                drafts.remove(packFor.productId)
                viewModel.setPack(packFor.productId, pack)
                settingPack = null
            },
        )
    }

    if (confirmFinish && done == 0) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(text = stringResource(R.string.sc_nothing_title)) },
            text = { Text(text = stringResource(R.string.sc_nothing_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFinish = false
                        viewModel.stop { navController.popBackStack() }
                    },
                ) { Text(text = stringResource(R.string.sc_stop_count)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) {
                    Text(text = stringResource(R.string.sc_keep_counting))
                }
            },
        )
    } else if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(text = stringResource(R.string.sc_skipped_title, list.size - done)) },
            text = { Text(text = stringResource(R.string.sc_skipped_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmFinish = false
                        viewModel.finish { navController.popBackStack() }
                    },
                ) { Text(text = stringResource(R.string.sc_finish_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) {
                    Text(text = stringResource(R.string.sc_keep_counting))
                }
            },
        )
    }
}

@Composable
private fun CountRow(
    item: ProductStock,
    counted: Boolean,
    draft: CountDraft,
    onPacks: (String) -> Unit,
    onOpen: (String) -> Unit,
    onSetPack: (() -> Unit)?,
    onTick: () -> Unit,
) {
    // The green edge is what says "done" from across the list, without reading a word of it.
    CardFlat(edge = if (counted) Ok else null) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ProductIdentity(
                name = item.name,
                brand = item.brand,
                detail = if (item.isKnownPack) {
                    stringResource(R.string.wh_pack_of, formatDecimal(item.packSize, 2), item.packUnit, packName(item.packType))
                } else {
                    stringResource(R.string.pack_not_set)
                },
                modifier = Modifier.weight(1f).padding(end = 10.dp),
            )
            SameButton(counted = counted, onClick = onTick)
        }
        if (!item.isKnownPack && onSetPack != null) {
            // Set here, mid-count, rather than on the product's own form three screens away.
            ActionLink(
                text = stringResource(R.string.pack_set),
                onClick = onSetPack,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (item.isKnownPack) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    CountLabel(stringResource(R.string.sc_full_packs, packsName(item.packType)))
                    Stepper(value = draft.packs, onValueChange = onPacks, decimals = 0)
                }
                Column(modifier = Modifier.weight(1f)) {
                    CountLabel(stringResource(R.string.sc_open_pack, item.packUnit))
                    Stepper(value = draft.open, onValueChange = onOpen, decimals = 1)
                }
            }
        } else {
            // No pack size, so no packs to count: only how much there is.
            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                CountLabel(stringResource(R.string.sc_amount, item.packUnit))
                Stepper(value = draft.open, onValueChange = onOpen, decimals = 1)
            }
        }
    }
}

/**
 * The field label, held to one line so the two steppers of a row stay level whatever the
 * language does to the words — the style is FieldLabel's, which cannot be told to stop at one.
 */
@Composable
private fun CountLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 11.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
    )
}

/** The tick: "same as the app has it". Filled in once the row has been gone through; tapped again, it comes off. */
@Composable
private fun SameButton(counted: Boolean, onClick: () -> Unit) {
    val ink = if (counted) Color.White else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (counted) {
                    Modifier.background(Ok)
                } else {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(if (counted) R.string.sc_counted else R.string.sc_same),
            tint = ink,
        )
    }
}
