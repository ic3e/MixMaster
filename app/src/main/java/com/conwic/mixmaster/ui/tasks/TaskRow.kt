package com.conwic.mixmaster.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.data.model.TaskPriority
import com.conwic.mixmaster.ui.theme.CardShape

/** Priority colours, matching the dots used elsewhere in the app. */
fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> Color(0xFFB3423A)
    TaskPriority.MEDIUM -> Color(0xFFB98A3E)
    TaskPriority.LOW -> Color(0xFF8A5A2E)
    TaskPriority.DONE -> Color(0xFF4B7A52)
}

/**
 * One task in a list: tick the box to close it off, tap the row to open it for editing.
 *
 * The box and the row are deliberately separate targets — ticking something done is the common
 * action and shouldn't cost a trip through a sheet.
 */
@Composable
fun TaskRow(
    title: String,
    subtitle: String,
    done: Boolean,
    priority: TaskPriority,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onEdit)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = done,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(9.dp)
                .background(priorityColor(priority), CircleShape),
        )
    }
}
