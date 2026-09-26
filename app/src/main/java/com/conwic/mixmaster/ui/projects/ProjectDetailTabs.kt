@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.projects

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.data.db.entity.FloorEntity
import com.conwic.mixmaster.data.db.entity.NoteEntity
import com.conwic.mixmaster.data.db.entity.PhotoEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.UsedAmount
import com.conwic.mixmaster.data.docs.SheetStore
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.photos.PhotoStore
import com.conwic.mixmaster.data.report.PickupLine
import com.conwic.mixmaster.data.report.PickupList
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.domain.quantityOf
import androidx.compose.ui.text.input.KeyboardType
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.domain.toNumberOr
import com.conwic.mixmaster.domain.BuildUpCoat
import com.conwic.mixmaster.domain.CoatMix
import com.conwic.mixmaster.domain.buildUp
import com.conwic.mixmaster.domain.buildUpMillimetres
import com.conwic.mixmaster.data.db.entity.SolutionEntity
import com.conwic.mixmaster.data.db.entity.coatLabel
import com.conwic.mixmaster.data.db.entity.RoomLayerEntity
import com.conwic.mixmaster.data.db.entity.ProductEntity
import java.time.Instant
import com.conwic.mixmaster.domain.formatDecimal
import com.conwic.mixmaster.domain.RecordedMix
import com.conwic.mixmaster.domain.formatStamp
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.domain.quantityFromGrams
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.BuildUpPanel
import com.conwic.mixmaster.ui.components.BuildUpRow
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.OnAccentCard
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.CardSoft
import com.conwic.mixmaster.ui.components.ContentImage
import com.conwic.mixmaster.ui.components.ProgressBarRow
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.ProductIdentity
import java.time.LocalDate
import java.time.ZoneId
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.tasks.TaskDraft
import com.conwic.mixmaster.ui.tasks.TaskEditorSheet
import com.conwic.mixmaster.ui.tasks.TaskRow
import com.conwic.mixmaster.ui.tasks.priorityColor
import com.conwic.mixmaster.ui.theme.Ok
import androidx.compose.ui.text.style.TextDecoration
import com.conwic.mixmaster.ui.tasks.toDraft
import kotlinx.coroutines.launch
import com.conwic.mixmaster.domain.toNumberOrNull
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.TaskPriority
import com.conwic.mixmaster.ui.components.packCount


