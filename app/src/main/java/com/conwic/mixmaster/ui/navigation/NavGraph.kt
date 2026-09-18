package com.conwic.mixmaster.ui.navigation

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
            modifier = Modifier.padding(insets),
        ) {
            composable(Routes.SIGN_IN) {
                SignInScreen(
                    onContinue = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.SIGN_IN) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) { HomeScreen(navController = navController) }
            composable(Routes.CALCULATOR) { CalculatorScreen(navController = navController) }
            composable(Routes.PRODUCTS) { ProductsScreen(navController = navController) }
            composable(
                route = Routes.PRODUCT_DETAIL,
                arguments = listOf(navArgument("productId") { type = NavType.LongType }),
            ) { entry ->
                val productId = entry.arguments?.getLong("productId") ?: 0L
                ProductDetailScreen(navController = navController, productId = productId)
            }
            composable(Routes.PRODUCT_ADD) {
                AddEditProductScreen(navController = navController, productId = null)
            }
            composable(
                route = Routes.PRODUCT_EDIT,
                arguments = listOf(navArgument("productId") { type = NavType.LongType }),
            ) { entry ->
                val productId = entry.arguments?.getLong("productId") ?: 0L
                AddEditProductScreen(navController = navController, productId = productId)
            }
            composable(Routes.PROJECTS) { ProjectsScreen(navController = navController) }
            composable(
                route = Routes.PROJECT_DETAIL,
                arguments = listOf(navArgument("projectId") { type = NavType.LongType }),
            ) { entry ->
                val projectId = entry.arguments?.getLong("projectId") ?: 0L
                ProjectDetailScreen(navController = navController, projectId = projectId)
            }
            composable(Routes.CALENDAR) { CalendarScreen(navController = navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController = navController) }
        }
    }
}
