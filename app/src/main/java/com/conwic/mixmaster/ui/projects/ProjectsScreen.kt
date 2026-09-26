package com.conwic.mixmaster.ui.projects

import com.conwic.mixmaster.ui.components.LocalBarInset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.ProgressBarRow
import com.conwic.mixmaster.ui.navigation.Routes
import androidx.compose.ui.draw.clip
import com.conwic.mixmaster.ui.theme.CardShape
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.model.ProjectStatus
import com.conwic.mixmaster.ui.company.rememberAccess

@Composable
fun ProjectsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: ProjectsViewModel = viewModel(
        factory = viewModelFactory { initializer { ProjectsViewModel(container.projectRepository) } },
    )
    val state by viewModel.uiState.collectAsState()
    val archived by viewModel.archived.collectAsState()
    var newProjectOpen by remember { mutableStateOf(false) }
    // Closed until asked for: what is archived is archived, and it should not sit between the
    // crew and the jobs they are on.
    var archivedOpen by remember { mutableStateOf(false) }

    // Starting a job, and bringing one back, are the plans' to change.
    val access = rememberAccess()

    Scaffold(
        floatingActionButton = {
            if (access.projects) FloatingActionButton(
                onClick = { newProjectOpen = true },
                // Flat like the rest of the design — the default FAB shadow reads as a smudge here.
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                ),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                // Above the tab bar: the page goes on under it, and so would the +.
                modifier = Modifier.padding(bottom = LocalBarInset.current),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.projects_new))
            }
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            // Extra room at the bottom so the floating + doesn't sit on top of the last card.
            contentPadding = pagePadding(bottom = 96.dp + LocalBarInset.current),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(text = stringResource(R.string.projects_title), style = MaterialTheme.typography.headlineMedium) }

            item {
                ChipRow(
                    // null is "All"; the rest come from the enum, so the chip shown and the
                    // value filtered on can't drift apart when the language changes.
                    options = (listOf(null) + ProjectStatus.entries).map { option ->
                        ChipOption(
                            label = stringResource(option?.labelRes() ?: R.string.status_all),
                            selected = option == state.filter,
                            onClick = { viewModel.setFilter(option) },
                        )
                    },
                )
            }

            items(state.visibleProjects) { item ->
                val project = item.project
                CardFlat(
                    modifier = Modifier.fillMaxWidth().clip(CardShape).clickable { navController.navigate(Routes.projectDetail(project.id)) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                        )
                        Text(text = stringResource(project.status.labelRes()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    // Client and site are optional, so only the parts that exist get printed —
                    // an empty project used to show a lone "·".
                    val subtitle = listOf(project.clientName, project.address)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProgressBarRow(progressPercent = item.progressPercent, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // The way back. Archiving used to be one-way: the list hides an archived job and
            // nothing anywhere else listed one, so a mis-tap took the rooms, the coats and the
            // receipts with it.
            if (archived.isNotEmpty()) {
                item {
                    ActionLink(
                        text = stringResource(
                            if (archivedOpen) R.string.projects_archived_hide else R.string.projects_archived_show,
                            archived.size,
                        ),
                        onClick = { archivedOpen = !archivedOpen },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (archivedOpen) {
                items(archived) { project ->
                    CardFlat(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(text = project.name, style = MaterialTheme.typography.titleMedium)
                                if (project.clientName.isNotBlank()) {
                                    Text(
                                        text = project.clientName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (access.projects) {
                                ActionLink(
                                    text = stringResource(R.string.projects_restore),
                                    onClick = { viewModel.unarchive(project) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (newProjectOpen) {
        NewProjectSheet(
            onDismiss = { newProjectOpen = false },
            onCreate = { name, client, address ->
                viewModel.createProject(name, client, address) { id ->
                    newProjectOpen = false
                    navController.navigate(Routes.projectDetail(id))
                }
            },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectSheet(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text(text = stringResource(R.string.projects_new), style = MaterialTheme.typography.headlineMedium)
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.project_name),
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = client,
                onValueChange = { client = it },
                label = stringResource(R.string.project_client_optional),
                modifier = Modifier.fillMaxWidth(),
            )
            FormTextField(
                value = address,
                onValueChange = { address = it },
                label = stringResource(R.string.project_site_optional),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                text = stringResource(R.string.project_create),
                onClick = { onCreate(name, client, address) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
