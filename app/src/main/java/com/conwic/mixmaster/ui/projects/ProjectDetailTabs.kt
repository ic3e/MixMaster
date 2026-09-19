@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.projects

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.photos.PhotoStore
import com.conwic.mixmaster.domain.MixResult
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.domain.formatKg
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.OnAccentCard
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ContentImage
import com.conwic.mixmaster.ui.components.ProgressBarRow
import com.conwic.mixmaster.ui.components.SectionLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.TaskPriority

private val noteTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
fun OverviewTab(data: ProjectDetailData, onAddressClick: () -> Unit) {
    val project = data.project ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            CardFlat {
                InfoRow(label = stringResource(R.string.prj_client), value = project.clientName.ifBlank { "—" })
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAddressClick).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = stringResource(R.string.prj_site_address), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        contentPadding = PaddingValues(20.dp),
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
    roomMixes: Map<Long, MixResult?>,
    isEmployer: Boolean,
    role: Role,
    onAddFloor: (String) -> Unit,
    onAddRoom: (Long, String, Double) -> Unit,
    onAssignProduct: (Long, Long?) -> Unit,
    onAddNote: (String, String, Role) -> Unit,
    onAddPhoto: (String) -> Unit,
    blueprintUri: String?,
    onSetBlueprint: (String) -> Unit,
) {
    var pickerRoom by remember { mutableStateOf<RoomAreaEntity?>(null) }
    var addFloorOpen by remember { mutableStateOf(false) }
    var addRoomForFloor by remember { mutableStateOf<Long?>(null) }
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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (role == Role.WORKER) {
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
                        value = "${formatKg(roomMixes.values.filterNotNull().sumOf { it.totalGrams })} kg",
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(text = stringResource(R.string.prj_floors_rooms))
                if (isEmployer) {
                    Text(text = stringResource(R.string.prj_add_floor), color = MaterialTheme.colorScheme.primary, modifier = Modifier.tappableText { addFloorOpen = true })
                }
            }
        }

        items(data.floors) { floor ->
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = floor.name, style = MaterialTheme.typography.titleMedium)
                    if (isEmployer) {
                        Text(text = stringResource(R.string.prj_add_room), color = MaterialTheme.colorScheme.primary, modifier = Modifier.tappableText { addRoomForFloor = floor.id })
                    }
                }
                data.rooms.filter { it.floorId == floor.id }.forEach { room ->
                    val mix = roomMixes[room.id]
                    val productName = data.products.firstOrNull { it.id == room.assignedProductId }?.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEmployer) { pickerRoom = room }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(text = room.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${formatArea(room.areaM2)} m²" + if (mix != null) " · ${formatKg(mix.totalGrams)} kg" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = productName ?: if (isEmployer) stringResource(R.string.prj_assign) else stringResource(R.string.prj_unassigned),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(text = stringResource(R.string.prj_photos, data.photos.size))
                Text(
                    text = stringResource(R.string.prj_add_photo),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.tappableText {
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
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)),
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
                OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text(stringResource(R.string.prj_add_note)) }, modifier = Modifier.weight(1f))
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
                    Text(text = note.authorName, style = MaterialTheme.typography.titleMedium)
                    Text(text = note.authorRole.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(text = note.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = note.createdAt.atZone(ZoneId.systemDefault()).format(noteTimestampFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }

    if (addFloorOpen) {
        AddFloorSheet(onDismiss = { addFloorOpen = false }, onAdd = { onAddFloor(it); addFloorOpen = false })
    }
    addRoomForFloor?.let { floorId ->
        AddRoomSheet(onDismiss = { addRoomForFloor = null }, onAdd = { name, area -> onAddRoom(floorId, name, area); addRoomForFloor = null })
    }
    pickerRoom?.let { room ->
        ProductPickerSheet(
            products = data.products,
            currentProductId = room.assignedProductId,
            onDismiss = { pickerRoom = null },
            onPick = { productId -> onAssignProduct(room.id, productId); pickerRoom = null },
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
                Text(
                    text = if (blueprintUri == null) stringResource(R.string.prj_attach) else stringResource(R.string.prj_replace),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.tappableText { pickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
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

@Composable
private fun AddFloorSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.prj_add_floor_action), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.prj_floor_name)) }, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text = stringResource(R.string.prj_add_floor_action), onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AddRoomSheet(onDismiss: () -> Unit, onAdd: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.prj_add_room_action), style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.prj_room_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text(stringResource(R.string.prj_room_area)) }, modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                text = stringResource(R.string.prj_add_room_action),
                onClick = { onAdd(name, area.toNumberOrNull() ?: 0.0) },
                enabled = name.isNotBlank() && area.toNumberOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProductPickerSheet(
    products: List<com.conwic.mixmaster.data.db.entity.ProductEntity>,
    currentProductId: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = stringResource(R.string.prj_assign_product), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = stringResource(R.string.prj_no_product),
                modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp),
                color = if (currentProductId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            products.forEach { product ->
                Text(
                    text = "${product.brand} — ${product.name}",
                    modifier = Modifier.fillMaxWidth().clickable { onPick(product.id) }.padding(vertical = 12.dp),
                    color = if (currentProductId == product.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun MaterialsTab(data: ProjectDetailData, roomMixes: Map<Long, MixResult?>) {
    val totalGrams = roomMixes.values.filterNotNull().sumOf { it.totalGrams }
    val loggedRooms = data.rooms.filter { roomMixes[it.id] != null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CardAccent {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = stringResource(R.string.prj_total_used), color = OnAccentCard)
                    Text(text = "${formatKg(totalGrams)} kg", style = MaterialTheme.typography.headlineMedium, color = OnAccentCard, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        item { SectionLabel(text = stringResource(R.string.prj_materials_logged)) }
        if (loggedRooms.isEmpty()) {
            item { Text(text = stringResource(R.string.prj_no_assigned), style = MaterialTheme.typography.bodyMedium) }
        }
        items(loggedRooms) { room ->
            val mix = roomMixes[room.id]!!
            val productName = data.products.firstOrNull { it.id == room.assignedProductId }?.name ?: stringResource(R.string.calc_product)
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = productName, style = MaterialTheme.typography.titleMedium)
                    Text(text = "${formatKg(mix.totalGrams)} kg", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "${room.name} · ${formatArea(room.areaM2)} m² · " + mix.components.joinToString(" / ") { "${formatKg(it.grams)} kg ${it.label}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CalendarTab(data: ProjectDetailData) {
    val sorted = data.tasks.sortedBy { it.dueDate }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (sorted.isEmpty()) {
            item { Text(text = stringResource(R.string.prj_no_scheduled), style = MaterialTheme.typography.bodyMedium) }
        }
        items(sorted) { task ->
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
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
