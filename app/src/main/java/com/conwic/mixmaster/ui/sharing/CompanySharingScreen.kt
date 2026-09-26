package com.conwic.mixmaster.ui.sharing

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.backup.BackupManager
import com.conwic.mixmaster.data.sync.CompanyLink
import com.conwic.mixmaster.data.sync.Invite
import com.conwic.mixmaster.data.sync.JoinCode
import com.conwic.mixmaster.data.sync.Member
import com.conwic.mixmaster.data.sync.RoleEmployer
import com.conwic.mixmaster.data.sync.RoleWorker
import com.conwic.mixmaster.data.sync.SyncStatus
import com.conwic.mixmaster.ui.LocalAppActivity
import com.conwic.mixmaster.ui.LocalAppContainer
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.MixMasterTopBar
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import com.conwic.mixmaster.ui.components.pagePadding
import com.conwic.mixmaster.ui.theme.Ok
import kotlinx.coroutines.flow.MutableStateFlow

/** An invite link that opened the app, waiting for this screen to pick it up. */
object JoinLinks {
    val pending = MutableStateFlow<String?>(null)
}

/**
 * One company's data on every phone in it: set up here by the employer, joined here by the crew.
 *
 * The Firebase side is the employer's to set up first (the setup guide, steps 1–5); this screen is
 * steps 6 and 7 — the settings file, the company, and the invites.
 */
