package com.conwic.mixmaster.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.conwic.mixmaster.ui.calculator.CalculatorScreen
import com.conwic.mixmaster.ui.calendarscreen.CalendarScreen
import com.conwic.mixmaster.ui.components.BottomNavBar
import com.conwic.mixmaster.ui.home.HomeScreen
import com.conwic.mixmaster.ui.onboarding.OnboardingScreen
import com.conwic.mixmaster.ui.products.AddEditProductScreen
import com.conwic.mixmaster.ui.products.ProductDetailScreen
import com.conwic.mixmaster.ui.products.ProductsScreen
import com.conwic.mixmaster.ui.projects.ProjectDetailScreen
import com.conwic.mixmaster.ui.projects.ProjectsScreen
import com.conwic.mixmaster.ui.settings.SettingsScreen
import com.conwic.mixmaster.ui.warehouse.WarehouseScreen
import com.conwic.mixmaster.ui.signin.SignInScreen

@Composable
fun MixMasterNavGraph(startDestination: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
            // No transitions. The default cross-fade draws the old and new screen on top of each
            // other for a few frames, which on site reads as the app glitching, and the slide
            // shoves the page sideways under your thumb.
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
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
            composable(Routes.CALCULATOR) { Inset(insets) { CalculatorScreen(navController = navController) } }
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
