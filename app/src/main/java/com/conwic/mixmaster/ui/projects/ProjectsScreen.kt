package com.conwic.mixmaster.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.ProgressBarRow
import com.conwic.mixmaster.ui.navigation.Routes

@Composable
fun ProjectsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val viewModel: ProjectsViewModel = viewModel(
        factory = viewModelFactory { initializer { ProjectsViewModel(container.projectRepository) } },
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createDraftProject { id -> navController.navigate(Routes.projectDetail(id)) } }) {
                Icon(Icons.Filled.Add, contentDescription = "New project")
            }
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(text = "Projects", style = MaterialTheme.typography.headlineMedium) }

            item {
                ChipRow(
                    options = viewModel.filterOptions.map { option ->
                        ChipOption(label = option, selected = option == state.filter, onClick = { viewModel.setFilter(option) })
                    },
                )
            }

            items(state.visibleProjects) { item ->
                val project = item.project
                CardFlat(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Routes.projectDetail(project.id)) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = project.name, style = MaterialTheme.typography.titleMedium)
                        Text(text = project.status.label(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "${project.clientName} · ${project.address}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    ProgressBarRow(progressPercent = item.progressPercent)
                }
            }
        }
    }
}
