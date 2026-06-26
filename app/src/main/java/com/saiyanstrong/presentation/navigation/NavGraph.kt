package com.saiyanstrong.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saiyanstrong.presentation.screens.exercises.ExerciseBrowserScreen
import com.saiyanstrong.presentation.screens.history.HistoryScreen
import com.saiyanstrong.presentation.screens.home.HomeScreen
import com.saiyanstrong.presentation.screens.session_complete.SessionCompleteScreen
import com.saiyanstrong.presentation.screens.settings.SettingsScreen
import com.saiyanstrong.presentation.screens.workout.ActiveWorkoutScreen
import com.saiyanstrong.presentation.theme.NeonGreen
import com.saiyanstrong.presentation.theme.SaiyanGray

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val TABS = listOf(
    BottomTab(Screen.Home.route,      "Home",      Icons.Default.Home),
    BottomTab(Screen.History.route,   "History",   Icons.Default.History),
    BottomTab(Screen.ActiveWorkout.route, "Workout", Icons.Default.FitnessCenter),
    BottomTab(Screen.Exercises.route, "Exercises", Icons.Default.FormatListBulleted),
    BottomTab(Screen.Settings.route,  "Settings",  Icons.Default.Settings)
)

private val ROUTES_WITHOUT_BOTTOM_NAV = setOf(
    Screen.ActiveWorkout.route,
    "session_complete"   // prefix match below
)

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.Home.route

    val showBottomBar = currentRoute != Screen.ActiveWorkout.route &&
            !currentRoute.startsWith("session_complete")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = SaiyanGray) {
                    TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(tab.icon, contentDescription = tab.label)
                            },
                            label = { Text(tab.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NeonGreen,
                                selectedTextColor = NeonGreen,
                                indicatorColor = NeonGreen.copy(alpha = 0.12f),
                                unselectedIconColor = Color.White.copy(alpha = 0.5f),
                                unselectedTextColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onStartWorkout = { navController.navigate(Screen.ActiveWorkout.route) },
                    onViewHistory = { navController.navigate(Screen.History.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.ActiveWorkout.route) {
                ActiveWorkoutScreen(
                    onWorkoutFinished = { sessionId ->
                        navController.navigate(Screen.SessionComplete.createRoute(sessionId)) {
                            popUpTo(Screen.Home.route)
                        }
                    },
                    onViewHistory = { navController.navigate(Screen.History.route) }
                )
            }

            composable(
                route = Screen.SessionComplete.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) {
                SessionCompleteScreen(
                    onDone = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onDeleted = { navController.popBackStack() }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate(Screen.SessionComplete.createRoute(sessionId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Exercises.route) {
                ExerciseBrowserScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
