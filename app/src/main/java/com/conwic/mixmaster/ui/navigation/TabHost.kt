package com.conwic.mixmaster.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.conwic.mixmaster.ui.home.HomeScreen
import com.conwic.mixmaster.ui.products.ProductsScreen
import com.conwic.mixmaster.ui.projects.ProjectsScreen
import com.conwic.mixmaster.ui.settings.SettingsScreen
import com.conwic.mixmaster.ui.warehouse.WarehouseScreen

/** Where the Home entry keeps which bottom-nav tab is showing. Saved state, so it survives the app being killed. */
internal const val SelectedTab = "selectedTab"

/**
 * The five bottom-nav pages, all inside the one navigation destination ([Routes.HOME]).
 *
 * They used to be five destinations, and every tap on the bar popped one off the back stack and
 * pushed the next. Now and then — about one tap in twenty when going along the bar quickly —
 * navigation lost track of the page being left in the middle of that. When it does, it drops
 * the transition altogether: the old page vanishes in a single frame and the new one is then
 * grown out of a dot in the middle of the screen by its own size animation, starting from
 * nothing. That is the odd transition in the screen recordings, and it lives inside the
 * library, out of reach of any setting.
 *
 * So the bar no longer goes through navigation at all. Switching tabs is a change of one saved
 * value, animated right here, and it is the same fade every time. Navigation is left to do what
 * it is good at: going into a product or a project and coming back.
 */
@Composable
internal fun TabHost(entry: NavBackStackEntry, navController: NavHostController, calm: Boolean) {
    val handle = entry.savedStateHandle
    val tab by remember(handle) { handle.getStateFlow(SelectedTab, Routes.HOME) }.collectAsState()
    val stores: TabStores = viewModel(viewModelStoreOwner = entry) { TabStores() }

    // A tab switched from while a detail page was on top (the calculator's "Open projects")
    // never had its page on screen to let go of it, so it lets go here instead.
    LaunchedEffect(stores) { stores.keepOnly(handle.get<String>(SelectedTab) ?: Routes.HOME) }

    // Back from any other tab goes to Home first, as it did when they were stacked on top of it.
    BackHandler(enabled = tab != Routes.HOME) { handle[SelectedTab] = Routes.HOME }

    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            // using(null): no size animation. Every tab fills the screen; there is nothing to grow.
            if (calm) {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            } else {
                (tabIn() togetherWith tabOut()).using(null)
            }
        },
        contentAlignment = Alignment.TopStart,
        modifier = Modifier.fillMaxSize(),
        label = "tab",
    ) { route ->
        // Each visit to a tab gets its own view models, dropped once you have moved on, the same
        // as when every tab was its own back-stack entry: a page you come back to starts fresh,
        // while one you only covered with a detail page (or turned the phone on) keeps its place.
        val owner = remember(route) { stores.owner(route) }
        DisposableEffect(route) {
            onDispose { if (handle.get<String>(SelectedTab) != route) stores.drop(route) }
        }
        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            when (route) {
                Routes.WAREHOUSE -> WarehouseScreen(navController = navController)
                Routes.PRODUCTS -> ProductsScreen(navController = navController)
                Routes.PROJECTS -> ProjectsScreen(navController = navController)
                Routes.SETTINGS -> SettingsScreen(navController = navController)
                else -> HomeScreen(navController = navController)
            }
        }
    }
}

/** The view models of each tab, held by the Home entry so they outlive a detail page covering them. */
internal class TabStores : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun owner(route: String): ViewModelStoreOwner {
        val store = stores.getOrPut(route) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    fun drop(route: String) {
        stores.remove(route)?.clear()
    }

    fun keepOnly(route: String) {
        (stores.keys - route).forEach(::drop)
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/**
 * A tab arriving, and the one it replaces leaving — one after the other, never at the same time.
 *
 * The old one is gone in [TabFadeMillis] and only then does the new one come up, growing the
 * last fraction of the way in. Nothing is ever half-drawn over anything else.
 */
private const val TabFadeMillis = 90

private fun tabIn(): EnterTransition =
    fadeIn(tween(PageMillis - TabFadeMillis, delayMillis = TabFadeMillis)) +
        scaleIn(
            initialScale = 0.94f,
            animationSpec = tween(PageMillis - TabFadeMillis, delayMillis = TabFadeMillis, easing = PageEase),
        )

private fun tabOut(): ExitTransition = fadeOut(tween(TabFadeMillis, easing = LinearEasing))
