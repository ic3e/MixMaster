package com.conwic.mixmaster.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.conwic.mixmaster.data.seed.DemoData
import com.conwic.mixmaster.ui.company.rememberAccess
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ConfirmDialog
import com.conwic.mixmaster.ui.components.GhostButton
import com.conwic.mixmaster.ui.components.SectionLabel
import kotlinx.coroutines.launch

/**
 * Loads the demo over everything: the firm's own catalogue, five jobs, the calendar and a shelf.
 *
 * Only on a phone that may change all of it — the catalogue, the projects and the warehouse — and
 * in a company only on the owner's, because on a shared phone the demo goes to everybody.
 */
@Composable
fun DemoSection(modifier: Modifier = Modifier) {
    val access = rememberAccess()
    if (!(access.catalogue && access.projects && access.warehouse && (access.owner || !access.inCompany))) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        SectionLabel(text = stringResource(R.string.settings_demo))
        CardFlat {
            Text(
                text = stringResource(R.string.settings_demo_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton(
                text = stringResource(R.string.settings_demo_load),
                onClick = { asking = true },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }

    if (asking) {
        val companyName = remember { CompanyStore.current(context.applicationContext)?.companyName }
        val shared = if (companyName != null) "\n\n" + stringResource(R.string.settings_demo_confirm_company, companyName) else ""
        ConfirmDialog(
            title = stringResource(R.string.settings_demo_confirm_title),
            message = stringResource(R.string.settings_demo_confirm_text) + shared,
            confirmText = stringResource(R.string.settings_demo_confirm),
            onConfirm = {
                asking = false
                loading = true
                scope.launch { DemoData.load(context) }
            },
            onDismiss = { asking = false },
        )
    }
    if (loading) {
        // No way out of this one: half a demo over half the old data is worse than either.
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(text = stringResource(R.string.settings_demo_loading)) },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
        )
    }
}
