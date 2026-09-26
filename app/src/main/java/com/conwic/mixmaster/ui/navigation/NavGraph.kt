package com.conwic.mixmaster.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.conwic.mixmaster.ui.calculator.CalculatorScreen
import com.conwic.mixmaster.ui.calculator.CoatHandover
import com.conwic.mixmaster.ui.calendarscreen.CalendarScreen
import com.conwic.mixmaster.ui.components.BottomNavBar
import com.conwic.mixmaster.ui.components.rememberMotionOff
import com.conwic.mixmaster.ui.onboarding.OnboardingScreen
import com.conwic.mixmaster.ui.products.AddEditProductScreen
import com.conwic.mixmaster.ui.products.ProductDetailScreen
import com.conwic.mixmaster.ui.projects.ProjectDetailScreen
import com.conwic.mixmaster.ui.solutions.SolutionEditorScreen
import com.conwic.mixmaster.ui.sharing.CompanySharingScreen
import com.conwic.mixmaster.ui.sharing.JoinLinks
import com.conwic.mixmaster.ui.signin.SignInScreen
import com.conwic.mixmaster.ui.warehouse.StockCountReminder
import com.conwic.mixmaster.ui.warehouse.StockCountScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MixMasterNavGraph(startDestination: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    // The bar shows while the tabs are on top, and marks whichever of them TabHost is showing.
    val tabsEntry = backStackEntry?.takeIf { it.destination.route == Routes.HOME }
    val tabFlow: StateFlow<String?> = remember(tabsEntry) {
        tabsEntry?.savedStateHandle?.getStateFlow(SelectedTab, Routes.HOME) ?: MutableStateFlow<String?>(null)
    }
    val tab by tabFlow.collectAsState()
    // Read once, out here: the transition lambdas below are not composable, so they cannot ask.
    val calm = rememberMotionOff()

    val ready = pastSignIn(backStackEntry)

    // An invite link was tapped: to the join screen, with the code already in it.
    val joinLink by JoinLinks.pending.collectAsState()
    LaunchedEffect(joinLink != null, ready) {
        if (joinLink != null && ready) {
            navController.navigate(Routes.COMPANY_SHARING) { launchSingleTop = true }
        }
    }

    // The count reminder was tapped: straight to the count, once the app is past sign-in.
    val openCount by StockCountReminder.openRequested.collectAsState()
    LaunchedEffect(openCount, ready) {
        if (openCount && ready) {
            StockCountReminder.openRequested.value = false
            navController.navigate(Routes.STOCK_COUNT) { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            if (tabsEntry != null) {
                BottomNavBar(currentRoute = tab) { route ->
                    if (route != tab) navController.navigateToTopLevel(route)
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            // A push, not a cross-fade. The default fade draws the old screen and the new one
            // on top of each other for a few frames, which on site reads as the app glitching;
            // and a full-width slide shoves the page sideways under your thumb. This is an
            // eighth of the width and a fifth of a second: enough to say which way you went,
            // gone before you have finished the tap.
            //
            // The page that is leaving moves less than the page arriving, so they do not travel
            // as one sheet — the new screen reads as coming over the old one rather than the two
            // of them being dragged across together. Going back runs it the other way, which is
            // the only thing on screen that says "back" when the gesture came from the edge.
            enterTransition = { if (calm) EnterTransition.None else pushIn(forward = true) },
            exitTransition = { if (calm) ExitTransition.None else pushOut(forward = true) },
            popEnterTransition = { if (calm) EnterTransition.None else pushIn(forward = false) },
            popExitTransition = { if (calm) ExitTransition.None else pushOut(forward = false) },
            // fillMaxSize, not padding(insets): the bottom bar is only on the top-level screens,
            // so padding the NavHost made it change size when you opened a product or a project.
            // Navigation animates that size change with a spring — which is the "new card sliding
            // in". The inset is applied inside each destination instead, where it costs nothing.
            modifier = Modifier.fillMaxSize(),
            // No size animation: every page fills the screen, so there is never a size worth
            // animating, and the default one is what grew pages out of a box in the middle of
            // the screen. (It still runs when navigation drops a transition — see TabHost.)
            sizeTransform = null,
        ) {
            composable(Routes.SIGN_IN) {
                Inset(insets) {
                    SignInScreen(
                        onContinue = {
                            navController.navigate(Routes.ONBOARDING) {
                                popUpTo(Routes.SIGN_IN) { inclusive = true }
                            }
                        },
                    )
                }
            }
            composable(Routes.ONBOARDING) {
                Inset(insets) {
                    OnboardingScreen(
                        onDone = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
            }
            // All five bottom-nav pages live in this one destination — see TabHost for why.
            composable(Routes.HOME) { entry -> Inset(insets) { TabHost(entry, navController, calm) } }
            composable(
                route = Routes.CALCULATOR,
                arguments = listOf(
                    navArgument(Routes.CALCULATOR_SOLUTION) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    // Carried as text rather than as numbers: a query argument that was never
                    // given has to read as absent, and a missing float would arrive as 0.
                    navArgument(Routes.CALCULATOR_AREA) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.CALCULATOR_DOSE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.CALCULATOR_COATS) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.CALCULATOR_JOB) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(Routes.CALCULATOR_PROJECT) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument(Routes.CALCULATOR_ROOM) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument(Routes.CALCULATOR_LAYER) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                val args = entry.arguments
                Inset(insets) {
                    CalculatorScreen(
                        navController = navController,
                        solutionId = args?.getLong(Routes.CALCULATOR_SOLUTION) ?: 0L,
                        handover = CoatHandover.fromRoute(
                            area = args?.getString(Routes.CALCULATOR_AREA),
                            dose = args?.getString(Routes.CALCULATOR_DOSE),
                            coats = args?.getString(Routes.CALCULATOR_COATS),
                            job = args?.getString(Routes.CALCULATOR_JOB),
                            projectId = args?.getLong(Routes.CALCULATOR_PROJECT) ?: 0L,
                            roomId = args?.getLong(Routes.CALCULATOR_ROOM) ?: 0L,
                            layerId = args?.getLong(Routes.CALCULATOR_LAYER) ?: 0L,
                        ),
                    )
                }
            }
            composable(
                route = Routes.PRODUCT_DETAIL,
                arguments = listOf(navArgument("productId") { type = NavType.LongType }),
            ) { entry ->
                val productId = entry.arguments?.getLong("productId") ?: 0L
                Inset(insets) { ProductDetailScreen(navController = navController, productId = productId) }
            }
            composable(Routes.PRODUCT_ADD) {
                Inset(insets) { AddEditProductScreen(navController = navController, productId = null) }
            }
            composable(
                route = Routes.PRODUCT_EDIT,
                arguments = listOf(navArgument("productId") { type = NavType.LongType }),
            ) { entry ->
                val productId = entry.arguments?.getLong("productId") ?: 0L
                Inset(insets) { AddEditProductScreen(navController = navController, productId = productId) }
            }
            composable(Routes.SOLUTION_ADD) {
                Inset(insets) { SolutionEditorScreen(navController = navController, solutionId = null) }
            }
            composable(
                route = Routes.SOLUTION_EDIT,
                arguments = listOf(navArgument("solutionId") { type = NavType.LongType }),
            ) { entry ->
                Inset(insets) {
                    SolutionEditorScreen(
                        navController = navController,
                        solutionId = entry.arguments?.getLong("solutionId") ?: 0L,
                    )
                }
            }
            composable(
                route = Routes.PROJECT_DETAIL,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                Inset(insets) { ProjectDetailScreen(navController = navController, projectId = projectId) }
            }
            composable(Routes.CALENDAR) { Inset(insets) { CalendarScreen(navController = navController) } }
            composable(Routes.STOCK_COUNT) { Inset(insets) { StockCountScreen(navController = navController) } }
            composable(Routes.COMPANY_SHARING) { Inset(insets) { CompanySharingScreen(navController = navController) } }
        }
    }
}

/** Anywhere but the first-run screens, where a link or a reminder would pull the rug out. */
private fun pastSignIn(entry: NavBackStackEntry?): Boolean {
    val route = entry?.destination?.route
    return route != null && route != Routes.SIGN_IN && route != Routes.ONBOARDING
}

/** Applies the bottom-bar inset inside a destination, so the NavHost itself never changes size. */
@Composable
private fun Inset(insets: PaddingValues, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(insets)) { content() }
}

/** How long a page takes to come over the one behind it, and the curve it does it on. */
internal const val PageMillis = 220
internal val PageEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** A page stepping in, from the right going deeper and from the left coming back. */
private fun pushIn(forward: Boolean): EnterTransition =
    slideInHorizontally(tween(PageMillis, easing = PageEase)) { if (forward) it / 8 else -it / 14 } +
        fadeIn(tween(PageMillis - 60))

/** The page it covers, moving a fraction of the distance so they never travel as one sheet. */
private fun pushOut(forward: Boolean): ExitTransition =
    slideOutHorizontally(tween(PageMillis, easing = PageEase)) { if (forward) -it / 14 else it / 8 } +
        fadeOut(tween(PageMillis - 90))
