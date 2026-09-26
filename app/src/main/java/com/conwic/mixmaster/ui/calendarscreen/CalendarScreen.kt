package com.conwic.mixmaster.ui.calendarscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.tasks.TaskDraft
import com.conwic.mixmaster.ui.tasks.TaskEditorSheet
import com.conwic.mixmaster.ui.tasks.TaskRow
import com.conwic.mixmaster.ui.tasks.toDraft
import com.conwic.mixmaster.ui.theme.ChipShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import com.conwic.mixmaster.domain.formatDayWithWeek
import com.conwic.mixmaster.domain.formatMonthYear
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.shortWeekdayNames
import androidx.compose.material3.HorizontalDivider
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.domain.formatDateRange
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.projects.labelRes
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.company.rememberAccess

@Composable
fun CalendarScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory { initializer { CalendarViewModel(container.projectRepository) } },
    )
    val state by viewModel.uiState.collectAsState()

    // Non-null while the add/edit sheet is open; holds what the sheet starts from.
    var editing by remember { mutableStateOf<TaskDraft?>(null) }
    // Tasks are site work: without that right they are read here, not added or changed.
    val access = rememberAccess()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Calendar isn't a bottom-nav destination, so the nav bar is hidden here — without
            // this there is no way back to Home except the system gesture.
            MixMasterTopBar(title = stringResource(R.string.calendar_title), onBack = { navController.popBackStack() })
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousMonth) { Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.calendar_prev_month)) }
                Text(text = formatMonthYear(state.currentMonth), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = viewModel::nextMonth) { Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.calendar_next_month)) }
            }
        }

        item {
            CardFlat {
                // A plain Column of Rows rather than a LazyVerticalGrid: a lazy grid nested in a
                // lazy column needs a fixed height, and the height it had cut the last week of
                // longer months off the bottom.
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.calendar_week_abbrev),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(30.dp),
                    )
                    shortWeekdayNames().forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                state.weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = week.weekNumber.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(30.dp),
                        )
                        week.days.forEach { cell ->
                            DayCell(
                                cell = cell,
                                onClick = { cell.date?.let(viewModel::selectDate) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                // Which colour is which job, for reading the month without tapping every day.
                if (state.monthProjects.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ProjectLegend(
                        projects = state.monthProjects,
                        onOpen = { id -> navController.navigate(Routes.projectDetail(id)) },
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(text = formatDayWithWeek(state.selectedDate))
                if (access.site) Text(
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
        }

        // The jobs on the go that day, above its tasks: tapped from a bar, this is what says
        // whose bar it was.
        items(state.selectedDayProjects, key = { "project-${it.id}" }) { project ->
            CardFlat(
                modifier = Modifier
                    .clip(CardShape)
                    .clickable { navController.navigate(Routes.projectDetail(project.id)) },
                edge = projectColour(project.colour, project.status == ProjectStatus.COMPLETED),
            ) {
                Text(text = project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = stringResource(
                        R.string.calendar_project_line,
                        formatDateRange(project.start, project.end),
                        stringResource(project.status.labelRes()),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.selectedDayTasks.isEmpty() && state.selectedDayProjects.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.calendar_empty_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.selectedDayTasks) { item ->
            CardFlat {
                TaskRow(
                    title = item.task.title,
                    subtitle = item.subtitle.ifBlank { stringResource(R.string.task_none_project) },
                    done = item.task.isDone,
                    priority = item.task.priority,
                    onEdit = if (access.site) {
                        { editing = item.task.toDraft() }
                    } else {
                        null
                    },
                )
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
private fun DayCell(cell: CalendarCell, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = when {
        cell.isToday -> MaterialTheme.colorScheme.primary
        cell.isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val content = if (cell.isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    // The cell itself has no side padding, so a project's bar runs on into the next day's and
    // reads as one line across the week. The circle round the date keeps its own room.
    Column(
        modifier = modifier.tappableText(enabled = cell.date != null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .padding(2.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = cell.date?.dayOfMonth?.toString().orEmpty(),
                color = content,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (cell.taskCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .background(
                            if (cell.isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                            CircleShape,
                        ),
                )
            }
        }
        ProjectBars(cell.bars)
    }
}
