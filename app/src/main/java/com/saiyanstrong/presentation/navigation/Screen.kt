package com.saiyanstrong.presentation.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding?replay={replay}") {
        fun createRoute(replay: Boolean = false) = "onboarding?replay=$replay"
    }
    data object Home           : Screen("home")
    data object WorkoutLanding : Screen("workout_landing")
    data object ActiveWorkout  : Screen("workout?templateId={templateId}&repeatLast={repeatLast}") {
        fun createRoute(templateId: Long = -1L, repeatLast: Boolean = false) =
            "workout?templateId=$templateId&repeatLast=$repeatLast"
    }
    data object SessionComplete: Screen("session_complete/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_complete/$sessionId"
    }
    data object History        : Screen("history")
    data object ExerciseDetail : Screen("exercise/{exerciseId}") {
        fun createRoute(exerciseId: Int) = "exercise/$exerciseId"
    }
    data object Exercises : Screen("exercises")
    data object Settings  : Screen("settings")
}
