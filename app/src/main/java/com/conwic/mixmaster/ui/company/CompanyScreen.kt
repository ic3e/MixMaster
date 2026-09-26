package com.conwic.mixmaster.ui.company

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.backup.BackupManager
import com.conwic.mixmaster.data.company.AccessCode
import com.conwic.mixmaster.data.company.CompanyLink
import com.conwic.mixmaster.data.company.Perms
import com.conwic.mixmaster.data.company.Person
import com.conwic.mixmaster.data.company.ServerKind
import com.conwic.mixmaster.data.company.SyncStatus
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
import com.conwic.mixmaster.ui.theme.CardShape
import com.conwic.mixmaster.ui.theme.Ok
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/**
 * One company's data on every phone in it.
 *
 * The employer sets the company up here against a server of the company's own — the website, or
 * a Google account — and hands out access codes, one per person, each with what that person may
 * change. Everybody else pastes their code here, and their phone fills with the company's data.
 */
@Composable
fun CompanyScreen(navController: NavHostController) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val viewModel: CompanyViewModel = viewModel(
        factory = viewModelFactory { initializer { CompanyViewModel(context.applicationContext, container.userPrefs) } },
    )
    val link by viewModel.link.collectAsState()
    val status by viewModel.status.collectAsState()
    val ui by viewModel.ui.collectAsState()

    // The person whose sheet is open; a blank one while adding somebody new.
    var editing by remember { mutableStateOf<Person?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = pagePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MixMasterTopBar(title = stringResource(R.string.co_title), onBack = { navController.popBackStack() }) }

        val problem = ui.problem
        if (problem != null) {
            item {
                CardFlat(edge = MaterialTheme.colorScheme.error) {
                    Text(text = stringResource(problemText(problem)), style = MaterialTheme.typography.bodyMedium)
                    ActionLink(
                        text = stringResource(R.string.co_ok),
                        onClick = viewModel::dismissProblem,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        val busy = ui.busy
        if (busy != null) {
            item {
                Text(
                    text = if (ui.progress > 0) {
                        stringResource(R.string.co_receiving_count, ui.progress)
                    } else {
                        stringResource(busy)
                    },
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
                    text = stringResource(R.string.co_lead),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { JoinCard(busy = busy != null, onJoin = viewModel::join) }
            item {
                SetUpCard(
                    ui = ui,
                    onCheck = viewModel::check,
                    onForget = viewModel::forgetFound,
                    onSetUp = viewModel::setUp,
                )
            }
        } else {
            item { CompanyCard(link = current, status = status, onSync = viewModel::syncNow) }
            if (current.owner) {
                val fresh = ui.fresh
                if (fresh != null && fresh.code != null) {
                    item {
                        FreshCodeCard(
                            person = fresh,
                            code = AccessCode(fresh.code, current.server),
                            companyName = current.companyName,
                            onDone = viewModel::dismissFresh,
                        )
                    }
                }
                item {
                    PeopleCard(
                        people = ui.people,
                        loaded = ui.peopleLoaded,
                        myId = current.personId,
                        busy = busy != null,
                        onAdd = { editing = Person(0L, "", false, Perms(), "pending", null, null) },
                        onOpen = { editing = it },
                    )
                }
            } else {
                item { MyPermsCard(perms = current.perms) }
            }
            item {
                LeaveButton(
                    link = current,
                    busy = busy != null,
                    onLeave = viewModel::leave,
                    onDisconnect = viewModel::disconnect,
                )
            }
        }
    }

    val person = editing
    if (person != null) {
        PersonSheet(
            person = person,
            isMe = person.id == link?.personId,
            onDismiss = { editing = null },
            onSave = { name, owner, perms ->
                viewModel.savePerson(person.id, name, owner, perms)
                editing = null
            },
            onShowCode = {
                viewModel.showCode(person)
                editing = null
            },
            onNewCode = {
                viewModel.newCode(person.id)
                editing = null
            },
            onRemove = {
                viewModel.removePerson(person.id)
                editing = null
            },
        )
    }
}

/** The server's or the app's word for what went wrong, as something a person can act on. */
internal fun problemText(code: String): Int = when (code) {
    "offline" -> R.string.co_err_offline
    "not_server" -> R.string.co_err_not_server
    "https" -> R.string.co_err_https
    "claimed" -> R.string.co_err_claimed
    "not_claimed" -> R.string.co_err_not_claimed
    "bad_code" -> R.string.co_err_bad_code
    "bad_format" -> R.string.co_err_bad_format
    "too_many" -> R.string.co_err_too_many
    "not_allowed" -> R.string.co_err_not_allowed
    "last_owner" -> R.string.co_err_last_owner
    "other_company" -> R.string.co_err_other_company
    "revoked" -> R.string.co_err_revoked
    else -> R.string.co_err_server
}

/** "5 min ago", worked out here rather than by the platform, which would say it in the phone's language. */
@Composable
internal fun agoText(at: Long?): String {
    if (at == null || at <= 0L) return stringResource(R.string.co_ago_never)
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> stringResource(R.string.co_ago_now)
        minutes < 60 -> stringResource(R.string.co_ago_minutes, minutes.toInt())
        minutes < 48 * 60 -> stringResource(R.string.co_ago_hours, (minutes / 60).toInt())
        else -> stringResource(R.string.co_ago_days, (minutes / (24 * 60)).toInt())
    }
}

// ---- Not in a company yet -----------------------------------------------------------------------

@Composable
private fun JoinCard(busy: Boolean, onJoin: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { BackupManager.export(context, it) }
    }

    Column {
        SectionLabel(text = stringResource(R.string.co_join_title))
        CardFlat {
            Text(
                text = stringResource(R.string.co_join_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormTextField(
                value = code,
                onValueChange = { code = it },
                label = stringResource(R.string.co_code_label),
                hint = stringResource(R.string.co_code_hint),
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GhostButton(
                    text = stringResource(R.string.co_paste),
                    onClick = { clipboard.getText()?.text?.let { pasted -> code = AccessCode.parse(pasted)?.shown ?: pasted.trim() } },
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.co_join),
                    onClick = { confirming = true },
                    enabled = code.isNotBlank() && !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.co_join_replaces),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            ActionLink(
                text = stringResource(R.string.co_backup_first),
                onClick = { backupLauncher.launch("mixmaster-backup.mmbackup") },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.co_join_confirm_title),
            message = stringResource(R.string.co_join_confirm_body),
            confirmText = stringResource(R.string.co_join),
            onConfirm = {
                confirming = false
                onJoin(code)
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun SetUpCard(
    ui: CompanyUi,
    onCheck: (String) -> Unit,
    onForget: () -> Unit,
    onSetUp: (companyName: String, yourName: String, startEmpty: Boolean) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(ServerKind.WEBSITE.key) }
    var address by rememberSaveable { mutableStateOf("") }
    var companyName by rememberSaveable { mutableStateOf("") }
    var yourName by rememberSaveable { mutableStateOf("") }
    var startEmpty by rememberSaveable { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val busy = ui.busy != null

    Column {
        SectionLabel(text = stringResource(R.string.co_setup_title))
        CardFlat {
            Text(
                text = stringResource(R.string.co_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val found = ui.found
            if (found == null) {
                ChipRow(
                    options = listOf(
                        ServerKind.WEBSITE to R.string.co_kind_website,
                        ServerKind.GOOGLE to R.string.co_kind_google,
                    ).map { (option, label) ->
                        ChipOption(label = stringResource(label), selected = option.key == kind, onClick = { kind = option.key })
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
                FormTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = stringResource(R.string.co_address),
                    hint = stringResource(if (kind == ServerKind.GOOGLE.key) R.string.co_address_hint_google else R.string.co_address_hint_website),
                    keyboardType = KeyboardType.Uri,
                    modifier = Modifier.padding(top = 10.dp),
                )
                PrimaryButton(
                    text = stringResource(R.string.co_check),
                    onClick = { onCheck(address) },
                    enabled = address.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            } else if (found.hello.claimed) {
                Text(
                    text = stringResource(R.string.co_found_claimed, found.hello.companyName.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp),
                )
                ActionLink(text = stringResource(R.string.co_other_address), onClick = onForget, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text(
                    text = stringResource(
                        if (found.hello.kind == ServerKind.GOOGLE) R.string.co_found_google else R.string.co_found_website,
                        found.server.removePrefix("https://"),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Ok,
                    modifier = Modifier.padding(top = 10.dp),
                )
                FormTextField(
                    value = companyName,
                    onValueChange = { companyName = it },
                    label = stringResource(R.string.co_company_name),
                    modifier = Modifier.padding(top = 10.dp),
                )
                FormTextField(
                    value = yourName,
                    onValueChange = { yourName = it },
                    label = stringResource(R.string.co_your_name),
                    modifier = Modifier.padding(top = 10.dp),
                )
                StartChoice(
                    text = stringResource(R.string.co_start_with_phone),
                    selected = !startEmpty,
                    onClick = { startEmpty = false },
                    modifier = Modifier.padding(top = 10.dp),
                )
                StartChoice(
                    text = stringResource(R.string.co_start_empty),
                    selected = startEmpty,
                    onClick = { startEmpty = true },
                )
                PrimaryButton(
                    text = stringResource(R.string.co_set_up),
                    onClick = {
                        if (startEmpty) confirmEmpty = true else onSetUp(companyName, yourName, false)
                    },
                    enabled = companyName.isNotBlank() && yourName.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                ActionLink(text = stringResource(R.string.co_other_address), onClick = onForget, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.co_empty_confirm_title),
            message = stringResource(R.string.co_empty_confirm_body),
            confirmText = stringResource(R.string.co_set_up),
            onConfirm = {
                confirmEmpty = false
                onSetUp(companyName, yourName, true)
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}

@Composable
private fun StartChoice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

// ---- In a company ---------------------------------------------------------------------------------

@Composable
private fun CompanyCard(link: CompanyLink, status: SyncStatus, onSync: () -> Unit) {
    val trouble = status.problem != null
    CardFlat(edge = if (trouble) MaterialTheme.colorScheme.error else Ok) {
        Text(text = link.companyName, style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(
                R.string.co_you_are,
                link.name,
                stringResource(if (link.owner) R.string.co_role_owner else R.string.co_role_worker),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = statusText(link, status),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (trouble) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = if (link.kind == ServerKind.GOOGLE) {
                stringResource(R.string.co_kept_google)
            } else {
                stringResource(R.string.co_kept_website, link.serverShown)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        ActionLink(
            text = stringResource(R.string.co_sync_now),
            onClick = onSync,
            enabled = !status.working,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun statusText(link: CompanyLink, status: SyncStatus): String {
    val problem = status.problem
    return when {
        problem != null -> stringResource(problemText(problem))
        status.waiting > 0 && status.offline -> stringResource(R.string.co_status_waiting, status.waiting)
        status.waiting > 0 -> stringResource(R.string.co_status_sending, status.waiting)
        status.offline -> stringResource(R.string.co_status_offline, agoText(link.lastContactAt))
        else -> stringResource(R.string.co_status_ok, agoText(link.lastContactAt))
    }
}

@Composable
private fun MyPermsCard(perms: Perms) {
    Column {
        SectionLabel(text = stringResource(R.string.co_my_perms))
        CardFlat {
            PermLine(R.string.co_perm_catalogue, perms.catalogue)
            PermLine(R.string.co_perm_projects, perms.projects)
            PermLine(R.string.co_perm_warehouse, perms.warehouse)
            PermLine(R.string.co_perm_site, perms.site)
            Text(
                text = stringResource(R.string.co_my_perms_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PermLine(label: Int, allowed: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (allowed) "✓" else "–",
            style = MaterialTheme.typography.titleMedium,
            color = if (allowed) Ok else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = if (allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FreshCodeCard(person: Person, code: AccessCode, companyName: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val message = stringResource(R.string.co_share_text, person.name, companyName, code.shown)
    val chooser = stringResource(R.string.co_share_chooser)
    CardFlat(edge = MaterialTheme.colorScheme.primary) {
        Text(
            text = stringResource(R.string.co_code_for, person.name),
            style = MaterialTheme.typography.titleMedium,
        )
        SelectionContainer {
            Text(
                text = code.shown,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.co_share),
                onClick = { share(context, message, chooser) },
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = stringResource(R.string.co_copy),
                onClick = { clipboard.setText(AnnotatedString(code.shown)) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.co_code_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        ActionLink(text = stringResource(R.string.co_done), onClick = onDone, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun share(context: Context, message: String, chooser: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message)
    runCatching { context.startActivity(Intent.createChooser(send, chooser)) }
}

@Composable
private fun PeopleCard(
    people: List<Person>,
    loaded: Boolean,
    myId: Long,
    busy: Boolean,
    onAdd: () -> Unit,
    onOpen: (Person) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = stringResource(R.string.co_people, people.size))
            ActionLink(text = stringResource(R.string.co_add_person), onClick = onAdd, enabled = !busy)
        }
        CardFlat(contentPadding = 12.dp) {
            if (!loaded) {
                Text(
                    text = stringResource(R.string.co_people_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            people.forEachIndexed { index, person ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .clickable { onOpen(person) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (person.id == myId) stringResource(R.string.co_person_me, person.name) else person.name,
                            style = MaterialTheme.typography.titleMedium,
                            overflow = TextOverflow.Visible,
                        )
                        Text(
                            text = when (person.status) {
                                "pending" -> stringResource(R.string.co_state_pending)
                                "left" -> stringResource(R.string.co_state_left)
                                else -> stringResource(R.string.co_state_active, agoText(person.seenAt))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (person.status == "active") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    Text(
                        text = stringResource(if (person.owner) R.string.co_role_owner else R.string.co_role_worker),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaveButton(link: CompanyLink, busy: Boolean, onLeave: () -> Unit, onDisconnect: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    GhostButton(
        text = stringResource(if (link.owner) R.string.co_disconnect else R.string.co_leave),
        onClick = { confirming = true },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirming) {
        ConfirmDialog(
            title = stringResource(if (link.owner) R.string.co_disconnect_title else R.string.co_leave_title, link.companyName),
            message = stringResource(if (link.owner) R.string.co_disconnect_body else R.string.co_leave_body),
            confirmText = stringResource(if (link.owner) R.string.co_disconnect else R.string.co_leave),
            onConfirm = {
                confirming = false
                if (link.owner) onDisconnect() else onLeave()
            },
            onDismiss = { confirming = false },
        )
    }
}
