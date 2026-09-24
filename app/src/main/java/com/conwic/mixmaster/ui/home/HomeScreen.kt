package com.conwic.mixmaster.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.formatDayWithWeek
import com.conwic.mixmaster.domain.formatShortWeekday
import com.conwic.mixmaster.domain.formatWeek
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.OnAccentCard
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.StatCard
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.navigation.navigateToTopLevel
import com.conwic.mixmaster.ui.warehouse.ArrivalDialog
import com.conwic.mixmaster.ui.warehouse.OrderDialog
import com.conwic.mixmaster.ui.tasks.TaskDraft
import com.conwic.mixmaster.ui.tasks.TaskEditorSheet
import com.conwic.mixmaster.ui.tasks.TaskRow
import com.conwic.mixmaster.ui.tasks.toDraft
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.ChipShape
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.conwic.mixmaster.ui.components.CrashReportCard
import com.conwic.mixmaster.ui.settings.UpdateBanner

@Composable
fun HomeScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.projectRepository,
                    container.productRepository,
                    container.stockRepository,
                    container.solutionRepository,
                    container.deliveryRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val now = remember(state) { LocalDateTime.now() }
    val today = now.toLocalDate()
    val dayPart = dayPartFor(now.toLocalTime())
    val quips = stringArrayResource(quipArrayRes(dayPart))

    // Non-null while the add/edit sheet is open; holds what the sheet starts from.
    var editing by remember { mutableStateOf<TaskDraft?>(null) }

    val alert by viewModel.shortOfMaterial.collectAsState()
    val due by viewModel.dueDeliveries.collectAsState()
    // Non-null while an order is being written down, from the shortage card.
    var ordering by remember { mutableStateOf<ShortItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.conwic_badge),
                        contentDescription = "ConWiC",
                        modifier = Modifier.size(38.dp),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = stringResource(greetingRes(dayPart)), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = quips[quipIndex(today, dayPart, quips.size)],
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item { CrashReportCard() }

        // Asked before the van is loaded, not after: a job with material assigned that the
        // shelf can't cover is the one thing worth interrupting the morning for.
        item {
            if (alert.items.isNotEmpty()) {
                CardAccent(
                    modifier = Modifier
                        .clip(CardShape)
                        .clickable { navController.navigateToTopLevel(Routes.WAREHOUSE) },
                ) {
                    Text(
                        text = stringResource(R.string.home_check_warehouse),
                        style = MaterialTheme.typography.titleLarge,
                        color = OnAccentCard,
                    )
                    Text(
                        text = stringResource(R.string.home_check_warehouse_sub, alert.projects.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnAccentCard.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    // Naming what is short, not just the job: "check the warehouse" on its own
                    // sends you to the shed to work out the same thing again.
                    alert.items.forEach { short ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { ordering = short }
                                .padding(vertical = 6.dp),
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = short.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnAccentCard,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.wh_short_by,
                                        "${formatDecimal(short.short, 2)} ${short.unit}",
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnAccentCard,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                            val dueOn = short.dueOn
                            if (short.onOrder > 0.0 && dueOn != null) {
                                Text(
                                    text = stringResource(
                                        R.string.wh_coming_due,
                                        "${formatDecimal(short.onOrder, 2)} ${short.unit}",
                                        formatDueDate(dueOn),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnAccentCard.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.home_short_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnAccentCard.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item { UpdateBanner(onOpen = { navController.navigateToTopLevel(Routes.SETTINGS) }) }

        item {
            // Three cards of one height, whatever the language does to the words: sized to
            // the tallest of them, so a label that takes two lines in Finnish lifts all three
            // instead of leaving one standing proud.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Each figure opens what it counts — a number on its own only raises the
                // question of where to go and see it. The figure is the figure: the word that
                // used to ride along with it ("1 active") is in the label, where a long
                // translation can wrap without pushing anything out of the card.
                StatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = stringResource(R.string.home_projects),
                    value = "${state.activeProjectCount}",
                    onClick = { navController.navigateToTopLevel(Routes.PROJECTS) },
                )
                StatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = stringResource(R.string.home_open_today),
                    value = "${state.todayTaskCount}",
                    valueColor = MaterialTheme.colorScheme.secondary,
                    onClick = { navController.navigate(Routes.CALENDAR) },
                )
                StatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    label = stringResource(R.string.home_products),
                    value = "${state.productCount}",
                    onClick = { navController.navigateToTopLevel(Routes.PRODUCTS) },
                )
            }
        }

        item {
            CardAccent(modifier = Modifier.clip(CardShape).clickable { navController.navigate(Routes.calculator()) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.home_quick_calculate), style = MaterialTheme.typography.titleLarge, color = OnAccentCard)
                        Text(
                            text = stringResource(R.string.home_quick_calculate_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnAccentCard.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.shiftWeek(-1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.home_prev_week))
                    }
                    Text(
                        text = stringResource(R.string.home_week, state.weekLabel.removePrefix("W")),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { viewModel.shiftWeek(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.home_next_week))
                    }
                    Box(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.home_full_calendar),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.tappableText { navController.navigate(Routes.CALENDAR) },
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    state.week.forEach { day ->
                        WeekDayCell(
                            day = day,
                            onClick = { viewModel.selectDate(day.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(
                        text = if (state.selectedDate == today) {
                            stringResource(R.string.date_today_with_week, formatWeek(today))
                        } else {
                            formatDayWithWeek(state.selectedDate)
                        },
                    )
                    Text(
                        text = stringResource(R.string.action_add_task),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .clip(ChipShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { editing = TaskDraft(dueDate = state.selectedDate) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                if (state.dayTasks.isEmpty()) {
                    CardFlat {
                        Text(
                            text = stringResource(R.string.home_nothing_today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    CardFlat {
                        state.dayTasks.forEachIndexed { index, item ->
                            TaskRow(
                                title = item.task.title,
                                subtitle = item.subtitle.ifBlank { stringResource(R.string.task_none_project) },
                                done = item.task.isDone,
                                priority = item.task.priority,
                                onEdit = { editing = item.task.toDraft() },
                            )
                            if (index != state.dayTasks.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        if (state.overdueTasks.isNotEmpty()) {
            item {
                Column {
                    SectionLabel(text = "Late · ${state.overdueTasks.size}")
                    CardFlat {
                        state.overdueTasks.forEachIndexed { index, item ->
                            TaskRow(
                                title = item.task.title,
                                subtitle = item.task.dueDate
                                    ?.let { stringResource(R.string.home_was_due, item.subtitle.ifBlank { stringResource(R.string.task_none_project) }, formatDueDate(it, today)) }
                                    ?: item.subtitle.ifBlank { stringResource(R.string.task_none_project) },
                                done = item.task.isDone,
                                priority = item.task.priority,
                                onEdit = { editing = item.task.toDraft() },
                            )
                            if (index != state.overdueTasks.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        if (state.undatedTasks.isNotEmpty()) {
            item {
                Column {
                    SectionLabel(text = stringResource(R.string.home_anytime, state.undatedTasks.size))
                    CardFlat {
                        state.undatedTasks.forEachIndexed { index, item ->
                            TaskRow(
                                title = item.task.title,
                                subtitle = item.subtitle.ifBlank { stringResource(R.string.task_none_project) },
                                done = item.task.isDone,
                                priority = item.task.priority,
                                onEdit = { editing = item.task.toDraft() },
                            )
                            if (index != state.undatedTasks.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }

        if (state.recentProducts.isNotEmpty()) {
            item {
                Column {
                    SectionLabel(text = stringResource(R.string.home_recent_products))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recentProducts.forEach { product ->
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { navController.navigate(Routes.productDetail(product.id)) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { draft ->
        TaskEditorSheet(
            draft = draft,
            projects = state.projects,
            onDismiss = { editing = null },
            onSave = { saved ->
                viewModel.saveTask(saved)
                editing = null
            },
            onDelete = draft.id?.let { id ->
                {
                    viewModel.deleteTask(id)
                    editing = null
                }
            },
        )
    }

    ordering?.let { short ->
        OrderDialog(
            productName = short.name,
            packType = short.packType,
            packUnit = short.unit,
            isKnownPack = short.isKnownPack,
            suggestedPacks = short.packsToOrder,
            suggestedAmount = short.stillToOrder,
            onDismiss = { ordering = null },
            onOrder = { packs, amount, expectedOn, note ->
                viewModel.order(short.productId, packs, amount, expectedOn, note)
                ordering = null
            },
        )
    }

    // Asked on the day, one order at a time: the next one comes up as soon as this is answered.
    due.firstOrNull()?.let { delivery ->
        ArrivalDialog(
            productName = delivery.productName,
            line = deliveryLine(delivery),
            onNotYet = { viewModel.postpone(delivery) },
            onArrived = { viewModel.receive(delivery) },
        )
    }
}

/** "2 canister · 50 kg · Fri 25 Sep", for the question asked on the day it was due. */
@Composable
private fun deliveryLine(due: DueDelivery): String {
    val figure = "${formatDecimal(due.amount, 2)} ${due.packUnit}"
    val packs = due.delivery.packs
    val amount = if (packs > 0) {
        stringResource(R.string.wh_packs_and_amount, packs, due.packType, figure)
    } else {
        figure
    }
    return "$amount · ${formatDueDate(due.delivery.expectedOn)}"
}

@Composable
private fun WeekDayCell(day: WeekDayUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = when {
        day.isSelected -> MaterialTheme.colorScheme.primary
        day.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (day.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatShortWeekday(day.date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "${day.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, color = foreground)
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .background(
                        color = if (day.taskCount > 0) {
                            if (day.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
