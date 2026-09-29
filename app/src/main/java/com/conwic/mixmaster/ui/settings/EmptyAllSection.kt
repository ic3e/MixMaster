package com.conwic.mixmaster.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.company.CompanyStore
import com.conwic.mixmaster.data.wipe.PhoneWipe
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Empty all: the app back to the day it was installed, with nothing of the firm's on the phone.
 *
 * Behind a typed word, not a tap — it takes every job, every photo and the settings with it.
 *
 * Not on a phone in a company. There the data is the company's as much as the phone's, and the
 * way off is already under Company: a worker leaves, and the company's data goes with them; the
 * owner disconnects the phone first, having made sure somebody else can still let people in.
 * Emptying from here would throw away the phone's key without the server ever hearing of it.
 */
@Composable
fun EmptyAllSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val link by remember { CompanyStore.link(app) }.collectAsState()
    var asking by remember { mutableStateOf(false) }
    var emptying by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        SectionLabel(text = stringResource(R.string.settings_empty))
        CardFlat {
            val company = link?.companyName
            Text(
                text = if (company != null) {
                    stringResource(R.string.settings_empty_company, company)
                } else {
                    stringResource(R.string.settings_empty_note)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (company == null) {
                ActionLink(
                    text = stringResource(R.string.settings_empty_action),
                    onClick = { asking = true },
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.settings_empty_confirm_title),
            message = stringResource(R.string.settings_empty_confirm_text),
            confirmText = stringResource(R.string.settings_empty_confirm),
            typedWord = stringResource(R.string.settings_empty_word),
            onConfirm = {
                asking = false
                emptying = true
                scope.launch {
                    withContext(Dispatchers.IO) { PhoneWipe.everything(app) }
                    PhoneWipe.restart(app)
                }
            },
            onDismiss = { asking = false },
        )
    }
    if (emptying) {
        // No way out of this one either: half a phone emptied is worse than all or none.
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(text = stringResource(R.string.settings_empty_working)) },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
        )
    }
}
