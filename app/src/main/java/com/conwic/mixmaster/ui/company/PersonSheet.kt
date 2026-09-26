@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.company.Perms
import com.conwic.mixmaster.data.company.Person
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.FormTextField
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel

/**
 * One person in the company: their name, whether they run it, and what they may change.
 *
 * Saving a new person makes their access code. For somebody already in, a new code is how a
 * lost or replaced phone is cut off, and removing them is how somebody leaves for good — either
 * way the phone they had empties itself the next time it reaches the server.
 */
@Composable
fun PersonSheet(
    person: Person,
    isMe: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, owner: Boolean, perms: Perms) -> Unit,
    onShowCode: () -> Unit,
    onNewCode: () -> Unit,
    onRemove: () -> Unit,
) {
    val isNew = person.id == 0L
    var name by remember { mutableStateOf(person.name) }
    var owner by remember { mutableStateOf(person.owner) }
    var catalogue by remember { mutableStateOf(person.perms.catalogue) }
    var projects by remember { mutableStateOf(person.perms.projects) }
    var warehouse by remember { mutableStateOf(person.perms.warehouse) }
    var site by remember { mutableStateOf(person.perms.site) }
    var confirmCode by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

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
            Text(
                text = if (isNew) stringResource(R.string.co_add_person) else person.name,
                style = MaterialTheme.typography.headlineMedium,
            )
            FormTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.co_person_name),
            )
            Column {
                SectionLabel(text = stringResource(R.string.co_person_role))
                ChipRow(
                    options = listOf(false to R.string.co_role_worker, true to R.string.co_role_owner).map { (option, label) ->
                        // Your own role is not yours to take away: another owner has to do it.
                        ChipOption(label = stringResource(label), selected = option == owner, onClick = { if (!isMe) owner = option })
                    },
                )
                Text(
                    text = stringResource(if (owner) R.string.co_owner_note else R.string.co_worker_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (!owner) {
                Column {
                    SectionLabel(text = stringResource(R.string.co_may_change))
                    PermSwitch(R.string.co_perm_catalogue, catalogue) { catalogue = it }
                    PermSwitch(R.string.co_perm_projects, projects) { projects = it }
                    PermSwitch(R.string.co_perm_warehouse, warehouse) { warehouse = it }
                    PermSwitch(R.string.co_perm_site, site) { site = it }
                }
            }
            PrimaryButton(
                text = stringResource(if (isNew) R.string.co_make_code else R.string.co_save),
                onClick = {
                    onSave(name, owner, Perms(catalogue = catalogue, projects = projects, warehouse = warehouse, site = site))
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!isNew && !isMe) {
                if (person.status == "pending" && person.code != null) {
                    ActionLink(text = stringResource(R.string.co_show_code), onClick = onShowCode)
                }
                ActionLink(text = stringResource(R.string.co_new_code), onClick = { confirmCode = true })
                ActionLink(
                    text = stringResource(R.string.co_remove),
                    onClick = { confirmRemove = true },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmCode) {
        ConfirmDialog(
            title = stringResource(R.string.co_new_code_title, person.name),
            message = stringResource(R.string.co_new_code_body),
            confirmText = stringResource(R.string.co_new_code),
            onConfirm = {
                confirmCode = false
                onNewCode()
            },
            onDismiss = { confirmCode = false },
        )
    }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.co_remove_title, person.name),
            message = stringResource(R.string.co_remove_body),
            confirmText = stringResource(R.string.co_remove),
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun PermSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weight, not just padding: without it the label takes the width it wants and pushes the
        // switch out past the edge.
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
