@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.projects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.domain.formatDueDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.data.report.ReportGenerator
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.SegmentedTabs
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.navigation.Routes
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R

// Resource ids: this list is built at class load, before any language is known.
private val tabTitleRes = listOf(
    R.string.tab_overview,
    R.string.tab_tasks,
    R.string.tab_layout,
    R.string.tab_materials,
    R.string.tab_calendar,
)

@Composable
fun ProjectDetailScreen(navController: NavHostController, projectId: Long) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: ProjectDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProjectDetailViewModel(
                    container.appContext,
                    container.projectRepository,
                    container.productRepository,
                    container.solutionRepository,
                    container.stockRepository,
                    projectId,
                )
            }
        },
    )
    val data by viewModel.data.collectAsState()
    val roomCoats by viewModel.roomCoats.collectAsState()
    val materials by viewModel.materials.collectAsState()
    val recordedMixes by viewModel.recordedMixes.collectAsState()
    val role by container.userPrefs.role.collectAsState(initial = Role.EMPLOYER)
    val project = data.project ?: return

    var selectedTab by remember { mutableStateOf(0) }
    var overflowOpen by remember { mutableStateOf(false) }
    var addressSheetOpen by remember { mutableStateOf(false) }
    var editSheetOpen by remember { mutableStateOf(false) }
    var generatedReportUri by remember { mutableStateOf<Uri?>(null) }
    var confirmArchive by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                MixMasterTopBar(
                    title = project.name,
                    onBack = { navController.popBackStack() },
                    actions = {
                        if (role == Role.EMPLOYER) {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.prj_generate_report)) },
                                    onClick = {
                                        overflowOpen = false
                                        generatedReportUri = ReportGenerator.generate(
                                            context = context,
                                            project = project,
                                            rooms = data.rooms,
                                            roomCoats = roomCoats,
                                            products = data.products,
                                            tasks = data.tasks,
                                            mixes = recordedMixes,
                                        )
                                    },
                                )
                                DropdownMenuItem(text = { Text(stringResource(R.string.prj_edit)) }, onClick = { overflowOpen = false; editSheetOpen = true })
                                DropdownMenuItem(text = { Text(stringResource(R.string.prj_archive)) }, onClick = { overflowOpen = false; confirmArchive = true })
                            }
                        }
                    },
                )
                SegmentedTabs(
                    titles = tabTitleRes.map { stringResource(it) },
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        },
    ) { insets ->
        Column(modifier = Modifier.fillMaxSize().padding(insets)) {
            when (selectedTab) {
                0 -> OverviewTab(data = data, onAddressClick = { addressSheetOpen = true })
                1 -> TasksTab(
                    data = data,
                    isEmployer = role == Role.EMPLOYER,
                    onToggle = viewModel::setTaskDone,
                    onSave = viewModel::saveTask,
                    onDelete = viewModel::deleteTask,
                )
                2 -> LayoutTab(
                    data = data,
                    roomCoats = roomCoats,
                    isEmployer = role == Role.EMPLOYER,
                    role = role,
                    onAddFloor = viewModel::addFloor,
                    onAddRoom = viewModel::addRoom,
                    onAddCoat = viewModel::addCoat,
                    onRemoveCoat = viewModel::removeCoat,
                    onEditCoat = viewModel::updateCoat,
                    onRenameFloor = viewModel::renameFloor,
                    onRemoveFloor = viewModel::removeFloor,
                    onEditRoom = viewModel::updateRoom,
                    onRemoveRoom = viewModel::removeRoom,
                    onRemoveNote = viewModel::removeNote,
                    onRemovePhoto = viewModel::removePhoto,
                    onMixCoat = { room, coat ->
                        // Straight into the calculator on that coat, with the room's area, the
                        // rate it is specified at and the number of passes already in. Nothing
                        // is written back: the calculator is where the batch gets worked out,
                        // the project is where the spec lives.
                        navController.navigate(
                            Routes.calculatorForCoat(
                                solutionId = coat.layer.solutionId,
                                areaM2 = room.areaM2,
                                doseGramsPerM2 = coat.doseGramsPerM2,
                                quantity = coat.layer.quantity,
                                jobLabel = "${project.name} · ${room.name}",
                                projectId = project.id,
                                roomId = room.id,
                                layerId = coat.layer.id,
                            ),
                        )
                    },
                    onSetCoatColour = viewModel::setCoatColour,
                    onAddNote = viewModel::addNote,
                    onAddPhoto = { uri -> viewModel.addPhoto(uri, roomId = null, caption = "") },
                    blueprintUri = data.project?.blueprintUri,
                    onSetBlueprint = viewModel::setBlueprintUri,
                )
                3 -> MaterialsTab(
                    data = data,
                    roomCoats = roomCoats,
                    materials = materials,
                    mixes = recordedMixes,
                    isEmployer = role == Role.EMPLOYER,
                    onRemoveMix = viewModel::removeRecordedMix,
                    onTakeOutOfStock = { viewModel.takeMaterialsOutOfStock() },
                )
                4 -> CalendarTab(data = data)
            }
        }
    }

    if (addressSheetOpen) {
        AddressActionSheet(
            address = project.address,
            onDismiss = { addressSheetOpen = false },
            context = context,
        )
    }

    if (editSheetOpen) {
        EditProjectSheet(
            initialName = project.name,
            initialClient = project.clientName,
            initialAddress = project.address,
            initialScope = project.scopeNotes,
            initialStatus = project.status,
            initialStart = project.startDate,
            initialTarget = project.targetFinishDate,
            onDismiss = { editSheetOpen = false },
            onSave = { name, client, address, scope, status, start, target ->
                viewModel.updateDetails(name, client, address, scope, start, target, status)
                editSheetOpen = false
            },
        )
    }

    generatedReportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { generatedReportUri = null },
            confirmButton = {
                // Sending it is what the report is for. It could only be opened, which left
                // everyone digging the file out of the app's folder to get it to a client.
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, project.name)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    generatedReportUri = null
                }) { Text(stringResource(R.string.prj_report_share)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                    generatedReportUri = null
                }) { Text(stringResource(R.string.action_open)) }
            },
            title = { Text(stringResource(R.string.prj_report_generated)) },
            text = { Text(stringResource(R.string.prj_report_saved, project.name)) },
        )
    }

    if (confirmArchive) {
        ConfirmDialog(
            title = stringResource(R.string.prj_archive_confirm),
            message = stringResource(R.string.prj_archive_confirm_body, project.name),
            confirmText = stringResource(R.string.prj_archive),
            onConfirm = {
                confirmArchive = false
                viewModel.archive { navController.popBackStack() }
            },
            onDismiss = { confirmArchive = false },
        )
    }
}

