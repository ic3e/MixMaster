package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one way a product says what it is, wherever it turns up.
 *
 * The app had grown four of them: the maker set small over the name in the Products and
 * Warehouse lists, a dark pill beside the name on the headers and the calculator, "Brand — Name"
 * run together on one line in the pickers, and the maker set plain and grey off to the right on
 * the project's material list. Four screens, four ways of saying the same thing — which is what
 * made moving between them feel like moving between apps.
 *
 * The maker goes over the name rather than beside it because a row has one line to give and a
 * product name takes all of it: "Microtopping® Base Coat Polymer" beside a pill left the two of
 * them meeting in the middle, and a dark pill at a size no other list used was the loudest thing
 * on a screen full of quiet rows.
 */
@Composable
fun ProductIdentity(
    name: String,
    brand: String,
    modifier: Modifier = Modifier,
    /** The line under the name: the pack it comes in, how many coats it has, what it goes on at. */
    detail: String? = null,
    /** For the head of a screen or a sheet, where the name is a heading rather than a row. */
    large: Boolean = false,
) {
    Column(modifier = modifier) {
        // Water has no maker, and an empty line still takes a line's height.
        if (brand.isNotBlank()) {
            Text(
                text = brand,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = name,
            style = if (large) {
                MaterialTheme.typography.headlineMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
