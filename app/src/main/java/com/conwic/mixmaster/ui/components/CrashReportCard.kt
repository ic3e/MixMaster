package com.conwic.mixmaster.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.crash.CrashLog

/**
 * Shown once after the app has closed on its own, with the stack trace and a way to copy it.
 *
 * On site "it closed" is the whole bug report. This puts the part that actually says where in
 * reach of whoever saw it happen.
 */
@Composable
fun CrashReportCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    val trace = report ?: return

    CardFlat(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.crash_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.crash_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = trace.lineSequence().take(8).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val copyLabel = stringResource(R.string.crash_copy)
        PrimaryButton(
            text = copyLabel,
            onClick = { copyToClipboard(context, copyLabel, trace) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
        GhostButton(
            text = stringResource(R.string.action_close),
            onClick = {
                CrashLog.clear(context)
                report = null
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
