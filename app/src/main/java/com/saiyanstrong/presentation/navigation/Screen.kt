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
    data object CoachSettings : Screen("coach_settings")
    data object CoachDashboard : Screen("coach_dashboard")
    data object AthleteDetail : Screen("athlete/{athleteId}") {
        fun createRoute(athleteId: String) = "athlete/$athleteId"
    }
    data object BarPathCapture : Screen("bar_path_capture?exerciseId={exerciseId}&setLogId={setLogId}&weightKg={weightKg}") {
        /** Set-linked, from the workout ⋮ menu — behaves exactly as before this route changed shape. */
        fun createRoute(exerciseId: Int, setLogId: Long, weightKg: Double) =
            "bar_path_capture?exerciseId=$exerciseId&setLogId=$setLogId&weightKg=${weightKg.toFloat()}"

        /** Standalone, from the Home card — no set, no weight known yet. */
        fun createStandaloneRoute(exerciseId: Int) =
            "bar_path_capture?exerciseId=$exerciseId&setLogId=-1&weightKg=-1"
    }

    data object BiomechanicsSelection : Screen("biomechanics")
    data object CustomProportions : Screen("biomechanics_custom")
    data object BiomechanicsLiftSelector : Screen("biomechanics_lift/{archetype}") {
        fun createRoute(archetype: String) = "biomechanics_lift/$archetype"
    }
    data object BiomechanicsVisualizer : Screen("biomechanics_visualizer/{archetype}/{lift}") {
        fun createRoute(archetype: String, lift: String) = "biomechanics_visualizer/$archetype/$lift"
    }
    data object BiomechanicsCompare : Screen("biomechanics_compare/{archetypes}/{lift}") {
        /** [archetypes] is a comma-separated enum-name list (2 for the visualizer's "Compare
         * with another build", 4 for selection's "Compare all four"). */
        fun createRoute(archetypes: List<String>, lift: String) =
            "biomechanics_compare/${archetypes.joinToString(",")}/$lift"
    }
}
