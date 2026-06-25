package com.saiyanstrong.presentation.navigation

sealed class Screen(val route: String) {
    data object Home           : Screen("home")
    data object ActiveWorkout  : Screen("workout")
    data object SessionComplete: Screen("session_complete/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_complete/$sessionId"
    }
    data object History        : Screen("history")
    data object ExerciseDetail : Screen("exercise/{exerciseId}") {
        fun createRoute(exerciseId: Int) = "exercise/$exerciseId"
    }
}
