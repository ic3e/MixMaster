package com.conwic.mixmaster.ui.navigation

import androidx.navigation.NavHostController

/**
 * Switches to one of the five bottom-nav destinations.
 *
 * Everything above Home is cleared first, so tabs can never stack on top of each other, and
 * Home is popped as well when it is the target so that tapping Home always lands on a fresh
 * Home rather than being a no-op against an entry that is already there.
 *
 * Deliberately no saveState/restoreState: restoring a saved back stack on top of the target
 * could put the screen you had just left straight back in front of you, which made the Home
 * tab look like it had stopped responding.
 */
fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { inclusive = route == Routes.HOME }
        launchSingleTop = true
    }
}
