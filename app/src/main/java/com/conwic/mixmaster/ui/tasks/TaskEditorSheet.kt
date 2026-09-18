@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.model.TaskPriority
import com.conwic.mixmaster.domain.formatDay
import com.conwic.mixmaster.domain.formatWeek
import com.conwic.mixmaster.domain.parseDueDate
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Everything the editor needs to show, and hands back on save. */
data class TaskDraft(
    val id: Long? = null,
    val title: String = "",
    val dueDate: LocalDate? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val projectId: Long? = null,
    val isDone: Boolean = false,
)

/** A project as the picker needs it — just enough to choose one by name. */
data class ProjectOption(val id: Long, val name: String)

fun TaskEntity.toDraft(): TaskDraft = TaskDraft(
    id = id,
    title = title,
    dueDate = dueDate,
    priority = priority,
    projectId = projectId,
    isDone = isDone,
)

fun TaskDraft.toEntity(): TaskEntity = TaskEntity(
    id = id ?: 0L,
    projectId = projectId,
    title = title.trim(),
    dueDate = dueDate,
    priority = priority,
    isDone = isDone,
)

private const val NoProject = "No project"

/**
 * The one sheet used to add and to edit a task, wherever tasks appear.
 *
 * Adding has to be quick above all else: the title field takes focus straight away, the due
 * date is pre-filled with whichever day the list was showing, and everything else already has
 * a sensible value — so a new task is type-the-title-and-tap.
 */
@Composable
fun TaskEditorSheet(
    draft: TaskDraft,
    projects: List<ProjectOption>,
    onDismiss: () -> Unit,
    onSave: (TaskDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
    showProjectPicker: Boolean = true,
) {
    val isNew = draft.id == null
    var title by remember { mutableStateOf(draft.title) }
    var dueText by remember { mutableStateOf(draft.dueDate?.toString().orEmpty()) }
    var priority by remember { mutableStateOf(draft.priority) }
    var projectId by remember { mutableStateOf(draft.projectId) }
    var isDone by remember { mutableStateOf(draft.isDone) }

    val today = remember { LocalDate.now() }
    val parsedDue = remember(dueText) { parseDueDate(dueText, today) }
    val dueIsBroken = dueText.isNotBlank() && parsedDue == null

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isNew) {
            // The sheet animates in, so the field isn't attached on the first frame — and a
            // request against a detached requester throws.
            runCatching { focusRequester.requestFocus() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Opens at full height: half-height would leave Save below the fold, and the whole point
        // is that adding a task takes one glance.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isNew) "New task" else "Edit task",
                style = MaterialTheme.typography.headlineMedium,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What needs doing") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )

            SectionLabel(text = "Due")
            ChipRow(
                options = buildList {
                    add(
                        ChipOption(
                            label = "Today",
                            selected = parsedDue == today,
                            onClick = { dueText = today.toString() },
                        ),
                    )
                    add(
                        ChipOption(
                            label = "Tomorrow",
                            selected = parsedDue == today.plusDays(1),
                            onClick = { dueText = today.plusDays(1).toString() },
                        ),
                    )
                    val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    add(
                        ChipOption(
                            label = "Next Mon",
                            selected = parsedDue == nextMonday,
                            onClick = { dueText = nextMonday.toString() },
                        ),
                    )
                    add(
                        ChipOption(
                            label = "No date",
                            selected = dueText.isBlank(),
                            onClick = { dueText = "" },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dueText,
                onValueChange = { dueText = it },
                label = { Text("Or type a date") },
                isError = dueIsBroken,
                singleLine = true,
                supportingText = {
                    Text(
                        text = when {
                            dueIsBroken -> "Can't read that date — try 18.09.2026"
                            parsedDue != null -> "${formatDay(parsedDue)} · ${formatWeek(parsedDue)}"
                            else -> "e.g. 18.09.2026, 18.09 or 2026-09-18"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(text = "Priority")
            ChipRow(
                options = listOf(TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW).map { option ->
                    ChipOption(
                        label = option.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = option == priority,
                        onClick = { priority = option },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (showProjectPicker) {
                DropdownField(
                    label = "Project",
                    selected = projects.firstOrNull { it.id == projectId }?.name ?: NoProject,
                    options = listOf(NoProject) + projects.map { it.name },
                    onSelect = { name ->
                        projectId = projects.firstOrNull { it.name == name }?.id
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!isNew) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Done", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = isDone, onCheckedChange = { isDone = it })
                }
            }

            PrimaryButton(
                text = if (isNew) "Add task" else "Save",
                onClick = {
                    onSave(
                        draft.copy(
                            title = title.trim(),
                            dueDate = parsedDue,
                            priority = priority,
                            projectId = projectId,
                            isDone = isDone,
                        ),
                    )
                },
                enabled = title.isNotBlank() && !dueIsBroken,
                modifier = Modifier.fillMaxWidth(),
            )

            if (onDelete != null) {
                GhostButton(text = "Delete task", onClick = onDelete, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