@Composable
fun CompanySharingScreen(navController: NavHostController) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = LocalAppActivity.current
    val viewModel: CompanySharingViewModel = viewModel(
        factory = viewModelFactory {
            initializer { CompanySharingViewModel(context.applicationContext, container.userPrefs) }
        },
    )
    val link by viewModel.link.collectAsState()
    val status by viewModel.status.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val members by viewModel.members.collectAsState()
    val invites by viewModel.invites.collectAsState()

    // A link opened while this phone is already in a company has nothing left to do.
    LaunchedEffect(link != null) {
        if (link != null) JoinLinks.pending.value = null
    }

    val chooser = stringResource(R.string.sh_share_chooser)
    // An invite that has just been made goes straight to WhatsApp, SMS or email.
    LaunchedEffect(ui.shareText) {
        val text = ui.shareText ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(Intent.createChooser(send, chooser)) }
        viewModel.sharedInvite()
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::readSettingsFile)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { BackupManager.export(context, it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MixMasterTopBar(title = stringResource(R.string.sh_title), onBack = { navController.popBackStack() }) }

        val problem = ui.problem
        if (problem != null) {
            item {
                CardFlat(edge = MaterialTheme.colorScheme.error) {
                    Text(text = stringResource(problem), style = MaterialTheme.typography.bodyMedium)
                    ActionLink(
                        text = stringResource(R.string.sh_ok),
                        onClick = viewModel::dismissProblem,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        if (ui.busy) {
            item {
                Text(
                    text = stringResource(R.string.sh_working),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        val current = link
        if (current == null) {
            item {
                Text(
                    text = stringResource(R.string.sh_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SetUpCard(
                    ui = ui,
                    onChooseFile = { fileLauncher.launch(arrayOf("application/json", "*/*")) },
                    onForgetFile = viewModel::forgetSettings,
                    onCreate = { name -> activity?.let { viewModel.createCompany(it, name) } },
                )
            }
            item {
                JoinCard(
                    busy = ui.busy,
                    onJoin = { code -> activity?.let { viewModel.join(it, code) } },
                    onBackup = { backupLauncher.launch("mixmaster-backup.mmbackup") },
                )
            }
        } else {
            item {
                CompanyCard(
                    link = current,
                    status = status,
                    email = viewModel.myEmail(),
                    busy = ui.busy,
                    onSignIn = { activity?.let { viewModel.signInAgain(it) } },
                )
            }
            item {
                PeopleCard(
                    link = current,
                    members = members,
                    invites = invites,
                    myEmail = viewModel.myEmail(),
                    busy = ui.busy,
                    onInvite = { name, email, role ->
                        viewModel.invite(name, email, role) { code -> inviteMessage(context, current, code) }
                    },
                    onResend = { viewModel.resend { code -> inviteMessage(context, current, code) } },
                    onCancelInvite = viewModel::cancelInvite,
                    onRemove = viewModel::remove,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.sh_files_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { DisconnectButton(onDisconnect = viewModel::disconnect) }
        }
    }
}

private fun inviteMessage(context: android.content.Context, link: CompanyLink, code: JoinCode): String =
    context.getString(R.string.sh_invite_message, link.companyName, code.link(), code.encode())

@Composable
private fun SetUpCard(
    ui: SharingUi,
    onChooseFile: () -> Unit,
    onForgetFile: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    Column {
        SectionLabel(text = stringResource(R.string.sh_setup_title))
        CardFlat {
            Text(
                text = stringResource(R.string.sh_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val settings = ui.settings
            if (settings == null) {
                PrimaryButton(
                    text = stringResource(R.string.sh_choose_file),
                    onClick = onChooseFile,
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.sh_file_ok, settings.projectId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ok,
                    modifier = Modifier.padding(top = 10.dp),
                )
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.sh_company_name),
                    modifier = Modifier.padding(top = 10.dp),
                )
                PrimaryButton(
                    text = stringResource(R.string.sh_sign_in_create),
                    onClick = { onCreate(name) },
                    enabled = name.isNotBlank() && !ui.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                ActionLink(
                    text = stringResource(R.string.sh_choose_other_file),
                    onClick = onForgetFile,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun JoinCard(busy: Boolean, onJoin: (String) -> Unit, onBackup: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var code by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val opened by JoinLinks.pending.collectAsState()
    // Opened from an invite link: the code is already here.
    LaunchedEffect(opened) {
        val text = opened ?: return@LaunchedEffect
        code = text
        JoinLinks.pending.value = null
    }

    Column {
        SectionLabel(text = stringResource(R.string.sh_join_title))
        CardFlat {
            Text(
                text = stringResource(R.string.sh_join_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormTextField(
                value = code,
                onValueChange = { code = it },
                label = stringResource(R.string.sh_code_label),
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton(
                    text = stringResource(R.string.sh_paste),
                    onClick = { clipboard.getText()?.text?.let { code = it } },
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.sh_join),
                    onClick = { confirming = true },
                    enabled = code.isNotBlank() && !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.sh_join_replaces),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            ActionLink(
                text = stringResource(R.string.sh_backup_first),
                onClick = onBackup,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.sh_join_confirm_title),
            message = stringResource(R.string.sh_join_confirm_body),
            confirmText = stringResource(R.string.sh_join_confirm),
            onConfirm = {
                confirming = false
                onJoin(code)
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun CompanyCard(
    link: CompanyLink,
    status: SyncStatus,
    email: String?,
    busy: Boolean,
    onSignIn: () -> Unit,
) {
    val trouble = status.noAccess || status.signedOut
    CardFlat(edge = if (trouble) MaterialTheme.colorScheme.error else Ok) {
        Text(text = link.companyName, style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(
                R.string.sh_you_are,
                stringResource(if (link.isEmployer) R.string.role_employer else R.string.role_worker),
                email.orEmpty(),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = statusText(status),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (trouble) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (status.signedOut) {
            PrimaryButton(
                text = stringResource(R.string.sh_sign_in_again),
                onClick = onSignIn,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun statusText(status: SyncStatus): String = when {
    status.noAccess -> stringResource(R.string.sh_no_access)
    status.signedOut -> stringResource(R.string.sh_status_signed_out)
    status.waiting > 0 && status.online -> stringResource(R.string.sh_status_sending, status.waiting)
    status.waiting > 0 -> stringResource(R.string.sh_status_waiting, status.waiting)
    status.online -> stringResource(R.string.sh_status_up_to_date)
    else -> stringResource(R.string.sh_status_offline)
}

@Composable
private fun PeopleCard(
    link: CompanyLink,
    members: List<Member>,
    invites: List<Invite>,
    myEmail: String?,
    busy: Boolean,
    onInvite: (String, String, String) -> Unit,
    onResend: () -> Unit,
    onCancelInvite: (Invite) -> Unit,
    onRemove: (Member) -> Unit,
) {
    var inviting by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Member?>(null) }

    Column {
        SectionLabel(text = stringResource(R.string.sh_people))
        CardFlat {
            members.forEachIndexed { index, member ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PersonRow(
                    name = member.name.ifBlank { member.email },
                    detail = member.email,
                    role = member.role,
                    action = if (link.isEmployer && !member.email.equals(myEmail, ignoreCase = true)) {
                        stringResource(R.string.action_remove) to { removing = member }
                    } else {
                        null
                    },
                )
            }
            if (invites.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.sh_invited),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                invites.forEach { invite ->
                    PersonRow(
                        name = invite.name.ifBlank { invite.email },
                        detail = invite.email,
                        role = invite.role,
                        action = stringResource(R.string.sh_resend) to onResend,
                        second = stringResource(R.string.action_cancel) to { onCancelInvite(invite) },
                    )
                }
            }
            if (link.isEmployer) {
                PrimaryButton(
                    text = stringResource(R.string.sh_invite),
                    onClick = { inviting = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }

    if (inviting) {
        InviteDialog(
            onDismiss = { inviting = false },
            onInvite = { name, email, role ->
                inviting = false
                onInvite(name, email, role)
            },
        )
    }
    val leaving = removing
    if (leaving != null) {
        ConfirmDialog(
            title = stringResource(R.string.sh_remove_title, leaving.name.ifBlank { leaving.email }),
            message = stringResource(R.string.sh_remove_body),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                removing = null
                onRemove(leaving)
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun PersonRow(
    name: String,
    detail: String,
    role: String,
    action: Pair<String, () -> Unit>?,
    second: Pair<String, () -> Unit>? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                text = detail + " · " + stringResource(
                    if (role == RoleEmployer) R.string.role_employer_short else R.string.role_worker_short,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (second != null) {
            ActionLink(text = second.first, onClick = second.second, modifier = Modifier.padding(end = 6.dp))
        }
        if (action != null) {
            ActionLink(text = action.first, onClick = action.second)
        }
    }
}

@Composable
private fun InviteDialog(onDismiss: () -> Unit, onInvite: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(RoleWorker) }
    val valid = email.contains('@') && email.substringAfter('@').contains('.')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sh_invite)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.sh_invite_name),
                )
                FormTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = stringResource(R.string.sh_invite_email),
                    keyboardType = KeyboardType.Email,
                )
                ChipRow(
                    options = listOf(
                        RoleWorker to R.string.role_worker,
                        RoleEmployer to R.string.role_employer,
                    ).map { (option, label) ->
                        ChipOption(label = stringResource(label), selected = option == role, onClick = { role = option })
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onInvite(name, email, role) }, enabled = valid) {
                Text(text = stringResource(R.string.sh_invite_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DisconnectButton(onDisconnect: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    GhostButton(
        text = stringResource(R.string.sh_disconnect),
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.sh_disconnect_title),
            message = stringResource(R.string.sh_disconnect_body),
            confirmText = stringResource(R.string.sh_disconnect_confirm),
            onConfirm = {
                confirming = false
                onDisconnect()
            },
            onDismiss = { confirming = false },
        )
    }
}
