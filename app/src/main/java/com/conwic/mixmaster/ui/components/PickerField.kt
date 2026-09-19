package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.conwic.mixmaster.ui.theme.FieldShape

/**
 * A "tap to choose" field.
 *
 * It *is* an [OutlinedTextField], disabled and painted to look live, rather than a hand-built
 * box that copies one. The hand-built version matched on height and shape but not on the space
 * Material reserves above the border for the floating label — about 6dp. Side by side with a
 * text field, the two borders sat 17px apart on a 480dpi screen, which is what "the fields
 * don't line up" was. Sharing the real component makes them line up by construction instead of
 * by two sets of numbers agreeing, so they cannot drift apart again.
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    // The field is disabled so it takes no focus and raises no keyboard; disabled widgets don't
    // consume pointer input, so the tap lands here.
    Box(modifier = modifier.clip(FieldShape).clickable(onClick = onClick)) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            enabled = false,
            label = { Text(label) },
            singleLine = true,
            leadingIcon = if (icon != null) {
                { Icon(imageVector = icon, contentDescription = null) }
            } else {
                null
            },
            trailingIcon = {
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            shape = FieldShape,
            // Disabled is a layout state here, not a "you can't touch this" state, so every
            // disabled colour is set to what the live field would use.
            colors = OutlinedTextFieldDefaults.colors(
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                disabledTrailingIconColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
