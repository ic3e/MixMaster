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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.conwic.mixmaster.ui.home.HomeScreen
import com.conwic.mixmaster.ui.onboarding.OnboardingScreen
import com.conwic.mixmaster.ui.products.AddEditProductScreen
import com.conwic.mixmaster.ui.products.ProductDetailScreen
import com.conwic.mixmaster.ui.products.ProductsScreen
import com.conwic.mixmaster.ui.projects.ProjectDetailScreen
import com.conwic.mixmaster.ui.projects.ProjectsScreen
import com.conwic.mixmaster.ui.settings.SettingsScreen
import com.conwic.mixmaster.ui.solutions.SolutionEditorScreen
import com.conwic.mixmaster.ui.warehouse.WarehouseScreen
import com.conwic.mixmaster.ui.signin.SignInScreen

@Composable
fun MixMasterNavGraph(startDestination: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Read once, out here: the transition lambdas below are not composable, so they cannot ask.
    val calm = rememberMotionOff()

    Scaffold(
        bottomBar = {
            if (currentRoute in Routes.bottomNavRoutes) {
                BottomNavBar(currentRoute = currentRoute) { route ->
                    if (route != currentRoute) navController.navigateToTopLevel(route)
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
            enterTransition = {
                if (calm) {
                    EnterTransition.None
                } else {
                    slideInHorizontally(tween(PageMillis, easing = PageEase)) { it / 8 } +
                        fadeIn(tween(PageMillis - 60))
                }
            },
            exitTransition = {
                if (calm) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(tween(PageMillis, easing = PageEase)) { -it / 14 } +
                        fadeOut(tween(PageMillis - 90))
                }
            },
            popEnterTransition = {
                if (calm) {
                    EnterTransition.None
                } else {
                    slideInHorizontally(tween(PageMillis, easing = PageEase)) { -it / 14 } +
                        fadeIn(tween(PageMillis - 60))
                }
            },
            popExitTransition = {
                if (calm) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(tween(PageMillis, easing = PageEase)) { it / 8 } +
                        fadeOut(tween(PageMillis - 90))
                }
            },
            // fillMaxSize, not padding(insets): the bottom bar is only on the top-level screens,
            // so padding the NavHost made it change size when you opened a product or a project.
            // Navigation animates that size change with a spring — which is the "new card sliding
            // in". The inset is applied inside each destination instead, where it costs nothing.
            modifier = Modifier.fillMaxSize(),
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
            composable(Routes.HOME) { Inset(insets) { HomeScreen(navController = navController) } }
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
            composable(Routes.WAREHOUSE) {
                Inset(insets) { WarehouseScreen(navController = navController) }
            }
            composable(Routes.PRODUCTS) { Inset(insets) { ProductsScreen(navController = navController) } }
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
            composable(Routes.PROJECTS) { Inset(insets) { ProjectsScreen(navController = navController) } }
            composable(
                route = Routes.PROJECT_DETAIL,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                Inset(insets) { ProjectDetailScreen(navController = navController, projectId = projectId) }
            }
            composable(Routes.CALENDAR) { Inset(insets) { CalendarScreen(navController = navController) } }
            composable(Routes.SETTINGS) { Inset(insets) { SettingsScreen(navController = navController) } }
        }
    }
}

/** Applies the bottom-bar inset inside a destination, so the NavHost itself never changes size. */
@Composable
private fun Inset(insets: PaddingValues, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(insets)) { content() }
}

/** How long a page takes to come over the one behind it, and the curve it does it on. */
private const val PageMillis = 220
private val PageEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
