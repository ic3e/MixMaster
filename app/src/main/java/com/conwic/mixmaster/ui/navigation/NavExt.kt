package com.conwic.mixmaster.ui.navigation

import androidx.navigation.NavHostController

/**
 * Switches to one of the five bottom-nav pages.
 *
 * They all live inside the Home destination (see [TabHost]), so this only changes which one it
 * shows, and closes whatever detail page is open on top of it. Nothing is pushed or popped
 * between the tabs themselves.
 */
fun NavHostController.navigateToTopLevel(route: String) {
    val tabs = runCatching { getBackStackEntry(Routes.HOME) }.getOrNull()
    if (tabs == null) {
        // Only reachable from before Home exists (sign-in, onboarding): start it, then pick.
        navigate(Routes.HOME) { popUpTo(graph.id) { inclusive = true } }
        currentBackStackEntry?.savedStateHandle?.set(SelectedTab, route)
        return
    }
    tabs.savedStateHandle[SelectedTab] = route
    if (currentBackStackEntry?.id != tabs.id) popBackStack(Routes.HOME, inclusive = false)
}
