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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.domain.AppLanguage
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.BuildConfig
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.backup.BackupManager
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ConwicLockup
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.tappableText
import com.conwic.mixmaster.ui.security.canLockApp
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.DisplayFontFamily

@Composable
fun SettingsScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current

    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container.userPrefs, container.teamRepository) } },
    )
    val state by viewModel.uiState.collectAsState()

    // Whether this phone has a fingerprint, face or screen lock to check against at all.
    val lockAvailable = remember { canLockApp(context) }

    // Non-null while the crew sheet is open; holds what it starts from.
    var editingMember by remember { mutableStateOf<TeamMemberEntity?>(null) }

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
        item { Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.settings_language))
                CardFlat {
                    ChipRow(
                        options = AppLanguage.entries.map { option ->
                            ChipOption(
                                label = option.label,
                                selected = option == state.language,
                                onClick = { viewModel.setLanguage(option) },
                            )
                        },
                    )
                    Text(
                        text = stringResource(R.string.settings_language_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.settings_appearance))
                CardFlat {
                    Text(text = stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                    ChipRow(
                        // The stored value stays English because the activity switches on it;
                        // only the label shown is translated.
                        options = listOf(
                            "Light" to R.string.theme_light,
                            "Dark" to R.string.theme_dark,
                            "Auto" to R.string.theme_auto,
                        ).map { (option, labelRes) ->
                            ChipOption(
                                label = stringResource(labelRes),
                                selected = option == state.theme,
                                onClick = { viewModel.setTheme(option) },
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_theme_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.settings_on_site))
                CardFlat {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Weight, not just padding: without it the label takes the width it
                        // wants and pushes the switch out past the edge of the card.
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = stringResource(R.string.settings_mixing_reminders), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.settings_mixing_reminders_note),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.mixingRemindersEnabled,
                            onCheckedChange = viewModel::setMixingRemindersEnabled,
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.settings_using_as))
                CardFlat {
                    ChipRow(
                        options = listOf(
                            Role.EMPLOYER to R.string.role_employer,
                            Role.WORKER to R.string.role_worker,
                        ).map { (option, labelRes) ->
                            ChipOption(
                                label = stringResource(labelRes),
                                selected = option == state.role,
                                onClick = { viewModel.setRole(option) },
                            )
                        },
                    )
                    Text(
                        text = stringResource(
                            if (state.role == Role.EMPLOYER) R.string.role_employer_note else R.string.role_worker_note,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (state.role == Role.EMPLOYER) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel(text = stringResource(R.string.settings_crew, state.team.size))
                        Text(
                            text = stringResource(R.string.settings_add_member),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.tappableText {
                                editingMember = TeamMemberEntity(name = "", email = "", role = Role.WORKER)
                            },
                        )
                    }
                    // 12 + 4 = the usual 16: the rows inset themselves so their text isn't
                    // flush against the rounded clip below, which was slicing the left edge off
                    // a leading T or j.
                    CardFlat(contentPadding = 12.dp) {
                        if (state.team.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_crew_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        state.team.forEachIndexed { index, member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CardShape)
                                    .clickable { editingMember = member }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    // Visible overflow: a glyph whose ink reaches left of its own
                                    // advance — Manrope's T and j do — was being clipped by the
                                    // text box, which turned "Tanel" into "Ганel".
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        overflow = TextOverflow.Visible,
                                    )
                                    if (member.email.isNotBlank()) {
                                        Text(
                                            text = member.email,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            overflow = TextOverflow.Visible,
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(if (member.role == Role.EMPLOYER) R.string.role_employer_short else R.string.role_worker_short),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (index != state.team.lastIndex) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.settings_security))
                CardFlat {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Weight, not just padding: without it the label takes the width it
                        // wants and pushes the switch out past the edge of the card.
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = stringResource(R.string.settings_lock), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(
                                    if (lockAvailable) {
                                        R.string.settings_lock_note
                                    } else {
                                        R.string.settings_lock_unavailable
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.appLockEnabled && lockAvailable,
                            onCheckedChange = viewModel::setAppLockEnabled,
                            enabled = lockAvailable,
                        )
                    }
                }
            }
        }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.settings_backup))
                CardFlat {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PrimaryButton(
                            text = stringResource(R.string.settings_export),
                            onClick = { exportLauncher.launch("mixmaster-backup.mmbackup") },
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = stringResource(R.string.settings_restore),
                            onClick = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_restore_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        item { UpdateSection(modifier = Modifier.fillMaxWidth()) }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.settings_help))
                CardFlat {
                    Text(
                        text = stringResource(R.string.settings_replay_tour),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tappableText { navController.navigate(com.conwic.mixmaster.ui.navigation.Routes.ONBOARDING) },
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ConwicLockup(height = 32.dp)
                Text(
                    text = "MixMaster · v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    editingMember?.let { member ->
        TeamMemberSheet(
            member = member,
            onDismiss = { editingMember = null },
            onSave = { saved ->
                viewModel.saveTeamMember(saved)
                editingMember = null
            },
            onRemove = if (member.id != 0L) {
                {
                    viewModel.removeTeamMember(member)
                    editingMember = null
                }
            } else {
                null
            },
        )
    }
}
