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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.model.TaskPriority
import com.conwic.mixmaster.domain.MixResult
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.domain.formatDay
import com.conwic.mixmaster.domain.formatKg
import com.conwic.mixmaster.domain.formatWeek
import com.conwic.mixmaster.domain.parseDueDate
import com.conwic.mixmaster.ui.components.CardAccent
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ContentImage
import com.conwic.mixmaster.ui.components.DropdownField
import com.conwic.mixmaster.ui.components.ProgressBarRow
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.theme.ChipShape
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.GhostButton

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
                InfoRow(label = "Client", value = project.clientName.ifBlank { "—" })
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAddressClick).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Site address", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(text = project.address.ifBlank { "—" }, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                InfoRow(label = "Start date", value = project.startDate?.toString() ?: "—")
                InfoRow(label = "Target finish", value = project.targetFinishDate?.toString() ?: "—")
            }
        }
        item {
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel(text = "Progress")
                    Text(text = "${data.progressPercent}%", fontWeight = FontWeight.Bold)
                }
                ProgressBarRow(progressPercent = data.progressPercent)
            }
        }
        item {
            CardFlat {
                SectionLabel(text = "Scope")
                Text(text = project.scopeNotes.ifBlank { "No scope notes yet." }, style = MaterialTheme.typography.bodyMedium)
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
    onAdd: (String, LocalDate?, TaskPriority) -> Unit,
) {
    var addSheetOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isEmployer) {
            item {
                GhostButton(text = "+ Add task", onClick = { addSheetOpen = true }, modifier = Modifier.fillMaxWidth())
            }
        }
        items(data.tasks.sortedBy { it.dueDate }) { task ->
            CardFlat(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(CardShape).clickable { onToggle(task.id, !task.isDone) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(text = task.dueDate?.let { "${formatDay(it)} · ${formatWeek(it)}" } ?: "No due date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(text = if (task.isDone) "Done" else task.priority.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    if (addSheetOpen) {
        AddTaskSheet(onDismiss = { addSheetOpen = false }, onAdd = { title, due, priority -> onAdd(title, due, priority); addSheetOpen = false })
    }
}

@Composable
private fun AddTaskSheet(onDismiss: () -> Unit, onAdd: (String, LocalDate?, TaskPriority) -> Unit) {
    var title by remember { mutableStateOf("") }
    var dueText by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    val today = remember { LocalDate.now() }
    val parsedDue = remember(dueText) { parseDueDate(dueText, today) }
    val dueIsBroken = dueText.isNotBlank() && parsedDue == null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Add task", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(
                value = dueText,
                onValueChange = { dueText = it },
                label = { Text("Due date (optional)") },
                isError = dueIsBroken,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Today" to today,
                    "Tomorrow" to today.plusDays(1),
                    "Next Mon" to today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                ).forEach { (label, date) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(ChipShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { dueText = date.toString() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            DropdownField(
                label = "Priority",
                selected = priority.name,
                options = listOf(TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW).map { it.name },
                onSelect = { name -> priority = TaskPriority.valueOf(name) },
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = "Add task",
                onClick = { onAdd(title, parsedDue, priority) },
                enabled = title.isNotBlank() && !dueIsBroken,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onAddPhoto(it.toString()) }
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
                        text = "Viewing as Worker — rooms, areas and product assignments are set up by your office. You can still add photos and notes below.",
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
                    AccentStat(label = "Total area", value = "${formatArea(data.totalAreaM2)} m²")
                    AccentStat(label = "Rooms · floors", value = "${data.rooms.size} · ${data.floors.size}")
                    AccentStat(
                        label = "Est. material",
                        value = "${formatKg(roomMixes.values.filterNotNull().sumOf { it.totalGrams })} kg",
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(text = "Floors & rooms")
                if (isEmployer) {
                    Text(text = "+ Add floor", color = MaterialTheme.colorScheme.primary, modifier = Modifier.tappableText { addFloorOpen = true })
                }
            }
        }

        items(data.floors) { floor ->
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = floor.name, style = MaterialTheme.typography.titleMedium)
                    if (isEmployer) {
                        Text(text = "+ Add room", color = MaterialTheme.colorScheme.primary, modifier = Modifier.tappableText { addRoomForFloor = floor.id })
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
                            text = productName ?: if (isEmployer) "Assign ›" else "Unassigned",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel(text = "Photos · ${data.photos.size}")
                Text(
                    text = "+ Add photo",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.tappableText {
                        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
        }
        if (data.photos.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(data.photos) { photo ->
                        ContentImage(
                            uri = photo.uri,
                            modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
        }

        item {
            SectionLabel(text = "Notes · ${data.notes.size}")
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Add a note") }, modifier = Modifier.weight(1f))
                PrimaryButton(
                    text = "Post",
                    onClick = {
                        val authorName = if (role == Role.EMPLOYER) "You (Employer)" else "You (Worker)"
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
            SectionLabel(text = "Blueprint")
            if (isEmployer) {
                Text(
                    text = if (blueprintUri == null) "+ Attach" else "Replace",
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
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                },
            ) {
                Text(text = "View blueprint ↗", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            CardFlat(modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    text = if (isEmployer) "No blueprint attached yet. Attach a floor plan image or PDF." else "No blueprint attached yet.",
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
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddFloorSheet(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Add floor", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Floor name") }, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text = "Add floor", onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AddRoomSheet(onDismiss: () -> Unit, onAdd: (String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Add room", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Room name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Area (m²)") }, modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                text = "Add room",
                onClick = { onAdd(name, area.toDoubleOrNull() ?: 0.0) },
                enabled = name.isNotBlank() && area.toDoubleOrNull() != null,
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
            Text(text = "Assign a product", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = "No product",
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
                    Text(text = "Total material used", color = MaterialTheme.colorScheme.onPrimary)
                    Text(text = "${formatKg(totalGrams)} kg", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        item { SectionLabel(text = "Materials logged") }
        if (loggedRooms.isEmpty()) {
            item { Text(text = "No rooms have an assigned product yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(loggedRooms) { room ->
            val mix = roomMixes[room.id]!!
            val productName = data.products.firstOrNull { it.id == room.assignedProductId }?.name ?: "Product"
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
            item { Text(text = "No scheduled tasks yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(sorted) { task ->
            CardFlat {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = task.dueDate?.let { "${formatDay(it)} · ${formatWeek(it)}" } ?: "No date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = task.priority.name, style = MaterialTheme.typography.labelSmall)
                }
                Text(text = task.title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
