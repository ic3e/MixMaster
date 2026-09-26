package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.conwic.mixmaster.R
import com.conwic.mixmaster.domain.nameMatches

/**
 * A text field that offers what has been entered before.
 *
 * Type anything — it's a free text field. But the names already in use that fit what is being
 * typed come up under it on their own, which is what stops "Ardex", "ARDEX" and "ardex "
 * becoming three brands that don't filter together. The ▾ lists them all.
 */
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    hint: String? = null,
    singleLine: Boolean = true,
) {
    // Opened by the ▾, for looking through the whole list.
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // The text the list was last closed or picked on, so it doesn't spring back for the same one.
    var settledOn by remember { mutableStateOf<String?>(null) }
    var fieldWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val pool = remember(suggestions) { suggestions.filter { it.isNotBlank() }.distinct() }
    val typing = value.isNotBlank()
    val matches = remember(value, pool) { if (typing) nameMatches(value, pool) else emptyList() }
    // While typing it opens by itself with what fits. Only then, though: an empty field with the
    // list hanging off it would cover the next field on the way past.
    val offering = !expanded && focused && typing && matches.isNotEmpty() && value != settledOn
    // The ▾ shows everything, whatever is typed — a new name has no matches to narrow it to.
    val shown = if (offering) matches else pool
    val open = (expanded || offering) && shown.isNotEmpty()

    Box(
        modifier = modifier.onSizeChanged { size ->
            with(density) { fieldWidth = size.width.toDp() }
        },
    ) {
        FormTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            hint = hint,
            singleLine = singleLine,
            trailing = if (pool.isEmpty()) {
                null
            } else {
                { expanded = true }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.hasFocus },
        )
        // A plain popup rather than a DropdownMenu. The menu grows in and fades out every time it
        // opens and shuts, and it hops above the field whenever a longer list would not fit
        // under it — typing a word, letter by letter, made it flicker between the two. This one
        // stays put under the field, does not animate, and only changes what is in it.
        if (open) {
            val gap = with(density) { 4.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { UnderField(gap) },
                onDismissRequest = {
                    expanded = false
                    settledOn = value
                },
                // Not focusable, or it takes the keyboard off the field the moment it appears
                // and the next letter goes nowhere.
                properties = PopupProperties(focusable = false),
            ) {
                SuggestionList(
                    header = if (offering) stringResource(R.string.suggest_already_in) else null,
                    names = shown,
                    // At least wide enough for a name: the Type field is the narrow half of a row.
                    width = maxOf(fieldWidth, 220.dp),
                    onPick = { option ->
                        onValueChange(option)
                        settledOn = option
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SuggestionList(
    header: String?,
    names: List<String>,
    width: Dp,
    onPick: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        shadowElevation = 6.dp,
        modifier = Modifier.width(width),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        ) {
            if (header != null) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            names.forEach { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Always straight under the field it belongs to — never flipped above it. */
private class UnderField(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val start = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
        } else {
            anchorBounds.right - popupContentSize.width
        }
        val x = start.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        return IntOffset(x, anchorBounds.bottom + gap)
    }
}
