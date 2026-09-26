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
 *
 * A blank entry — the brand of something with none, like the tap water — is shown as "Other",
 * the word the stock count already files those under. It was a blank line in the list, and
 * picking it left the field looking empty.
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
    val other = stringResource(R.string.sc_no_brand)
    fun word(value: String) = when {
        value == FilterAll -> all
        value.isBlank() -> other
        else -> value
    }
    val shown = options.map(::word)
    DropdownField(
        label = label,
        selected = word(selected),
        options = shown,
        onSelect = { picked -> onSelect(options.getOrElse(shown.indexOf(picked)) { picked }) },
        modifier = modifier,
    )
}
