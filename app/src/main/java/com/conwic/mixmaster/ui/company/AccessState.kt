package com.conwic.mixmaster.ui.company

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.conwic.mixmaster.data.company.Access
import com.conwic.mixmaster.data.company.CompanyStore
import com.conwic.mixmaster.data.model.Role
import com.conwic.mixmaster.ui.LocalAppContainer

/**
 * What this phone may change, for a screen deciding which buttons to show.
 *
 * Starts from the company link, which is known at once, so a worker's phone never shows an edit
 * button for the frame it takes the role to load.
 */
@Composable
fun rememberAccess(): Access {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val initial = remember { Access.of(Role.EMPLOYER, CompanyStore.current(context.applicationContext)) }
    val access by container.access.collectAsState(initial = initial)
    return access
}
