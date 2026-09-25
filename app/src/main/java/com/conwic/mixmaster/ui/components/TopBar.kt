package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R
import androidx.compose.ui.unit.dp

@Composable
fun MixMasterTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    /** Given a width by the caller, it takes that instead of the whole row — see the tabs. */
    modifier: Modifier = Modifier.fillMaxWidth(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            // Next to nothing: the row is already 48dp tall because of the back button's
            // touch target, and the title sits in the middle of that with room to spare
            // above and below it. Padding on top of that was a second gap under the
            // first one.
            .padding(horizontal = 12.dp, vertical = byHeight(tight = 0.dp, roomy = 2.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 8.dp else 0.dp),
        )
        actions()
    }
}