@Composable
private fun AddressActionSheet(address: String, onDismiss: () -> Unit, context: Context) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Resolved out here: the clipboard label is set inside a click handler, which is
            // never a composable.
            val addressLabel = stringResource(R.string.prj_site_address)
            Text(text = address, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                    onDismiss()
                }.padding(vertical = 12.dp),
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text = stringResource(R.string.prj_get_directions), modifier = Modifier.padding(start = 12.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(addressLabel, address))
                    onDismiss()
                }.padding(vertical = 12.dp),
            ) {
                Text(text = stringResource(R.string.prj_copy_address), modifier = Modifier.padding(start = 36.dp))
            }
        }
    }
}

@Composable
private fun EditProjectSheet(
    initialName: String,
    initialClient: String,
    initialAddress: String,
    initialScope: String,
    initialStatus: ProjectStatus,
    initialStart: LocalDate?,
    initialTarget: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, ProjectStatus, LocalDate?, LocalDate?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var client by remember { mutableStateOf(initialClient) }
    var address by remember { mutableStateOf(initialAddress) }
    var scope by remember { mutableStateOf(initialScope) }
    // Where the job has got to. It was fixed at "planning" from the moment a project was made,
    // with nothing anywhere to move it on — which left the filters on the list, and the count
    // on the home screen, reading something that could never come true.
    var status by remember { mutableStateOf(initialStatus) }
    var startDate by remember { mutableStateOf(initialStart) }
    var targetDate by remember { mutableStateOf(initialTarget) }
    // Which of the two dates the picker is open for, or null when it is closed.
    var picking by remember { mutableStateOf<DateField?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Lifts the sheet clear of the keyboard and lets it scroll under it: without
                // this the last field and the save button sit behind the keys.
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.prj_edit), style = MaterialTheme.typography.headlineMedium)
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.project_name),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = client,
                onValueChange = { client = it },
                label = stringResource(R.string.prj_client),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = address,
                onValueChange = { address = it },
                label = stringResource(R.string.prj_site_address),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = scope,
                onValueChange = { scope = it },
                label = stringResource(R.string.prj_scope),
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(text = stringResource(R.string.prj_status))
            ChipRow(
                options = ProjectStatus.entries.map { option ->
                    ChipOption(
                        label = stringResource(option.labelRes()),
                        selected = option == status,
                        onClick = { status = option },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(text = stringResource(R.string.prj_dates))
            DateRow(
                label = stringResource(R.string.prj_start_date),
                date = startDate,
                onPick = { picking = DateField.START },
                onClear = { startDate = null },
            )
            DateRow(
                label = stringResource(R.string.prj_target_finish),
                date = targetDate,
                onPick = { picking = DateField.TARGET },
                onClear = { targetDate = null },
            )

            PrimaryButton(
                text = stringResource(R.string.prj_save),
                onClick = { onSave(name, client, address, scope, status, startDate, targetDate) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    picking?.let { field ->
        val current = if (field == DateField.START) startDate else targetDate
        // The picker works in UTC millis, so the date goes in and comes back out at UTC midnight
        // rather than through the device's zone, where it can land a day either side.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (current ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            if (field == DateField.START) startDate = picked else targetDate = picked
                        }
                        picking = null
                    },
                ) { Text(stringResource(R.string.task_set_date)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Which date the one picker is standing in for. */
private enum class DateField { START, TARGET }

/** A date on the project, with a way to clear it — both are optional on a job. */
@Composable
private fun DateRow(label: String, date: LocalDate?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = date?.let { formatDueDate(it) } ?: stringResource(R.string.task_no_date),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.tappableText(onClick = onPick),
            )
            if (date != null) {
                Text(
                    text = stringResource(R.string.action_clear),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp).tappableText(onClick = onClear),
                )
            }
        }
    }
}