@Composable
fun OverviewTab(data: ProjectDetailData, onAddressClick: () -> Unit) {
    val project = data.project ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The tab strip above already leaves its own room underneath, so this opens tight
        // against it rather than adding a second gap on top of the first.
        contentPadding = pagePadding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CardFlat {
                InfoRow(label = stringResource(R.string.prj_client), value = project.clientName.ifBlank { "—" })
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAddressClick).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(modifier = Modifier.weight(1f, fill = false), text = stringResource(R.string.prj_site_address), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(text = project.address.ifBlank { "—" }, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                InfoRow(label = stringResource(R.string.prj_start_date), value = project.startDate?.toString() ?: "—")
                InfoRow(label = stringResource(R.string.prj_target_finish), value = project.targetFinishDate?.toString() ?: "—")
            }
        }
        item {
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(text = stringResource(R.string.prj_progress))
                    Text(text = "${data.progressPercent}%", fontWeight = FontWeight.Bold)
                }
                ProgressBarRow(progressPercent = data.progressPercent)
            }
        }
        item {
            CardFlat {
                SectionLabel(text = stringResource(R.string.prj_scope))
                Text(text = project.scopeNotes.ifBlank { stringResource(R.string.prj_no_scope) }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(modifier = Modifier.weight(1f, fill = false), text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun TasksTab(
    data: ProjectDetailData,
    isEmployer: Boolean,
    onToggle: (Long, Boolean) -> Unit,
    onSave: (TaskDraft) -> Unit,
    onDelete: (Long) -> Unit,
) {
    // Non-null while the add/edit sheet is open; holds what the sheet starts from.
    var editing by remember { mutableStateOf<TaskDraft?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The tab strip above already leaves its own room underneath, so this opens tight
        // against it rather than adding a second gap on top of the first.
        contentPadding = pagePadding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isEmployer) {
            item {
                GhostButton(
                    text = stringResource(R.string.action_add_task),
                    onClick = { editing = TaskDraft(dueDate = LocalDate.now()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(data.tasks.sortedWith(compareBy({ it.isDone }, { it.dueDate }))) { task ->
            CardFlat(modifier = Modifier.fillMaxWidth()) {
                TaskRow(
                    title = task.title,
                    subtitle = task.dueDate?.let { formatDueDate(it) } ?: stringResource(R.string.prj_no_due_date),
                    done = task.isDone,
                    priority = task.priority,
                    onToggle = { onToggle(task.id, !task.isDone) },
                    onEdit = { editing = task.toDraft() },
                )
            }
        }
    }

    editing?.let { draft ->
        TaskEditorSheet(
            draft = draft,
            // The project is fixed here — these tasks belong to the project being looked at.
            projects = emptyList(),
            showProjectPicker = false,
            onDismiss = { editing = null },
            onSave = { saved ->
                onSave(saved)
                editing = null
            },
            onDelete = draft.id?.let { id ->
                {
                    onDelete(id)
                    editing = null
                }
            },
        )
    }
}

@Composable
fun LayoutTab(
    data: ProjectDetailData,
    roomCoats: Map<Long, List<CoatMix>>,
    isEmployer: Boolean,
    role: Role,
    onAddFloor: (String) -> Unit,
    onAddRoom: (Long, String, Double) -> Unit,
    onAddCoat: (Long, Long, Long, Double, Double) -> Unit,
    onRemoveCoat: (RoomLayerEntity) -> Unit,
    /** The rate and the number of passes, put right without taking the coat off. */
    onEditCoat: (RoomLayerEntity, Double, Double) -> Unit,
    onRenameFloor: (FloorEntity, String) -> Unit,
    onRemoveFloor: (FloorEntity) -> Unit,
    onEditRoom: (RoomAreaEntity, String, Double) -> Unit,
    onRemoveRoom: (RoomAreaEntity) -> Unit,
    onRemoveNote: (NoteEntity) -> Unit,
    onRemovePhoto: (PhotoEntity) -> Unit,
    /** Takes one coat of one room into the calculator, with the room's figures. */
    onMixCoat: (RoomAreaEntity, CoatMix) -> Unit,
    onSetCoatColour: (RoomLayerEntity, Long, Double, String, Int) -> Unit,
    onAddNote: (String, String, Role) -> Unit,
    onAddPhoto: (String) -> Unit,
    blueprintUri: String?,
    onSetBlueprint: (String) -> Unit,
) {
    var pickerRoom by remember { mutableStateOf<RoomAreaEntity?>(null) }
    var colourCoat by remember { mutableStateOf<CoatMix?>(null) }
    // Asked before a coat comes off: it takes the room's material booking with it, and the
    // word sits a thumb's width from the rate you were reading.
    var removingCoat by remember { mutableStateOf<CoatMix?>(null) }
    var addFloorOpen by remember { mutableStateOf(false) }
    var addRoomForFloor by remember { mutableStateOf<Long?>(null) }
    // Non-null while one of them is being put right, rather than added.
    var editingFloor by remember { mutableStateOf<FloorEntity?>(null) }
    var editingRoom by remember { mutableStateOf<RoomAreaEntity?>(null) }
    var editingCoat by remember { mutableStateOf<CoatMix?>(null) }
    var removingFloor by remember { mutableStateOf<FloorEntity?>(null) }
    var removingRoom by remember { mutableStateOf<RoomAreaEntity?>(null) }
    var removingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var openPhoto by remember { mutableStateOf<PhotoEntity?>(null) }
    var noteText by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoProblem by remember { mutableStateOf<String?>(null) }
    // Resolved here: the failure is set from inside a coroutine in the picker's callback, which
    // is not composable.
    val photoFailedMessage = stringResource(R.string.prj_photo_copy_failed)

    // The picker's permission on this URI dies with the process, so the bytes are copied into the
    // app before the photo is recorded — otherwise it loads today and never again.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val stored = PhotoStore.keep(context, uri)
                if (stored != null) {
                    photoProblem = null
                    onAddPhoto(stored)
                } else {
                    photoProblem = photoFailedMessage
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The tab strip above already leaves its own room underneath, so this opens tight
        // against it rather than adding a second gap on top of the first.
        contentPadding = pagePadding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Said to whoever cannot change the layout, which in a company is not only the crew.
        if (!isEmployer) {
            item {
                CardFlat {
                    Text(
                        text = stringResource(R.string.prj_worker_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            BlueprintSection(blueprintUri = blueprintUri, isEmployer = isEmployer, onSetBlueprint = onSetBlueprint)
        }

        item {
            CardAccent {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AccentStat(label = stringResource(R.string.prj_total_area), value = "${formatArea(data.totalAreaM2)} m²")
                    AccentStat(label = stringResource(R.string.prj_rooms_floors), value = "${data.rooms.size} · ${data.floors.size}")
                    AccentStat(
                        label = stringResource(R.string.prj_est_material),
                        value = quantityFromGrams(roomCoats.values.flatten().sumOf { it.totalGrams }).text,
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(text = stringResource(R.string.prj_floors_rooms))
                if (isEmployer) {
                    ActionLink(text = stringResource(R.string.prj_add_floor), onClick = { addFloorOpen = true })
                }
            }
        }

        items(data.floors) { floor ->
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    // The name is the way in to putting it right: a floor was add-only, so a
                    // typo on the first morning stood for the life of the job.
                    Text(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(if (isEmployer) Modifier.tappableText { editingFloor = floor } else Modifier),
                        text = floor.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (isEmployer) {
                        ActionLink(text = stringResource(R.string.prj_add_room), onClick = { addRoomForFloor = floor.id })
                    }
                }
                data.rooms.filter { it.floorId == floor.id }.forEach { room ->
                    val coats = roomCoats[room.id].orEmpty()
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            // Same as the floor: tapping the name opens it for a rename or a
                            // re-measure, which is what happens once somebody has been round
                            // the bay with a tape.
                            Text(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .then(if (isEmployer) Modifier.tappableText { editingRoom = room } else Modifier),
                                text = room.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${formatArea(room.areaM2)} m²" +
                                    if (coats.isNotEmpty()) " · ${quantityFromGrams(coats.sumOf { it.totalGrams }).text}" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (coats.isEmpty()) {
                            Text(
                                text = stringResource(R.string.prj_no_coats),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        coats.forEachIndexed { index, coat ->
                            // The coat, then what it is tinted with, then what can be done to
                            // it. The actions used to sit beside the name at label size with a
                            // tap area the width of the word — three of them, a thumb apart.
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(
                                    text = "${index + 1}. ${coat.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.prj_coat_rate,
                                        formatDecimal(coat.doseGramsPerM2, 1),
                                        quantityFromGrams(coat.totalGrams).text,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val colour = coat.colour
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (colour == null) {
                                            stringResource(R.string.prj_no_colour)
                                        } else {
                                            stringResource(
                                                R.string.prj_colour_line,
                                                colour.name,
                                                quantityOf(colour.amount, colour.unit).text,
                                                colour.againstLabel,
                                            )
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (colour == null) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    )
                                    if (isEmployer) {
                                        ActionLink(
                                            text = stringResource(R.string.prj_set_colour),
                                            onClick = { colourCoat = coat },
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Only where there is something to mix: a coat laid as a
                                    // single ready product has no recipe for the calculator to
                                    // work out.
                                    if (coat.layer.solutionId > 0L) {
                                        ActionLink(
                                            text = stringResource(R.string.prj_mix_coat),
                                            onClick = { onMixCoat(room, coat) },
                                        )
                                    }
                                    if (isEmployer) {
                                        ActionLink(
                                            text = stringResource(R.string.action_edit),
                                            onClick = { editingCoat = coat },
                                        )
                                        ActionLink(
                                            text = stringResource(R.string.action_remove),
                                            onClick = { removingCoat = coat },
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                        // The floor as a floor, not as a list. Closed it is one line — the strip
                        // at thumbnail size and the total — and it opens into the coats, drawn to
                        // scale against a millimetre rule.
                        val stack = buildUp(coats, room.areaM2) { it.title }
                        if (stack.isNotEmpty()) {
                            var buildUpOpen by rememberSaveable(room.id) { mutableStateOf(false) }
                            val millimetres = buildUpMillimetres(stack)
                            BuildUpPanel(
                                rows = stack.map { coat ->
                                    BuildUpRow(
                                        number = coat.number,
                                        title = coat.title,
                                        detail = buildUpDetail(coat),
                                        millimetres = coat.millimetres,
                                        weight = coat.weight,
                                        brand = coat.brand,
                                        mmText = coat.millimetres?.let { formatDecimal(it, 2) },
                                    )
                                },
                                summary = pluralStringResource(R.plurals.prj_buildup_coats, stack.size, stack.size),
                                totalText = millimetres?.let {
                                    stringResource(R.string.prj_buildup_total, formatDecimal(it, 2))
                                },
                                // Closed it says what to do with it; open, where the count has
                                // gone from the head of the panel, it carries the count instead.
                                footNote = if (buildUpOpen) {
                                    pluralStringResource(R.plurals.prj_buildup_coats, stack.size, stack.size)
                                } else {
                                    stringResource(R.string.prj_buildup_hint)
                                },
                                totalShort = millimetres?.let {
                                    stringResource(R.string.prj_buildup_mm, formatDecimal(it, 2))
                                },
                                expanded = buildUpOpen,
                                onToggle = { buildUpOpen = !buildUpOpen },
                                openLabel = stringResource(
                                    if (buildUpOpen) R.string.prj_buildup_close else R.string.prj_buildup_open,
                                ),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        if (isEmployer) {
                            ActionLink(
                                text = stringResource(R.string.prj_add_coat),
                                onClick = { pickerRoom = room },
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(text = stringResource(R.string.prj_photos, data.photos.size))
                ActionLink(
                    text = stringResource(R.string.prj_add_photo),
                    onClick = {
                        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
        }
        photoProblem?.let { problem ->
            item {
                Text(
                    text = problem,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (data.photos.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(data.photos) { photo ->
                        ContentImage(
                            uri = photo.uri,
                            targetSize = 84.dp,
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { openPhoto = photo },
                        )
                    }
                }
            }
        }

        item {
            SectionLabel(text = stringResource(R.string.prj_notes, data.notes.size))
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                // A text field is taller than a button; without this they hang off the top edge
                // together and read as misaligned.
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = stringResource(R.string.prj_add_note),
                    singleLine = false,
                    modifier = Modifier.weight(1f),
                )
                // Resolved above the callback — onClick is never composable.
                val employerName = stringResource(R.string.prj_you_employer)
                val workerName = stringResource(R.string.prj_you_worker)
                PrimaryButton(
                    text = stringResource(R.string.prj_post),
                    onClick = {
                        val authorName = if (role == Role.EMPLOYER) employerName else workerName
                        onAddNote(noteText, authorName, role)
                        noteText = ""
                    },
                    enabled = noteText.isNotBlank(),
                )
            }
        }
        items(data.notes) { note ->
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(modifier = Modifier.weight(1f, fill = false), text = note.authorName, style = MaterialTheme.typography.titleMedium)
                    Text(text = note.authorRole.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(text = note.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = formatStamp(note.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // A note was write-only: a wrong one, or one meant for another job, stayed
                    // on the record for good.
                    if (isEmployer) {
                        ActionLink(
                            text = stringResource(R.string.action_remove),
                            onClick = { removingNote = note },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (addFloorOpen) {
        FloorSheet(
            initialName = "",
            onDismiss = { addFloorOpen = false },
            onSave = { onAddFloor(it); addFloorOpen = false },
            onRemove = null,
        )
    }
    addRoomForFloor?.let { floorId ->
        RoomSheet(
            initialName = "",
            initialArea = "",
            onDismiss = { addRoomForFloor = null },
            onSave = { name, area -> onAddRoom(floorId, name, area); addRoomForFloor = null },
            onRemove = null,
        )
    }
    editingFloor?.let { floor ->
        FloorSheet(
            initialName = floor.name,
            onDismiss = { editingFloor = null },
            onSave = { name ->
                onRenameFloor(floor, name)
                editingFloor = null
            },
            onRemove = {
                editingFloor = null
                removingFloor = floor
            },
        )
    }
    editingRoom?.let { room ->
        RoomSheet(
            initialName = room.name,
            initialArea = formatDecimal(room.areaM2, 2),
            onDismiss = { editingRoom = null },
            onSave = { name, area ->
                onEditRoom(room, name, area)
                editingRoom = null
            },
            onRemove = {
                editingRoom = null
                removingRoom = room
            },
        )
    }
    editingCoat?.let { coat ->
        EditCoatSheet(
            coat = coat,
            onDismiss = { editingCoat = null },
            onSave = { dose, quantity ->
                onEditCoat(coat.layer, dose, quantity)
                editingCoat = null
            },
        )
    }
    colourCoat?.let { coat ->
        ColourSheet(
            coat = coat,
            products = data.products,
            onDismiss = { colourCoat = null },
            onClear = {
                onSetCoatColour(coat.layer, 0L, 0.0, "kg", 0)
                colourCoat = null
            },
            onSet = { product, rate, againstIndex ->
                onSetCoatColour(coat.layer, product.id, rate, product.packageUnit, againstIndex)
                colourCoat = null
            },
        )
    }

    pickerRoom?.let { room ->
        CoatPickerSheet(
            solutions = data.solutions,
            products = data.products,
            onDismiss = { pickerRoom = null },
            onPickSolution = { solution ->
                onAddCoat(room.id, solution.id, 0L, 0.0, 1.0)
                pickerRoom = null
            },
            onPickProduct = { product, dose ->
                onAddCoat(room.id, 0L, product.id, dose, 1.0)
                pickerRoom = null
            },
        )
    }

    removingFloor?.let { floor ->
        ConfirmDialog(
            title = stringResource(R.string.prj_remove_floor_confirm),
            message = stringResource(R.string.prj_remove_floor_confirm_body, floor.name),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                removingFloor = null
                onRemoveFloor(floor)
            },
            onDismiss = { removingFloor = null },
        )
    }

    removingRoom?.let { room ->
        ConfirmDialog(
            title = stringResource(R.string.prj_remove_room_confirm),
            message = stringResource(R.string.prj_remove_room_confirm_body, room.name),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                removingRoom = null
                onRemoveRoom(room)
            },
            onDismiss = { removingRoom = null },
        )
    }

    removingNote?.let { note ->
        ConfirmDialog(
            title = stringResource(R.string.prj_remove_note_confirm),
            message = stringResource(R.string.prj_remove_note_confirm_body),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                removingNote = null
                onRemoveNote(note)
            },
            onDismiss = { removingNote = null },
        )
    }

    // Full size, because a site photo is taken to be looked at rather than thumbed past.
    openPhoto?.let { photo ->
        PhotoViewer(
            photo = photo,
            canRemove = isEmployer,
            onRemove = {
                onRemovePhoto(photo)
                openPhoto = null
            },
            onDismiss = { openPhoto = null },
        )
    }

    removingCoat?.let { coat ->
        ConfirmDialog(
            title = stringResource(R.string.coat_remove_confirm),
            message = stringResource(R.string.coat_remove_confirm_body, coat.title),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                removingCoat = null
                onRemoveCoat(coat.layer)
            },
            onDismiss = { removingCoat = null },
        )
    }
}

@Composable
private fun BlueprintSection(blueprintUri: String?, isEmployer: Boolean, onSetBlueprint: (String) -> Unit) {
    val context = LocalContext.current
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onSetBlueprint(uri.toString())
        }
    }

    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionLabel(text = stringResource(R.string.prj_blueprint))
            if (isEmployer) {
                ActionLink(
                    text = if (blueprintUri == null) stringResource(R.string.prj_attach) else stringResource(R.string.prj_replace),
                    onClick = { pickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                )
            }
        }
        if (blueprintUri != null) {
            CardFlat(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(CardShape).clickable {
                    val uri = Uri.parse(blueprintUri)
                    val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        // NEW_TASK because the context here is the locale wrapper, not the
                        // activity, and startActivity throws without it.
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                },
            ) {
                Text(text = stringResource(R.string.prj_view_blueprint), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            CardFlat(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = if (isEmployer) stringResource(R.string.prj_no_blueprint_employer) else stringResource(R.string.prj_no_blueprint_worker),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccentStat(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = OnAccentCard.copy(alpha = 0.85f))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = OnAccentCard, fontWeight = FontWeight.Bold)
    }
}

/**
 * A floor, being added or being put right.
 *
 * Both at once because they are the same three fields, and a floor was add-only: a name typed
 * wrong on the first morning stood for the life of the job.
 */
@Composable
private fun FloorSheet(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val isNew = initialName.isBlank()
    var name by remember { mutableStateOf(initialName) }
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
            SheetHeader(
                title = stringResource(if (isNew) R.string.prj_add_floor_action else R.string.prj_edit_floor),
                onRemove = onRemove,
                removeDescription = stringResource(R.string.prj_remove_floor),
            )
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.prj_floor_name),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(if (isNew) R.string.prj_add_floor_action else R.string.action_save),
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A room, being added or re-measured — a bay gets paced out again once somebody has a tape on it. */
@Composable
private fun RoomSheet(
    initialName: String,
    initialArea: String,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val isNew = initialName.isBlank()
    var name by remember { mutableStateOf(initialName) }
    var area by remember { mutableStateOf(initialArea) }
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
            SheetHeader(
                title = stringResource(if (isNew) R.string.prj_add_room_action else R.string.prj_edit_room),
                onRemove = onRemove,
                removeDescription = stringResource(R.string.prj_remove_room),
            )
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.prj_room_name),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = area,
                onValueChange = { area = it },
                label = stringResource(R.string.prj_room_area),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(if (isNew) R.string.prj_add_room_action else R.string.action_save),
                onClick = { onSave(name, area.toNumberOrNull() ?: 0.0) },
                enabled = name.isNotBlank() && area.toNumberOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * What a coat is laid at: the rate, and how many passes of it.
 *
 * A coat could be added and taken off but not corrected, so a rate typed as 300 instead of 3000
 * meant removing it and starting again — and with it the colour that had been set on it.
 */
@Composable
private fun EditCoatSheet(
    coat: CoatMix,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit,
) {
    var dose by remember { mutableStateOf(formatDecimal(coat.doseGramsPerM2, 2)) }
    var quantity by remember { mutableStateOf(formatDecimal(coat.layer.quantity, 2)) }
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
            Text(text = stringResource(R.string.prj_edit_coat), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = coat.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormTextField(
                value = dose,
                onValueChange = { dose = it },
                label = stringResource(R.string.prj_coat_coverage),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = stringResource(R.string.calc_coats),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { onSave(dose.toNumberOr(0.0), quantity.toNumberOr(1.0)) },
                enabled = dose.toNumberOrNull() != null && quantity.toNumberOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One site photo, full width, with the date it was taken and a way to take it off.
 *
 * The strip on the Layout tab is thumbnails: a crack or a colour reference read at 84 dp is no
 * use to anybody, and there was nothing to tap.
 */
@Composable
private fun PhotoViewer(
    photo: PhotoEntity,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ContentImage(
                uri = photo.uri,
                targetSize = 320.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = formatStamp(photo.takenAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canRemove) {
                    ActionLink(
                        text = stringResource(R.string.action_remove),
                        onClick = { confirming = true },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.prj_remove_photo_confirm),
            message = stringResource(R.string.prj_remove_photo_confirm_body),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                confirming = false
                onRemove()
            },
            onDismiss = { confirming = false },
        )
    }
}

/** A sheet's title with the way out of it — kept away from the save button, as everywhere else. */
@Composable
private fun SheetHeader(title: String, onRemove: (() -> Unit)?, removeDescription: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = removeDescription,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Which colour this coat is tinted with, and at what rate.
 *
 * Asked on the room because that is where it is decided: the same topping goes down ocra in one
 * bay and grey in the next. The rate says which part it is measured against, so a pigment given
 * as "28 g per kg of polymer" stays that and is not quietly re-based on the whole batch.
 */
@Composable
private fun ColourSheet(
    coat: CoatMix,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onSet: (ProductEntity, Double, Int) -> Unit,
) {
    var chosen by remember { mutableStateOf(products.firstOrNull { it.id == coat.layer.colourProductId }) }
    var rate by remember {
        mutableStateOf(
            if (coat.layer.colourAmountPerKg > 0.0) formatDecimal(coat.layer.colourAmountPerKg * 1000, 1) else "",
        )
    }
    var againstIndex by remember { mutableStateOf(coat.layer.colourAgainstIndex) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(text = stringResource(R.string.prj_set_colour), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = coat.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            DropdownField(
                label = stringResource(R.string.prj_colour),
                selected = chosen?.name ?: stringResource(R.string.prj_no_colour),
                options = products.map { it.name },
                onSelect = { name -> chosen = products.firstOrNull { it.name == name } },
                modifier = Modifier.fillMaxWidth(),
            )
            val partLabels = coat.parts.map { it.label }
            if (partLabels.size > 1) {
                DropdownField(
                    label = stringResource(R.string.calc_measured_against),
                    selected = partLabels.getOrNull(againstIndex) ?: partLabels.first(),
                    options = partLabels,
                    onSelect = { label -> againstIndex = partLabels.indexOf(label).coerceAtLeast(0) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            FormTextField(
                value = rate,
                onValueChange = { rate = it },
                label = stringResource(
                    R.string.prj_colour_rate,
                    if (chosen?.packageUnit == "L") "ml" else "g",
                    partLabels.getOrNull(againstIndex) ?: "",
                ),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton(
                    text = stringResource(R.string.prj_no_colour),
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = {
                        // Typed in grams per kilo, held in kilos per kilo, the way every other
                        // rate in the app is.
                        chosen?.let { onSet(it, rate.toNumberOr(0.0) / 1000.0, againstIndex) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CoatPickerSheet(
    solutions: List<SolutionEntity>,
    products: List<ProductEntity>,
    onDismiss: () -> Unit,
    onPickSolution: (SolutionEntity) -> Unit,
    onPickProduct: (ProductEntity, Double) -> Unit,
) {
    var productFor by remember { mutableStateOf<ProductEntity?>(null) }
    var dose by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            val chosen = productFor
            if (chosen == null) {
                Text(
                    text = stringResource(R.string.prj_add_coat),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.prj_coat_pick_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                SectionLabel(text = stringResource(R.string.prj_solutions))
                // Every coat, not every mix: a room takes architop's first coat and then its
                // second, and they are different recipes at different rates.
                solutions.forEach { solution ->
                    ProductIdentity(
                        name = solution.coatLabel,
                        brand = solution.brand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickSolution(solution) }
                            .padding(vertical = 10.dp),
                    )
                }
                SectionLabel(text = stringResource(R.string.prj_straight_from_the_tin))
                products.forEach { product ->
                    ProductIdentity(
                        name = product.name,
                        brand = product.brand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { productFor = product; dose = "" }
                            .padding(vertical = 10.dp),
                    )
                }
            } else {
                // A product laid as it comes has no recipe to take a coverage from, so it has
                // to be said here.
                Text(text = chosen.name, style = MaterialTheme.typography.headlineMedium)
                FormTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = stringResource(R.string.prj_coat_coverage),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.padding(top = 12.dp),
                )
                PrimaryButton(
                    text = stringResource(R.string.prj_add_coat),
                    onClick = { onPickProduct(chosen, dose.toNumberOr(0.0)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
fun MaterialsTab(
    data: ProjectDetailData,
    roomCoats: Map<Long, List<CoatMix>>,
    materials: List<ProjectMaterial>,
    /** What the job needs that is found on site rather than taken from the shed. */
    siteMaterials: List<ProjectMaterial>,
    /** What has actually been mixed on this job, newest first. */
    mixes: List<RecordedMix>,
    isEmployer: Boolean,
    onRemoveMix: (Long) -> Unit,
    onTakeOutOfStock: () -> Unit,
) {
    val totalGrams = roomCoats.values.flatten().sumOf { it.totalGrams }
    val laidRooms = data.rooms.filter { roomCoats[it.id].orEmpty().isNotEmpty() }
    var pickupOpen by remember { mutableStateOf(false) }
    var removingMix by remember { mutableStateOf<RecordedMix?>(null) }
    var sheetsOpen by remember { mutableStateOf(false) }
    // The paperwork behind what is going on this floor: the safety and technical sheets of
    // every product the job books, whether they are links or files carried in the app.
    val jobSheets = remember(materials, data.products) {
        val used = materials.map { it.productId }.toSet()
        data.products
            .filter { it.id in used }
            .flatMap { product ->
                listOfNotNull(
                    product.safetySheetUrl.takeIf { it.isNotBlank() }
                        ?.let { JobSheet(product.name, R.string.product_safety_sheet, it) },
                    product.technicalSheetUrl.takeIf { it.isNotBlank() }
                        ?.let { JobSheet(product.name, R.string.product_technical_sheet, it) },
                )
            }
    }
    // Every receipt added up per material. By product where there is one, so the same powder
    // mixed under two recipes is one line.
    val mixedTotals = remember(mixes) {
        val totals = linkedMapOf<String, UsedAmount>()
        mixes.flatMap { it.parts }.forEach { part ->
            val key = if (part.productId > 0L) "p${part.productId}" else "l${part.label}"
            val running = totals[key]
            totals[key] = part.copy(grams = (running?.grams ?: 0.0) + part.grams)
        }
        totals.values.sortedByDescending { it.grams }
    }
    val mixedGrams = mixedTotals.sumOf { it.grams }
    // Taken here rather than inside the sheet: printing has to be asked for from the screen's
    // own context, and a sheet hands out the dialog window it lives in.
    val screenContext = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The tab strip above already leaves its own room underneath, so this opens tight
        // against it rather than adding a second gap on top of the first.
        contentPadding = pagePadding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CardAccent {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(modifier = Modifier.weight(1f, fill = false), text = stringResource(R.string.prj_total_used), color = OnAccentCard)
                    Text(text = quantityFromGrams(totalGrams).text, style = MaterialTheme.typography.headlineMedium, color = OnAccentCard, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        // What the plan says is above; this is what has gone down. Kept next to it because the
        // question on site is the difference between the two.
        item { SectionLabel(text = stringResource(R.string.prj_mixed_so_far)) }
        if (mixes.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.prj_no_mixes_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                // Set back into the page, because this half of the tab is a record of what has
                // happened. What is still to be fetched from the shed is on white below.
                CardSoft {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = pluralStringResource(R.plurals.prj_mixes_count, mixes.size, mixes.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = quantityFromGrams(mixedGrams).text,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    mixedTotals.forEach { part ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                modifier = Modifier.weight(1f, fill = false),
                                text = part.label,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = quantityFromGrams(part.grams).text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            items(mixes, key = { it.id }) { mix ->
                CardSoft {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = mix.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = quantityFromGrams(mix.totalGrams).text,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (mix.jobLabel.isNotBlank()) {
                        Text(
                            text = mix.jobLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = stringResource(
                                R.string.prj_mix_stamp,
                                formatStamp(Instant.ofEpochMilli(mix.mixedAt)),
                                mix.batches,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isEmployer) {
                            ActionLink(
                                text = stringResource(R.string.action_remove),
                                onClick = { removingMix = mix },
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.prj_from_warehouse)) }

        if (materials.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.prj_no_materials_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(materials) { material ->
            // A red edge on the ones the shed cannot cover, nothing on the ones it can: the
            // question this list is scrolled for is which lines need an order, and it was
            // answered by reading three rows of every card down to the last figure.
            CardFlat(
                edge = if (material.shortfall > 0.0) MaterialTheme.colorScheme.error else null,
            ) {
                ProductIdentity(
                    name = material.name,
                    brand = material.stock.brand,
                    modifier = Modifier.fillMaxWidth(),
                )
                MaterialRow(
                    label = stringResource(R.string.prj_need),
                    value = packAmountText(
                        packs = material.packsToTake,
                        packType = material.stock.packType,
                        amount = material.need,
                        unit = material.stock.packUnit,
                    ),
                )
                MaterialRow(
                    label = stringResource(R.string.prj_in_stock),
                    value = packAmountText(
                        packs = material.packsAvailable,
                        packType = material.stock.packType,
                        amount = material.available,
                        unit = material.stock.packUnit,
                    ),
                )
                val packs = material.packsToOrder
                MaterialRow(
                    label = stringResource(R.string.prj_to_order),
                    value = when {
                        material.shortfall <= 0.0 -> stringResource(R.string.prj_nothing_to_order)
                        packs != null -> packCount(packs, material.stock.packType)
                        else -> "${formatDecimal(material.shortfall, 2)} ${material.stock.packUnit}"
                    },
                    strong = material.shortfall > 0.0,
                )
            }
        }

        if (materials.isNotEmpty()) {
            item {
                GhostButton(
                    text = stringResource(R.string.prj_pickup_list),
                    onClick = { pickupOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                val issuedAt = data.project?.materialsIssuedAt
                if (issuedAt == null) {
                    PrimaryButton(
                        text = stringResource(R.string.prj_take_out_of_stock),
                        onClick = onTakeOutOfStock,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.prj_materials_taken,
                            formatDueDate(Instant.ofEpochMilli(issuedAt).atZone(ZoneId.systemDefault()).toLocalDate()),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Water and anything else that comes from the client's side: how much the job needs, so
        // it can be asked for before the day, but never measured against the shed.
        if (siteMaterials.isNotEmpty()) {
            item { SectionLabel(text = stringResource(R.string.prj_on_site), modifier = Modifier.padding(top = 6.dp)) }
            item {
                CardSoft {
                    Text(
                        text = stringResource(R.string.prj_on_site_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    siteMaterials.forEach { material ->
                        MaterialRow(
                            label = material.name,
                            value = "${formatDecimal(material.need, 1)} ${material.stock.packUnit}",
                        )
                    }
                }
            }
        }

        // What a client asks for when the floor is down, and what the crew wants before it goes
        // down. Kept on the job rather than left to be hunted for product by product.
        item { SectionLabel(text = stringResource(R.string.prj_sheets)) }
        item {
            CardFlat {
                Text(
                    text = if (jobSheets.isEmpty()) {
                        stringResource(R.string.prj_sheets_none)
                    } else {
                        pluralStringResource(R.plurals.prj_sheets_count, jobSheets.size, jobSheets.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (jobSheets.isNotEmpty()) {
                    ActionLink(
                        text = stringResource(R.string.prj_sheets_pick),
                        onClick = { sheetsOpen = true },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item { SectionLabel(text = stringResource(R.string.prj_materials_logged)) }
        if (laidRooms.isEmpty()) {
            item { Text(text = stringResource(R.string.prj_no_assigned), style = MaterialTheme.typography.bodyMedium) }
        }
        items(laidRooms) { room ->
            val coats = roomCoats[room.id].orEmpty()
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(modifier = Modifier.weight(1f, fill = false), text = room.name, style = MaterialTheme.typography.titleMedium)
                    Text(text = quantityFromGrams(coats.sumOf { it.totalGrams }).text, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "${formatArea(room.areaM2)} m²",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                coats.forEachIndexed { index, coat ->
                    Text(
                        text = "${index + 1}. ${coat.title} · " +
                            coat.result.components.joinToString(" / ") { "${quantityFromGrams(it.grams).text} ${it.label}" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }

    removingMix?.let { mix ->
        ConfirmDialog(
            title = stringResource(R.string.prj_mix_remove_confirm),
            message = stringResource(
                R.string.prj_mix_remove_confirm_body,
                mix.title,
                quantityFromGrams(mix.totalGrams).text,
            ),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                removingMix = null
                onRemoveMix(mix.id)
            },
            onDismiss = { removingMix = null },
        )
    }

    if (sheetsOpen) {
        JobSheetsSheet(
            projectName = data.project?.name.orEmpty(),
            sheets = jobSheets,
            screenContext = screenContext,
            onDismiss = { sheetsOpen = false },
        )
    }

    if (pickupOpen) {
        PickupSheet(
            projectName = data.project?.name.orEmpty(),
            materials = materials,
            screenContext = screenContext,
            onDismiss = { pickupOpen = false },
        )
    }
}

/** One sheet behind one product on the job. */
data class JobSheet(val productName: String, @StringRes val kindRes: Int, val value: String)

/**
 * Picks which safety and technical sheets go out, and sends them.
 *
 * A client asks for "the safety sheets", not for one — and it is never all of them either, so
 * they are ticked. Files go as attachments; links go as text in the same message, because a
 * sheet held as a link cannot be attached to anything.
 */
@Composable
private fun JobSheetsSheet(
    projectName: String,
    sheets: List<JobSheet>,
    screenContext: android.content.Context,
    onDismiss: () -> Unit,
) {
    // Everything ticked to begin with: asking for all of them is the common case, and clearing
    // two boxes is less work than ticking six.
    val picked = remember(sheets) { mutableStateListOf<Int>().apply { addAll(sheets.indices) } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = stringResource(R.string.prj_sheets_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.prj_sheets_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            sheets.forEachIndexed { index, sheet ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (index in picked) picked.remove(index) else picked.add(index)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = index in picked,
                        onCheckedChange = {
                            if (index in picked) picked.remove(index) else picked.add(index)
                        },
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(text = sheet.productName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(sheet.kindRes) + " · " +
                                if (SheetStore.isStored(sheet.value)) {
                                    stringResource(R.string.prj_sheets_file)
                                } else {
                                    stringResource(R.string.prj_sheets_link)
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ActionLink(
                        text = stringResource(R.string.action_open),
                        onClick = { SheetStore.open(screenContext, sheet.value) },
                    )
                }
            }
            PrimaryButton(
                text = stringResource(R.string.prj_sheets_send),
                onClick = {
                    val chosen = picked.sorted().mapNotNull { sheets.getOrNull(it) }
                    sheetsShareIntent(screenContext, projectName, chosen)?.let { intent ->
                        runCatching { screenContext.startActivity(intent) }
                    }
                    onDismiss()
                },
                enabled = picked.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/**
 * The message the sheets go out in.
 *
 * Files are attached and links are written into the body, so one send carries both — a client
 * gets a single email with the PDFs the app holds and the addresses of the ones it doesn't.
 */
private fun sheetsShareIntent(
    context: android.content.Context,
    projectName: String,
    sheets: List<JobSheet>,
): Intent? {
    if (sheets.isEmpty()) return null
    val files = ArrayList<Uri>()
    val lines = mutableListOf<String>()
    sheets.forEach { sheet ->
        val kind = context.getString(sheet.kindRes)
        if (SheetStore.isStored(sheet.value)) {
            SheetStore.shareable(context, sheet.value)?.let { files.add(it) }
                ?: lines.add("${sheet.productName} — $kind")
        } else {
            lines.add("${sheet.productName} — $kind: ${sheet.value}")
        }
    }
    val subject = context.getString(R.string.prj_sheets_subject, projectName)
    val body = lines.joinToString("\n")
    return when {
        files.isEmpty() -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "application/pdf"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, files)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.let { Intent.createChooser(it, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}

/**
 * What to load out of the shed for this job.
 *
 * Packs, not kilos: the van is loaded in bags and canisters, and 1446.67 kg is not something
 * anyone can act on standing in front of a pallet. The exact figure stays beside it, because
 * that is what the mix will actually take, and the list can be sent to whoever is loading.
 */
@Composable
private fun PickupSheet(
    projectName: String,
    materials: List<ProjectMaterial>,
    screenContext: android.content.Context,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
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
            Text(text = stringResource(R.string.prj_pickup_list), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = stringResource(R.string.prj_pickup_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$projectName · ${formatDueDate(LocalDate.now())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (materials.isEmpty()) {
                Text(
                    text = stringResource(R.string.prj_pickup_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            materials.forEach { material ->
                CardFlat {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = material.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        Text(
                            text = packAmountText(
                                packs = material.packsToTake,
                                packType = material.stock.packType,
                                amount = material.need,
                                unit = material.stock.packUnit,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    // Said here as well as on the tab behind: whoever is loading the van is the
                    // one who finds out the shelf cannot cover it.
                    if (material.shortfall > 0.0) {
                        Text(
                            text = stringResource(
                                R.string.wh_short_by,
                                "${formatDecimal(material.shortfall, 2)} ${material.stock.packUnit}",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            if (materials.isNotEmpty()) {
                PrimaryButton(
                    text = stringResource(R.string.prj_pickup_share),
                    onClick = { context.startActivity(pickupShareIntent(context, projectName, materials)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Android's own print dialog, which is also where "Save as PDF" lives — so the
                // same sheet goes to the office printer or into a folder.
                GhostButton(
                    text = stringResource(R.string.prj_pickup_print),
                    onClick = {
                        PickupList.print(
                            context = screenContext,
                            title = context.getString(R.string.prj_pickup_header, projectName),
                            subtitle = formatDueDate(LocalDate.now()),
                            lines = pickupLines(context, materials),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The list as rows of name, figure and shortfall — what the printed sheet is set out from. */
private fun pickupLines(context: android.content.Context, materials: List<ProjectMaterial>): List<PickupLine> =
    materials.map { material ->
        val figure = "${formatDecimal(material.need, 2)} ${material.stock.packUnit}"
        val packs = material.packsToTake
        PickupLine(
            name = material.name,
            amount = if (packs != null && packs > 0) {
                context.getString(R.string.wh_packs_and_amount, packCount(context, packs.toDouble(), material.stock.packType), figure)
            } else {
                figure
            },
            short = if (material.shortfall > 0.0) {
                context.getString(
                    R.string.wh_short_by,
                    "${formatDecimal(material.shortfall, 2)} ${material.stock.packUnit}",
                )
            } else {
                null
            },
        )
    }

/** The same list as plain text, for the chooser — WhatsApp to the yard, or a note to self. */
private fun pickupShareIntent(
    context: android.content.Context,
    projectName: String,
    materials: List<ProjectMaterial>,
): Intent {
    val title = context.getString(R.string.prj_pickup_header, projectName)
    val body = buildString {
        appendLine(title)
        appendLine(formatDueDate(LocalDate.now()))
        appendLine()
        materials.forEach { material ->
            val figure = "${formatDecimal(material.need, 2)} ${material.stock.packUnit}"
            val packs = material.packsToTake
            appendLine(
                if (packs != null && packs > 0) {
                    "${material.name}: " +
                        context.getString(
                            R.string.wh_packs_and_amount,
                            packCount(context, packs.toDouble(), material.stock.packType),
                            figure,
                        )
                } else {
                    "${material.name}: $figure"
                },
            )
            if (material.shortfall > 0.0) {
                appendLine(
                    "  " + context.getString(
                        R.string.wh_short_by,
                        "${formatDecimal(material.shortfall, 2)} ${material.stock.packUnit}",
                    ),
                )
            }
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** "2 canisters · 43.4 kg", or the amount on its own when nobody has said what a pack holds. */
@Composable
private fun packAmountText(packs: Int?, packType: String, amount: Double, unit: String): String {
    val figure = "${formatDecimal(amount, 2)} $unit"
    return if (packs != null && packs > 0) {
        stringResource(R.string.wh_packs_and_amount, packCount(packs, packType), figure)
    } else {
        figure
    }
}

@Composable
fun CalendarTab(data: ProjectDetailData) {
    val sorted = data.tasks.sortedBy { it.dueDate }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // The tab strip above already leaves its own room underneath, so this opens tight
        // against it rather than adding a second gap on top of the first.
        contentPadding = pagePadding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (sorted.isEmpty()) {
            item { Text(text = stringResource(R.string.prj_no_scheduled), style = MaterialTheme.typography.bodyMedium) }
        }
        items(sorted) { task ->
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = task.dueDate?.let { formatDueDate(it) } ?: stringResource(R.string.task_no_date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // This used to print the priority whatever had happened to the task, so a
                    // task closed off in the Tasks tab still read "LOW" here.
                    Text(
                        text = if (task.isDone) {
                            stringResource(R.string.task_done)
                        } else {
                            // The enum constant would read "LOW" in every language.
                            stringResource(
                                when (task.priority) {
                                    TaskPriority.HIGH -> R.string.priority_high
                                    TaskPriority.MEDIUM -> R.string.priority_medium
                                    TaskPriority.LOW -> R.string.priority_low
                                    TaskPriority.DONE -> R.string.task_done
                                },
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.isDone) Ok else priorityColor(task.priority),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                )
            }
        }
    }
}


/** A label and its figure, for the warehouse lines on the materials tab. */
@Composable
private fun MaterialRow(label: String, value: String, strong: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(modifier = Modifier.weight(1f, fill = false), text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (strong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Bold,
        )
    }
}

/**
 * The line under a coat's name in the build-up: how much goes on, and what it is tinted with.
 *
 * The thickness is not in here — it has a column of its own, where it lines up down the stack.
 */
@Composable
private fun buildUpDetail(coat: BuildUpCoat): String {
    val rate = stringResource(R.string.prj_buildup_rate, formatDecimal(coat.gramsPerM2, 0))
    val colour = coat.colourName ?: return rate
    return stringResource(R.string.prj_buildup_tinted, rate, colour)
}
