package com.conwic.mixmaster.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.conwic.mixmaster.R

/** The first entry of every filter list — kept in English, because it is a value, not a word. */
const val FilterAll = "All"

/**
 * A dropdown filter whose first entry is [FilterAll].
 *
 * The lists hold "All" as their stored value, and it was shown as stored: an Estonian calculator
 * offered "Bränd: All". It is shown here in the app's language and handed back as the value.
 */
@Composable
fun FilterField(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = stringResource(R.string.status_all)
    val shown = options.map { if (it == FilterAll) all else it }
    DropdownField(
        label = label,
        selected = if (selected == FilterAll) all else selected,
        options = shown,
        onSelect = { picked -> onSelect(options.getOrElse(shown.indexOf(picked)) { picked }) },
        modifier = modifier,
    )
}
