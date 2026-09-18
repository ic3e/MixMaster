package com.conwic.mixmaster.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.BuildConfig
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.backup.BackupManager
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.SectionLabel

@Composable
fun SettingsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current

    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.userPrefs, container.teamRepository) } },
    )
    val state by viewModel.uiState.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { BackupManager.export(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { BackupManager.importAndRestart(context, it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(text = "Settings", style = MaterialTheme.typography.headlineMedium) }

        item {
            Column {
                SectionLabel(text = "Appearance")
                CardFlat {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Theme")
                        ChipRow(
                            options = listOf("Light", "Dark", "Auto").map { option ->
                                ChipOption(label = option, selected = option == state.theme, onClick = { viewModel.setTheme(option) })
                            },
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Units")
                        ChipRow(
                            options = listOf("Metric", "Imperial").map { option ->
                                ChipOption(label = option, selected = option == state.units, onClick = { viewModel.setUnits(option) })
                            },
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel(text = "Data & backup")
                CardFlat {
                    Button(onClick = { exportLauncher.launch("mixmaster-backup.mmbackup") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Export database (.mmbackup)")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Import database")
                    }
                    Text(
                        text = "Importing replaces everything on this device with the backup file and restarts the app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            Column {
                SectionLabel(text = "Account")
                CardFlat {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Signed in as")
                        ChipRow(
                            options = listOf(Role.EMPLOYER, Role.WORKER).map { r ->
                                ChipOption(label = r.name, selected = r == state.role, onClick = { viewModel.setRole(r) })
                            },
                        )
                    }
                }
            }
        }

        if (state.role == Role.EMPLOYER) {
            item {
                Column {
                    SectionLabel(text = "Team")
                    CardFlat {
                        state.team.forEachIndexed { index, member ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(text = member.name, style = MaterialTheme.typography.titleMedium)
                                    Text(text = member.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(text = member.role.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (index != state.team.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel(text = "Security & guidance")
                CardFlat {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "App lock (biometric)")
                        Switch(checked = state.appLockEnabled, onCheckedChange = viewModel::setAppLockEnabled)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Contextual tips after inactivity")
                        Switch(checked = state.tipsEnabled, onCheckedChange = viewModel::setTipsEnabled)
                    }
                    Text(
                        text = "Replay app tour",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { navController.navigate(com.conwic.mixmaster.ui.navigation.Routes.ONBOARDING) },
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.conwic_lockup),
                    contentDescription = "ConWiC",
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                Text(
                    text = "MixMaster by ConWiC · v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
