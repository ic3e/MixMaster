package com.conwic.mixmaster.ui.settings

import com.conwic.mixmaster.ui.components.LocalBarInset
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.graphicsLayer
import com.conwic.mixmaster.ui.components.rememberMotionOff
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.conwic.mixmaster.data.company.CompanyStore
import androidx.compose.ui.text.font.FontWeight
import com.conwic.mixmaster.ui.navigation.Routes
import com.conwic.mixmaster.ui.breaktime.PourDayShare
import kotlinx.coroutines.launch
import com.conwic.mixmaster.data.prefs.AlertSoundStore
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
        factory = viewModelFactory { initializer { SettingsViewModel(container.userPrefs) } },
    )
    val state by viewModel.uiState.collectAsState()
    val company by remember { CompanyStore.link(context.applicationContext) }.collectAsState()

    // Whether this phone has a fingerprint, face or screen lock to check against at all.
    val lockAvailable = remember { canLockApp(context) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { BackupManager.export(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { BackupManager.importAndRestart(context, it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = pagePadding(bottom = 20.dp + LocalBarInset.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium) }

        item {
            Column {
                SectionLabel(text = stringResource(R.string.co_title))
                CardFlat(
                    modifier = Modifier.clip(CardShape).clickable { navController.navigate(Routes.COMPANY) },
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val linked = company
                        Text(
                            text = if (linked != null) {
                                stringResource(R.string.co_settings_on, linked.companyName)
                            } else {
                                stringResource(R.string.co_settings_off)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (linked != null) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        ActionLink(
                            text = stringResource(if (linked != null) R.string.co_settings_open else R.string.co_settings_set_up),
                            onClick = { navController.navigate(Routes.COMPANY) },
                        )
                    }
                }
            }
        }

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

        // Somewhere to be while the slab hardens.
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayingPad()
                    SectionLabel(text = stringResource(R.string.settings_break_time))
                }
                BreakTimeCard(onPlay = { navController.navigate(Routes.BREAK_TIME) })
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
                                // In a company the employer's list says who runs things, not this switch.
                                onClick = { if (company == null) viewModel.setRole(option) },
                            )
                        },
                    )
                    Text(
                        text = if (company != null) stringResource(R.string.co_role_locked) else stringResource(
                            if (state.role == Role.EMPLOYER) R.string.role_employer_note else R.string.role_worker_note,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
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
                    // The company's data stays with the company: a worker's phone makes no copies of it.
                    if (company?.owner == false) {
                        Text(
                            text = stringResource(R.string.co_backup_worker),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
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

}

/**
 * Which sound says the batch is up.
 *
 * The phone's own picker rather than a list of our own: it already knows every alarm and
 * notification tone on the phone, it plays each one as you move down it, and it is the list
 * somebody has already used to set their alarm clock. Silence is on it too — the buzz still
 * goes, and a crew working somewhere that has to stay quiet is a real thing.
 */
/**
 * Pour Day: play it, or send it to a friend. What goes to the friend is the Pour Day app on its own
 * (see [PourDayShare]) — the game and nothing of MixMaster.
 */
@Composable
private fun BreakTimeCard(onPlay: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // the copy out of the app takes a moment; a second tap in it would send twice
    var sending by remember { mutableStateOf(false) }
    val subject = stringResource(R.string.break_time_send_subject)
    val text = stringResource(R.string.break_time_send_text)
    val chooser = stringResource(R.string.break_time_send_chooser)
    CardFlat(modifier = Modifier.clip(CardShape).clickable(onClick = onPlay)) {
        Text(text = stringResource(R.string.break_time_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.break_time_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionLink(
                text = stringResource(R.string.break_time_send),
                enabled = !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        val apk = PourDayShare.prepare(context)
                        if (apk != null) PourDayShare.send(context, apk, subject, text, chooser)
                        sending = false
                    }
                },
            )
            ActionLink(text = stringResource(R.string.break_time_play), onClick = onPlay)
        }
    }
}

/**
 * A gamepad in front of "Break time", somebody's thumbs on it: every few seconds it jiggles and
 * hops, then lies still. Still for good when the phone has been told to keep still.
 *
 * The movement is worked out inside the layer rather than while composing, so the settings list
 * isn't recomposed sixty times a second for a two-degree wiggle.
 */
@Composable
private fun PlayingPad() {
    val calm = rememberMotionOff()
    val play = if (calm) null else rememberInfiniteTransition(label = "pad").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "play",
    )
    Icon(
        imageVector = Icons.Rounded.SportsEsports,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        // the label under it keeps 8 dp below itself; the same here keeps the two centred together
        modifier = Modifier
            .padding(bottom = 8.dp, end = 6.dp)
            .size(18.dp)
            .graphicsLayer {
                val t = (play?.value ?: 1f) * 3.4f
                if (t < 0.7f) {
                    // a shake that dies away, and one little hop at the start of it
                    val fade = 1f - t / 0.7f
                    rotationZ = 16f * sin(t * 2f * PI.toFloat() * 3.2f) * fade * fade
                    translationY = if (t < 0.35f) -3.dp.toPx() * sin(PI.toFloat() * t / 0.35f) else 0f
                } else {
                    rotationZ = 0f
                    translationY = 0f
                }
            },
    )
}

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
