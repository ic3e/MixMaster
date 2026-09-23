@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.conwic.mixmaster.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.data.db.entity.TeamMemberEntity
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.components.ChipOption
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.ChipRow
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.SectionLabel
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R

/** Add or edit one person on the crew. The team list used to be read-only. */
@Composable
fun TeamMemberSheet(
    member: TeamMemberEntity,
    onDismiss: () -> Unit,
    onSave: (TeamMemberEntity) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val isNew = member.id == 0L
    var name by remember { mutableStateOf(member.name) }
    var email by remember { mutableStateOf(member.email) }
    var role by remember { mutableStateOf(member.role) }
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(if (isNew) R.string.crew_add else R.string.crew_edit),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                // Away from the thumb, like the task sheet: a full-width remove under Save is
                // tapped by accident sooner or later.
                if (onRemove != null) {
                    IconButton(onClick = { confirmRemove = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.crew_remove),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.crew_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.crew_contact)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(text = stringResource(R.string.crew_can_do))
            ChipRow(
                options = listOf(Role.EMPLOYER, Role.WORKER).map { option ->
                    ChipOption(
                        label = stringResource(if (option == Role.EMPLOYER) R.string.role_employer else R.string.role_worker),
                        selected = option == role,
                        onClick = { role = option },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.crew_roles_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrimaryButton(
                text = stringResource(if (isNew) R.string.crew_add_action else R.string.task_save),
                onClick = { onSave(member.copy(name = name.trim(), email = email.trim(), role = role)) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmRemove && onRemove != null) {
        ConfirmDialog(
            title = stringResource(R.string.crew_remove_confirm),
            message = stringResource(R.string.crew_remove_confirm_body, name.ifBlank { member.name }),
            confirmText = stringResource(R.string.action_remove),
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}
