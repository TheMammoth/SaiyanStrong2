package com.saiyanstrong.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saiyanstrong.presentation.screens.barpath.BarPathCaptureScreen
import com.saiyanstrong.presentation.screens.biomechanics.ArchetypeSelectionScreen
import com.saiyanstrong.presentation.screens.biomechanics.BiomechanicsCompareScreen
import com.saiyanstrong.presentation.screens.biomechanics.BiomechanicsVisualizerScreen
import com.saiyanstrong.presentation.screens.biomechanics.CustomProportionsScreen
import com.saiyanstrong.presentation.screens.biomechanics.LiftSelectorScreen
import com.saiyanstrong.domain.model.Archetype
import com.saiyanstrong.domain.model.LiftType
import com.saiyanstrong.presentation.screens.coach.AthleteDetailScreen
import com.saiyanstrong.presentation.screens.coach.CoachDashboardScreen
import com.saiyanstrong.presentation.screens.coach.CoachSettingsScreen
import com.saiyanstrong.presentation.screens.exercises.ExerciseBrowserScreen
import com.saiyanstrong.presentation.screens.exercises.ExerciseDetailScreen
import com.saiyanstrong.presentation.screens.history.HistoryScreen
import com.saiyanstrong.presentation.screens.home.HomeScreen
import com.saiyanstrong.presentation.screens.onboarding.OnboardingScreen
import com.saiyanstrong.presentation.screens.session_complete.SessionCompleteScreen
import com.saiyanstrong.presentation.screens.settings.SettingsScreen
import com.saiyanstrong.presentation.screens.workout.ActiveWorkoutScreen
import com.saiyanstrong.presentation.screens.workout.WorkoutLandingScreen
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
    BottomTab(Screen.WorkoutLanding.route, "Workout", Icons.Default.FitnessCenter),
    BottomTab(Screen.Exercises.route, "Exercises", Icons.Default.FormatListBulleted),
    BottomTab(Screen.BiomechanicsSelection.route, "Body", Icons.Default.Accessibility),
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
            currentRoute != Screen.Onboarding.route &&
            !currentRoute.startsWith("session_complete") &&
            !currentRoute.startsWith("bar_path_capture") &&
            !currentRoute.startsWith("biomechanics_lift") &&
            !currentRoute.startsWith("biomechanics_visualizer") &&
            !currentRoute.startsWith("biomechanics_compare") &&
            !currentRoute.startsWith("biomechanics_custom")

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
            startDestination = Screen.Onboarding.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Onboarding.route,
                arguments = listOf(navArgument("replay") { type = NavType.BoolType; defaultValue = false })
            ) { backStackEntry ->
                val replay = backStackEntry.arguments?.getBoolean("replay") ?: false
                OnboardingScreen(
                    onFinished = {
                        if (replay) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    onStartWorkout = { navController.navigate(Screen.WorkoutLanding.route) },
                    onViewHistory = { navController.navigate(Screen.History.route) },
                    onSettings = { navController.navigate(Screen.Settings.route) },
                    onStartBarPathAnalysis = { exerciseId ->
                        navController.navigate(Screen.BarPathCapture.createStandaloneRoute(exerciseId))
                    },
                    onOpenCoachUpgrade = { navController.navigate(Screen.CoachSettings.route) }
                )
            }

            composable(Screen.WorkoutLanding.route) {
                WorkoutLandingScreen(
                    onStartEmpty = { navController.navigate(Screen.ActiveWorkout.createRoute()) },
                    onStartTemplate = { templateId ->
                        navController.navigate(Screen.ActiveWorkout.createRoute(templateId = templateId))
                    },
                    onRepeatLast = { navController.navigate(Screen.ActiveWorkout.createRoute(repeatLast = true)) }
                )
            }

            composable(
                route = Screen.ActiveWorkout.route,
                arguments = listOf(
                    navArgument("templateId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("repeatLast") { type = NavType.BoolType; defaultValue = false }
                )
            ) {
                ActiveWorkoutScreen(
                    onWorkoutFinished = { sessionId ->
                        navController.navigate(Screen.SessionComplete.createRoute(sessionId)) {
                            popUpTo(Screen.Home.route)
                        }
                    },
                    onViewHistory = { navController.navigate(Screen.History.route) },
                    onRecordBarPath = { exerciseId, setLogId, weightKg ->
                        navController.navigate(Screen.BarPathCapture.createRoute(exerciseId, setLogId, weightKg))
                    }
                )
            }

            composable(
                route = Screen.BarPathCapture.route,
                arguments = listOf(
                    navArgument("exerciseId") { type = NavType.IntType },
                    navArgument("setLogId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("weightKg") { type = NavType.FloatType; defaultValue = -1f }
                )
            ) {
                BarPathCaptureScreen(onDone = { navController.popBackStack() })
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
                ExerciseBrowserScreen(
                    onExerciseClick = { exerciseId ->
                        navController.navigate(Screen.ExerciseDetail.createRoute(exerciseId))
                    }
                )
            }

            composable(
                route = Screen.ExerciseDetail.route,
                arguments = listOf(navArgument("exerciseId") { type = NavType.IntType })
            ) {
                ExerciseDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onReplayIntro = { navController.navigate(Screen.Onboarding.createRoute(replay = true)) },
                    onManageCoachMode = { navController.navigate(Screen.CoachSettings.route) }
                )
            }

            composable(Screen.CoachSettings.route) {
                CoachSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onViewDashboard = { navController.navigate(Screen.CoachDashboard.route) }
                )
            }

            composable(Screen.CoachDashboard.route) {
                CoachDashboardScreen(
                    onBack = { navController.popBackStack() },
                    onAthleteClick = { athleteId -> navController.navigate(Screen.AthleteDetail.createRoute(athleteId)) }
                )
            }

            composable(
                route = Screen.AthleteDetail.route,
                arguments = listOf(navArgument("athleteId") { type = NavType.StringType })
            ) {
                AthleteDetailScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.BiomechanicsSelection.route) {
                ArchetypeSelectionScreen(
                    onArchetypeChosen = { archetype ->
                        if (archetype == Archetype.CUSTOM) {
                            navController.navigate(Screen.CustomProportions.route)
                        } else {
                            navController.navigate(Screen.BiomechanicsLiftSelector.createRoute(archetype.name))
                        }
                    },
                    onCompareAll = {
                        // "Compare all four" — CUSTOM isn't one of the 4 fixed body types this
                        // was built to compare, so it's excluded here even though it's a 5th
                        // Archetype.entries value.
                        val fixedArchetypes = Archetype.entries.filter { it != Archetype.CUSTOM }.map { it.name }
                        navController.navigate(
                            Screen.BiomechanicsCompare.createRoute(fixedArchetypes, LiftType.SQUAT.name)
                        )
                    }
                )
            }

            composable(Screen.CustomProportions.route) {
                CustomProportionsScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = {
                        navController.navigate(
                            Screen.BiomechanicsVisualizer.createRoute(Archetype.CUSTOM.name, LiftType.SQUAT.name)
                        ) {
                            popUpTo(Screen.BiomechanicsSelection.route)
                        }
                    }
                )
            }

            composable(
                route = Screen.BiomechanicsLiftSelector.route,
                arguments = listOf(navArgument("archetype") { type = NavType.StringType })
            ) { backStackEntry ->
                val archetype = Archetype.valueOf(backStackEntry.arguments?.getString("archetype") ?: Archetype.PROPORTIONAL.name)
                LiftSelectorScreen(
                    archetype = archetype,
                    onLiftChosen = { lift ->
                        navController.navigate(Screen.BiomechanicsVisualizer.createRoute(archetype.name, lift.name))
                    }
                )
            }

            composable(
                route = Screen.BiomechanicsVisualizer.route,
                arguments = listOf(
                    navArgument("archetype") { type = NavType.StringType },
                    navArgument("lift") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val archetype = backStackEntry.arguments?.getString("archetype") ?: Archetype.PROPORTIONAL.name
                val lift = backStackEntry.arguments?.getString("lift") ?: LiftType.SQUAT.name
                BiomechanicsVisualizerScreen(
                    onBack = { navController.popBackStack() },
                    onCompare = {
                        navController.navigate(
                            Screen.BiomechanicsCompare.createRoute(
                                listOf(archetype, Archetype.entries.first { it.name != archetype }.name),
                                lift
                            )
                        )
                    }
                )
            }

            composable(
                route = Screen.BiomechanicsCompare.route,
                arguments = listOf(
                    navArgument("archetypes") { type = NavType.StringType },
                    navArgument("lift") { type = NavType.StringType }
                )
            ) {
                BiomechanicsCompareScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
