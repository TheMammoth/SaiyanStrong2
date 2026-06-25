package com.saiyanstrong.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saiyanstrong.presentation.screens.history.HistoryScreen
import com.saiyanstrong.presentation.screens.home.HomeScreen
import com.saiyanstrong.presentation.screens.session_complete.SessionCompleteScreen
import com.saiyanstrong.presentation.screens.workout.ActiveWorkoutScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartWorkout = { navController.navigate(Screen.ActiveWorkout.route) },
                onViewHistory = { navController.navigate(Screen.History.route) }
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
                }
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
    }
}
