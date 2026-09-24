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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.conwic.mixmaster.data.report.PickupLine
import com.conwic.mixmaster.data.report.PickupList
import com.conwic.mixmaster.domain.MixResult
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.ui.components.DropdownField
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
import com.conwic.mixmaster.domain.formatDueDate
import com.conwic.mixmaster.domain.quantityFromGrams
import com.conwic.mixmaster.ui.components.BuildUpPanel
import com.conwic.mixmaster.ui.components.BuildUpRow
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.ConfirmDialog
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
import androidx.compose.ui.res.pluralStringResource
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
    roomCoats: Map<Long, List<CoatMix>>,
    isEmployer: Boolean,
    role: Role,
    onAddFloor: (String) -> Unit,
    onAddRoom: (Long, String, Double) -> Unit,
    onAddCoat: (Long, Long, Long, Double, Double) -> Unit,
    onRemoveCoat: (RoomLayerEntity) -> Unit,
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
                        value = quantityFromGrams(roomCoats.values.flatten().sumOf { it.totalGrams }).text,
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
                    Text(modifier = Modifier.weight(1f, fill = false), text = floor.name, style = MaterialTheme.typography.titleMedium)
                    if (isEmployer) {
                        Text(text = stringResource(R.string.prj_add_room), color = MaterialTheme.colorScheme.primary, modifier = Modifier.tappableText { addRoomForFloor = floor.id })
                    }
                }
                data.rooms.filter { it.floorId == floor.id }.forEach { room ->
                    val coats = roomCoats[room.id].orEmpty()
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(modifier = Modifier.weight(1f, fill = false), text = room.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
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
                                }
                                if (isEmployer) {
                                    Text(
                                        text = stringResource(R.string.action_remove),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.tappableText { removingCoat = coat },
                                    )
                                }
                            }
                            val colour = coat.colour
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
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
                                    modifier = Modifier.weight(1f),
                                )
                                if (isEmployer) {
                                    Text(
                                        text = stringResource(R.string.prj_set_colour),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.tappableText { colourCoat = coat },
                                    )
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
                                expanded = buildUpOpen,
                                onToggle = { buildUpOpen = !buildUpOpen },
                                openLabel = stringResource(
                                    if (buildUpOpen) R.string.prj_buildup_close else R.string.prj_buildup_open,
                                ),
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                        if (isEmployer) {
                            Text(
                                text = stringResource(R.string.prj_add_coat),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp).tappableText { pickerRoom = room },
                            )
                        }
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
                    Text(modifier = Modifier.weight(1f, fill = false), text = note.authorName, style = MaterialTheme.typography.titleMedium)
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    Text(
                        text = if (solution.brand.isBlank()) {
                            solution.coatLabel
                        } else {
                            "${solution.brand} — ${solution.coatLabel}"
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onPickSolution(solution) }.padding(vertical = 12.dp),
                    )
                }
                SectionLabel(text = stringResource(R.string.prj_straight_from_the_tin))
                products.forEach { product ->
                    Text(
                        text = if (product.brand.isBlank()) product.name else "${product.brand} — ${product.name}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { productFor = product; dose = "" }
                            .padding(vertical = 12.dp),
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
    onTakeOutOfStock: () -> Unit,
) {
    val totalGrams = roomCoats.values.flatten().sumOf { it.totalGrams }
    val laidRooms = data.rooms.filter { roomCoats[it.id].orEmpty().isNotEmpty() }
    var pickupOpen by remember { mutableStateOf(false) }
    // Taken here rather than inside the sheet: printing has to be asked for from the screen's
    // own context, and a sheet hands out the dialog window it lives in.
    val screenContext = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
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
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(modifier = Modifier.weight(1f, fill = false), text = material.name, style = MaterialTheme.typography.titleMedium)
                    if (material.stock.brand.isNotBlank()) {
                        Text(
                            text = material.stock.brand,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
                        packs != null -> stringResource(R.string.wh_order_packs, packs, material.stock.packType)
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

    if (pickupOpen) {
        PickupSheet(
            projectName = data.project?.name.orEmpty(),
            materials = materials,
            screenContext = screenContext,
            onDismiss = { pickupOpen = false },
        )
    }
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
                context.getString(R.string.wh_packs_and_amount, packs, material.stock.packType, figure)
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
                        context.getString(R.string.wh_packs_and_amount, packs, material.stock.packType, figure)
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

/** "2 canister · 43.4 kg", or the amount on its own when nobody has said what a pack holds. */
@Composable
private fun packAmountText(packs: Int?, packType: String, amount: Double, unit: String): String {
    val figure = "${formatDecimal(amount, 2)} $unit"
    return if (packs != null && packs > 0) {
        stringResource(R.string.wh_packs_and_amount, packs, packType, figure)
    } else {
        figure
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
