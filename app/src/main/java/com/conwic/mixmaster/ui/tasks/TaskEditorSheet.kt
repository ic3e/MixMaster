@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.tasks

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.data.model.TaskPriority
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.FieldLabel
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.PickerField
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.ui.components.formFieldColors
import com.conwic.mixmaster.ui.theme.FieldShape

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

// Resolved in the UI so it follows the language.
private val NoProjectRes = R.string.task_none_project

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
    var dueDate by remember { mutableStateOf(draft.dueDate) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf(draft.priority) }
    var projectId by remember { mutableStateOf(draft.projectId) }
    var isDone by remember { mutableStateOf(draft.isDone) }
    var confirmDelete by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }

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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (isNew) R.string.task_new else R.string.task_edit),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                // Up here rather than under Save: a full-width delete at the bottom of a sheet
                // is exactly where a thumb lands when it means to save.
                if (onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.task_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            // Not a FormTextField: the sheet opens with the keyboard already up on this one,
            // and the focus request has to land on the field itself rather than on a column
            // around it.
            FieldLabel(text = stringResource(R.string.task_what))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = FieldShape,
                colors = formFieldColors(),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )

            SectionLabel(text = stringResource(R.string.task_due))
            ChipRow(
                options = buildList {
                    add(
                        ChipOption(
                            label = stringResource(R.string.task_today),
                            selected = dueDate == today,
                            onClick = { dueDate = today },
                        ),
                    )
                    add(
                        ChipOption(
                            label = stringResource(R.string.task_tomorrow),
                            selected = dueDate == today.plusDays(1),
                            onClick = { dueDate = today.plusDays(1) },
                        ),
                    )
                    val nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    add(
                        ChipOption(
                            label = stringResource(R.string.task_next_monday),
                            selected = dueDate == nextMonday,
                            onClick = { dueDate = nextMonday },
                        ),
                    )
                    add(
                        ChipOption(
                            label = stringResource(R.string.task_no_date),
                            selected = dueDate == null,
                            onClick = { dueDate = null },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            PickerField(
                label = stringResource(R.string.task_pick_day),
                value = dueDate?.let { formatDueDate(it, today) } ?: stringResource(R.string.task_no_date),
                onClick = { datePickerOpen = true },
                icon = Icons.Filled.CalendarMonth,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(text = stringResource(R.string.task_priority))
            ChipRow(
                options = listOf(
                    TaskPriority.HIGH to R.string.priority_high,
                    TaskPriority.MEDIUM to R.string.priority_medium,
                    TaskPriority.LOW to R.string.priority_low,
                ).map { (option, labelRes) ->
                    ChipOption(
                        label = stringResource(labelRes),
                        selected = option == priority,
                        onClick = { priority = option },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (showProjectPicker) {
                DropdownField(
                    label = stringResource(R.string.task_project),
                    selected = projects.firstOrNull { it.id == projectId }?.name ?: stringResource(NoProjectRes),
                    options = listOf(stringResource(NoProjectRes)) + projects.map { it.name },
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
                    Text(text = stringResource(R.string.task_done), style = MaterialTheme.typography.titleMedium)
                    Switch(checked = isDone, onCheckedChange = { isDone = it })
                }
            }

            PrimaryButton(
                text = stringResource(if (isNew) R.string.task_add else R.string.task_save),
                onClick = {
                    onSave(
                        draft.copy(
                            title = title.trim(),
                            dueDate = dueDate,
                            priority = priority,
                            projectId = projectId,
                            isDone = isDone,
                        ),
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

        }
    }

    if (datePickerOpen) {
        // The picker works in UTC millis, so the date goes in and comes back out at UTC midnight
        // rather than through the device's zone, where it can land a day either side.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (dueDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            dueDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        datePickerOpen = false
                    },
                ) { Text(stringResource(R.string.task_set_date)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dueDate = null
                        datePickerOpen = false
                    },
                ) { Text(stringResource(R.string.task_no_date)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.task_delete_confirm),
            message = stringResource(R.string.task_delete_confirm_body, title.ifBlank { draft.title }),
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
