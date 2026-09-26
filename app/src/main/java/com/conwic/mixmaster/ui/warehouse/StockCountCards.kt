package com.conwic.mixmaster.ui.warehouse

import android.Manifest
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.conwic.mixmaster.domain.AppLocale
import com.conwic.mixmaster.domain.SameDayAsLastCount
import java.time.DayOfWeek
import java.time.format.TextStyle
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import java.util.Calendar
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.conwic.mixmaster.ui.components.ProductIdentity
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.theme.FieldShape
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
import com.conwic.mixmaster.ui.components.packCount

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
    var pickingTime by remember { mutableStateOf(false) }
    var pickingDay by remember { mutableStateOf(false) }

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
            val time = timeText(context, count.reminderMinute)
            ReminderSettingRow(
                label = stringResource(R.string.sc_day_label),
                value = weekdayName(count.reminderWeekday),
                action = stringResource(R.string.sc_change_day),
                onClick = { pickingDay = true },
                modifier = Modifier.padding(top = 12.dp),
            )
            ReminderSettingRow(
                label = stringResource(R.string.sc_time_label),
                value = time,
                action = stringResource(R.string.sc_change_time),
                onClick = { pickingTime = true },
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.sc_reminder_hint, time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    if (pickingDay) {
        ReminderDayDialog(
            selected = count.reminderWeekday,
            onDismiss = { pickingDay = false },
            onPick = { weekday ->
                pickingDay = false
                StockCountStore.setReminderWeekday(context, weekday)
                StockCountReminder.schedule(context)
            },
        )
    }

    if (pickingTime) {
        ReminderTimeDialog(
            minuteOfDay = count.reminderMinute,
            onDismiss = { pickingTime = false },
            onPick = { minute ->
                pickingTime = false
                StockCountStore.setReminderMinute(context, minute)
                StockCountReminder.schedule(context)
            },
        )
    }
}

/** A setting under the reminder chips: what it is, what it is set to, and the way to change it. */
@Composable
private fun ReminderSettingRow(
    label: String,
    value: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        ActionLink(text = action, onClick = onClick)
    }
}

/** "Monday" in the app's language, or the words for keeping to the last count's own day. */
@Composable
private fun weekdayName(weekday: Int): String =
    if (weekday in 1..7) {
        DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, AppLocale.current)
            .replaceFirstChar { it.uppercase(AppLocale.current) }
    } else {
        stringResource(R.string.sc_day_same)
    }

/** One tap on a day picks it; there is nothing else on the dialog to confirm. */
@Composable
private fun ReminderDayDialog(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sc_pick_day)) },
        text = {
            // Scrolls: eight rows and a line of explanation do not fit a phone held sideways.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.sc_day_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                (listOf(SameDayAsLastCount) + (1..7)).forEach { day ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FieldShape)
                            .selectable(selected = day == selected, role = Role.RadioButton, onClick = { onPick(day) })
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = day == selected, onClick = null)
                        Text(
                            text = weekdayName(day),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

/** The reminder time the way the phone writes times: 08:00, or 8:00 AM where that is the habit. */
private fun timeText(context: Context, minuteOfDay: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

/** The clock face, in the phone's own 12- or 24-hour habit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(minuteOfDay: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = minuteOfDay / 60,
        initialMinute = minuteOfDay % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sc_pick_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * What the count that was just finished turned up, until somebody has read it.
 *
 * Three figures first, then the products behind them one to a row, set out the way products are
 * everywhere else in the app. It used to run every product nobody got to into one paragraph,
 * which for a count finished early was the whole shed in a single block of commas.
 */
@Composable
internal fun CountSummaryCard(summary: CountSummary, shelf: List<ProductStock>, onDismiss: () -> Unit) {
    val byId = shelf.associateBy { it.productId }
    val changes = summary.changes.mapNotNull { change -> byId[change.productId]?.let { it to change } }
    val skipped = summary.skipped.mapNotNull { byId[it] }
    var showAllSkipped by rememberSaveable { mutableStateOf(false) }

    CardSoft {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(text = stringResource(R.string.sc_done_heading), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatDueDate(dayOf(summary.finishedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ActionLink(text = stringResource(R.string.sc_dismiss), onClick = onDismiss)
        }

        // Sized to the tallest, so a label that wraps in Finnish lifts all three together.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryFigure(
                value = summary.counted,
                label = stringResource(R.string.sc_counted),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SummaryFigure(
                value = changes.size,
                label = stringResource(R.string.sc_stat_changed),
                highlight = changes.isNotEmpty(),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            SummaryFigure(
                value = skipped.size,
                label = stringResource(R.string.sc_stat_skipped),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        if (changes.isNotEmpty()) {
            SummaryList(title = stringResource(R.string.sc_section_changed)) {
                changes.forEachIndexed { index, (item, change) ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProductIdentity(
                            name = item.name,
                            brand = item.brand,
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                        )
                        Text(
                            text = changeText(LocalContext.current, change, item),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(if (summary.counted > 0) R.string.sc_no_changes else R.string.sc_nothing_counted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (skipped.isNotEmpty()) {
            // Three, and the rest on request: the list is for spotting the one that was missed,
            // not for reading the whole catalogue back.
            val shown = if (showAllSkipped) skipped else skipped.take(SkippedPreview)
            SummaryList(title = stringResource(R.string.sc_stat_skipped)) {
                shown.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ProductIdentity(
                        name = item.name,
                        brand = item.brand,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    )
                }
            }
            if (skipped.size > SkippedPreview) {
                ActionLink(
                    text = if (showAllSkipped) {
                        stringResource(R.string.sc_show_fewer)
                    } else {
                        stringResource(R.string.sc_show_all, skipped.size)
                    },
                    onClick = { showAllSkipped = !showAllSkipped },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

private const val SkippedPreview = 3

/** One of the three figures across the top of the summary. */
@Composable
private fun SummaryFigure(value: Int, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, FieldShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A heading and a white panel of rows under it, set into the soft card. */
@Composable
private fun SummaryList(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(text = title, modifier = Modifier.padding(top = 16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, FieldShape)
            .padding(horizontal = 14.dp),
        content = content,
    )
}

/**
 * "6 → 4 bag (−2)" where only whole packs moved, which is how it is said at the rack; the
 * total in the pack's unit where the open one changed too, since packs alone would hide that.
 */
internal fun changeText(context: Context, change: CountChange, item: ProductStock): String {
    val (before, after) = change.before to change.after
    val sameOpen = abs(before.open - after.open) < 0.0005
    return if (item.isKnownPack && sameOpen) {
        val delta = after.packs - before.packs
        "${before.packs} → ${packCount(context, after.packs.toDouble(), item.packType)} (${signed(delta.toDouble())})"
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
