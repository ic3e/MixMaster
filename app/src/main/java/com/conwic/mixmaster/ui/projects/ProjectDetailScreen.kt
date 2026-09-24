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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.SegmentedTabs
import com.conwic.mixmaster.ui.components.PrimaryButton
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
            onDismiss = { editSheetOpen = false },
            onSave = { name, client, address, scope ->
                viewModel.updateDetails(name, client, address, scope, project.startDate, project.targetFinishDate)
                editSheetOpen = false
            },
        )
    }

    generatedReportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { generatedReportUri = null },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                    generatedReportUri = null
                }) { Text(stringResource(R.string.action_open)) }
            },
            dismissButton = { TextButton(onClick = { generatedReportUri = null }) { Text(stringResource(R.string.action_close)) } },
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
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var client by remember { mutableStateOf(initialClient) }
    var address by remember { mutableStateOf(initialAddress) }
    var scope by remember { mutableStateOf(initialScope) }

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
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.project_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = client, onValueChange = { client = it }, label = { Text(stringResource(R.string.prj_client)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(stringResource(R.string.prj_site_address)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = scope, onValueChange = { scope = it }, label = { Text(stringResource(R.string.prj_scope)) }, modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                text = stringResource(R.string.prj_save),
                onClick = { onSave(name, client, address, scope) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
