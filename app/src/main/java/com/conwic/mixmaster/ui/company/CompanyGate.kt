package com.conwic.mixmaster.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.company.CompanyLink
import com.conwic.mixmaster.data.company.CompanyStore
import com.conwic.mixmaster.data.company.SyncEngine
import com.conwic.mixmaster.ui.components.ActionLink
import com.conwic.mixmaster.ui.components.CardFlat
import com.conwic.mixmaster.ui.components.ConwicLockup
import com.conwic.mixmaster.ui.components.PrimaryButton
import com.conwic.mixmaster.ui.components.pagePadding

/**
 * How long a worker's phone goes on showing the company's data without hearing from the server.
 *
 * Taking somebody off the list empties their phone the next time it gets through — which a phone
 * kept offline never does. Two weeks is long enough for a job somewhere with no signal, and
 * short enough that a phone in a drawer does not stay an open copy of the company for good. The
 * data is locked, not deleted: the first answer from the server opens it again.
 */
const val OfflineLockDays = 14

@Composable
fun CompanyGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val link by remember { CompanyStore.link(context.applicationContext) }.collectAsState()
    val current = link
    val lockedOut = current != null && !current.owner &&
        System.currentTimeMillis() - current.lastContactAt > OfflineLockDays * 24L * 60 * 60 * 1000
    if (lockedOut && current != null) OfflineLock(current) else content()
    EndedNotice()
}

@Composable
private fun OfflineLock(link: CompanyLink) {
    val status by SyncEngine.status.collectAsState()
    val days = ((System.currentTimeMillis() - link.lastContactAt) / (24L * 60 * 60 * 1000)).toInt()
    Column(
        modifier = Modifier.fillMaxSize().padding(pagePadding()),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConwicLockup(height = 34.dp)
        Text(text = stringResource(R.string.co_lock_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.co_lock_body, link.companyName, days),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val problem = status.problem
        if (problem != null || status.offline) {
            Text(
                text = stringResource(if (problem != null) problemText(problem) else R.string.co_err_offline),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
        }
        PrimaryButton(
            text = stringResource(if (status.working) R.string.co_checking else R.string.co_try_again),
            onClick = SyncEngine::syncNow,
            enabled = !status.working,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Said once, after the phone has been emptied because the company cut it off. */
@Composable
private fun EndedNotice() {
    val context = LocalContext.current
    val ended by remember { CompanyStore.endedNotice(context.applicationContext) }.collectAsState()
    val company = ended ?: return
    AlertDialog(
        onDismissRequest = { CompanyStore.dismissEnded(context) },
        title = { Text(text = stringResource(R.string.co_ended_title)) },
        text = { Text(text = stringResource(R.string.co_ended_body, company)) },
        confirmButton = {
            TextButton(onClick = { CompanyStore.dismissEnded(context) }) { Text(text = stringResource(R.string.co_ok)) }
        },
    )
}

/** On Home, for a worker whose phone is not in the company yet: where the access code goes. */
@Composable
fun JoinCompanyCard(onOpen: () -> Unit) {
    CardFlat(edge = MaterialTheme.colorScheme.primary) {
        Text(text = stringResource(R.string.co_home_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.co_home_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        ActionLink(text = stringResource(R.string.co_home_action), onClick = onOpen, modifier = Modifier.padding(top = 8.dp))
    }
}
