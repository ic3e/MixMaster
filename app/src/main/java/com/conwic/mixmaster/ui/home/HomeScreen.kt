package com.conwic.mixmaster.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
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
import com.conwic.mixmaster.ui.tasks.TaskDraft
import com.conwic.mixmaster.ui.tasks.TaskEditorSheet
import com.conwic.mixmaster.ui.tasks.TaskRow
import com.conwic.mixmaster.ui.tasks.toDraft
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.ChipShape
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.conwic.mixmaster.ui.settings.UpdateBanner

@Composable
fun HomeScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.projectRepository, container.productRepository) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val now = remember(state) { LocalDateTime.now() }
    val today = now.toLocalDate()
    val dayPart = dayPartFor(now.toLocalTime())

    // Non-null while the add/edit sheet is open; holds what the sheet starts from.
    var editing by remember { mutableStateOf<TaskDraft?>(null) }

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
                        Text(text = greetingFor(dayPart), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = quipFor(today, dayPart),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item { UpdateBanner(onOpen = { navController.navigateToTopLevel(Routes.SETTINGS) }) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(modifier = Modifier.weight(1f), label = "Projects", value = "${state.activeProjectCount} active")
                StatCard(modifier = Modifier.weight(1f), label = "Open today", value = "${state.todayTaskCount}", valueColor = MaterialTheme.colorScheme.secondary)
                StatCard(modifier = Modifier.weight(1f), label = "Products", value = "${state.productCount}")
            }
        }

        item {
            CardAccent(modifier = Modifier.clip(CardShape).clickable { navController.navigateToTopLevel(Routes.CALCULATOR) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Quick calculate", style = MaterialTheme.typography.titleLarge, color = OnAccentCard)
                        Text(
                            text = "Get an exact mix split in seconds",
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
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week")
                    }
                    Text(
                        text = "Week ${state.weekLabel.removePrefix("W")}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { viewModel.shiftWeek(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next week")
                    }
                    Box(modifier = Modifier.weight(1f))
                    Text(
                        text = "Full calendar",
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
                        text = "+ Add task",
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
                            text = "Nothing on this day. Tap + Add task.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    CardFlat {
                        state.dayTasks.forEachIndexed { index, item ->
                            TaskRow(
                                title = item.task.title,
                                subtitle = item.subtitle,
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
                                    ?.let { "${item.subtitle} · was due ${formatDueDate(it, today)}" }
                                    ?: item.subtitle,
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
                    SectionLabel(text = "Anytime · ${state.undatedTasks.size}")
                    CardFlat {
                        state.undatedTasks.forEachIndexed { index, item ->
                            TaskRow(
                                title = item.task.title,
                                subtitle = item.subtitle,
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
                    SectionLabel(text = "Recently added products")
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
