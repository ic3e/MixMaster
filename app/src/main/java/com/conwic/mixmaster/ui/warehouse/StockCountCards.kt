package com.conwic.mixmaster.ui.warehouse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.prefs.CountChange
import com.conwic.mixmaster.data.prefs.CountSummary
import com.conwic.mixmaster.data.prefs.StockCountState
import com.conwic.mixmaster.data.prefs.StockCountStore
import com.conwic.mixmaster.domain.CountInterval
import com.conwic.mixmaster.domain.ProductStock
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.CardSoft
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.theme.CardShape
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private fun dayOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** The count's own state, read once per screen and shared with the reminder. */
@Composable
fun rememberStockCount(): StockCountState {
    val context = LocalContext.current
    val state by remember { StockCountStore.state(context) }.collectAsState()
    return state
}

/**
 * Where the count lives on the warehouse page: when it was last done, a button to do it, and
 * how often the phone should ask.
 */
@Composable
internal fun StockCountCard(
    count: StockCountState,
    shelf: List<ProductStock>,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val due = count.isDue(now)
    val dueAt = count.dueAt()
    // Asked for the moment a reminder is switched on — the one point where it is obvious why.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val status = when {
        count.inProgress -> stringResource(
            R.string.sc_in_progress,
            shelf.count { it.productId in count.counted },
            shelf.size,
        )
        due && count.lastCountAt > 0L -> stringResource(R.string.sc_due_since, formatDueDate(dayOf(count.lastCountAt)))
        due -> stringResource(R.string.sc_due_first)
        count.lastCountAt > 0L && dueAt != null ->
            stringResource(R.string.sc_last_next, formatDueDate(dayOf(count.lastCountAt)), formatDueDate(dayOf(dueAt)))
        count.lastCountAt > 0L -> stringResource(R.string.sc_last, formatDueDate(dayOf(count.lastCountAt)))
        dueAt != null -> stringResource(R.string.sc_never_next, formatDueDate(dayOf(dueAt)))
        else -> stringResource(R.string.sc_never)
    }

    // An overdue count gets the red edge a short product does: something to be done about it.
    CardFlat(edge = if (due && !count.inProgress) MaterialTheme.colorScheme.error else null) {
        Text(text = stringResource(R.string.sc_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (due && !count.inProgress) FontWeight.Bold else FontWeight.Normal,
            color = if (due && !count.inProgress) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        PrimaryButton(
            text = stringResource(if (count.inProgress) R.string.sc_continue else R.string.sc_start),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
        )
        Text(
            text = stringResource(R.string.sc_reminder),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val labels = mapOf(
            CountInterval.OFF to stringResource(R.string.sc_every_off),
            CountInterval.WEEKLY to stringResource(R.string.sc_every_week),
            CountInterval.FORTNIGHTLY to stringResource(R.string.sc_every_2weeks),
            CountInterval.MONTHLY to stringResource(R.string.sc_every_month),
        )
        ChipRow(
            options = CountInterval.values().map { interval ->
                ChipOption(
                    label = labels.getValue(interval),
                    selected = interval == count.interval,
                    onClick = {
                        StockCountStore.setInterval(context, interval)
                        StockCountReminder.schedule(context)
                        if (interval != CountInterval.OFF &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            runCatching { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        }
                    },
                )
            },
        )
        if (count.interval != CountInterval.OFF) {
            Text(
                text = stringResource(R.string.sc_reminder_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** What the count that was just finished turned up, until somebody has read it. */
@Composable
internal fun CountSummaryCard(summary: CountSummary, shelf: List<ProductStock>, onDismiss: () -> Unit) {
    val byId = shelf.associateBy { it.productId }
    val changes = summary.changes.mapNotNull { change -> byId[change.productId]?.let { it to change } }
    val skipped = summary.skipped.mapNotNull { byId[it]?.name }

    CardSoft {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sc_done_title, formatDueDate(dayOf(summary.finishedAt))),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            ActionLink(text = stringResource(R.string.sc_dismiss), onClick = onDismiss)
        }
        Text(
            text = stringResource(R.string.sc_done_counts, summary.counted, changes.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
        if (changes.isEmpty()) {
            Text(text = stringResource(R.string.sc_no_changes), style = MaterialTheme.typography.bodyMedium)
        }
        changes.forEach { (item, change) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(
                    text = changeText(change, item),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (skipped.isNotEmpty()) {
            Text(
                text = stringResource(R.string.sc_not_counted, skipped.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * "6 → 4 bag (−2)" where only whole packs moved, which is how it is said at the rack; the
 * total in the pack's unit where the open one changed too, since packs alone would hide that.
 */
internal fun changeText(change: CountChange, item: ProductStock): String {
    val (before, after) = change.before to change.after
    val sameOpen = abs(before.open - after.open) < 0.0005
    return if (item.isKnownPack && sameOpen) {
        val delta = after.packs - before.packs
        "${before.packs} → ${after.packs} ${item.packType} (${signed(delta.toDouble())})"
    } else {
        val size = if (item.isKnownPack) item.packSize else 0.0
        val was = before.packs * size + before.open
        val now = after.packs * size + after.open
        "${formatDecimal(was, 1)} → ${formatDecimal(now, 1)} ${item.packUnit} (${signed(now - was)})"
    }
}

/** A difference with its sign always shown, and a real minus rather than a hyphen. */
private fun signed(value: Double): String {
    val text = formatDecimal(abs(value), 1)
    return if (value < 0) "−$text" else "+$text"
}

/**
 * On the home screen while a count is due. Styled like the update banner — the other card that
 * is only ever there when there is something to do.
 */
@Composable
fun StockCountDueCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val count = rememberStockCount()
    if (!count.isDue()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, CardShape)
            .border(1.dp, MaterialTheme.colorScheme.primary, CardShape)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_count_due),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = if (count.lastCountAt > 0L) {
                stringResource(R.string.sc_last, formatDueDate(dayOf(count.lastCountAt)))
            } else {
                stringResource(R.string.sc_never)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 2.dp),
        )
        PrimaryButton(
            text = stringResource(if (count.inProgress) R.string.sc_continue else R.string.sc_start),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}
