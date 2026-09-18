package com.conwic.mixmaster.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
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
import com.conwic.mixmaster.ui.theme.Ok

/** Priority colours, matching the dots used elsewhere in the app. */
fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> Color(0xFFB3423A)
    TaskPriority.MEDIUM -> Color(0xFFB98A3E)
    TaskPriority.LOW -> Color(0xFF8A5A2E)
    TaskPriority.DONE -> Color(0xFF4B7A52)
}

/**
 * One task in a list. Tapping the row always opens it for editing.
 *
 * [onToggle] is opt-in, and deliberately left out on the screens you land on rather than go to.
 * A one-tap "done" is too easy to hit by accident with a wet glove, or with the phone loose in a
 * pocket — closing a task off there goes through the editor instead, where it takes a switch and
 * a save.
 */
@Composable
fun TaskRow(
    title: String,
    subtitle: String,
    done: Boolean,
    priority: TaskPriority,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = onEdit)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggle != null) {
            Checkbox(
                checked = done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline,
                ),
            )
        } else {
            // Same footprint as the checkbox, so rows line up whichever screen they're on.
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                DoneMark(done = done)
            }
        }
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

/** Shows whether a task is done without offering to change it. */
@Composable
private fun DoneMark(done: Boolean) {
    if (done) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Done",
            tint = Ok,
            modifier = Modifier.size(20.dp),
        )
    } else {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}
