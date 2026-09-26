package com.conwic.mixmaster.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.domain.AppLanguage
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.BuildConfig
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.backup.BackupManager
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.data.prefs.AlertSoundStore
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ConwicLockup
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.security.canLockApp
import com.conwic.mixmaster.ui.theme.CardShape

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
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    AlertSoundRow()
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
                        ActionLink(
                            text = stringResource(R.string.settings_add_member),
                            onClick = {
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
                    ActionLink(
                        text = stringResource(R.string.settings_replay_tour),
                        onClick = { navController.navigate(com.conwic.mixmaster.ui.navigation.Routes.ONBOARDING) },
                        modifier = Modifier.fillMaxWidth(),
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

/**
 * Which sound says the batch is up.
 *
 * The phone's own picker rather than a list of our own: it already knows every alarm and
 * notification tone on the phone, it plays each one as you move down it, and it is the list
 * somebody has already used to set their alarm clock. Silence is on it too — the buzz still
 * goes, and a crew working somewhere that has to stay quiet is a real thing.
 */
@Composable
private fun AlertSoundRow() {
    val context = LocalContext.current
    // Resolved up here: the summary is worked out in a remember block, which is not a place a
    // string resource can be read from.
    val silentLabel = stringResource(R.string.settings_alert_sound_silent)
    val defaultLabel = stringResource(R.string.settings_alert_sound_default)
    val pickerTitle = stringResource(R.string.settings_alert_sound)
    // The store is SharedPreferences, so nothing tells the screen it has changed. Picking a
    // sound turns this over, and the line under the setting is worked out again.
    var revision by remember { mutableStateOf(0) }
    val summary = remember(revision, silentLabel, defaultLabel) {
        when {
            AlertSoundStore.isSilent(context) -> silentLabel
            else -> AlertSoundStore.title(context) ?: defaultLabel
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // No URI at all is the picker's way of saying silent, not its way of failing.
            @Suppress("DEPRECATION")
            val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            AlertSoundStore.write(context, picked)
            revision++
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = pickerTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ActionLink(
            text = stringResource(R.string.settings_alert_sound_pick),
            // A phone with no picker on it would otherwise take the Settings screen down with
            // it, which is a steep price for a tone.
            onClick = { runCatching { picker.launch(alertSoundPicker(context, pickerTitle)) } },
        )
    }
}

private fun alertSoundPicker(context: Context, title: String): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        // Alarms and notification tones both: an alarm tone is what a mixing timer wants, but
        // on most phones the short ones are filed under notifications.
        putExtra(
            RingtoneManager.EXTRA_RINGTONE_TYPE,
            RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION,
        )
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        // What "Default" on that list means here: whatever the phone's alarm clock is set to,
        // which is where this app started before anybody touched the setting.
        AlertSoundStore.deviceAlarm()?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, it) }
        AlertSoundStore.uri(context)?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it) }
    }
