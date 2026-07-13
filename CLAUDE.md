# CLAUDE.md — SaiyanStrong Android App

## Project identity

Native Android powerlifting tracker with a "Saiyan" aesthetic. Dark industrial UI.
Users log sets, earn Power Level (a numeric score), and unlock Super Saiyan evolution
stages as their lifetime training volume grows. The signature feature is the
**Dynamic Exercise Visualizer** — a 3-state animated muscle anatomy pipeline that
activates as a user performs each exercise.

**Package:** `com.saiyanstrong`  
**Min SDK:** 26  
**Target SDK:** 35  
**Language:** Kotlin only. No Java.  
**UI:** Jetpack Compose only. No XML layouts, ever.

---

## Non-negotiable rules

1. **Metric units everywhere, always.** Weight = kg. Volume = kg. Never "lbs", "lb",
   "pounds". The string "lb" must not appear in any Kotlin file, string resource,
   or layout. All weight columns in Room are named `weight_kg`, `total_volume_kg`,
   `estimated_1rm_kg`. All display formatting goes through `WeightFormatter`.

2. **One formatting utility for weight.** `util/WeightFormatter.kt` is the only
   place that converts a `Double` to a display string. No screen formats kg inline.

3. **No hardcoded colors.** Use only `MaterialTheme.colorScheme.*` or tokens from
   `SaiyanTheme.kt`. The theme is dark industrial — matte black backgrounds, neon
   green accents (`#39FF14`), amber/orange for power-level indicators.

4. **StateFlow everywhere.** ViewModels expose `StateFlow<UiState>`, never
   `LiveData`. Screens collect with `collectAsStateWithLifecycle()`.

5. **Room + Flow.** Every DAO query that feeds a screen returns `Flow<T>`.
   One-shot operations (insert, update) are `suspend fun`.

6. **Clean Architecture layers — no skipping.** Screens talk to ViewModels.
   ViewModels call Use Cases. Use Cases call Repository interfaces.
   Repository implementations live in the `data` layer and are never imported
   directly by ViewModels or Use Cases.

7. **Hilt for all injection.** No manual `by lazy { }` dependency construction.
   Every ViewModel is `@HiltViewModel`. Every repository is `@Singleton`.

8. **After every completed task**, update this CLAUDE.md under `## Progress log`
   with what was built. Commit message format: `feat(layer): short description`.

---

## Tech stack

```
Jetpack Compose BOM      2024.12.01
Navigation Compose       2.8.4
Lifecycle / ViewModel    2.8.7
Room                     2.6.1
DataStore Preferences    1.1.1
Hilt                     2.55
KSP                      2.1.0-1.0.29
Lottie Compose           6.6.0
Kotlin Coroutines        1.8.1
Coil Compose             2.7.0
Material Icons Extended  (BOM)
```

All versions live in `gradle/libs.versions.toml`. Never hardcode a version string
in `build.gradle.kts`.

---

## Project structure

```
app/src/main/
├── java/com/saiyanstrong/
│   ├── SaiyanStrongApp.kt          ← @HiltAndroidApp, seeds exercises on first run
│   ├── MainActivity.kt             ← single activity, hosts NavHost
│   │
│   ├── data/
│   │   ├── local/
│   │   │   ├── AppDatabase.kt       ← v5 + all migrations
│   │   │   ├── dao/
│   │   │   │   ├── ExerciseDao.kt
│   │   │   │   ├── SessionDao.kt
│   │   │   │   ├── ExerciseLogDao.kt
│   │   │   │   ├── SetLogDao.kt     ← incl. getHistoryForExercise join
│   │   │   │   ├── TemplateDao.kt
│   │   │   │   └── BodyWeightDao.kt
│   │   │   ├── entity/
│   │   │   │   ├── ExerciseEntity.kt
│   │   │   │   ├── SessionEntity.kt
│   │   │   │   ├── ExerciseLogEntity.kt
│   │   │   │   ├── SetLogEntity.kt
│   │   │   │   ├── TemplateEntity.kt
│   │   │   │   ├── TemplateExerciseEntity.kt
│   │   │   │   └── BodyWeightEntity.kt
│   │   │   └── seed/
│   │   │       └── ExerciseSeeder.kt      ← 150 exercises
│   │   ├── datastore/
│   │   │   └── UserPreferencesDataStore.kt
│   │   ├── mapper/
│   │   │   ├── ExerciseMapper.kt
│   │   │   ├── SessionMapper.kt
│   │   │   └── SetLogMapper.kt
│   │   └── repository/
│   │       ├── ExerciseRepositoryImpl.kt
│   │       ├── SessionRepositoryImpl.kt
│   │       ├── UserRepositoryImpl.kt
│   │       ├── TemplateRepositoryImpl.kt
│   │       └── ExerciseMediaRepositoryImpl.kt  ← free-exercise-db fetch/cache/match
│   │
│   ├── di/
│   │   ├── DatabaseModule.kt
│   │   └── RepositoryModule.kt
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Exercise.kt
│   │   │   ├── MuscleGroup.kt
│   │   │   ├── SetLog.kt
│   │   │   ├── ExerciseLog.kt
│   │   │   ├── WorkoutSession.kt
│   │   │   ├── PowerLevel.kt        ← includes SaiyanStage enum
│   │   │   ├── WorkoutTemplate.kt
│   │   │   ├── BodyWeightLog.kt
│   │   │   ├── ExerciseSetHistory.kt
│   │   │   ├── ExerciseMedia.kt
│   │   │   └── AppUpdate.kt
│   │   ├── repository/
│   │   │   ├── ExerciseRepository.kt
│   │   │   ├── SessionRepository.kt
│   │   │   ├── UserRepository.kt
│   │   │   ├── TemplateRepository.kt
│   │   │   └── ExerciseMediaRepository.kt
│   │   └── usecase/
│   │       ├── LogSetUseCase.kt
│   │       ├── CompleteSessionUseCase.kt
│   │       ├── CalculatePowerLevelUseCase.kt
│   │       ├── EstimateOneRepMaxUseCase.kt
│   │       ├── GetEvolutionStageUseCase.kt
│   │       ├── GetLastSessionSetsUseCase.kt
│   │       └── CheckForUpdateUseCase.kt
│   │
│   ├── presentation/
│   │   ├── navigation/
│   │   │   ├── NavGraph.kt
│   │   │   └── Screen.kt            ← sealed class with routes
│   │   ├── theme/
│   │   │   ├── SaiyanTheme.kt
│   │   │   ├── Color.kt
│   │   │   └── Type.kt
│   │   ├── components/
│   │   │   ├── TelemetryLog.kt      ← typewriter-effect log line
│   │   │   ├── PowerLevelBar.kt     ← Canvas segmented bar (SegmentedBar)
│   │   │   ├── ScouterGauge.kt      ← Home hero: 240° arc gauge + ticks
│   │   │   └── SaiyanButton.kt      ← + scanlineTexture / glow Modifiers
│   │   └── screens/
│   │       ├── home/                ← scouter dashboard
│   │       │   ├── HomeScreen.kt
│   │       │   └── HomeViewModel.kt ← DashboardStats, DOTS, bodyweight, updater
│   │       ├── workout/
│   │       │   ├── WorkoutLandingScreen.kt   ← start empty / templates / repeat last
│   │       │   ├── WorkoutLandingViewModel.kt
│   │       │   ├── ActiveWorkoutScreen.kt    ← set table, rest pill bar
│   │       │   ├── ActiveWorkoutViewModel.kt ← templateId/repeatLast nav args
│   │       │   └── ExercisePickerSheet.kt
│   │       ├── exercises/
│   │       │   ├── ExerciseBrowserScreen.kt  ← search/filter/sort list
│   │       │   ├── ExerciseBrowserViewModel.kt
│   │       │   ├── ExerciseDetailScreen.kt   ← ABOUT/CHARTS/RECORDS/HISTORY tabs
│   │       │   └── ExerciseDetailViewModel.kt
│   │       ├── visualizer/          ← dormant since Sprint 5 (kept, not routed)
│   │       │   ├── VisualizerScreen.kt
│   │       │   ├── VisualizerViewModel.kt
│   │       │   ├── VisualizerState.kt
│   │       │   ├── AnatomyOverlayCanvas.kt
│   │       │   └── ParticleTendrilCanvas.kt
│   │       ├── session_complete/
│   │       │   ├── SessionCompleteScreen.kt  ← volume hero, results, save-as-template
│   │       │   └── SessionCompleteViewModel.kt
│   │       ├── history/
│   │       │   ├── HistoryScreen.kt ← month groups, Sets|Best set cards, swipe delete
│   │       │   └── HistoryViewModel.kt
│   │       └── settings/
│   │           ├── SettingsScreen.kt         ← updates, DOTS formula toggle
│   │           └── SettingsViewModel.kt
│   │
│   └── util/
│       ├── WeightFormatter.kt
│       └── UpdateInstaller.kt       ← direct HTTP APK download + FileProvider
│
└── res/
    ├── assets/anatomy/              ← legacy big-4 PNGs (unused, candidates for removal)
    ├── xml/file_paths.xml           ← FileProvider cache path
    └── values/
        └── strings.xml              ← no weight strings here, all formatted in code
```

---

## Domain models (source of truth)

```kotlin
// MuscleGroup.kt
enum class MuscleGroup {
    QUADRICEPS, HAMSTRINGS, GLUTEUS_MAXIMUS, ERECTOR_SPINAE,
    PECTORALIS_MAJOR, DELTOIDS, TRICEPS, BICEPS,
    LATISSIMUS_DORSI, TRAPEZIUS, RECTUS_ABDOMINIS, CALVES
}

// Exercise.kt
enum class ExerciseCategory { SQUAT, HINGE, PUSH, PULL }

data class Exercise(
    val id: Int,
    val name: String,
    val category: ExerciseCategory,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val lottieAsset: String,
    val svgAssetName: String
)

// SetLog.kt
data class SetLog(
    val id: Long = 0,
    val setNumber: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Float? = null,
    val volumeKg: Double = weightKg * reps,
    val timestampMs: Long = System.currentTimeMillis()
)

// ExerciseLog.kt
data class ExerciseLog(
    val id: Long = 0,
    val exercise: Exercise,
    val sets: List<SetLog>,
    val orderIndex: Int
)

// WorkoutSession.kt
data class WorkoutSession(
    val id: Long = 0,
    val dateMs: Long,
    val durationMs: Long,
    val exerciseLogs: List<ExerciseLog>,
    val totalVolumeKg: Double,
    val powerEarned: Int,
    val notes: String = ""
)

// PowerLevel.kt
enum class SaiyanStage(val label: String, val threshold: Int) {
    BASE("Base Saiyan",       0),
    SSJ1("Super Saiyan",      20_000),
    SSJ2("Super Saiyan 2",    50_000),
    SSJ3("Super Saiyan 3",    120_000),
    SSJ_GOD("Saiyan God",     300_000),
    ULTRA("Ultra Instinct",   750_000)
}

data class PowerLevel(
    val current: Int,
    val stage: SaiyanStage,
    val nextStageThreshold: Int,
    val progressToNext: Float   // 0f..1f
)
```

---

## Room schema (source of truth)

All column names use snake_case. All weight/volume columns end in `_kg`.

```
exercises          : id(PK), name, category, primary_muscles(CSV), secondary_muscles(CSV),
                     lottie_asset, svg_asset_name

sessions           : id(PK autoGen), date_ms, duration_ms, total_volume_kg,
                     power_earned, notes, title

exercise_logs      : id(PK autoGen), session_id(FK→sessions CASCADE),
                     exercise_id(FK→exercises), order_index

set_logs           : id(PK autoGen), exercise_log_id(FK→exercise_logs CASCADE),
                     set_number, weight_kg, reps, rpe(nullable), is_failure,
                     volume_kg, timestamp_ms

templates          : id(PK autoGen), name, created_ms

template_exercises : id(PK autoGen), template_id(FK→templates CASCADE),
                     exercise_id(FK→exercises), order_index

body_weight_logs   : id(PK autoGen), date_ms, weight_kg

bar_path_metrics   : id(PK autoGen), set_log_id(FK→set_logs CASCADE, nullable),
                     exercise_id(FK→exercises), created_at_ms,
                     peak_velocity_ms, mean_concentric_velocity_ms, peak_power_watts,
                     mean_power_watts, range_of_motion_cm, bar_path_deviation_cm,
                     velocity_zone(TEXT)
```

Room DB version: **9**. Migrations: 1→2 sessions.title, 2→3 set_logs.is_failure,
3→4 exercise re-seed (DELETE FROM exercises), 4→5 templates/template_exercises/
body_weight_logs, 5→6 exercises.rest_timer_sec, 6→7 templates.is_from_coach,
7→8 bar_path_metrics, 8→9 bar_path_metrics widened to exercise-scoped (set_log_id
nullable, + exercise_id, + created_at_ms — table-recreate migration, see Sprint 28).
Any future schema change requires a Migration, never `fallbackToDestructiveMigration()`
in production.

---

## Business logic

### Weight formatter

```kotlin
object WeightFormatter {
    fun format(kg: Double): String =
        if (kg == kg.toLong().toDouble()) "${kg.toLong()} kg"
        else "%.1f kg".format(kg)

    fun formatVolume(kg: Double): String =
        if (kg >= 1_000) "%.2f t".format(kg / 1_000)
        else "${kg.toInt()} kg"

    fun formatOneRm(kg: Double): String = "%.1f kg".format(kg)
}
```

### 1RM estimation (Epley formula)

```kotlin
// EstimateOneRepMaxUseCase.kt
fun execute(weightKg: Double, reps: Int): Double =
    if (reps == 1) weightKg else weightKg * (1.0 + reps / 30.0)
```

### Power Level calculation

```kotlin
// CalculatePowerLevelUseCase.kt
companion object {
    const val BASE_POWER = 0

    fun intensityMultiplier(reps: Int): Double = when {
        reps <= 3 -> 1.5
        reps <= 5 -> 1.25
        reps <= 8 -> 1.0
        else      -> 0.85
    }
}

fun sessionPowerGained(sets: List<SetLog>): Int =
    sets.sumOf { (it.volumeKg * intensityMultiplier(it.reps)).toInt() }

fun getPowerLevel(lifetimePowerEarned: Int): PowerLevel {
    val total = BASE_POWER + lifetimePowerEarned
    val stage = SaiyanStage.entries
        .filter { it.threshold <= total }
        .maxByOrNull { it.threshold } ?: SaiyanStage.BASE
    val next = SaiyanStage.entries.firstOrNull { it.threshold > total }
    val progress = next?.let {
        val base = stage.threshold.coerceAtLeast(BASE_POWER)
        ((total - base).toFloat() / (it.threshold - base)).coerceIn(0f, 1f)
    } ?: 1f
    return PowerLevel(total, stage, next?.threshold ?: total, progress)
}
```

---

## Visualizer state machine

```kotlin
// VisualizerState.kt
sealed class VisualizerState {
    data object Idle : VisualizerState()

    // State 0: exercise selected → show anatomy SVG with muscle highlights
    data class Static(
        val exercise: Exercise,
        val highlightedMuscles: List<MuscleGroup>
    ) : VisualizerState()

    // State 1: user taps "Begin Set" → Lottie 3-pose sequence plays
    data class DynamicTransition(
        val exercise: Exercise,
        val poseIndex: Int = 0          // 0, 1, 2
    ) : VisualizerState()

    // State 2: set logged → full activation, Canvas particles fire
    data class FullActivation(
        val exercise: Exercise,
        val powerLevelGained: Int,
        val estimatedOneRmKg: Double
    ) : VisualizerState()
}
```

State transitions:
- `Idle` → `Static`: exercise selected in picker
- `Static` → `DynamicTransition`: user taps "Begin Set"
- `DynamicTransition` → `FullActivation`: user logs the set (confirms reps + kg)
- `FullActivation` → `Static`: user taps "Next Set" (same exercise) or picker reopens

The `TelemetryLog` composable shows a typewriter-animated string that updates on
each state transition. `ParticleTendrilCanvas` (Canvas API) only renders during
`FullActivation`.

---

## Exercise seed data (Big 4 — start here)

```kotlin
object ExerciseSeeder {
    val DATA = listOf(
        ExerciseEntity(1, "Barbell Squat", "SQUAT",
            "QUADRICEPS,GLUTEUS_MAXIMUS,ERECTOR_SPINAE",
            "HAMSTRINGS,CALVES,RECTUS_ABDOMINIS",
            "squat_transition.json", "muscle_squat"),
        ExerciseEntity(2, "Deadlift", "HINGE",
            "ERECTOR_SPINAE,GLUTEUS_MAXIMUS,HAMSTRINGS",
            "TRAPEZIUS,LATISSIMUS_DORSI,QUADRICEPS",
            "deadlift_transition.json", "muscle_deadlift"),
        ExerciseEntity(3, "Bench Press", "PUSH",
            "PECTORALIS_MAJOR,DELTOIDS,TRICEPS",
            "BICEPS,RECTUS_ABDOMINIS",
            "bench_transition.json", "muscle_bench"),
        ExerciseEntity(4, "Overhead Press", "PUSH",
            "DELTOIDS,TRICEPS",
            "TRAPEZIUS,ERECTOR_SPINAE,RECTUS_ABDOMINIS",
            "ohp_transition.json", "muscle_ohp")
    )
}
```

Seeder runs in `SaiyanStrongApp.onCreate()` via an IO coroutine.
`OnConflictStrategy.IGNORE` makes it safe to run every launch.

---

## Theme (SaiyanTheme.kt)

Dark-only theme. Key color tokens:

```kotlin
val NeonGreen    = Color(0xFF39FF14)   // primary accent, muscle highlights
val PowerAmber   = Color(0xFFF5A623)   // power level, evolution indicators
val MatteBlack   = Color(0xFF0D0D0D)   // surface background
val SaiyanGray   = Color(0xFF1A1A1A)   // card surfaces
val TelemetryGreen = Color(0xFF00FF41) // monospace telemetry log text
val DangerRed    = Color(0xFFFF3B3B)   // RPE warnings, overtraining flag
```

All screens use `MaterialTheme.colorScheme.*`. The dark color scheme is injected
via `SaiyanTheme { }` in `MainActivity`.

---

## Navigation routes (Screen.kt)

```kotlin
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
```

---

## Build phases — status

**All 5 original phases are complete** (data foundation, domain layer, active workout,
visualizer, session complete + history). Ongoing work is sprint-based and tracked in
`## Progress log` below. Current app version: see `versionName` in `app/build.gradle.kts`
(kept in sync with GitHub release tags — see `## Release rules`).

**Verify any change:** `.\gradlew assembleDebug` must pass (use PowerShell — the rtk
Bash hook rewrites `./gradlew` and hangs). `grep -r " lb" app/src` must return zero
results.

**Current app map (beyond the original spec):**
- 5-tab bottom nav: Home (scouter gauge dashboard) | History | Workout (landing →
  active session) | Exercises (browser → detail tabs ABOUT/CHARTS/RECORDS/HISTORY) |
  Settings
- Workout templates + repeat-last (workout landing / SAVE AS TEMPLATE on session complete)
- Bodyweight log + DOTS score (Home card, formula toggle in Settings)
- Exercise media: free-exercise-db flip-book photos + instructions (ABOUT tab, Coil)
- In-app updater: GitHub releases/latest → direct HTTP download to cache → FileProvider
  install (`util/UpdateInstaller.kt`)

---

## Coding style

- `when` expressions over `if/else` chains for state handling
- Prefer `data class` over `class` for all models
- Extension functions go in a companion `Extensions.kt` next to the class they extend
- No abbreviations in variable names: `weightKg` not `wKg`, `exerciseLog` not `exLog`
- All `suspend fun` in Use Cases take named parameters, never positional-only
- `TODO("Phase N:")` comments for anything deferred to a later phase
- Compose previews for every screen composable using `@PreviewLightDark`

---

## Progress log

_(Claude Code appends here after each completed task)_

- [x] Phase 1 — data foundation: gradle wrapper/version catalog, app module wired
  (AGP 8.7.3, Kotlin 2.1.0, KSP 2.1.0-1.0.29); 4 Room entities + DAOs; AppDatabase;
  DatabaseModule + empty RepositoryModule stub; ExerciseSeeder (Big 4); SaiyanStrongApp
  seeds on IO coroutine; MainActivity + minimal SaiyanTheme/Color (added ahead of
  schedule since MainActivity requires it to compile); WeightFormatter. No "lb" found
  in app/src. NOT YET VERIFIED: `./gradlew assembleDebug` — no Gradle/Android SDK on
  this machine, and no gradlew wrapper jar generated (open in Android Studio once to
  auto-create it, or run `gradle wrapper` if a system Gradle is available).
- [x] Phase 2 — domain layer: 6 models (MuscleGroup, Exercise+ExerciseCategory, SetLog,
  ExerciseLog, WorkoutSession, PowerLevel+SaiyanStage) verbatim from spec; 3 repository
  interfaces (Exercise/Session/User — no impls yet, that's pending repo-impl work,
  not listed as its own phase); 5 use cases (EstimateOneRepMax, CalculatePowerLevel,
  GetEvolutionStage, LogSet, CompleteSession), all `@Inject constructor` for Hilt.
  RepositoryModule is still the Phase 1 empty stub — ViewModels in Phase 3 will need
  ExerciseRepositoryImpl/SessionRepositoryImpl/UserRepositoryImpl + @Binds before the
  Hilt graph that uses them resolves.
- [x] Repository implementations: ExerciseRepositoryImpl, SessionRepositoryImpl
  (transactional save via `AppDatabase.withTransaction`, manual entity↔domain joins
  across session/exercise_log/set_log since there's no Room @Relation yet),
  UserRepositoryImpl backed by new UserPreferencesDataStore (DataStore Preferences,
  lifetime power earned). Added ExerciseMapper/SessionMapper/SetLogMapper. RepositoryModule
  now binds all three via @Binds (changed from object to abstract class). Added
  lifecycle-runtime-compose dep (needed for collectAsStateWithLifecycle, was missing
  from Phase 1 catalog).
- [x] Phase 3 — active workout screen: ActiveWorkoutViewModel (set logging against
  LogSetUseCase, 90s rest timer via coroutine countdown, finish-workout calls
  CompleteSessionUseCase), ActiveWorkoutScreen + stateless ActiveWorkoutContent
  (previewable without Hilt) + ExercisePickerSheet (ModalBottomSheet). MainActivity
  now hosts ActiveWorkoutScreen directly as a temporary measure — Screen.kt,
  NavGraph.kt, and HomeScreen don't exist yet (no phase explicitly owns them), so
  there's nowhere to navigate on workout-finished; onWorkoutFinished is a no-op.
- [x] Phase 4 — visualizer: VisualizerState.kt verbatim from spec; VisualizerViewModel
  is its own @HiltViewModel (not bound to a nav route — there isn't one for it in
  Screen.kt — instead it's hoisted in ActiveWorkoutScreen alongside ActiveWorkoutViewModel
  and shares its lifetime). All 4 transitions wired: exercise-select → Static,
  Begin Set → DynamicTransition (drives poseIndex 0..2 on a 600ms coroutine tick,
  cosmetic only — the FullActivation transition is gated on the user actually
  logging the set, not on the pose animation finishing), log set → FullActivation
  (power/1RM computed by reusing CalculatePowerLevelUseCase.sessionPowerGained on a
  1-set list + EstimateOneRepMaxUseCase), Next Set → back to Static. Added
  TelemetryLog (typewriter component) and ParticleTendrilCanvas (Canvas API,
  rendered only in FullActivation). Reworked ActiveWorkoutScreen: the per-exercise
  set-entry form only shows during DynamicTransition now; the old inline
  `"${weightKg} kg"` string in the summary rows is gone too — that was a rule-2
  violation from Phase 3, now goes through WeightFormatter.
  KNOWN GAP: VisualizerState.Static/DynamicTransition show plain text, not the real
  anatomy SVG / Lottie pose sequence — no lottie/svg asset files exist in
  res/assets yet (lottie-compose dep has been in the catalog since Phase 1 but is
  still unused). Wire real rendering once those assets land.
- [x] Phase 5 — session complete + history: SessionCompleteScreen (workout summary,
  power earned, PowerLevelBar), SessionCompleteViewModel (combines SessionRepository
  + GetEvolutionStageUseCase flows via `combine`), HistoryScreen (lazy list of past
  sessions, date + volume + power per row), HistoryViewModel (streams getAllSessions).
  Screen.kt + NavGraph.kt wire all routes: ActiveWorkout → SessionComplete (sessionId
  arg), History → SessionComplete (tap row), back-stack pops to ActiveWorkout on Done.
  MainActivity now hosts NavGraph() instead of ActiveWorkoutScreen directly. PowerLevelBar
  composable in components/ shows stage label, numeric power, and LinearProgressIndicator.
- [x] UI visual design — dark industrial theme applied to all screens:
  Type.kt (SaiyanTypography — FontWeight.Black titles, Monospace bodySmall/label);
  SaiyanButton.kt (neon green BorderStroke button, WeightKnobButton circular knob,
  saiyanGlowBorder + scanlineTexture Modifier extensions); SaiyanTheme wired to
  SaiyanTypography. ActiveWorkoutScreen rewritten: amber "SAIYAN STRONG" header,
  NeonGreen-bordered ExerciseLogCard, SetInputPanel with WeightKnobButton +10/+25
  and reps ±, "LOG SET >>>" SaiyanButton, amber-bordered RestTimerRow, telemetry bar
  in TelemetryGreen on black. SessionCompleteScreen rewritten: StatCard row
  (VOLUME/POWER/TIME), PowerLevelBar card, ExerciseResultCard with Epley 1RM in kg
  (WeightFormatter), "DONE >>>" SaiyanButton. HomeScreen + HomeViewModel created:
  power level via GetEvolutionStageUseCase.execute() → StateFlow, "BEGIN TRAINING"
  and "SESSION HISTORY" SaiyanButtons, telemetry bar. NavGraph updated: Home is now
  startDestination; ActiveWorkout → SessionComplete pops to Home; Done → Home clears
  back-stack. Anatomy PNG overlay committed (AnatomyOverlayCanvas). Hilt bumped to
  2.54.1 to resolve Kotlin 2.1.0 metadata incompatibility.
- [x] Logic hardening (SPEC.md): two bugs fixed. (1) SetInputPanel:
  `remember(initialWeightKg)` → `remember` (no key) — prevents weight/reps
  resetting to initial values mid-composition if a parent recomposition fires
  while user is adjusting inputs. (2) SessionCompleteViewModel: `isLoading = false`
  → `isLoading = session == null` — keeps loading state true until Room actually
  returns the session row, preventing a blank flash on the SessionComplete screen.
  All other spec items already correct: rest timer cancels on skip/finish and
  nulls on expiry; DataStore.edit accumulates atomically; Epley uses 30.0 (no
  integer division); CompleteSessionUseCase computes volume+power before save;
  SessionRepositoryImpl wraps save in withTransaction (session→exercise_log→set_log);
  SessionDao orders by date_ms DESC; completedSessionId emitted only after suspend
  resolves; 150 exercises seeded (IDs 1–150); ExercisePickerSheet has search + chips.
- [x] Sprint 4 — workout table UI + history intelligence: ExerciseLogCard rewritten
  as a compact set-table (SET | PREV | KG | REPS columns); HistoryScreen now groups
  sessions by month with MonthHeaderRow and SessionCard showing best set per exercise
  + Epley-based PR count badge; HistoryViewModel computes 8-week bar chart data
  (HomeViewModel WeekBar); HomeScreen WorkoutsPerWeekChart via Canvas; ExerciseLogDao
  adds getUsageCounts() + getMostRecentExerciseLogId; GetLastSessionSetsUseCase added;
  ExercisePickerSheet gains A-Z/RECENT sort chips and usage badge. DB version → 3
  (migration adds is_failure INTEGER column to set_logs).
- [x] Sprint 5 — UX overhaul: Visualizer removed from ActiveWorkoutScreen (source
  files kept). VisualizerViewModel, VisualizerScreen, onBeginSet, onNextSet all
  removed from the workout flow. ExerciseLogCard gains inline InlineSetInput +
  `+ ADD SET >>>` button + × delete on set rows. ActiveWorkoutUiState: replaced
  activeExerciseId with expandedExerciseId + restTimerForExerciseId. SetLog.isFailure
  and SetLogEntity.is_failure added.
- [x] Sprint 6 — app icon + Canvas PowerLevelBar + SessionCompleteScreen HUD:
  App icon committed at all mipmap densities (barbell/POWER:9001 scouter aesthetic)
  with adaptive foreground PNGs + mipmap-anydpi-v26 XML. PowerLevelBar completely
  rewritten as native Canvas segmented bar: 10 segments, 2dp gaps, tapers 100%→65%
  width bottom-to-top, active brush Brush.verticalGradient(DangerRed→PowerAmber),
  inactive SolidColor(SaiyanGray), litSegments from (progress × 10).roundToInt();
  all Brushes remembered with density key for performance. Flame placeholder:
  Icons.Filled.LocalFireDepartment tinted #F5A623, alpha pulsing 0.5f→1.0f via
  rememberInfiniteTransition (800ms FastOutSlowInEasing RepeatMode.Reverse). Added
  material-icons-extended to libs.versions.toml + build.gradle.kts.
  SessionCompleteScreen rebuilt as Dragon Ball HUD: NeonGreen centered header
  "SESSION COMPLETE!", hero Row with OutlinedCard (SaiyanGray + NeonGreen border)
  showing total volume in headline monospace + per-exercise best-set table, paired
  with PowerLevelBar on right; stat chips row (POWER/TIME/EXERCISES); side-by-side
  EST. 1RM and SETS LOG OutlinedCards; session title input; DONE/DELETE buttons;
  telemetry bar. Build verified: assembleDebug SUCCESSFUL.
- [x] Sprint 7a — in-app updater: CheckForUpdateUseCase polls GitHub Releases API
  (api.github.com/repos/TheMammoth/SaiyanStrong2/releases/latest), compares tag_name
  against BuildConfig.VERSION_NAME, returns AppUpdate with APK browser_download_url.
  UpdateInstaller wraps DownloadManager: enqueue to public Downloads, poll
  STATUS_SUCCESSFUL every 500ms, return content:// URI via getUriForDownloadedFile().
  HomeViewModel checks on init; exposes updateAvailable + UpdateDownloadState (Idle/
  Downloading/Ready). HomeScreen amber banner slides in with animated visibility;
  UPDATE button checks canRequestPackageInstalls() first — if not granted opens
  Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES; on Ready fires ACTION_VIEW with APK
  MIME type. Permissions: INTERNET, REQUEST_INSTALL_PACKAGES, WRITE_EXTERNAL_STORAGE
  (maxSdkVersion=28). buildConfig=true; versionCode 1→4, versionName 1.0→0.4.0.
- [x] Sprint 7b — HomeScreen this-week stats row: WeekStats data class (sessions,
  volumeKg, topLiftKg, topLiftName) computed from getAllSessions() filtered to current
  ISO week. HomeViewModel exposes thisWeekStats: StateFlow<WeekStats>. HomeScreen
  shows ThisWeekRow with three MiniStatChip composables (SESSIONS / VOLUME / TOP LIFT)
  in NeonGreen on SaiyanGray cards, visible only when sessions > 0.
- [x] Sprint 8 — icon gradient + updater hardening (v0.6.1–v0.6.4):
  (1) Icon background: replaced @color/ic_launcher_background (#0D0D0D) with
  @drawable/ic_launcher_background — a 135° linear gradient #FFD600 → #FF8F00 →
  #E64A19 (SSJ gold → Goku orange). Both adaptive icon XMLs updated. Fallback color
  updated to #E64A19.
  (2) Transparent foreground: ic_launcher_foreground PNGs at all densities had solid
  black background baked in, covering the gradient. BFS flood-fill from corners (threshold
  < 40 per channel) made background pixels transparent — barbell + POWER:9001 scouter
  now float on the gradient.
  (3) Updater retry: checkForUpdate() retries at 0s / 5s / 15s if GitHub API returns
  null — covers devices where WiFi connects after app launch.
  (4) User-Agent fix: GitHub API returns 403 for requests without User-Agent header;
  added 'SaiyanStrong-Android'. This was the root cause of the banner never appearing.
  (5) Version in UI: BuildConfig.VERSION_NAME shown in HomeScreen telemetry bar so
  installed version is always visible.
  (6) versionName kept in sync with release tags from v0.6.1 onward (versionCode 8,
  versionName 0.6.3 as of v0.6.4 build). Rule: bump both on every release.

- [x] Sprint 11 — equipment variants + 5-tab nav + multi-pending rows (v0.9.0–v0.9.1):
  19 exercises renamed with (Barbell)/(Dumbbell) suffixes; DB version 3→4, MIGRATION_3_4
  clears and re-seeds exercises. 5-tab NavigationBar (Home|History|Workout|Exercises|Settings)
  in NavGraph; hidden on ActiveWorkout + SessionComplete routes; ExerciseBrowserScreen +
  ExerciseBrowserViewModel added for Exercises tab. ActiveWorkoutUiState: replaced
  expandedExerciseId with `pendingSetCounts: Map<Int,Int>` — each exercise tracks its own
  visible pending row count independently. HomeScreen gear icon removed (Settings = bottom tab).
  CheckForUpdateUseCase no longer swallows exceptions (was returning null = "Up to date"
  on network errors); HomeViewModel retry loop now shows real error. Root cause was private
  GitHub repo; fixed by making repo public. Released v0.9.0 and v0.9.1.
- [x] Sprint 12 — ActiveWorkoutScreen full redesign (v0.9.2):
  Flat table layout: SET | PREVIOUS | KG | REPS | ✓. No +/- buttons anywhere.
  Completed rows: Color(0xFF1A3A1A) full green background, visual-only ✓, long-press=delete,
  tap SET number=toggle failure (F). Active rest timer: full-width PowerAmber bar with
  large countdown + -30s/+30s/SKIP text buttons. Rest label: small centered green text
  between sets. Pending rows: muted gray style, NeonGreen-outlined ✓ button logs set.
  Exercise header: NeonGreen bold name + Link + MoreVert placeholder icons. Top bar:
  ExpandMore + Refresh left, session timer center, FINISH right. ADD SET (X:XX) per card.
  All weight through WeightFormatter.format().replace(" kg",""). lint.checkReleaseBuilds=false
  to bypass UAST/SDK crash in lintVitalAnalyzeRelease.
- [x] Fixed signing — persistent keystore (v0.9.2):
  app/saiyanstrong.keystore generated (RSA-2048, 10000 days, alias=saiyanstrong).
  keystore.properties added to .gitignore (credentials never committed). build.gradle.kts
  reads keystore.properties via Properties() and wires signingConfigs.release to both
  debug and release buildTypes. APKs from any machine with keystore.properties install
  as upgrades over previous builds.
- [x] Sprint 12b — inline KG/REPS editing, no dialogs (v0.9.3):
  AlertDialog NumberInputDialog removed entirely. KG and REPS cells are now BasicTextField
  (SetCell composable): KeyboardType.Decimal/Number, ImeAction.Next moves focus KG→REPS,
  ImeAction.Done logs/saves. Active cell gets NeonGreen border + faint green background.
  NeonGreen cursor. ✓ button on pending rows still works as alternative to DONE.
- [x] Bug fixes (v0.9.4):
  (1) Update banner reappear fix: last_dismissed_update_version stringPreferencesKey added
  to DataStore; UserRepository interface + UserRepositoryImpl expose getLastDismissedUpdateVersion()
  / saveDismissedUpdateVersion(); HomeViewModel reads dismissed tag at check time and skips
  banner if tag matches; saves on both dismiss (✕) and UPDATE tap. Banner never reappears
  for the same release.
  (2) KG/REPS select-all on focus: SetCell switched from String to TextFieldValue state;
  LaunchedEffect(isFocused) with rememberUpdatedState selects TextRange(0, length) when
  field gains focus — first keystroke replaces the old number instead of appending.
- [x] Sprint 13 — templates + exercise progress + bodyweight/DOTS (v0.10.0):
  DB v4→5 (MIGRATION_4_5 creates templates, template_exercises, body_weight_logs).
  (1) Workout templates: TemplateEntity/TemplateExerciseEntity + TemplateDao (join query
  for exercise names), WorkoutTemplate domain model, TemplateRepository+Impl (transactional
  save), bound in RepositoryModule. ActiveWorkoutScreen shows QuickStartPanel when workout
  is empty: REPEAT LAST WORKOUT (amber) + saved template cards (tap=load exercises,
  long-press=delete). SessionCompleteScreen gains SAVE AS TEMPLATE button (uses session
  title or "Workout M/D" fallback; disabled after save showing TEMPLATE SAVED ✓).
  (2) Exercise progress page: SetLogDao.getHistoryForExercise (3-table join → SetWithDate),
  SessionRepository.getExerciseHistory, ExerciseSetHistory model. ExerciseDetailScreen +
  ExerciseDetailViewModel: stat chips (best set / est 1RM / total sets / volume), Canvas
  e1RM-over-time line chart (date-proportional X axis), per-session set history list.
  ExerciseBrowserScreen rows now clickable → Screen.ExerciseDetail route wired in NavGraph.
  (3) Bodyweight + DOTS: BodyWeightEntity/BodyWeightDao, BodyWeightLog model, UserRepository
  bodyweight CRUD + useFemaleDotsFormula pref (DataStore booleanPreferencesKey).
  HomeScreen BodyWeightCard: latest kg + delta vs previous, 15-point sparkline, inline
  BasicTextField LOG input (comma tolerated as decimal), DOTS score chip. DOTS computed in
  HomeViewModel from best e1RM of Squat(Barbell)/Bench Press(Barbell)/Deadlift (excl.
  Romanian/Stiff) + latest bodyweight, male/female polynomial coefficients; toggle row in
  Settings under new TRAINING section. versionCode 18, versionName 0.10.0.
  NOTE: rtk hook rewrites `./gradlew` → `rtk gradlew` in Bash tool and hangs — build via
  PowerShell `.\gradlew assembleDebug` instead. Build verified SUCCESSFUL.
- [x] Sprint 14 — scouter dashboard (v0.10.1): HomeScreen fully reworked.
  New components/ScouterGauge.kt: 250dp Canvas arc gauge (240° sweep from 150°, round-cap
  stroke, 25 radial ticks w/ brighter majors, amber reticle dot at sweep tip, inner hairline
  ring), center readout POWER LEVEL / %,d number / stage label; progress animates via
  animateFloatAsState tween 900ms. HomeContent rebuilt: quiet header (SAIYAN STRONG +
  "SCOUTER ONLINE"), gauge hero + "NEXT: stage · threshold" line, 3 stat tiles
  (STREAK / THIS WEEK volume+sessions / DOTS amber), BIG THREE row (SQ/BP/DL chips with
  best e1RM + 10-point sparkline), 12-week CONSISTENCY heat strip (alpha by session count,
  amber border marks current week, tap → History), slim BodyWeightCard retained (DOTS chip
  suppressed there — lives in tile row), pinned "▶ BEGIN TRAINING" CTA + telemetry bar,
  content scrolls above pinned footer. HomeViewModel: WeekBar/buildWeekBars/weeklyBars
  removed; DashboardStats(streakWeeks, bigThree: List<LiftStat>, heat 12wk) computed from
  sessions; streak tolerates empty current week; matchesLift(SQ/BP/DL) shared by DOTS +
  big-three (DOTS refactored onto it). SESSION HISTORY button dropped (bottom tab covers it).
  versionCode 19, versionName 0.10.1.
- [x] Sprint 14b — bottom-nav padding fix + SessionComplete rework (v0.10.2):
  (1) BUG: NavGraph Scaffold computed innerPadding for the bottom NavigationBar but never
  applied it — every tab screen's content (incl. Home BEGIN TRAINING button) slid under
  the bar. Fixed: NavHost(modifier = Modifier.padding(innerPadding)).
  (2) SessionCompleteScreen rebuilt from scratch: the 3-panel HUD (7–9sp text, colliding
  1RM/REPS columns, 58%-height panels of dead space, floating barbell image) replaced with
  single-column dashboard-language layout — SESSION COMPLETE header + date, volume hero
  tile (32sp mono volume + POWER EARNED amber), DURATION/SETS/EXERCISES tiles, power stage
  card w/ thin amber StageProgressBar Canvas, RESULTS list (name + sets·reps | BEST kg +
  e1RM per row), title input, SAVE AS TEMPLATE; DELETE/DONE pinned at bottom outside the
  scroll so they're always visible. SessionCompleteViewModel: weeklyBars/strengthProgressPct/
  buildWeekBars/allSessions removed (charts gone). versionCode 20, versionName 0.10.2.
- [x] Sprint 15 — Strong-inspired UX (v0.11.0), four features from reference screenshots:
  (1) Workout landing page: new Screen.WorkoutLanding ("workout_landing") is the Workout
  tab + Home BEGIN TRAINING target — START AN EMPTY WORKOUT SaiyanButton, REPEAT LAST
  WORKOUT row (amber, exercise-name preview), MY TEMPLATES (n) 2-col LazyVerticalGrid
  (TemplateCard: tap=start, long-press=delete). ActiveWorkout route now
  "workout?templateId={id}&repeatLast={bool}" (nav args, defaults -1/false);
  ActiveWorkoutViewModel takes SavedStateHandle and preloads exercises from template or
  last session; QuickStartPanel + templates/lastSessionExerciseIds removed from active
  screen/uiState. WorkoutLandingViewModel/Screen added.
  (2) Rest timer inline bar: restTimerTotalSeconds in uiState (set on every
  startRestTimerFrom); RestTimerBar restyled Strong-like — slim 36dp pill, PowerAmber fill
  fraction = remaining/total draining left-to-right, countdown centered (flips amber when
  fill < 45%), -30s/+30s/SKIP small text row. Active-workout header shows time-of-day
  title (MORNING/AFTERNOON/EVENING/NIGHT WORKOUT).
  (3) Exercise detail tabs: ABOUT (category, primary/secondary muscles, lifetime chips) /
  CHARTS (est. 1RM, max weight, session volume line charts w/ peak value) / RECORDS
  (Estimated 1RM, Max weight, Max session volume rows + rep-max table 1–10: best
  performance + date + Epley est.) / HISTORY (per-session set list). ChartPoint replaces
  E1RmChartPoint; RepMaxRecord + maxSessionVolumeKg + weight/volume charts added to VM.
  (4) History cards Strong-style: title + "EEEE, MMMM d, yyyy 'at' h:mm a" date,
  Sets | Best set two-column exercise list ("6 × Deadlift (Barbell)" | "190 kg × 6 [F]"),
  footer icon chips (Schedule=duration, FitnessCenter=volume, EmojiEvents=PR count amber
  when >0). versionCode 21, versionName 0.11.0.
- [x] Sprint 16 — exercise media on ABOUT tab (v0.12.0): free-exercise-db integration
  (github.com/yuhonas/free-exercise-db, Unlicense/public domain, 800+ exercises).
  ExerciseMedia model + ExerciseMediaRepository interface; ExerciseMediaRepositoryImpl
  downloads dist/exercises.json once (User-Agent header, cached to filesDir/
  free_exercise_db.json, parsed lazily under Mutex with org.json), matches our exercise
  names by token-overlap score (|ours∩theirs|/|ours| ≥ 0.75, prefers highest coverage then
  fewest extra tokens), returns raw.githubusercontent image URLs + step instructions.
  Coil 2.7.0 added (coil-compose in catalog). ExerciseDetailViewModel exposes
  media: StateFlow<ExerciseMedia?> loaded after exercise name resolves. AboutTab (now
  default tab): FlipBookImage — white 1.5:1 card, Crossfade(350ms) between start/end
  photos every 900ms = flip-book animation; numbered INSTRUCTIONS section; unmatched
  exercises gracefully show text-only as before. Manifest: android:theme
  Theme.Material.NoActionBar (kills redundant "SaiyanStrong" ActionBar on every screen +
  suspected top-gap decor bug on detail screen). versionCode 22, versionName 0.12.0.
- [x] Updater rewrite (v0.12.1) — root cause of "can't update": DownloadManager stalls on
  GitHub's 302→objects.githubusercontent.com redirect chain and the 500ms poll loop only
  exits on SUCCESSFUL/FAILED, so downloads spun forever. UpdateInstaller rewritten:
  suspend downloadToCache() does direct HttpURLConnection download (manual redirect
  follow ≤5 hops, User-Agent, 64KB buffer, onProgress callback %) into
  cacheDir/updates/, returns FileProvider uri (new provider com.saiyanstrong.fileprovider
  + res/xml/file_paths.xml cache-path). DownloadManager/poll code deleted from both
  HomeViewModel and SettingsViewModel; Downloading/InProgress states now carry percent
  (shown in banner + Settings row). Second bug fixed: onDownloadUpdate no longer saves
  dismissed version on UPDATE tap (a failed download used to hide the banner forever);
  dismissed is saved only on ✕. Verified v0.11.0↔v0.12.0 APKs share signing cert
  59ae14f6… and release asset is byte-identical to local build — install path was fine,
  only download was broken. NOTE: devices on ≤0.12.0 must sideload once (old downloader);
  updater self-heals from 0.12.1 onward. versionCode 23, versionName 0.12.1.
- [x] Sprint 17 — deletes with confirm, steppers, adjustable rest timers (v0.13.0):
  (1) components/ConfirmDialog.kt (dark AlertDialog, DELETE red / CANCEL green) used
  everywhere destructive: set delete (long-press completed row), REMOVE EXERCISE (new —
  ⋮ DropdownMenu on exercise card, was a dead placeholder icon; Link icon dropped),
  History swipe (confirmValueChange now returns false and opens dialog — row snaps back
  on cancel), SessionComplete DELETE button, template long-press on workout landing.
  ActiveWorkoutViewModel.onRemoveExercise reindexes orderIndex + clears pending/prev-perf
  + cancels rest timer if it was that exercise's.
  (2) KG/REPS steppers: SetCell gained onFocusChanged; focused cell shows a StepperRow
  (−step/+step chips) under the row — kg step 2.0 dumbbell/kettlebell else 2.5, reps ±1;
  completed rows commit the edit immediately, pending rows stay local until ✓.
  (3) Rest timers: DataStore default_rest_seconds (90 default, clamp 10–600) —
  UserRepository get/setDefaultRestSeconds; Settings TRAINING gains "Default rest timer"
  −15s/+15s row. Per-exercise override: DB v6 (MIGRATION_5_6 adds exercises.rest_timer_sec
  INTEGER nullable), Exercise.restTimerSec, ExerciseDao.updateRestTimer,
  ExerciseRepository.setRestTimerSec; ⋮ → REST TIMER opens RestTimerDialog (−15s/+15s,
  SAVE / USE DEFAULT=null). onLogSet + ADD SET label use restSecondsFor(exerciseId) =
  override ?: default; VM defaultRestSeconds StateFlow feeds the screen.
  versionCode 24, versionName 0.13.0.
- [x] Sprint 18 — Supabase auth + cloud backup (v0.14.0): highest-priority feature per
  SPEC.md — users no longer lose training history on phone loss. Auth is optional,
  local-first stays the default; app fully usable signed-out.
  (1) Google Sign-In via Credential Manager (androidx.credentials + googleid, not the
  legacy GoogleSignInClient). util/GoogleSignInHelper.kt builds a GetGoogleIdOption with
  a SHA-256-hashed nonce, returns (idToken, rawNonce) from CredentialManager.getCredential.
  (2) domain/model/AuthUser+BackupInfo, domain/repository/AuthRepository+BackupRepository,
  4 use cases (SignInWithGoogle, SignOut, BackupNow, RestoreBackup) — Clean Architecture
  layers held throughout, ViewModels never touch data/ directly.
  (3) data/remote/SupabaseClientProvider.kt builds a SupabaseClient (Auth + Storage
  plugins) from BuildConfig fields (SUPABASE_URL/ANON_KEY/GOOGLE_WEB_CLIENT_ID), sourced
  from local.properties — never hardcoded, never committed. AuthRepositoryImpl wraps
  supabase-kt Auth (signInWith(IDToken), sessionStatus → Flow<AuthUser?>, currentUserOrNull).
  (4) data/backup/BackupPayload.kt (@Serializable DTOs, all 6 user-data tables + DOTS/rest
  prefs + exercise rest-timer overrides — exercises themselves aren't backed up since
  ExerciseSeeder re-seeds them every launch) + BackupSerializer.kt (DAOs+DataStore ↔ JSON,
  restore does one AppDatabase.withTransaction wiping + reinserting all tables with
  original PKs preserved so FKs stay intact, no remapping needed).
  BackupRepositoryImpl uploads/downloads a single {userId}/latest.json in a private
  Supabase Storage bucket (`backups`, RLS-restricted to auth.uid() folder — SQL in
  scripts/supabase_backups_rls.sql); restore rejects backups with a higher
  appVersionCode than the installed app.
  (5) Auto-backup: BackupWorker (HiltWorker, WorkManager, NetworkType.UNMETERED) enqueued
  via BackupRepository.scheduleAutoBackup() (no-op signed-out) from the end of
  CompleteSessionUseCase.execute — every finished workout backs up on Wi-Fi without user
  action. SaiyanStrongApp now implements Configuration.Provider for HiltWorkerFactory;
  manifest disables WorkManager's default (non-Hilt) initializer.
  (6) Settings gains an ACCOUNT section (top of the scroll, above TRAINING): signed-out
  shows one SIGN IN WITH GOOGLE button; signed-in shows email, last-backup timestamp,
  BACKUP NOW / RESTORE FROM BACKUP / SIGN OUT. Restore reuses the existing ConfirmDialog
  ("local data will be replaced ... cannot be undone"). All errors surface via a themed
  Snackbar (new to this screen) instead of failing silently.
  (7) New deps (version catalog only, no hardcoded versions): supabase-kt 3.1.4
  (auth-kt, storage-kt) + ktor-client-android 3.0.3, androidx.credentials 1.3.0 +
  googleid 1.1.1, work-runtime-ktx 2.10.0 + hilt-work 1.2.0, kotlinx-serialization-json
  1.7.3 (+ kotlin-serialization plugin). No Room schema change — backup is fully external
  to the local DB shape.
  Infra set up outside the repo: new dedicated Google Cloud project "saiyanstrong" (OAuth
  consent screen published to production, Web + Android OAuth clients), Supabase project
  barbell-io has Google auth provider enabled and the `backups` bucket + RLS policies.
  KNOWN GAP: build verified (assembleDebug SUCCESSFUL, zero " lb" hits) but the actual
  sign-in/backup/restore flow has not been runtime-tested on a device — no
  emulator/device was available this session. Test per SPEC.md `## 6. Testing strategy`
  checklist before fully trusting it in production use.
  versionCode 25, versionName 0.14.0.
- [x] Sprint 19 — first-launch onboarding + Power Level info sheet (v0.15.0):
  (1) presentation/screens/onboarding/OnboardingScreen.kt + OnboardingViewModel.kt: 4-page
  Compose HorizontalPager (LOG YOUR SETS static set-table mock → EARN POWER with the real
  ScouterGauge animating 0→68% progress each time the page becomes current → EVOLVE listing
  every SaiyanStage with its threshold → final TIME TO TRAIN page with BEGIN TRAINING CTA
  + optional "Back up your power" Google sign-in reusing GoogleSignInHelper/
  SignInWithGoogleUseCase from the auth sprint). SKIP button top-right (hidden on the last
  page), dot PageIndicator, NEXT button advances the pager (final page has no NEXT — its
  own CTA finishes instead).
  (2) Shown-once gating: UserPreferencesDataStore.onboardingComplete (new boolean key) +
  UserRepository get/setOnboardingComplete. Screen.Onboarding is now the NavGraph
  startDestination (route `onboarding?replay={replay}`); OnboardingViewModel exposes a
  3-state gate (Loading/ShowOnboarding/SkipToHome) so a completed user never sees a pager
  flash — SkipToHome fires onFinished() before any page renders. Finishing for real (not
  replay) writes onboarding_complete=true and navigates to Home with
  popUpTo(Onboarding){inclusive=true}; replay mode (from Settings) ignores the flag,
  always shows the pager, and just pops back to Settings on finish.
  (3) Settings → ABOUT gains a "Replay intro" row → Screen.Onboarding.createRoute(replay
  = true).
  (4) Home scouter gauge gains a small "?" (HelpOutline) IconButton, top-right of the
  gauge area → PowerLevelInfoSheet, a ModalBottomSheet explaining how Power Level is
  earned and listing all 6 SaiyanStage thresholds with the current one highlighted
  (▶ amber row) — answers "is 34,200 good?" for first-time users without leaving Home.
  No new dependencies (HorizontalPager + ModalBottomSheet are both already part of the
  existing Compose BOM/foundation).
  versionCode 26, versionName 0.15.0.
- [x] Sprint 20 — DBZ-styled session share card (v0.16.0): SessionCompleteScreen gains a
  SHARE button that renders a 1080×1350 portrait card and opens the Android share sheet
  (WhatsApp-first — plain ACTION_SEND image/png via FileProvider, no platform-specific SDK).
  (1) presentation/screens/session_complete/ShareCard.kt: pure offscreen Compose content —
  amber "⚡ SAIYAN STRONG" logo header, session title + long-form date, hero total volume
  (WeightFormatter.format, 92sp mono) + "POWER EARNED +X" in amber, a bordered Power Level
  card whose border/label color comes from a new private SaiyanStage.glowColor() mapping
  (BASE→white, SSJ1-3→amber, SSJ_GOD→red, ULTRA→neon-green — all existing SaiyanTheme
  tokens, nothing hardcoded), top-3 exercises by summed set volume with each one's best
  set, a real ScouterGauge at 10% alpha bleeding off the top-right corner as the "subtle
  scouter graphic," and a pinned "TRACKED WITH SAIYANSTRONG" footer (the growth-loop line
  — every share is free marketing).
  (2) presentation/components/ComposeCapture.kt: rememberComposeGraphicsLayer() — the
  project's Compose UI is pinned to 1.7.6 (via compose-bom 2024.12.01), which predates the
  official rememberGraphicsLayer() convenience function, so this hand-rolls it from
  LocalGraphicsContext.createGraphicsLayer()/releaseGraphicsLayer() (verified against the
  actual 1.7.6 AAR bytecode before writing, since this API surface isn't in training data
  for this exact version). SessionCompleteScreen renders ShareCardContent offscreen inside
  a CompositionLocalProvider(LocalDensity provides Density(1f)) (so 1.dp==1px → pixel-exact
  1080×1350 regardless of device density) wrapped in a zero-size clipToBounds() Box so nothing
  overflows onto the visible screen; a drawWithContent modifier records every draw pass into
  the GraphicsLayer. Tapping SHARE awaits two frames (withFrameNanos) for safety, then calls
  the suspend graphicsLayer.toImageBitmap().asAndroidBitmap().
  (3) util/SessionShareImageSaver.kt (new, mirrors UpdateInstaller.kt's FileProvider
  pattern): writes the PNG to cacheDir/shares/, wraps it via the existing
  com.saiyanstrong.fileprovider authority, fires ACTION_SEND with EXTRA_STREAM +
  FLAG_GRANT_READ_URI_PERMISSION through Intent.createChooser. res/xml/file_paths.xml
  gained a second cache-path entry (name="shares", path="shares/") alongside the updater's.
  SessionCompleteViewModel.onShare(bitmap) is the only new ViewModel surface — capture stays
  in the UI layer, file IO/Intent launch stays in the util layer, matching how
  UpdateInstaller is already used from ViewModels elsewhere.
  No new dependencies — GraphicsLayer/LocalGraphicsContext ship in the ui-graphics/ui
  artifacts already on the classpath.
  versionCode 27, versionName 0.16.0.
- [x] Sprint 21 — Play Store submission prep (v0.17.0): no new features — this sprint is
  entirely build/config/docs hardening ahead of a Play Console submission.
  (1) **DISTRIBUTION build flavors**: new `distribution` flavor dimension, two flavors —
  `github` (BuildConfig.DISTRIBUTION="github", keeps the self-updater) and `play`
  (BuildConfig.DISTRIBUTION="play", self-updater fully dark — Play policy forbids
  self-updating apps). Both flavors share the same applicationId and the same
  versionCode/versionName stream (no flavor-specific version overrides) — this is one
  app on two channels, not two separate installs. `HomeViewModel.checkForUpdate()`
  no-ops immediately when DISTRIBUTION != "github" (covers both its init-time call and
  the Settings retry button); HomeScreen's telemetry line and SettingsScreen's whole
  UPDATES section (plus the "Update API" debug row) are conditionally rendered on the
  same flag. The update banner on Home already self-gated for free since
  `updateAvailable` simply never gets set when the check never runs.
  (2) **Permission audit**: `WRITE_EXTERNAL_STORAGE` removed entirely — it was vestigial
  from the pre-v0.12.1 DownloadManager-based updater; the rewrite moved to
  cacheDir+FileProvider, which needs no storage permission, and grep confirmed zero
  remaining usages anywhere in app/src. `REQUEST_INSTALL_PACKAGES` moved out of the main
  manifest into `app/src/github/AndroidManifest.xml`, a flavor-specific manifest that
  only merges into github builds — verified via the actual merged manifests
  (`app/build/intermediates/merged_manifest/{flavor}Release/AndroidManifest.xml`) that
  play builds declare neither permission while github builds still declare
  REQUEST_INSTALL_PACKAGES. The remaining permissions in both flavors (WAKE_LOCK,
  ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE,
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) are auto-merged in by WorkManager itself
  (needed for the auto-backup job) and aren't something the app declares directly.
  (3) **R8/ProGuard hardening**: `app/proguard-rules.pro` created and wired into the
  release buildType (previously `isMinifyEnabled = true` had zero custom rules —
  release builds were relying entirely on whatever consumer-rules.txt each AAR happened
  to bundle). Verified library-by-library by decompiling the actual AARs in the Gradle
  cache rather than assuming: Room/Hilt/Coil ship their own consumer rules and needed
  nothing extra; `androidx.hilt:hilt-work` ships a `-keepnames @HiltWorker` rule
  covering BackupWorker already; `googleid`'s Bundle-based credential parsing is
  reflection-free so needs no rule (kept it anyway as cheap insurance). The real gap:
  **supabase-kt (auth-kt, storage-kt) and the whole kotlinx.serialization/Ktor chain
  ship literally zero consumer ProGuard rules** — confirmed by unzipping the AARs and
  finding no proguard.txt/consumer-rules.txt at all. Added the official
  kotlinx.serialization rule set for our own `data/backup/BackupPayload.kt` DTOs, plus
  a broad `-keep class io.github.jan.supabase.** { *; }` since the library's
  JSON-parsed models (UserInfo, UserSession, etc.) live entirely in library bytecode we
  don't control — verified in the R8 mapping.txt after a real `assembleGithubRelease`
  build that both `BackupPayload` and `UserSession`/`UserInfo` came through unrenamed
  and unremoved. **Not verified**: an actual signed release APK installed and exercised
  on a device — see the KNOWN GAP note below.
  (4) **Signing**: added a second signing config, `playRelease`, reading from a new
  `play-keystore.properties` (gitignored, same pattern as `keystore.properties`) so the
  eventual Play upload key stays fully separate from the GitHub-distribution keystore —
  a compromise of one never touches the other. Falls back to the GitHub keystore when
  `play-keystore.properties` doesn't exist yet (it doesn't, as of this sprint) purely so
  `assemblePlayRelease` still builds locally for verification; **the user must create
  play-keystore.properties pointing at a real, dedicated Play upload keystore before
  ever uploading a playRelease build to Play Console** — a shared debug/GitHub key
  reaching Play Console would be a real security mistake, not just a style nit.
  (5) **Docs**: `PRIVACY_POLICY.md` (local-first storage, what the optional Supabase
  backup uploads and why, no ads/tracking, permission-by-permission rationale, contact
  via GitHub Issues) — flagged as a real compliance gap: Play's Account Deletion
  requirement (apps with sign-in need an in-app *and* web-reachable deletion path) isn't
  fully met yet, since there's no self-service "delete my cloud backup" flow, only a
  support-request path. `store/LISTING.md` (short description, 77/80 chars; full
  description, ~3.6k/4000 chars) built around "Log sets. Earn Power. Evolve."
  `store/play_store_icon_512.png` — flat 512×512 Play hi-res icon, composited from the
  existing adaptive icon's gradient background + barbell/scouter foreground layers via
  a PowerShell System.Drawing script (no image-gen tooling used or needed — the
  in-app/launcher adaptive icon from Sprint 8 was already final, this is just the
  separate flat export Play's listing page requires).
  KNOWN GAP: both `assembleGithubRelease` and `assemblePlayRelease` build clean with R8
  and the manifest/BuildConfig differences were verified directly, but nothing here was
  installed and run on a real device or emulator this session — "release build runs
  identically to debug" is unverified beyond compiling and the R8 mapping-file spot
  checks described above. Do a real install-and-test pass (sign-in, backup/restore,
  session share, exercise photos, updater on the github flavor) before trusting a
  release build in production.
  versionCode 28, versionName 0.17.0.
- [x] Coach Mode (v0.18.0) — the monetization tier, built as 7 incremental slices per
  SPEC.md, each independently verified and committed. Full stack: Postgres schema on the
  existing `barbell-io` Supabase project, Paddle subscription billing, and the client-side
  Coach Dashboard/invite/template-push UI.
  (1) **Schema + RLS** (Slice 1, migration 0001): `profiles` gains
  `coach_entitlement_active`/`coach_entitlement_expires_at`/`paddle_subscription_id`/
  `paddle_customer_id` (additive — this Supabase project turned out to already have an
  unrelated `profiles` table + 5 empty scaffolding tables from something else; none of
  those were touched). `is_coach()` is the single entitlement-check function, backed by
  role='coach' AND entitlement_active AND not expired — every client-side gate calls this,
  nothing re-implements the check. New tables: `coach_invite_codes`, `coach_athletes`,
  `coach_pushed_templates`, each with RLS plus BEFORE UPDATE triggers restricting an
  athlete to exactly the one state transition they're allowed (revoke / accept — never
  reassigning `coach_id`). Along the way, found and fixed a real pre-existing hole: the
  original `profiles` table had a permissive UPDATE policy letting any signed-in user set
  `role='admin'` on themselves; `profiles` is now client-read-only, writable only via the
  service role (the webhook).
  (2) **Client entitlement plumbing** (Slice 2): added `postgrest-kt` (verified against
  actual 3.1.4 bytecode before use). `IsCoachUseCase` → `CoachRepository.isCoach()` →
  `is_coach()` RPC is the only gating path anywhere in the app. Settings shows live COACH
  MODE status for signed-in users.
  (3) **Invite/consent/linking** (Slice 3, migration 0002): `profiles.email` added
  (populated server-side via the sign-up trigger — Google Sign-In never fills
  display_name, and profiles has no client-writable columns after the Slice 1 fix) plus a
  `linked_profile_public` view (`security_invoker`, id/email/display_name only) so a
  linked coach/athlete never sees the other side's billing columns. New
  `CoachSettingsScreen`: coaches generate/share an 8-char invite code; any signed-in user
  can redeem one behind an explicit consent dialog; "Linked Coaches" list with revoke.
  (4) **Coach Dashboard** (Slice 4): athlete list (last session, this-week volume, Saiyan
  stage, red flag at 7+ days quiet) built by downloading and decoding each linked
  athlete's existing cloud backup (`BackupSerializer.decode()` — never `restore()`, so a
  coach's device never writes an athlete's data locally) rather than a new sync table.
  Tapping an athlete opens a read-only session history reconstructed from the same
  payload. KNOWN GAP: no per-exercise e1RM progression charts for athletes (session-level
  history and best-set breakdown only) — a reasonable follow-up, not built this round.
  (5) **Push templates to athletes** (Slice 5): Room DB v6→7 adds
  `templates.is_from_coach` (threaded through `BackupSerializer`/`BackupPayload` too, so
  it survives cloud backup/restore) for a permanent "FROM COACH" badge. Coach picks one of
  their own existing templates from `AthleteDetailScreen`; athlete sees a "FROM YOUR
  COACH" banner on the Workout tab and accepts it into their own MY TEMPLATES.
  `AcceptPushedTemplateUseCase` is the one place that orchestrates across both
  `CoachRepository` and `TemplateRepository` — repositories never call each other.
  (6) **Paddle checkout + entitlement sync** (Slice 6): new "SaiyanStrong Coach" Paddle
  product (monthly €12, annual €120) created alongside the pre-existing unrelated "Zona X
  Premium" product in the same vendor account — nothing there was touched.
  `supabase/functions/paddle-webhook` verifies Paddle's HMAC signature, maps the event to
  a Supabase user via `custom_data.supabase_user_id` (set at checkout time), writes
  entitlement via the service role key, then re-queries and verifies the write actually
  landed before returning success — the payment-reliability rule, not skipped. Checkout
  itself is a static page (`docs/checkout.html` + `checkout-success.html`, Paddle.js
  overlay) hosted on GitHub Pages — enabled for this repo, now also solves the
  PRIVACY_POLICY.md hosting gap from the Play-prep sprint. The app never processes
  payment in-app on either flavor; `CoachSettingsScreen`'s "BECOME A COACH" section just
  opens the web page with the signed-in user's id.
  (7) **Entitlement reconcile** (Slice 7, migrations 0003–0004): `reconcile-entitlements`
  Edge Function runs daily via pg_cron, lists Paddle's active subscriptions (a new
  read-only `saiyanstrong-reconcile` Paddle API key, Subscriptions:Read only — **expires
  Oct 7, 2026, needs manual rotation**), compares against Supabase entitlements, flags
  mismatches into `entitlement_reconcile_flags` (service-role-only, no RLS grants at
  all) without ever auto-correcting anything. First design attempt (a custom shared
  secret stored via `vault.create_secret()` typed into the SQL Editor) was correctly
  blocked mid-session — the raw value would have sat in the editor's saved query history
  indefinitely. Redesigned instead: the function keeps Supabase's own "Verify JWT" gate
  ON (unlike paddle-webhook) and the cron job authenticates with the project's anon key,
  safe to commit since it's public by design and already ships inside the compiled app.
  Deployment was also interrupted mid-slice by a confirmed Supabase platform incident
  (dashboard hangs, verified via status.supabase.com) — waited it out rather than working
  around it.
  KNOWN GAPS carried forward, not silently dropped: (a) the actual Paddle purchase flow
  has never been exercised against a real card — this is a live production Paddle
  account, so a real test means either a real charge or standing up Paddle sandbox
  separately; (b) the Play-flavor billing-policy question from SPEC.md §7 (web checkout
  satisfies "no in-app payment processing," confirmed acceptable) is resolved, but Play
  Console submission itself is still the unfinished item from the Sprint 21 Play-prep
  KNOWN GAP, unchanged by this feature; (c) reconcile function has been deployed and
  scheduled but not yet manually invoked end-to-end — first real run is the scheduled
  03:00 UTC cron fire, worth checking `entitlement_reconcile_flags` + function logs after.
  versionCode 29, versionName 0.18.0.
- [x] Bug fix (v0.18.1): Power Level stayed inflated after deleting sessions from History
  (a user reported seeing Power Level 19,971 with History showing "NO SESSIONS LOGGED
  YET" after deleting everything). Root cause: `CompleteSessionUseCase` incremented a
  separate `lifetimePowerEarned` counter in DataStore on every finished session, but
  nothing ever decremented it when a session was deleted (`HistoryViewModel.deleteSession`
  and `SessionCompleteViewModel.onDeleteSession` both called
  `sessionRepository.deleteSession()` directly) — the counter only ever went up,
  permanently drifting from reality.
  Real fix, not a patch: Power Level is now derived live from `SUM(sessions.power_earned)`
  via a new `SessionDao.getTotalPowerEarned()` query, not from the separately-maintained
  counter — `GetEvolutionStageUseCase` sources from `SessionRepository` instead of
  `UserRepository`. This self-heals any already-corrupted install with no reset step
  needed (0 sessions → 0 power, automatically) and eliminates the whole bug class going
  forward, since nothing needs to remember to keep a counter in sync. Same latent bug
  existed in the Coach Dashboard (Slice 4) — `CoachRepositoryImpl.getAthleteSummaries()`
  was trusting an athlete's backed-up `lifetimePowerEarned` field, which could be
  corrupted by this exact bug on their device too; now sums `payload.sessions` directly
  instead. Added `DeleteSessionUseCase` (orchestrates `SessionRepository` +
  `UserRepository`, subtracting the deleted session's power from the legacy DataStore
  counter too, clamped at 0) so the vestigial counter — still kept for
  `BackupPayload.lifetimePowerEarned` backup-format compatibility, no longer read for
  display — doesn't drift either, even though nothing authoritative depends on it anymore.
  versionCode 30, versionName 0.18.1.
- [x] Sprint 22 — editable, fully-detailed session results (v0.19.0): user feedback was
  that SessionCompleteScreen (also the read-only view opened from History) only showed a
  best-set summary per exercise ("1 sets · 5 reps — BEST 60kg") with no way to see or
  correct individual sets after the fact.
  (1) Data layer: `SetLogDao.update`/`deleteById` + `SessionDao.updateTotals` added.
  `SessionRepository`/`SessionRepositoryImpl` gain `updateSet`/`deleteSet`, both
  transactional (`AppDatabase.withTransaction`) and both end by recomputing the parent
  session's `total_volume_kg`/`power_earned` from every remaining set via
  `CalculatePowerLevelUseCase.sessionPowerGained` — same pattern `CoachRepositoryImpl`
  already used for deriving power from raw sets, so an edited/deleted set can never leave
  the session's stored totals stale or double-counted.
  (2) `ExerciseRow` (SessionCompleteViewModel) now carries the full `sets: List<SetLog>`
  for its exercise, not just the aggregated best/e1RM numbers. New `onEditSet`/
  `onDeleteSet` ViewModel functions call straight through to the repository (matching the
  existing `onTitleChange`/`updateTitle` precedent of simple pass-through ops bypassing a
  dedicated use case).
  (3) UI: new `ExerciseResultCard`/`EditableResultSetRow` (session_complete/
  SessionResultsSection.kt) render every logged set per exercise — SET/KG/REPS row,
  inline-editable KG/REPS (BasicTextField, select-all-on-focus, ±step chips while
  focused), tap SET number to toggle failure, long-press a row for a delete
  ConfirmDialog — reusing the exact interaction language ActiveWorkoutScreen already
  taught users during a live workout, just now also available after DONE and from
  History (History → session card tap already routed here; no History changes needed).
  (4) Extracted `SetCell`/`StepperRow` out of ActiveWorkoutScreen.kt into shared
  `presentation/components/SetInputCell.kt` (identical behavior, now reused by both
  screens instead of duplicated) — incidental cleanup, not a rewrite; trimmed
  ActiveWorkoutScreen.kt from 792→707 lines in the process.
  KNOWN GAP: not device/emulator-tested this session (no device available) — verified via
  `assembleGithubDebug` compiling clean only. Test the edit/delete-set flow on a real
  session before trusting it fully; also note repeated set edits on a very large session
  are O(n) per edit (re-reads every set to recompute totals) — fine at real-world set
  counts, would need a SUM query instead of the Kotlin-side fold if that ever mattered.
  versionCode 31, versionName 0.19.0.
- [x] Power Level base changed to 0 (v0.19.1): `BASE_POWER` was a `9_001` "over 9000" easter
  egg added into every user's total from Phase 1 onward (`getPowerLevel` = `BASE_POWER +
  lifetimePowerEarned`). User feedback: prefer starting from 0 so the number and stage
  thresholds map directly onto power actually earned. Changed `CalculatePowerLevelUseCase.
  BASE_POWER` to `0` — no other logic changes needed, since it was already a single
  additive constant read at display time (not stored per-session), so every screen that
  shows Power Level picks up the new baseline immediately with no migration. Updated the
  onboarding "EARN POWER" demo animation (`OnboardingScreen.kt` `EarnPowerPage`), which had
  `9_001 + 25_199 * progress` hardcoded to land inside the Super Saiyan band at its 0.68
  demo progress — replaced with `35_000 * progress` so the demo still lands inside the
  same Super Saiyan (20,000–50,000) band, just re-based from 0. The launcher icon artwork
  itself still shows "POWER:9001" as a baked-in scouter aesthetic graphic (Sprint 8) — that
  is static image asset text, unrelated to this runtime constant, and was left untouched.
  versionCode 32, versionName 0.19.1.
- [x] Sprint 23 — rest timer sound cues + RPE entry (v0.20.0), per SPEC.md (both features
  approved via clarifying questions before building — synthesized sounds, bottom-sheet RPE
  picker, active-workout-only scope, RPE editable on both pending and already-logged rows).
  (1) `util/RestTimerSoundPlayer.kt` (new, `@Singleton`): `playTick()` via
  `ToneGenerator(STREAM_MUSIC, TONE_PROP_BEEP2)`; `playGong()` via a hand-rolled `AudioTrack`
  playing a procedurally generated ~900ms PCM buffer (two detuned low sine partials + an
  exponential-decay envelope) — no bundled audio assets, no licensing/sourcing question, no new
  permissions. Both use `STREAM_MUSIC` deliberately (not `STREAM_ALARM`) so a silenced/media-muted
  phone stays silent.
  (2) `ActiveWorkoutViewModel.startRestTimerFrom` — the existing countdown coroutine — now calls
  `playTick()` when `secondsLeft == 3` and `playGong()` right after the `downTo 1` loop completes
  naturally; cancellation (SKIP, or restarting via `onAdjustRestTimer`) throws
  `CancellationException` mid-`delay`, so an interrupted countdown never fires the gong — no
  extra guard needed, it fell out of the existing coroutine shape for free.
  (3) Mute toggle: `UserPreferencesDataStore.restTimerSoundsEnabled` (boolean key, default true) +
  `UserRepository`/`Impl` get/set, following the exact `useFemaleDotsFormula`/`defaultRestSeconds`
  precedent. Settings TRAINING section gains a tap-to-toggle "Rest timer sounds" ON/OFF row,
  same interaction language as the existing DOTS-formula row right above it.
  (4) `presentation/components/RpeBottomSheet.kt` (new): `ModalBottomSheet` with the reference
  screenshot's explanatory line + a 5-column chip grid (6, 6.5, 7, 7.5, 8, 8.5, 9, 9.5, 10) plus
  a "NO RPE" clear row. `SetLog.rpe`/`set_logs.rpe` already existed end-to-end since Phase 1 —
  this was purely a UI gap; `PendingSetRow.logSet()` was hardcoding `onLogSet(kg, r, null,
  isFailure)`, now passes the picked value.
  (5) Both `PendingSetRow` and `CompletedSetRow` (ActiveWorkoutScreen.kt) gain a small "+ RPE" /
  "RPE 8.5" chip below the SET/KG/REPS row, tapping it opens `RpeBottomSheet`; selecting a value
  updates local state immediately for pending rows, and calls `onEdit(...)` immediately for
  completed rows (same "commit on change" behavior the failure-toggle and steppers already use).
  `CompletedSetRow.onEdit` and `ActiveWorkoutViewModel.onEditSet` both extended with an `rpe:
  Float?` parameter threaded alongside the existing weight/reps/failure fields.
  KNOWN GAP: not device/emulator-tested this session — verified via `assembleGithubDebug`
  compiling clean only. The gong/tick tone character (frequencies, envelope, duration) is a
  first-pass guess with no way to preview audio without a real device this session — flagged in
  SPEC.md as something to tune after hearing it once. Test the full flow (timer running to
  completion, SKIP suppressing the gong, mute toggle, RPE picker on both pending and completed
  rows, RPE surviving through to the exercise detail HISTORY tab) on a real device before fully
  trusting it.
  versionCode 33, versionName 0.20.0.
- [x] Sprint 24 — RPE-based progression hints (v0.21.0), per SPEC.md: since RPE entry now exists
  (Sprint 23) but nothing read it back, the PREVIOUS column in the active workout stayed a plain
  readout. Closed that loop with a real autoregulation hint, not a guess.
  (1) `domain/util/RpeChart.kt` (new, pure object): the standard published RTS/Tuchscherer
  %1RM-by-RPE table (12 rep rows × 9 RPE columns, 6.0–10.0 in 0.5 steps).
  `percentOf1Rm(reps, rpe)` clamps reps to 1..12 and RPE to 6.0–10.0 (documented, explicit
  approximations, not silent ones); `estimateTrue1Rm(weightKg, reps, rpe)` backs out an estimated
  true 1RM from *any* logged set, not just an AMRAP one like the existing Epley-based
  `EstimateOneRepMaxUseCase` (left completely untouched — this is additive, not a replacement).
  (2) `domain/model/LoadSuggestion.kt` (sealed class: `MoreWeight`/`MoreReps`/`Hold`/`EaseOff`) +
  `domain/usecase/SuggestNextLoadUseCase.kt`: reads the previous set's RPE and applies a real
  coaching rule, not a fixed percentage-progression scheme — RPE ≤8 → more weight at the same
  reps (targeting RPE9 next time); RPE 8.5–9 → **one more rep** if reps ≤6 (the gentler
  progression at lower rep counts) else a small weight bump (adding a whole rep at already-high
  rep counts is the bigger relative jump, not the safer one); RPE9.5 → hold; RPE10 → ease off.
  No previous RPE recorded → returns `null`, existing plain PREVIOUS text is unchanged. Weight
  suggestions round to the exercise's existing step convention (2.0kg dumbbell/kettlebell, 2.5kg
  else) and are floored to strictly exceed the previous weight (a monotonic-table rounding-edge
  guard, not expected to trigger in practice since targeting a harder RPE than what was actually
  logged is always heavier on this chart).
  (3) `ActiveWorkoutScreen.kt`: `PendingSetRow`/`CompletedSetRow` compute the suggestion from
  their existing `previousSet` prop (`remember(previousSet, stepKg) { ... }`, `
  SuggestNextLoadUseCase` instantiated inline — a zero-dependency pure class, no Hilt/ViewModel
  plumbing needed) and render it as a second `NeonGreen` line under the existing PREVIOUS text —
  "100kg × 5 @9" / "→ try 6 reps". No changes to `ExerciseDetailScreen`, `HistoryScreen`,
  `SessionCompleteScreen`, or any DOTS/big-three calculation — scoped to the active workout only,
  per spec.
  (4) First real unit tests in this project: `app/src/test/java/com/saiyanstrong/domain/`
  (`RpeChartTest.kt`, `SuggestNextLoadUseCaseTest.kt`, 16 tests total, all passing via
  `testGithubDebugUnitTest`) — added `junit:junit:4.13.2` as `testImplementation` (version
  catalog). Pure-function domain logic with zero Android dependencies made this the first
  genuinely test-friendly code in the project; every other sprint's verification stays
  manual/build-only as before.
  KNOWN GAP: not device/emulator-tested this session — the math itself is now unit-tested and
  verified correct, but the actual on-screen rendering (second PREVIOUS line, real RPE-logged
  history feeding it) has not been eyeballed on a device. Hint wording is a first pass per
  SPEC.md, adjustable once seen.
  versionCode 34, versionName 0.21.0.
- [x] Sprint 25 — velocity-based training (VBT) foundation, per SPEC.md (v0.22.0): this is
  deliberately **half a feature**, per your own choice when scoping it — the physics engine and
  a place to store results, not a working camera feature yet. Nothing changed visibly in the app.
  Real bar-marker tracking and the camera recording screen need a real device/real footage to
  build honestly and are explicitly deferred (see SPEC.md §8 for exactly where this picks up).
  (1) `domain/model/BarPathSample.kt` (tracked marker position + timestamp), `VelocityZone.kt`
  (the Bryan Mann VBT zone table — Absolute Strength/Strength-Speed/Speed-Strength/Speed
  Accelerative/Speed Max, population-level and explicitly documented as an approximation, not
  lift-specific), `BarPathAnalysis.kt` (the computed result: peak/mean velocity, peak/mean power,
  range of motion, bar-path deviation, zone).
  (2) `domain/usecase/AnalyzeBarPathUseCase.kt`: pure physics, zero Android dependencies. Converts
  pixel positions to real-world meters (with the up/down image-coordinate inversion handled and
  specifically unit-tested, since it's exactly the kind of sign-flip bug that's invisible without
  a test), computes instantaneous velocity/acceleration/force (`F = m·(g+a)`, mass from the
  set's logged `weightKg`)/power per frame pair, and reports `meanConcentricVelocityMs` as total
  displacement ÷ total time (the actual VBT "MCV" metric — not an average of instantaneous
  velocities, a different and wrong number). Fixed a real alignment bug during development: an
  early version silently dropped zero-`Δt` frame pairs mid-loop, which would have desynced the
  velocity/power arrays from the sample window by index on any duplicate-timestamp frame; fixed
  by deduplicating timestamps up front so every consecutive pair is guaranteed `Δt > 0`, removing
  the skip-logic (and the bug) entirely.
  (3) Room v7→8 (`MIGRATION_7_8`): new `bar_path_metrics` table (own table, not nullable columns
  bolted onto `set_logs`, since this data only exists for sets recorded with the not-yet-built
  camera flow), FK CASCADE to `set_logs`, unique index on `set_log_id`. New
  `BarPathMetricsEntity`/`BarPathMetricsDao`, `domain/repository/BarPathRepository.kt` +
  `BarPathRepositoryImpl` (its own repository, matching the one-repository-per-concern pattern
  already used for `TemplateRepository`/`ExerciseMediaRepository`), bound in `RepositoryModule`.
  (4) 11 new unit tests (`AnalyzeBarPathUseCaseTest.kt`, alongside the RPE tests from Sprint 24) —
  hand-computed synthetic sample lists: constant-velocity rep, a sticking-point profile (verifying
  peak velocity reflects the fastest instant, not an average), coordinate-inversion sign checks,
  range-of-motion/bar-path-deviation against known values, and the &lt;2-sample degenerate case.
  Brought the "Room schema (source of truth)" reference section in CLAUDE.md up to date while
  touching schema — it had drifted stale since v0.13.0 (still said "Room DB version: 5").
  KNOWN GAPS, explicitly deferred, not forgotten (SPEC.md §8 has the full list): no calibration
  UI, no CameraX recording screen, no actual marker-tracking algorithm, no rep-window detection,
  no UI anywhere showing this data, and `BackupSerializer`/`BackupPayload` do not yet include
  `bar_path_metrics` — harmless today since nothing populates the table yet, but must be added
  before this feature goes live or a backup/restore would silently drop real velocity data.
  versionCode 35, versionName 0.22.0.
- [x] Sprint 26 — VBT camera capture + marker tracking (v0.23.0): closes out SPEC.md §8 on top of
  Sprint 25's physics/schema foundation, per your explicit instruction to build it now rather than
  wait for a device session. **Read the KNOWN GAP at the end of this entry before trusting any
  number this produces** — the tracking algorithm itself is unverified against real footage.
  (1) CameraX added (`camera-core`/`camera2`/`lifecycle`/`video`/`view` 1.4.0) — first native
  camera dependency in this project. `CAMERA` permission + `android.hardware.camera.any`
  (`required=false`, so devices without a camera can still install) added to the manifest.
  `util/barpath/BarPathVideoRecorder.kt`: thin wrapper — binds `Preview`+`VideoCapture` to a
  `PreviewView` via `ProcessCameraProvider`, records silently (no audio track — one less
  permission, audio isn't used for anything) to a cache file.
  (2) `util/barpath/MarkerColorMatcher.kt`: pure Kotlin RGB→HSV conversion + thresholding (no
  `android.graphics.Color` dependency, so the *entire* color-matching pipeline including the RGB→
  HSV math is unit-testable without Robolectric) — tuned for a bright magenta/pink marker (hue
  300–345°, sat/value floors to reject skin tones and gray plates). 11 new unit tests covering
  known-color conversions and true/false-positive cases (skin tone, desaturated pink, dark gray
  plate). `util/barpath/BarPathFrameTracker.kt`: extracts frames via `MediaMetadataRetriever`
  (33ms/~30fps sampling, frames downscaled 4× before pixel-scanning for speed), finds the
  marker's centroid per frame (rejects frames with too few matching pixels rather than guessing),
  returns a `List<BarPathSample>` feeding directly into last sprint's `AnalyzeBarPathUseCase`.
  (3) `presentation/screens/barpath/BarPathCaptureScreen.kt` + `BarPathCaptureViewModel.kt`: a
  4-step flow — RECORD (camera preview + record/stop) → CALIBRATE (tap two points on the first
  frame + enter their real-world distance in cm — a plate diameter or the bar sleeve) → PROCESSING
  (frame extraction + tracking + physics, off the main thread) → RESULTS (peak/mean velocity,
  zone, peak/mean power, ROM, bar path deviation) → SAVE writes to `BarPathRepository`. MVP scope:
  one rep per recording (the whole clip is treated as a single concentric phase) — automatic
  multi-rep segmentation is still a documented future item, not built.
  (4) Entry point: `ExerciseLogCard`'s existing ⋮ menu (REST TIMER / REMOVE EXERCISE) gains
  "RECORD BAR PATH (SET n)" for the exercise's most recently logged set, only shown once at least
  one set exists. New route `Screen.BarPathCapture` (`bar_path_capture/{setLogId}/{weightKg}`),
  hidden from the bottom nav bar like `ActiveWorkout`/`SessionComplete`.
  (5) Closed the backup gap flagged at the end of Sprint 25: `BackupPayload` gains
  `barPathMetrics: List<BarPathMetricsDto> = emptyList()` (defaulted, so older backup JSON without
  this field still decodes — same pattern as `TemplateDto.isFromCoach`), `BackupSerializer`
  reads/writes/restores it, `BarPathMetricsDao` gained `getAll`/`insertAll`/`deleteAll` to support
  it. A backup/restore cycle no longer silently drops recorded velocity data.
  KNOWN GAP — the important one: **the actual marker-tracking algorithm has not been run against
  a single real video this session.** `MarkerColorMatcher`'s pure color math is unit-tested and
  correct on its own terms; `BarPathFrameTracker`/`BarPathVideoRecorder`/the whole CameraX binding
  path compile but are unverified — real gym lighting, real marker visibility, real motion blur,
  and CameraX's actual runtime behavior on a physical device are all unknowns until this is tried
  against one real recorded lift. Treat every number this pipeline produces as untrusted until
  that happens. Also not built: automatic multi-rep segmentation (one recording = one rep for
  now), any UI surfacing saved `BarPathAnalysis` data back on `SessionCompleteScreen`/
  `ExerciseDetailScreen` (it saves, but nothing displays it yet), and the personal
  load-velocity-profile upgrade over the generic Bryan Mann zone table (SPEC.md §8, still future).
  versionCode 36, versionName 0.23.0.
- [x] Sprint 27 — VBT results UI (v0.24.0), per SPEC.md: closes the display gap from the last two
  sprints — `BarPathAnalysis` has been saveable since v0.22.0 but nothing read it back until now.
  Lower-risk than the camera/tracking sprint — pure UI wiring over already-unit-tested logic.
  (1) Real plumbing gap fixed first: `ExerciseSetHistory`/`SetWithDate`/`SetLogDao
  .getHistoryForExercise` never carried the set's own id, so there was no way to join a history
  row against `bar_path_metrics` (keyed by `set_log_id`). Added `id`/`setLogId` through the whole
  chain (query → DAO projection → domain model → repository mapper) — additive, no other caller
  of `ExerciseSetHistory` broke.
  (2) Batch lookup over per-row subscriptions: `BarPathMetricsDao.getForSetLogIds` +
  `BarPathRepository.getBarPathMetricsForSets(List<Long>): Flow<Map<Long, BarPathAnalysis>>` —
  both `SessionCompleteViewModel` and `ExerciseDetailViewModel` fetch once for all relevant set
  ids via `flatMapLatest`, not N independent Flows.
  (3) `SessionCompleteScreen`: a tracked set (one with saved `BarPathAnalysis`) shows a small ⚡
  badge next to its KG/REPS cells; tapping expands an inline detail block (zone, peak/mean
  velocity, peak/mean power, ROM, bar path deviation) below the row, same "expand below" language
  the KG/REPS steppers already use. Untracked sets render byte-for-byte as before.
  (4) `ExerciseDetailScreen`: new `internal fun buildVelocityChart` (top-level, not a ViewModel
  method, specifically so it's unit-testable without touching Hilt/SavedStateHandle) picks the
  heaviest tracked set per session and plots its mean concentric velocity — same "best of session"
  convention `weightChart`/`e1RmChart` already use. Reuses the existing `ChartCard`/
  `DetailLineChart` composables verbatim; the new "BAR SPEED" card only appears once ≥2 sessions
  have tracked data, so exercises nobody's recorded yet look untouched. 5 new unit tests
  (`ExerciseDetailViewModelTest.kt`) covering the "heaviest tracked set wins, even if an untracked
  set was heavier" rule and chronological ordering.
  KNOWN GAP unchanged from Sprint 26: this displays whatever the marker-tracking pipeline
  produces, and that pipeline is still unverified against real footage. This sprint made the
  *data* visible, not the *tracking* trustworthy — those are still two separate open items.
  versionCode 37, versionName 0.24.0.
- [x] Sprint 28 — calibration scroll fix + standalone bar path analysis + free/Coach quota
  (v0.25.0): a real device test surfaced a genuine bug and a real UX gap, fixed together.
  (0) **Calibration screen scroll fix**: after recording and tapping the two calibration
  points, the reference-length field and ANALYZE button were unreachable. Root cause:
  `CalibrationStep`'s outer `Column` had no scroll and the portrait video frame preview
  (`fillMaxWidth().aspectRatio(...)`) consumed the whole viewport height on a tall clip,
  pushing the rest of the screen below the fold with no way to reach it. Fixed: capped the
  frame preview to `heightIn(max = 420.dp)` and wrapped the step in `verticalScroll`. Not a
  tracking/logic bug — `BarPathCaptureViewModel.onConfirmCalibration` was already correct and
  reachable, just not visible. While diagnosing this the user separately reported a genuine
  discoverability gap: no way to find bar path capture outside an active workout. That's the
  rest of this sprint.
  (1) **Schema**: Room v8→9 (`MIGRATION_8_9`, table-recreate — SQLite can't alter `NOT NULL`/
  drop a unique index in place). `bar_path_metrics.set_log_id` is now nullable; gained
  `exercise_id` (NOT NULL) and `created_at_ms` (NOT NULL), backfilled for existing rows via a
  `set_logs → exercise_logs` join. The old unique index on `set_log_id` is dropped — SQLite
  partial unique indexes aren't expressible via Room's `@Index`, so "one bar-path row per set"
  for the existing linked flow is now enforced in `BarPathRepositoryImpl.saveBarPathMetrics`
  (delete-then-insert) instead of the DB layer, a deliberate simplification, not an oversight.
  (2) **Repository**: `BarPathRepository` gained `saveFreestandingBarPathMetrics`,
  `getFreestandingAnalysesForExercise`, `getFreestandingCountThisMonth` (new
  `TimestampedBarPathAnalysis` — a freestanding row has no set to hang a date off of, so it
  carries its own timestamp). `GetBarPathQuotaUseCase` (mirrors `IsCoachUseCase`'s "one shared
  check" precedent) combines the live monthly count with `IsCoachUseCase` into a `BarPathQuota`;
  the count is derived from `COUNT(*) WHERE created_at_ms >= monthStart`, never a separately
  maintained counter — the same fix pattern as the Power Level drift bug (v0.18.1). Free limit:
  5/calendar month; Coach entitlement (existing, reused as-is — no new Paddle product) =
  unlimited.
  (3) **Home card**: new `BarPathCard` on `HomeScreen` (after `BodyWeightCard`) shows
  "X/5 analyses this month" or "UNLIMITED · COACH"; NEW ANALYSIS opens the existing
  `ExercisePickerSheet` (reused verbatim) when quota allows, or a `ConfirmDialog`-based upsell
  pointing at `CoachSettingsScreen` when exhausted. `HomeViewModel` gained `exercises` and
  `barPathQuota` StateFlows.
  (4) **Capture flow, standalone mode**: `Screen.BarPathCapture` route changed shape
  (`bar_path_capture?exerciseId={}&setLogId={}&weightKg={}`, sentinel `-1` for the latter two
  matching the existing `templateId=-1` convention) — the workout ⋮-menu path is unchanged
  behaviorally, just now also passes `exerciseId` (threaded through
  `ActiveWorkoutScreen`/`ExerciseLogCard`'s `onRecordBarPath` callback, which gained an
  `exerciseId` parameter). `BarPathCaptureViewModel.isStandalone = setLogId <= 0`.
  `RecordingStep` gains a RECORD/IMPORT FROM GALLERY choice, standalone-only — gallery import
  uses `ActivityResultContracts.PickVisualMedia` (Android Photo Picker, no storage permission)
  and a new `util/barpath/BarPathVideoImporter.kt` copies the picked `content://` Uri to a
  cache file (mirrors `SessionShareImageSaver`'s existing cache-copy pattern in reverse) since
  `MediaMetadataRetriever`/`BarPathFrameTracker` both need a real path. `CalibrationStep` gains
  a "Weight lifted (kg)" field, standalone-only, validated the same way `referenceLengthCm`
  already is.
  (5) **ExerciseDetailScreen**: `buildVelocityChart` (Sprint 27) is unchanged; new pure
  `mergeVelocityChart` folds in freestanding analyses (keyed by their own timestamp, not a
  session) so the existing "BAR SPEED" chart card picks up standalone-recorded data for free,
  including exercises with freestanding-only tracked data and no session history at all.
  (6) **Backup**: `BarPathMetricsDto` gained `exerciseId`/`createdAtMs` (both defaulted so
  pre-v0.25.0 backups still decode); `BackupSerializer.restore` backfills a missing
  `exerciseId` from the payload's own `setLogs`/`exerciseLogs` lists (in-memory join, no extra
  DB query needed since those are already loaded for the restore).
  (7) Unit tests: `GetBarPathQuotaUseCaseTest` (3 tests, pure `computeBarPathQuota` extracted
  for testability without faking the repository chain) + 2 new `ExerciseDetailViewModelTest`
  cases for `mergeVelocityChart`.
  KNOWN GAP unchanged: the marker-tracking pipeline itself is still unverified against real
  footage — this sprint adds more ways to trigger it (gallery import, standalone entry), not
  less risk on that front. Real-device testing of the now-fixed calibration flow, gallery
  import, and the Home card end-to-end is still owed before trusting any of this in production.
  Also not built: any UI listing past freestanding analyses directly (they surface only via the
  ExerciseDetailScreen velocity chart, per spec — no dedicated history list this sprint).
  versionCode 38, versionName 0.25.0.
- [x] First real-footage test + marker-tracking fix (v0.25.1): this is the milestone the VBT
  feature has been waiting on since Sprint 26 — a real recorded lift, run end to end through
  record → calibrate → track → analyze. It "worked" in the sense of not crashing, but the
  numbers were nonsense: peak velocity 13.34 m/s (world-class bar speed tops out ~2 m/s), peak
  power 916,357 W (human peak output tops out a few thousand watts), mean concentric velocity
  0.00 m/s despite a nonzero peak, bar path deviation 33.7cm (very jittery). That exact
  signature — huge peak, ~zero mean, high deviation — means the tracked centroid jumped to a
  false-positive match somewhere else in frame for one instant and then returned close to
  where it started.
  Root cause, found by inspection (not yet re-verified on device): `BarPathFrameTracker.
  findMarkerCentroid` averaged *every* pixel in the frame matching the color threshold into
  one centroid, regardless of whether they were spatially contiguous. The test video's
  background had other pink/magenta-ish objects (a bucket, a towel) in frame — if any of them
  passed `MarkerColorMatcher`'s threshold even briefly, the averaged centroid snapped toward
  it, producing exactly this kind of spurious one-frame velocity spike.
  Fix: connected-component blob detection (`findBlobs`, 4-connected BFS flood fill over the
  match mask) replaces the naive whole-frame average, and `chooseTrackedBlob` picks whichever
  blob is nearest to the previous frame's tracked position once tracking has started (falling
  back to the largest blob to seed the very first frame) — a real marker can't teleport across
  a room in 33ms, so nearest-neighbor tracking rejects most false positives for free. Both
  functions are pure top-level `internal fun`s (no `Bitmap`/Android dependency) specifically so
  this exact failure mode is unit-testable going forward — 6 new tests in
  `BarPathFrameTrackerTest.kt`, including one that directly encodes the bug ("a small nearby
  blob wins over a large but distant one").
  Also fixed a real UX gap flagged in the same test: gallery import + first-frame extraction
  had no loading indicator, so the screen looked frozen for several seconds. `BarPathCaptureUiState`
  gained `isPreparingVideo`; `BarPathCaptureScreen` shows a dimmed overlay + spinner during that
  window (the existing PROCESSING step's spinner already covered the tracking/analysis phase).
  **Not built**: real-time/live tracking during recording (analyzing camera preview frames as
  they're captured, rather than post-processing the saved clip) — this was suggested by the
  user as a way to avoid the wait entirely, but it's a materially different architecture
  (CameraX `ImageAnalysis` use case + on-frame processing + a live overlay) from the current
  record-then-post-process pipeline, and wasn't built this pass. Worth scoping as its own
  spec if the post-processing wait remains a problem after this fix.
  KNOWN GAP: the blob/nearest-neighbor fix is unit-tested on synthetic data but **not yet
  re-verified against a real recorded lift** — it should meaningfully improve tracking
  robustness against background clutter, but whether it produces genuinely plausible velocity
  numbers this time still needs a real device retest.
  versionCode 39, versionName 0.25.1.
- [x] Sprint 29 — best-effort VBT: tap-to-calibrate color, tracked-path preview, usage tips
  (v0.26.0): user asked "build me the best VBT option possible" after the first real-footage
  test. Also answered a real technical question in the same session: NFC/AirTags are not viable
  for this — NFC is proximity-only with no positional tracking, and AirTags' UWB ranging data
  isn't exposed to third-party apps via any public API even in principle, so camera + colored
  marker remains the correct (not just cheapest) approach; a physical linear position
  transducer or wearable IMU would work but requires the user to buy separate hardware, out of
  scope for an app feature.
  (1) **Tap-to-calibrate marker color** — the single highest-leverage fix available without new
  hardware, targeting the exact failure mode from the first real test (a fixed guessed
  hue/saturation/value threshold matched a background object). New `util/barpath/
  MarkerColorProfile.kt` (pure, no Android dependency): `sample(r,g,b)` builds a profile
  centered on a real sampled color (saturation/value floors set below the sample — an explicit,
  documented approximation for lighting variation across a clip, same style as `RpeChart`);
  `matches(r,g,b)` uses circular hue distance (hue wraps at 360°, so 350° and 10° are 20° apart,
  not 340° — directly unit-tested); `default()` is a defensive-only fallback matching the old
  fixed range. Calibration is now a 3-tap sequence: tap the marker itself first (averaged over a
  small pixel neighborhood around the tap to reduce single-pixel noise — new
  `BarPathCaptureViewModel.sampleMarkerColor`), then the existing two reference-length taps;
  dynamic instruction text tracks which tap is next, and the marker-sample dot renders in
  `PowerAmber` distinct from the green reference-point dots. `BarPathFrameTracker.trackMarker`/
  `findMarkerCentroid` now take a `MarkerColorProfile` parameter instead of the hardcoded
  `MarkerColorMatcher.matchesRgb` call — Sprint 28's blob-detection + nearest-neighbor tracking
  fix is unchanged, only *what counts as a match* changed. 6 new unit tests
  (`MarkerColorProfileTest.kt`).
  (2) **Tracked path preview** — turns "trust the number" into "see the tracking worked."
  `BarPathCaptureUiState` gained `trackedSamples: List<BarPathSample>`, populated from the same
  samples already used to compute `BarPathAnalysis` (no extra tracking pass). New
  `TrackedPathPreview` composable on the RESULTS screen draws the tracked path as a polyline
  over the calibration frame (reusing `CalibrationStep`'s existing frame-to-box scaling
  approach), green dot at the start, red dot at the end — a jagged or jumping line makes bad
  tracking visually obvious before the user taps SAVE TO SET, rather than presenting an opaque
  number as gospel.
  (3) **Dismissible usage tips** — `UserPreferencesDataStore`/`UserRepository`/`Impl` gained
  `barPathTipsDismissed` (same dismiss-and-remember pattern as `lastDismissedUpdateVersion`).
  `RecordingStep` shows a small card (marker-vs-background contrast, calibration object choice,
  camera placement) until dismissed once, then never again — same UX language as the existing
  update banner's dismiss.
  KNOWN GAP unchanged: none of this has been re-tested against real footage yet this session —
  the tap-to-calibrate color sampling and path preview are new surface area that should help,
  but "should help" isn't "verified." Real-time/live tracking during recording remains
  explicitly out of scope (different architecture, noted again per the user's original
  question) — worth its own spec if the post-record wait is still a problem after this.
  versionCode 40, versionName 0.26.0.
- [x] Sprint 30 — Savitzky-Golay velocity smoothing (v0.27.0): replaces frame-to-frame finite
  differences (spiky, chases single-frame tracking jitter) with a proper local-quadratic
  smoothing filter, the standard fix for exactly this class of noise in motion-tracking data.
  New `domain/util/SavitzkyGolayFilter.kt` (pure Kotlin `object`, no Android dependency, same
  home as `RpeChart` — not `domain/analysis/` as originally requested, since that package
  doesn't exist in this project and this is the established location for stateless
  computational utilities): `smooth(positions, windowSize=7)` fits a local quadratic over
  integer index offsets; `differentiate(positions, timestamps, windowSize=7)` fits a local
  quadratic against each window's REAL timestamps (not assumed evenly-spaced — genuine frame
  extraction timestamps aren't perfectly uniform) and returns the analytical derivative at the
  center, which is what actually feeds `AnalyzeBarPathUseCase` now. Both the smoothing and
  differentiation math go through one shared `fitLocalQuadratic` (Gaussian elimination with
  partial pivoting on the 3x3 normal-equations system) rather than hand-derived closed-form
  coefficient formulas — fewer places for an algebra mistake to hide, and it naturally handles
  non-uniform timestamps for free. Edge handling: the first/last `windowSize/2` points (not
  enough neighbors for a full symmetric window) fall back to finite differences per-point;
  fewer than `windowSize` samples total falls back to finite differences for the whole series.
  `AnalyzeBarPathUseCase.execute` swaps its inline finite-difference velocity loop for
  `SavitzkyGolayFilter.differentiate(heightsMeters, timestamps)` — clean replacement, no
  commented-out "legacy" implementation kept alongside it (that was explicitly requested but
  conflicts with this project's own no-dead-code rule; git history is the real A/B mechanism —
  `git show <previous-commit>:app/src/.../AnalyzeBarPathUseCase.kt` recovers the old version
  if a side-by-side numeric comparison is ever actually needed).
  Verified the swap doesn't silently break the physics: every existing
  `AnalyzeBarPathUseCaseTest` fixture has fewer than 7 samples in its window, so with the
  default `windowSize=7` every existing test hits the finite-difference fallback path and
  produces byte-identical output to before — confirmed by hand-tracing the math, not assumed,
  before touching the file. Added one new integration test with 9 samples (enough to actually
  exercise the real SG-fit code path) simulating a single jittered tracked frame — asserts the
  smoothed peak velocity stays well below what a raw one-sided finite difference would report
  for that spike, while staying non-degenerate. 7 new unit tests in
  `SavitzkyGolayFilterTest.kt`, including a sine-wave-vs-analytical-cosine-derivative check
  (within 5%) and a too-short-series-doesn't-throw check.
  KNOWN GAP unchanged: this improves velocity/power *precision* for a given (already tracked)
  position series — it does nothing for the underlying marker-tracking accuracy itself (still
  the Sprint 28/29 territory: blob detection, tap-to-calibrate color, perpendicularity). Not
  yet re-verified against real footage this session.
  versionCode 41, versionName 0.27.0.
- [x] Sprint 31 — weighted subpixel blob centroid (v0.28.0): every matching pixel in a blob was
  previously weighted equally when computing its centroid; a pixel right at the edge of the
  color-match tolerance pulled the centroid just as hard as one dead-center on the sample. This
  swaps in a color-distance-weighted centroid so a poor-but-still-passing match barely moves it.
  `MarkerColorProfile` gains `satCenter`/`satTolerance`/`valCenter`/`valTolerance` (previously
  only asymmetric floors — `minSaturation`/`minValue` — existed, with no symmetric center/range
  to measure distance from) and a new `matchScore(r,g,b): Double` — 1.0 for a pixel exactly on
  the sample, trending toward 0 as hue/saturation/value distance from the sample approaches its
  tolerance, **clamped at 0 rather than allowed negative**: a pixel can pass the asymmetric,
  floor-based `matches()` test (e.g. a pixel brighter than the sample still clears the value
  floor) while scoring poorly on this symmetric, distance-based scale — confirmed by a new test
  (`a pixel brighter than the sample can still pass matches but score below the maximum`) rather
  than assumed. `matches()` itself — the actual accept/reject gate — is completely unchanged;
  `matchScore` is purely an additional weight for pixels that already passed it.
  `findBlobs` (Sprint 28) now takes a parallel `weights: DoubleArray` alongside the existing
  `mask: BooleanArray` — **connectivity is still driven entirely by `mask`, exactly as before**,
  so blob shape/detection is unaffected; only the centroid within an unchanged blob shifts
  toward higher-scoring pixels. A blob whose every pixel happens to score exactly 0 falls back
  to the old unweighted (count-based) centroid rather than dividing by zero. `matchScore` is
  floored at a small constant (`MIN_WEIGHT = 0.01`) before being written into the weights array
  so a boundary-scoring pixel still contributes a token amount rather than literally vanishing
  from the weighted average (its blob membership was already decided by `mask`, unaffected
  either way — this only keeps the weighted-centroid math itself well-behaved).
  4 new/updated tests in `BarPathFrameTrackerTest.kt` (heavily-weighted pixel pulls the
  centroid, all-zero-weight blob falls back correctly) and 4 new in `MarkerColorProfileTest.kt`
  (score at the sample ≈1.0, score decreases with distance, never goes negative, the
  brighter-pixel edge case above).
  KNOWN GAP unchanged: still no real-footage re-verification this session. This is a precision
  refinement on top of Sprint 28's blob detection + Sprint 29's tap-to-calibrate color — it
  doesn't change what counts as a match, only how confidently the centroid trusts each match.
  versionCode 42, versionName 0.28.0.
- [x] Sprint 32 — depth-drift scale correction (v0.29.0): the standing accuracy caveat since
  Sprint 25 ("a single 2D camera has no depth — if the bar drifts toward/away during a rep,
  that foreshortens apparent displacement and under-reports velocity") now has a real, if
  partial, fix. Uses the tracked marker's own apparent size as a depth proxy: if it shrinks
  relative to its first successfully-tracked-frame baseline, the bar has moved farther away and
  its real displacement is being under-represented in pixels, so displacement gets scaled back
  up (and vice versa for a marker growing larger).
  New `domain/util/ScaleCorrection.kt` (pure, `domain/util/` — not `domain/analysis/`, which
  doesn't exist in this project, same correction as the last two requests): `compute(baseline,
  current)` returns `baseline/current` clamped to `[0.5, 2.0]` (one bad frame can't corrupt a
  whole rep), falling back to `1.0` (no correction) when either diameter is missing or below a
  3px reliability floor.
  `Blob` (Sprint 28) gains `diameterPx` — the bounding-box diameter (larger of width/height)
  tracked during the same BFS flood fill that already computes the weighted centroid (Sprint
  31), no second pass over the mask. `BarPathSample` gains `apparentDiameterPx: Double?`
  (defaulted, so every existing test fixture that constructs one positionally still compiles
  unchanged), populated by `BarPathFrameTracker` alongside centroid x/y, scaled back to
  full-frame pixels the same way centroid already is.
  **A real bug caught before it shipped**: the first implementation attempt applied the scale
  correction by multiplying the raw `yPx` pixel coordinate directly — wrong, because pixel Y
  has no true physical zero to scale from (it's an arbitrary frame-relative coordinate, not a
  displacement). Caught by hand-deriving a concrete worked example (marker shrinking 40px→20px
  should double a 0.8 m/s reading to 1.6 m/s) before trusting the code, which the flawed version
  did not produce. Fixed by correcting each frame-to-frame PIXEL DISPLACEMENT and rebuilding the
  position series via cumulative sum instead — proven algebraically (not just tested) to
  telescope back to the exact original formula when no diameter data is present, and the new
  `depth-drift correction doubles apparent displacement...` test locks in the corrected 1.6 m/s
  result so this can't silently regress back to the broken version.
  Declined to build (per the user's own admission they're not needed for this correction):
  a "marker diameter (cm)" session-setup input field and a debug overlay showing `apparentDiameterPx`
  — both were requested as groundwork for a hypothetical future absolute-depth-estimation
  feature with nothing yet to connect them to.
  9 new tests in `ScaleCorrectionTest.kt`, 2 new integration tests in
  `AnalyzeBarPathUseCaseTest.kt`, 2 new/updated in `BarPathFrameTrackerTest.kt` (bounding-box
  diameter, larger-of-width/height selection).
  KNOWN GAP unchanged: still no real-footage re-verification this session, and this only
  corrects the Y-axis (vertical, velocity-relevant) displacement — `barPathDeviationCm` (X-axis
  range) is untouched, matching the minimal-scope precedent from prior sprints.
  versionCode 43, versionName 0.29.0.
- [x] Sprint 33 — high-speed (120fps) capture attempt, redirected mid-request (v0.30.0): user
  asked for raw-Camera2 `CameraConstrainedHighSpeedCaptureSession`. **This app's camera pipeline
  is CameraX** (`BarPathVideoRecorder.kt`), and that session type has no CameraX equivalent —
  reaching it literally would mean dropping CameraX for the recording path and hand-rolling raw
  Camera2 session management, losing CameraX's device-compatibility/lifecycle handling in the
  process. Flagged this before writing anything (per the user's own instruction to flag
  architecture conflicts rather than silently do something unexpected) and got direction: stay
  on CameraX, nudge frame rate via Camera2Interop instead — narrower device coverage than the
  constrained high-speed API guarantees, but no rewrite.
  Two things resolved without new code: the "don't add ImageAnalysis surfaces to a high-speed
  session" warning doesn't apply — this pipeline records to a file and analyzes it afterward via
  `MediaMetadataRetriever`, there's no live `ImageAnalysis`/`ImageReader` surface here at all.
  And "derive dt from real timestamps, never hardcode 1/30 or 1/120" was already true —
  `AnalyzeBarPathUseCase`/`SavitzkyGolayFilter` have always computed dt from each sample's
  actual `timestampMs`, never a frame-rate constant.
  A separate, real bottleneck the user hadn't asked about but which made the whole feature
  pointless without fixing it: `BarPathFrameTracker.trackMarker`'s frame-extraction interval was
  hardcoded to 33ms (~30fps) regardless of the source video's actual frame rate — recording
  faster footage would have changed nothing, since the tracker would still only pull ~30
  samples/second. Fixed: `sampleIntervalMs` now defaults to null, deriving the interval from
  `METADATA_KEY_CAPTURE_FRAMERATE` (falling back to the historical 33ms when an encoder doesn't
  report it — not guaranteed present on every device).
  (1) New `util/barpath/HighSpeedCapabilityChecker.kt`: checks
  `CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES` via `Camera2CameraInfo` (not
  `SCALER_STREAM_CONFIGURATION_MAP`'s high-speed sizes, which is specifically for the
  constrained-high-speed session type this app isn't using) — returns `FPS_120`/`FPS_60`/
  `STANDARD_30`.
  (2) `BarPathVideoRecorder.bindCamera` gains `highSpeedEnabled`/`onHighSpeedUnavailable`:
  applies `CONTROL_AE_TARGET_FPS_RANGE` via `Camera2Interop.Extender` on the `Preview.Builder`
  (a session-wide 3A parameter, not stream-specific, so this influences the whole bound session
  including what `VideoCapture` records — applying it to any one bound use case's interop point
  is sufficient). This is a *request*, not the guarantee `CameraConstrainedHighSpeedCaptureSession`
  provides — some devices will silently record at a lower rate than requested rather than throw,
  which the already-derived-from-real-timestamps dt handling absorbs gracefully. Bind failure
  while high-speed was requested retries once at standard rate and invokes the callback.
  (3) `RecordingStep` (standalone flow only — capability check needs a `ProcessCameraProvider`,
  meaningless for gallery-imported footage recorded by some other app): checks capability on
  compose, shows a toggle only when the device supports it, defaults to on unless the user has
  explicitly chosen otherwise (new `UserPreferencesDataStore`/`UserRepository` nullable
  `highSpeedModeEnabled` — null means "never explicitly set," same dismiss-and-remember
  DataStore pattern as `barPathTipsDismissed` but tri-state instead of defaulting to a fixed
  boolean). Toggle copy dropped the "reduces resolution to 720p" framing from the original ask —
  this app's `Quality.HD` is already fixed at 720p regardless of high-speed mode, so there's no
  resolution tradeoff to warn about here. Bind-failure fallback surfaces via a `SnackbarHost`
  local to `RecordingStep`, matching the exact requested copy ("High-speed mode unavailable on
  this device, using 30fps").
  KNOWN GAP, larger than usual for this feature: **none of this has been tested on a real
  device**, and unlike prior VBT sprints this one carries a real, not-yet-measured performance
  risk — extracting frames at ~8ms intervals (120fps) via `MediaMetadataRetriever.getFrameAtTime`
  means several times more individual seek+decode calls than the 30fps path, and this session
  had no device to check whether that's fast enough to be usable or a real problem. Test high-
  speed mode's actual achieved frame rate, the toggle's device-support detection, and frame-
  extraction performance before trusting this feature. Also known and accepted: toggling
  high-speed mode after the camera preview is already showing doesn't re-bind live — takes
  effect next time this screen opens, an `AndroidView` factory-runs-once limitation already
  present in this file before this sprint, not fixed here (out of scope, real risk to a live
  camera session for a toggle that's expected to be set before recording anyway).
  versionCode 44, versionName 0.30.0.
- [x] Sprint 34 — dual-marker depth calibration (v0.31.0): the biggest single VBT addition this
  session. Two independently color-tracked markers a known real-world distance apart (e.g. the
  bar's two sleeve end-caps) give a directly-measured pixels-per-meter for every single frame,
  replacing the whole session-wide-constant + diameter-heuristic calibration stack with a real
  geometric measurement.
  **The interaction this needed to get right, not mentioned in the request**: dual-marker
  per-frame ppm and Sprint 32's single-marker diameter-based `ScaleCorrection` both correct for
  the exact same physical effect (depth drift). Running both at once would double-correct. They
  are now strictly mutually exclusive — `AnalyzeBarPathUseCase.execute`'s new
  `effectivePixelsPerMeter(sample)` picks `sample.perFramePixelsPerMeter` outright when present
  (dual-marker), otherwise falls through to the existing diameter/`ScaleCorrection` path
  unchanged. Proved algebraically (not just tested) that this reduces to the *exact* pre-Sprint-34
  formula, including the first-sample edge case, when `perFramePixelsPerMeter` is absent — a new
  test (`dual-marker ppm takes priority over apparentDiameterPx — never both corrections at
  once`) locks in that a sample carrying both a real depth-implying diameter AND a
  no-depth-change ppm produces the ppm's answer, not a blend.
  **A real efficiency adaptation from the literal request**: rather than two independent
  `MarkerTracker` objects each running their own frame-decode loop (doubling the video seek+decode
  cost already flagged as an unmeasured performance risk in Sprint 33), `BarPathFrameTracker`
  decodes each frame once and runs blob detection against it twice — once per marker's color
  profile — via a new shared `findMarkerCentroidInScaledBitmap` (refactored out of the existing
  single-marker `findMarkerCentroid`, which is now a thin wrapper over it, completely unchanged
  in behavior). New `trackMarkerPair(videoPath, primaryProfile, referenceProfile,
  referenceDistanceMeters, ...)`: for each frame, finds both markers independently (each with its
  own nearest-neighbor tracking continuity, exactly like the single-marker path), computes
  `pixelDist(A, B) / referenceDistanceMeters`, and carries the last successfully-measured ppm
  forward when the reference marker (but not the primary) is occluded for a frame — per the
  literal request, not left null to silently fall through to a different fallback mechanism.
  `BarPathSample` gains `perFramePixelsPerMeter: Double?` (defaulted, existing single-marker path
  and every existing test fixture untouched).
  Calibration is now a genuine mode choice, not additive taps on top of the existing flow:
  **dual-marker mode** (default, "recommended") — tap marker A, tap marker B, type the known
  distance between them (default 130cm, "standard bar sleeve-to-sleeve distance" per the
  request) — 2 taps + a number, actually *simpler* than before. **Manual mode** (the pre-existing
  flow, fully preserved as a toggle-away fallback) — tap the marker, tap two reference points,
  type their known length — unchanged. Switching modes clears all taps, since a tap means
  something different in each mode. `CalibrationStep` gained a two-button mode row (matching the
  existing RECORD/IMPORT visual pattern) and a third tap-dot color (`MarkerBlue`) so marker A/B/
  reference-points are visually distinguishable at a glance.
  `ResultsStep` gains a small `PpmChart` (a new local `Canvas` line chart — `ExerciseDetailScreen`'s
  `ChartCard`/`DetailLineChart` are private to that file, not reused across packages) plotting
  `perFramePixelsPerMeter` over the rep when dual-marker data exists, titled "SCALE (PX/M) OVER
  REP — FLAT = NO DEPTH DRIFT" per the request's own framing for validating the correction.
  KNOWN GAP, larger than most: **zero device testing**, on top of the already-substantial
  untested surface from the last several sprints (marker tracking, high-speed capture, frame
  extraction performance). This sprint is pure logic + UI wiring, algebraically verified where it
  touches existing math, but the actual two-marker tracking reliability, the mode-toggle UX, and
  whether users can realistically place and track two distinguishable-colored markers on a
  barbell sleeve are all real open questions a real device session needs to answer.
  versionCode 45, versionName 0.31.0.
- [x] Sprint 35 — Kalman filter foundation for live velocity smoothing (v0.32.0), scoped down
  from the request via a clarifying question: the request asked to build a 2D Kalman filter AND
  wire it into "the live capture loop" to smooth a "real-time velocity number that updates each
  frame." **That live loop does not exist** — grep-confirmed no `ImageAnalysis`, no live
  per-frame analysis, no live velocity anywhere; the pipeline records to a file and analyzes it
  afterward (`BarPathFrameTracker` via `MediaMetadataRetriever`). So the ViewModel injection +
  live-loop wiring + "don't feed Kalman into SG" tasks all referenced infrastructure that isn't
  there (and "don't feed Kalman into SG" is already true by construction). Asked how to proceed;
  user chose "build the Kalman class + constants only, as an unwired foundation."
  (1) `domain/util/KalmanTracker2D.kt` (pure Kotlin, no Android dependency — `domain/util/` not
  the requested `domain/analysis/`, which doesn't exist here): constant-acceleration 6-state
  filter `[x,y,vx,vy,ax,ay]`, `predict(dt)`/`update(x,y)`/`reset(x,y)`, `smoothedPosition`
  (returns a pure `Point2D`, substituted for the requested `android.graphics.PointF` to honor
  "no Android dependencies"), `smoothedVelocityMps` and `accelerationMagnitudeMps2` properties.
  Physical sanity clamp on predicted acceleration at 15 m/s² (≈1.5g) — a magnitude above that is
  a tracking error, not real barbell motion; the clamp needs a pixels-per-meter scale to convert
  m/s²↔pixel-space, supplied via a `pixelsPerMeter` field the caller sets each frame (can vary
  per frame in dual-marker mode), which also keeps `predict(dt)` at its requested signature and
  makes `smoothedVelocityMps` a real property rather than a contradictory parameterized one.
  Matrix math via minimal dense helpers + a closed-form 2×2 innovation-covariance inverse; the
  process-noise covariance is the standard σ²·G·Gᵀ constant-acceleration form (noise-gain
  G=[½dt²,dt,1] per axis, independent x/y blocks). Stateful, so when eventually wired it must be
  instantiated per rep/session, never injected as a singleton — documented in the class.
  (2) `domain/util/VbtConstants.kt`: `KALMAN_MEASUREMENT_NOISE = 2.0`, `KALMAN_PROCESS_NOISE =
  50.0`, wired as the filter's constructor defaults (an `object` for greppable namespacing rather
  than bare top-level `const val`s).
  (3) 6 unit tests (`KalmanTracker2DTest.kt`): constant-velocity convergence, missed-frame
  coasting on momentum, reset zeroing, the acceleration clamp holding its physical bound,
  stationary→~zero velocity, and defaults-wire-through-from-VbtConstants.
  KNOWN GAP: this is a deliberately unwired building block — nothing in the app calls it, because
  there's no live-analysis loop to call it from. It does nothing user-visible; it's the correct
  foundation IF a live velocity display is ever built (which would be a materially new subsystem:
  a CameraX `ImageAnalysis` use case + on-device live blob detection, explicitly out of scope).
  The final build/test verification for this was interrupted last session by a transient platform
  outage; verified green (all 6 tests pass) at the start of the following session before release.
  versionCode 46, versionName 0.32.0.
- [x] Sprint 36 — MediaCodec/MediaExtractor streaming decode path (v0.33.0): a faster frame-
  extraction alternative to `BarPathFrameTracker`'s per-timestamp `getFrameAtTime()` seeks (which
  re-seek to a sync frame and decode forward on every sampled timestamp — expensive at high fps,
  the bottleneck flagged since Sprint 33). Preceded by a read-only camera-pipeline audit (the
  user asked for one to plan this) that re-confirmed: the pipeline is CameraX, record-to-file
  then extract-and-analyze, no live/ImageAnalysis path, no raw Camera2 classes anywhere.
  (1) New `util/barpath/BarPathVideoDecoder.kt`: `MediaExtractor` selects the video track →
  `MediaCodec` decoder in ByteBuffer mode (no Surface) → `getOutputImage()` yields a YUV_420_888
  `Image` per frame → converted to a full-res ARGB_8888 `Bitmap`. Decodes the track once,
  sequentially, emitting one frame per sample interval (kept when presentationTime ≥ next sample
  point) via a synchronous callback; the Bitmap is recycled right after the callback returns.
  The one pure/testable piece — `yuvToRgb(y,u,v)` (BT.601, clamped, opaque) — is a top-level
  `internal fun` with 5 unit tests (gray/black/white, red primary, opacity, clamping).
  (2) Wired into `BarPathFrameTracker` (now `@Inject`ing the decoder) behind a
  `useStreamingDecode: Boolean = false` opt-in flag on both `trackMarker`/`trackMarkerPair`, with
  automatic fallback: if streaming throws OR yields zero frames, it falls back to the unchanged
  retriever loop. Achieved via a refactor extracting the shared frame-driving into a `collect {
  drive -> ... }` helper that builds a FRESH sample list per attempt (so a failed streaming
  attempt's partial list is discarded — no double-counting), plus `readTiming` (metadata-only
  interval/duration read) and `driveRetrieverFrames` (the proven getFrameAtTime loop, behavior-
  preserved: same grid timestamps, same recycle). The retriever path's semantics are byte-for-
  byte what they were; the streaming path uses real frame presentation timestamps (marginally
  more accurate, and SG handles non-uniform timestamps).
  **Deliberately opt-in / default-off** — the two judgment calls: (a) MediaCodec is the most
  device-fragile Android API and could not be run even once this session, so the fallback keeps
  the feature working if it fails on a device; (b) whether it's actually *faster* is unmeasurable
  here — sequential decode beating repeated seeks is the expectation, but the software YUV→RGB
  conversion adds cost the hardware getFrameAtTime path doesn't have, so it could even be a
  regression on some devices. Default-off keeps the user's real-footage validation baseline
  (retriever) unchanged and lets them A/B on a device before flipping the default (a one-line
  change: pass `useStreamingDecode = true` from `BarPathCaptureViewModel`, or change the param
  default). Nothing calls it with `true` yet.
  KNOWN GAP: the entire MediaCodec/MediaExtractor/YUV-plane path is unverified on a real device —
  only `yuvToRgb`'s color math is unit-tested. Codec support, output color format, YUV plane
  strides, and actual decode performance all vary by device and are unknown until run against
  real footage. This adds a *ready* faster path, not a *proven* one.
  versionCode 47, versionName 0.33.0.
- [x] Sprint 37 — live analysis loop, slice 1 (v0.34.0): the subsystem that did NOT exist and was
  the shared blocker behind two earlier requests (the Kalman filter, Sprint 35; a live motion-trail
  overlay, declined). Both depended on a live per-frame centroid+velocity stream during recording;
  the pipeline only ever recorded-to-file-then-analyzed. The trail-overlay request also assumed a
  `VbtSessionViewModel`, a camera Fragment, a `TextureView`/`SurfaceView`, and XML/FrameLayout —
  none of which exist (Compose-only, no fragments, `PreviewView` in an `AndroidView`). Flagged all
  that; user chose "build the live analysis loop first."
  (1) `util/barpath/BarPathLiveAnalyzer.kt` — a CameraX `ImageAnalysis.Analyzer`: per frame it
  converts the YUV_420_888 `ImageProxy` to a downsampled ARGB grid (reusing the tested `yuvToRgb`),
  runs marker detection, feeds the centroid through a `KalmanTracker2D` (the filter's FIRST real
  consumer — Sprint 35's foundation finally wired up), and emits a `LiveFrameResult(detected, x, y,
  smoothedVelocity)`. The detection core is a pure top-level `detectMarkerCentroidInPixels()`
  (reuses `findBlobs`/`chooseTrackedBlob`/`MarkerColorProfile`), unit-tested with synthetic pixel
  grids (4 tests: centroid, no-match, below-min-pixels, nearest-neighbor of two blobs).
  (2) `BarPathVideoRecorder.bindCamera` gains an optional `onLiveResult` callback: when non-null it
  binds `ImageAnalysis` alongside preview+video and reports per-frame tracking. **Fully guarded** —
  binding a third camera stream (preview+video+analysis) exceeds some devices' supported
  combination, so if that bind fails it falls back to preview+video only; recording always
  survives, only the live loop degrades. Null (the prior signature's behavior) leaves recording
  untouched. Analyzer runs on a dedicated executor (shut down/recreated per bind); Kalman resets on
  `startRecording` (rep start).
  (3) `BarPathCaptureViewModel` exposes `liveTracking`/`liveVelocity` StateFlows fed from the
  analyzer's background-thread callback (MutableStateFlow.value is thread-safe). `RecordingStep`
  shows a small live readout over the preview: "● TRACKING"/"○ SEARCHING" + the smoothed speed.
  DELIBERATE SLICE-1 LIMITS, all documented in code:
  - Detection uses `MarkerColorProfile.default()` (magenta/pink) — the user's real marker color is
    only sampled AFTER recording today, so arbitrary colors need a pre-record color-sampling step
    (calibration-ordering rework, follow-up). The user's tested marker was pinkish, so default may
    actually detect it.
  - Velocity is UNCALIBRATED (relative, not true m/s) — real m/s needs a pixels-per-meter scale,
    also only known post-record. Labelled "~X.XX (rel. speed)", not "m/s", to avoid implying a real
    reading.
  - Centroid is in downsampled analysis-image space, NOT mapped onto the preview — the positioned
    motion-trail overlay (with the CameraX rotation/crop/mirror transform) is the next slice, now
    unblocked by this centroid stream.
  KNOWN GAP: the entire CameraX ImageAnalysis + YUV-plane + live-detection path is unverified on a
  real device — only the pure pixel-grid detection is unit-tested. Whether the third stream binds,
  whether detection keeps up at frame rate, and whether the default color profile actually finds the
  user's marker are all unknown until run on a device. This establishes the loop; it does not prove
  it works.
  versionCode 48, versionName 0.34.0.
- [x] Sprint 38 — post-rep video replay with synced bar-path overlay (v0.35.0): plays the recorded
  lift back with the tracked path overlaid, velocity-colour-coded, synced to playback. Request
  assumed a `VbtReplayFragment` + custom `View` + a persisted `List<TrackedFrame>`; none fit — this
  app is Compose-only (no fragments/XML), and NO per-frame path is persisted (only the aggregate
  `BarPathAnalysis` reaches the DB; per-frame velocities were computed-then-discarded inside
  `AnalyzeBarPathUseCase`; the video lives only in cache). Flagged that replay is therefore
  inherently EPHEMERAL — feasible only right after a rep from the cached video + in-memory path,
  not from saved History. User chose the ephemeral in-session scope.
  (1) Data: new `AnalyzeBarPathUseCase.trackFrames(...)` returns the per-frame `TrackedFrame`
  (timestamp, x, y, SG-velocity) series — refactored the shared windowing/velocity math out of
  `execute` into a private `computeSeries` so both paths use identical numbers (all 16 existing
  analyzer tests still pass, confirming behavior-preserving). New `domain/model/TrackedFrame.kt`.
  `BarPathFrameTracker.videoDimensions()` reads rotation-applied display W/H (so centroid coords,
  which come from rotation-applied getFrameAtTime frames, map correctly onto playback).
  (2) Dependency: added `androidx.media3:media3-exoplayer` + `media3-ui` 1.4.1 (first video-playback
  lib; version-pinned in the catalog as always).
  (3) `BarPathReplayContent.kt` — a Compose screen (NOT a Fragment/custom-View), hosting an
  ExoPlayer `PlayerView` via `AndroidView` (paused-on-load, looping, muted) with a Compose `Canvas`
  overlay: LAYER 1 full-path ghost + LAYER 3 peak(green)/sticking(red) markers + start/end triangles
  are cached via `drawWithCache` (rebuilt only on frames/size change, honoring the O(n) perf note);
  LAYER 2 velocity-coloured progressive path + LAYER 4 white cursor with velocity-coloured ring
  redraw every frame; a top-end HUD (Mean/Peak/ROM/Current) and a Material3 scrub slider + play/pause
  drive playback. 60fps position poll via a `LaunchedEffect` loop (Compose idiom, not a Handler).
  The velocity→colour mapping (`velocityColorArgb`, smooth interpolation red→orange→yellow→green,
  no Android `ArgbEvaluator` so it's pure) and the letterbox `computeFittedVideoRect` are pure
  top-level `internal fun`s with 7 unit tests.
  (4) Wired in-session: `BarPathCaptureViewModel` computes `trackedFrames` + video dims after
  analysis and exposes a `showReplay` flag; the RESULTS screen gets a "▶ REPLAY WITH BAR PATH"
  button (shown only when frames+video+dims are available); `BarPathCaptureScreen` renders the
  replay over the results when toggled, with a BACK button.
  KNOWN GAPS: ephemeral only (no History replay — would need persisting video + per-frame path,
  the bigger option deliberately not taken). Entirely unverified on a real device — the ExoPlayer
  playback, the overlay coordinate mapping (esp. the rotation assumption), and sync are all
  untested. Coordinate mapping assumes both getFrameAtTime and PlayerView render in the same
  rotation-applied display space — plausible but a real thing to verify on a device.
  versionCode 49, versionName 0.35.0.
- [x] Sprint 39 — shareable "rep card" image (v0.36.0): generates a 1080×1920 (9:16 Stories) PNG
  combining the bar-path viz + metrics, for sharing to Instagram/WhatsApp. Fits the codebase well —
  most of what it needs already existed (TrackedFrame series from Sprint 38, `SessionShareImageSaver`
  + the `shares/` FileProvider path from Sprint 20, `velocityColorArgb` from Sprint 38).
  (1) `presentation/screens/barpath/RepCardGenerator.kt` (`object`, `android.graphics` only — no XML,
  no third-party libs): `generateRepCard(data): Bitmap` draws a dark-crimson→black gradient, then
  top 40% = bar-path viz (white ghost + velocity-coloured path reusing `velocityColorArgb` + peak/
  sticking markers + subtle rack-upright line art), middle 30% = 2×2 metric grid, bottom 30% =
  exercise/weight/date + "TRACKED WITH SAIYANSTRONG". The path-fitting geometry (`boundsOf`,
  `fitTransform`/`RepCardTransform`) is pure and unit-tested (5 tests incl. the still-marker
  zero-size guard).
  (2) `RepCardData` — adapted from the requested `VbtSessionResult`, honestly: sets/reps omitted
  (a VBT recording is one rep; standalone analyses have no logged set), "Mean Propulsive Velocity"
  is the app's mean *concentric* velocity (labelled "MEAN VELOCITY", not mis-claimed as MPV), Time
  Under Tension derived from the tracked frame span (not a stored metric). `generateRepCard` takes
  no `Context` (pure Canvas, hardcoded theme colours/typeface, no resources needed).
  (3) `BarPathCaptureViewModel.onShareRep()` (injects `ExerciseRepository` for the name +
  `SessionShareImageSaver`) assembles the data, generates the bitmap on `Dispatchers.Default`, and
  shares it via the existing util (cache PNG → FileProvider → ACTION_SEND — the requested steps b/c/d,
  already built in Sprint 20, reused not reinvented). A "SHARE REP CARD" button on the RESULTS
  screen (shown when ≥2 tracked frames exist) triggers it — a button on the review screen rather
  than the requested FAB in the replay screen; RESULTS is where all the post-rep actions live and
  it works without entering replay (video not required — the card is path + metrics only, so it
  also works for gallery-imported analyses).
  KNOWN GAP: `RepCardGenerator`'s actual Canvas drawing (layout, gradient, text placement) is
  unverified — `android.graphics.Bitmap` can't run in plain unit tests, so only the geometry
  helpers + colour mapping are tested; the visual result needs a device to eyeball. The share
  path itself (FileProvider/ACTION_SEND) is the proven Sprint-20 util, unchanged.
  versionCode 50, versionName 0.36.0.
- [x] Sprint 40 — lift-phase state machine (settling/onset gating for camera-shake jitter),
  v0.37.0: the tracker treated camera shake during the stationary pre-lift phase as bar movement;
  this adds a rep-phase state machine that gates velocity to the actual lift.
  (1) `domain/util/LiftPhaseDetector.kt` (pure Kotlin, no Android — `domain/util/`, not the
  requested `domain/analysis/`; `Point2D` substituted for `PointF`): IDLE → SETTLING (via
  `startRep()`) → READY → MOVING → COMPLETE → READY. Settling measures a per-session baseline
  centroid + variance (what "stationary" looks like on THIS device); READY clamps a near-baseline
  centroid to (0,0) so a still bar produces no phantom velocity; onset (READY→MOVING) requires
  displacement > 12px AND direction-consistent motion (positive dot of consecutive motion vectors)
  for 8 consecutive frames — random back-and-forth jitter resets the run; completion (MOVING→
  COMPLETE) requires sustained low velocity (15 frames < 0.05 m/s) AND real ROM (> 0.05 m), so a
  camera bump can't end a rep; COMPLETE→READY after 500ms, rebaselining. **10 unit tests** cover
  every transition incl. the jitter-rejection and ROM-gated-completion cases — the most thoroughly
  tested piece of the whole VBT feature, and (unlike everything camera-side) genuinely verified.
  SPEC BUG FIXED: the READY clamp was specified as "displacement < baselineVariance * 2.0", but
  displacement is px and variance px² — dimensionally invalid and pathological as noise scales;
  implemented as 2 standard deviations (2·√variance), the correct "within settling noise" band,
  and documented.
  (2) Full live integration: `BarPathLiveAnalyzer` runs each centroid through the detector before
  the Kalman filter and only computes/reports velocity in MOVING (resetting the Kalman at movement
  onset so the READY→MOVING coordinate jump doesn't spike velocity — a deliberate deviation from
  the literal "feed baseline-subtracted coords into the Kalman", which would create exactly that
  spike). `LiveFrameResult` gains `phase` + `repJustCompleted`. `BarPathVideoRecorder.startRep()`
  and `BarPathLiveAnalyzer.startRep()` added; `startRecording` no longer resets the analyzer (the
  phase machine owns its own lifecycle now). `BarPathCaptureViewModel` exposes `livePhase`.
  (3) UI: the live readout is phase-aware (TAP START REP / SETTLING… / ● READY / ▲ MOVING + speed /
  REP COMPLETE), velocity shown only in MOVING; a large "START REP" button overlays the preview
  when IDLE/COMPLETE. Adaptations from the request, flagged: "remove automatic velocity-threshold
  rep-start" — there was none to remove (recording is manual). "MOVING: show the full velocity-
  coloured trail" — no live trail exists (declined earlier), so the readout shows phase + gated
  speed instead. START REP drives the live phase machine independently of the video RECORD/STOP
  (which still produces the file for post-hoc analysis) — a coexistence rough edge worth unifying
  later.
  KNOWN GAP: the detector itself is fully unit-tested and trustworthy, but its LIVE behaviour
  (running on real camera frames at frame rate, the uncalibrated m/s completion threshold, the
  whole live-analysis path) is unverified on a device — same standing caveat as the rest of the
  live loop.
  versionCode 51, versionName 0.37.0.
- [x] Sprint 41 — gyroscope shake compensation, offline path (v0.39.0 "Sensor Fusion Active",
  through v0.41.0): compensates for camera rotation by subtracting the gyro-predicted apparent
  centroid shift, so shaking the phone during a stationary/lifting bar doesn't corrupt the tracked
  path. User chose (over live-only) to fix the SAVED numbers, so this runs in the OFFLINE analysis
  path (the one that produces the persisted results / rep card / replay), not just the live
  overlay. NOTE: this sprint was completed partly by parallel work on the repo — the version
  stream advanced 0.37→0.41 and releases v0.38–v0.41 were cut outside this session's own
  build/commit flow (a GitHub release workflow was also added/fixed), and this CLAUDE.md entry was
  written after the fact to close the progress-log gap.
  (1) Pure, unit-tested cores (`domain/util/`, not the requested `domain/analysis/`): `ShakeCompensator`
  (`compensate` subtracts `focalLengthPx × cumulativeAngle`; `focalLengthPx` = pinhole formula) —
  4 tests; `GyroTimeline` (records cumulative integrated angle keyed by device-uptime ns, linear
  interpolation with end-clamping) — 4 tests. **SPEC CORRECTNESS FIX**: the spec passed the angle
  *since the last frame* and subtracted it from the raw centroid — mathematically wrong (the raw
  centroid carries the *accumulated* rotation, so per-frame subtraction leaves position and
  frame-to-frame velocity corrupted). Uses the CUMULATIVE angle since a reference instead, which
  makes compensated frame-to-frame displacement = real motion − that interval's rotation (correct
  for velocity). `PointF` → pure `Point2D`.
  (2) `BarPathVideoRecorder.startRecording` records the gyro (`TYPE_GYROSCOPE_UNCALIBRATED` with
  `TYPE_GYROSCOPE` fallback, `SENSOR_DELAY_FASTEST`) into a `GyroTimeline` for the duration of the
  capture, anchors the video-timeline↔gyro-timeline alignment on `SystemClock.elapsedRealtimeNanos()`
  captured at `VideoRecordEvent.Start`, reads focal length + sensor width from the back camera's
  `CameraCharacteristics`, and hands all of it to the callback on finalize (gracefully null when no
  gyro). `BarPathCaptureViewModel` threads it through; `BarPathFrameTracker.trackMarker`/
  `trackMarkerPair` map each frame's video-PTS → uptime via the anchor, look up the cumulative
  angle, and compensate the centroid before it enters the pipeline.
  KNOWN GAP — the important one: the offline gyro↔video timebase alignment is the single most
  device-fragile, unverifiable part. It assumes the gyro `SensorEvent.timestamp` and
  `elapsedRealtimeNanos()` share a timebase (device-dependent), and that the recording-start anchor
  matches video-PTS 0 (there's real capture-start latency). The pure compensation math is tested
  and correct; whether the *alignment* is accurate enough to actually clean up real footage is
  entirely unverified on a device. Also: `imageWidthPx` for the focal→pixel conversion uses the
  extracted frame width, and rotation-axis handling for portrait video is unverified. The live-path
  gyro wiring (task 4/6 of the request) was NOT added — this is offline-only, per the chosen scope.
  versionCode 55, versionName 0.41.0 (through parallel releases v0.38–v0.41).
- [x] Sprint 42 — adaptive Kalman noise + velocity deadband (v0.42.0): the live VBT readout
  (`KalmanTracker2D`, consumed by `BarPathLiveAnalyzer`) previously ran one fixed process/measurement
  noise pair, so it chased marker jitter during the pre-lift stationary phase and showed a phantom
  ~0.02 m/s. Two fixes, both on the pure/testable filter:
  (1) `KalmanTracker2D.setPhase(LiftPhase)` retunes both noise levels to the current phase —
  process σ dropped hard while stationary (IDLE/SETTLING 0.1, READY/COMPLETE 0.5) and opened to
  `VbtConstants.KALMAN_PROCESS_NOISE` (50) in MOVING; measurement σ inverted (8.0 stationary, 2.0
  MOVING). `measurementNoiseSigma`/`processNoiseSigma` became mutable; R (2x2, dt-independent) is
  rebuilt once per phase change in `setPhase`, while Q stays assembled per-frame in `predict` since
  it scales with the variable frame dt and can't be precomputed. `setPhase` is idempotent (no-op if
  phase unchanged).
  (2) Velocity deadband on `smoothedVelocityMps`: readings under 3 cm/s (`VELOCITY_DEADBAND_MPS`)
  are clamped to exactly 0 UNLESS phase == MOVING (a genuine slow grind can sit below that), so the
  live number reads a clean 0.00 while racked.
  Wiring: the prompt asked for a `tracker.setPhase(phase)` call "from the ViewModel", but the Kalman
  lives inside `BarPathLiveAnalyzer` (alongside the phase detector, which is where the per-frame
  phase is known) — so the single call site is the analyzer, right after `phaseDetector.update`, not
  the ViewModel. Added 5 unit tests (deadband clamps outside MOVING / preserved during MOVING,
  MOVING more responsive than READY, setPhase idempotency).
  KNOWN GAP: `BarPathLiveAnalyzer` currently only runs the Kalman during MOVING (it resets at
  movement onset and gates velocity to that phase), so the READY/IDLE/COMPLETE process-noise settings
  are set-but-dormant for the live readout as wired today — the *active* effects are the deadband and
  the MOVING-phase tuning. Making the stationary-phase damping actually shape the on-screen number
  would mean running the filter continuously across phases (a `BarPathLiveAnalyzer` change, not done
  here). And, as with the whole live VBT stack, none of this is verified on a real device this session.
  versionCode 56, versionName 0.42.0.
- [x] v0.42.1 — hardened bar-path recording against on-device crash: user reported the app
  crashing "when recording" — the first real-device run of the live-camera capture path (never
  device-tested before). No stack trace available (USB debugging not set up), so this is a
  probable-cause hardening pass, not a confirmed fix, chosen by the user over connecting for the
  exact trace.
  (1) **High-speed capture defaulted OFF**: `effectiveHighSpeedEnabled` was
  `highSpeedPreference ?: deviceSupportsHighSpeed` (auto-on for any device advertising a high fps),
  now `?: false` (opt-in only). Forcing a fixed `CONTROL_AE_TARGET_FPS_RANGE(120,120)` via
  Camera2Interop is the most device-fragile part of the pipeline and the leading crash suspect —
  many devices only advertise a *variable* `[30,120]` range and reject a fixed one at bind.
  (2) **Hard guards so a camera failure surfaces instead of crashing**: `BarPathVideoRecorder`
  gained an `onError: (String) -> Unit` param; the entire `ProcessCameraProvider` provider-ready
  callback body in `bindCamera` is wrapped in `try/catch (Throwable)` (last-resort — nothing on
  the camera main-executor callback may crash the app), and `startRecording`'s whole body is
  likewise wrapped (record start can throw on device from codec/camera state), resetting the gyro
  listener + reporting `onFinalized(null,...)` so the UI unwinds `isRecording` instead of hanging.
  `stopRecording` wraps its `stop()`/`unregisterListener` in `runCatching`. `onError` is wired to
  the existing `RecordingStep` snackbar.
  (3) `BarPathLiveAnalyzer.analyze` per-frame catch broadened `Exception` → `Throwable` (covers an
  OOM on a large frame — a dropped frame beats a downed camera).
  KNOWN GAP: still no confirmed root cause — if the crash persists after this, the real fix needs
  the logcat stack trace via USB debugging. versionCode 57, versionName 0.42.1.
- [x] Sprint 43 — live tap-to-color-sample (v0.43.0): closes the gap flagged in Sprint 37/39 —
  live tracking used a fixed `MarkerColorProfile.default()` (magenta/pink) because the user's real
  marker color was only ever sampled AFTER recording (Sprint 29's calibration-frame tap). Now the
  user can tap the marker directly in the LIVE preview during `RecordingStep` and lock onto it.
  (1) `util/barpath/ColorPatchSampler.kt` (new, pure — no Bitmap/Android dependency, operates on
  the same flat ARGB IntArray `BarPathLiveAnalyzer` already decodes): `sampleColorPatch` averages
  HSV statistics over a small patch instead of one pixel — saturation-filtered first (drops
  near-grey specular highlights on a metal bar/plate), falling back to the unfiltered patch if
  fewer than 20 pixels clear the filter (the user may have tapped a genuinely low-saturation
  marker). Uses `MarkerColorMatcher.rgbToHsv`'s [0,360) hue convention — deliberately NOT
  `android.graphics.Color.colorToHSV`'s [0,180], which the prompt suggested but would silently
  desync from every other hue calculation in this pipeline (`MarkerColorProfile.hueDistance` etc.
  all assume 360°). `circularMeanDegrees`/`circularStdDegrees` implement the real Fisher
  circular-statistics formulas (mean via atan2(mean sin, mean cos); std = sqrt(-2 ln R)) — the
  prompt's pseudocode left "circular std" as an unfilled TODO comment, this fills it in properly
  rather than approximating. Tolerances: hue = 2.5σ with an 8° floor (prevents a perfectly uniform
  patch collapsing to a zero-width range), sat/val = 2σ + a small floor, mapped onto
  `MarkerColorProfile`'s REAL fields (hueCenter/hueTolerance/minSaturation/minValue/satCenter/
  satTolerance/valCenter/valTolerance) — not the differently-shaped `ColorProfile` the prompt
  described (hueRange/satMin/satMax/valMin/valMax/sampleSize/...), which doesn't exist in this
  codebase and would have forked the profile type the rest of the pipeline already depends on.
  10 unit tests (uniform-patch matching, tolerance floor holding, edge-clamped tap, saturation-
  filter relaxation, distinguishing two different patch colors, circular mean/std correctness).
  (2) `BarPathLiveAnalyzer` gains `pendingColorSample: PendingColorSample?` (`@Volatile`, set by
  the tap handler, consumed — and cleared — by the next `analyze()` call) and `LiveFrameResult`
  gains `sampledColorProfile: MarkerColorProfile?` (non-null only on the frame that consumed a
  pending request), reusing the single existing `onResult` callback conduit rather than adding a
  second one. `analysisStep` (the downsample factor) is now a named constant shared between the
  pixel-decode call and the tap-coordinate math, instead of the literal `2` appearing twice, which
  would have been exactly the kind of silent-desync bug this project's retros keep catching.
  **The genuinely unverified piece**: `pendingColorSample.normX/normY` come from CameraX's
  `PreviewView.meteringPointFactory.createPoint(x, y)` — the same documented mechanism CameraX
  uses for tap-to-focus, normalized to the sensor's active array, and used here (not hand-rolled
  crop/letterbox math) specifically because it's the one API actually designed for "map a screen
  tap to a sensor-relative point." The mapping assumes Preview and the live `ImageAnalysis` stream
  share the same crop region — a known CameraX gotcha when their target aspect ratios differ, and
  something this session cannot verify without a device.
  (3) `BarPathVideoRecorder.requestColorSample(normX, normY)` forwards to the bound analyzer
  (no-op if the live loop isn't bound), mirroring the existing `startRep()` precedent.
  (4) `BarPathCaptureViewModel`: `liveColorProfile`/`liveColorLockedOn` StateFlows, updated from
  `onLiveResult` exactly like the existing `liveTracking`/`liveVelocity`/`livePhase` fields;
  `onRetapColor()` re-arms tap capture.
  (5) `RecordingStep` (`BarPathCaptureScreen.kt`): a transparent tap-catcher `Box` layered over the
  live `PreviewView`, active only while not yet locked on (an accidental tap mid-recording can't
  silently re-sample); on tap, hoists the actual `PreviewView` reference (captured in the
  `AndroidView` factory) to compute the metering point, calls `recorder.requestColorSample(...)`
  directly (matching how `recorder.startRep()`/`startRecording()` are already called straight from
  this composable, not routed through the ViewModel), and shows a white dashed 30×30dp square at
  the tap point that fades out over 1.5s (`Animatable` + `tween(1500)`) — confirms to the user
  where the app actually sampled from. A RE-TAP button appears top-end once locked on.
  KNOWN GAP: the metering-point-to-analysis-buffer coordinate mapping is the standing camera-
  geometry risk (same category as every prior VBT sprint's device caveat) — unverified whether
  Preview/ImageAnalysis crop regions actually align on a real device. If a live tap consistently
  samples the wrong spot, that mismatch is the first thing to check.
  versionCode 58, versionName 0.43.0.
- [x] Sprint 44 — phone stability indicator for live tap-to-sample (v0.44.0): a tap during camera
  shake produces a blurred, spatially-averaged patch sample ([sampleColorPatch] averages a pixel
  neighborhood, so motion blur across it corrupts the color) — this warns the user before they
  tap, and gates what actually happens when they do.
  (1) No `GyroscopeIntegrator` class existed (the prompt assumed one) — the real gyro code is
  `GyroTimeline`, an unrelated concern (cumulative angle over a recording, for OFFLINE shake
  compensation, alive only during `startRecording`). Rather than bolt an "instantaneous magnitude"
  reading onto that class, built a separate, always-on piece: `domain/util/CameraStability.kt`
  (pure — `StabilityLevel` enum + `angularVelocityMagnitude(x,y,z)` = sqrt(ωx²+ωy²+ωz²) + the
  0.05/0.15 rad/s threshold classifier, all unit-tested) and `util/barpath/StabilityMonitor.kt`
  (Android-touching — wraps a `SensorEventListener` on `TYPE_GYROSCOPE`, falling back to
  `TYPE_GYROSCOPE_UNCALIBRATED`, `SENSOR_DELAY_UI` since this only drives a UI hint and doesn't
  need the recording-time listener's `SENSOR_DELAY_FASTEST` precision). Runs for the whole time
  the camera preview is visible (`DisposableEffect` in `RecordingStep`, scoped to the
  `hasPermission` block so it starts "immediately when the camera opens," per spec), fully
  independent of `BarPathVideoRecorder`'s own recording-time gyro listener.
  (2) Stability is classified in the TAP HANDLER itself (`RecordingStep`'s `detectTapGestures`),
  not inside `BarPathLiveAnalyzer`'s frame loop — the analyzer runs on a background camera thread
  with no gyro access today, while the tap handler already has the current angular-velocity value
  synchronously on the main thread. MOVING: no `requestColorSample` call at all, a gentle (not
  harsh) snackbar — "Hold the phone still, then tap" — exactly the wording asked for. SETTLING:
  proceeds, with a `widenTolerance=true` flag riding along in `PendingColorSample`. STABLE:
  proceeds normally.
  (3) `MarkerColorProfile.widened(factor)` (new extension in `MarkerColorProfile.kt`): scales
  `hueTolerance`/`satTolerance`/`valTolerance` by the factor — the real fields, not the prompt's
  invented `hueRange`/`satRange`/`valRange`, which don't exist on this profile shape. Deliberately
  leaves `minSaturation`/`minValue` (the floor-based accept/reject gate) untouched — only the
  distance-scale tolerance fields widen. Applied in `BarPathLiveAnalyzer.analyze()` right after
  `sampleColorPatch` computes the raw profile, before it's stored as `colorProfile`.
  (4) `angularVelocityMagnitudeAtTap` rides through `PendingColorSample` → `BarPathLiveAnalyzer`
  (logged via `Log.i("BarPathColorSample", ...)` alongside the resulting profile, genuinely
  correlated in one line rather than two disconnected log calls from different threads/times) →
  `LiveFrameResult.sampledAngularVelocityMagnitude` → a new `liveColorSampleAngularVelocity`
  StateFlow in the ViewModel, cleared on RE-TAP. **Scope call, flagged rather than silently
  under-built**: this is in-memory for the capture session only, not written to Room — the color
  profile itself isn't persisted either (it only configures the live tracker at runtime), so
  persisting just the stability reading without the profile it was measured against wouldn't
  support real cross-session correlation anyway. True "correlate with tracking quality later"
  would need a new persisted calibration-diagnostics table; out of scope unless asked for.
  (5) UI: `StabilityIndicator` composable (top-left, stacked above the existing
  `LiveTrackingReadout` in a shared Column so they don't overlap) — colored dot (DangerRed/
  PowerAmber/NeonGreen, all existing theme tokens, no hardcoded colors) + label, pulsing alpha via
  `rememberInfiniteTransition` only in SETTLING. Hidden once `liveColorLockedOn` — "only needed
  pre-tap," per spec.
  14 new unit tests (4 CameraStability threshold/magnitude tests, 4 MarkerColorProfile.widened
  tests, plus reuse of existing ColorPatchSampler/analyzer test infra — no changes needed there).
  KNOWN GAP: unverified on a real device this session, same standing caveat as the whole live VBT
  stack — whether the 0.05/0.15 rad/s thresholds actually feel right in the hand (too twitchy, too
  lenient) is a real tuning question that needs a phone, not just math.
  versionCode 59, versionName 0.44.0.
- [x] Sprint 45 — lock-on targeting reticle (v0.45.0): the "magnet" state between a successful
  tap and the lift starting — confirms the tracker actually found (and is holding) the right
  object before the user trusts it enough to lift.
  Two real mismatches flagged before building, not silently worked around: (1) the prompt asked
  for a custom `android.view.View` + `ValueAnimator`/`ObjectAnimator` — this project's CLAUDE.md
  lists "Compose only. No XML layouts, ever" as a NON-NEGOTIABLE rule. Built as a Compose
  composable on a Compose `Canvas` instead, using Compose's own first-party animation APIs
  (`Animatable`, `animateFloatAsState`, `spring()`, `rememberInfiniteTransition`) — genuinely
  first-party to this UI toolkit, so "no third-party animation libraries" is honored in spirit,
  just through Compose's own system rather than the View-system one. (2) `BarTrailOverlayView`
  does not exist (grep-confirmed) — no live trail overlay was ever built (explicitly deferred in
  Sprint 37/40 as a materially different architecture, CameraX `ImageAnalysis` + live rendering).
  Implemented the fade-out-on-MOVING behavior the reticle itself needs; skipped "BarTrailOverlayView
  becomes visible" since there's nothing to make visible — that remains its own separate, larger,
  not-yet-built feature.
  (1) `domain/util/LockOnTracker.kt` (pure, no Android — mirrors `LiftPhaseDetector`'s pattern of
  a small stateful class driven by per-frame updates): `ReticleState{SEARCHING,ACQUIRING,LOCKED}`,
  literal threshold rules from spec (5 consecutive detections -> LOCKED; any pre-lock miss resets
  the streak; once LOCKED, misses are tolerated up to 10 consecutive frames before reverting to
  SEARCHING with a one-shot `justLostLock` flag). Confidence = 1 - coefficient-of-variation of a
  10-frame blob-diameter window (a real marker holds roughly constant apparent size; a false
  positive — a shirt, a reflection — tends to fluctuate). This is explicitly a DIFFERENT axis from
  `LiftPhaseDetector` (detection-quality vs. rep-timing), not a replacement or a merge. 10 unit
  tests (every transition, the pre-lock-vs-post-lock miss-tolerance asymmetry, confidence on
  stable vs. wildly-varying diameter, reset).
  (2) `BarPathLiveAnalyzer`/`LiveFrameResult` gained `blobDiameterPx`/`frameWidthPx`/
  `frameHeightPx` (all additive, existing detection data that was being computed and discarded —
  no new detection logic). `BarPathCaptureViewModel` owns the `LockOnTracker` instance, feeds it
  from `onLiveResult` only once `liveColorLockedOn` is true (pre-tap, there's no profile to detect
  against, so the reticle has nothing to show) — new `reticleState`/`reticleConfidence`/
  `liveMarkerFrame`/`lockLost` StateFlows, all reset together on a fresh sample or RE-TAP.
  (3) `presentation/screens/barpath/LockOnReticle.kt`: SEARCHING (rotating dashed white circle at
  the last tap point, 360°/2s), ACQUIRING (amber, pulsing 80%<->40% alpha over 600ms, radius
  animating toward the detected blob size), LOCKED (green camera-autofocus-style corner brackets
  — 4 hand-drawn L-shapes, not a full square — snapped to the blob bbox + 8dp padding via a
  `spring()` "snap" animation, "Locked ✓" label fading after 1.5s, a confidence bar colored
  green/amber/red by the 0.7/0.4 thresholds, and a "LIFT WHEN READY" prompt after 1s of sustained
  lock — all via fresh `LaunchedEffect(Unit)` scopes that correctly restart every time LOCKED is
  freshly (re)entered, since this branch is only composed while state==LOCKED). Position exponential
  smoothing (spec's literal `pos = pos*0.7 + new*0.3`) via a `LaunchedEffect` keyed on the raw
  target. Whole reticle fades over 300ms when `livePhase == MOVING`. A separate `LockLostBanner`
  ("Lost marker — retap to reselect" + a RETAP button reusing the existing `onRetapColor()` from
  Sprint 39/40) — per spec, does NOT reset the session, just re-arms the tap-catcher with the
  existing profile still configuring the analyzer as a starting point.
  KNOWN GAP, and the important one: [xPx]/[yPx] -> screen-position mapping reuses the SAME
  approximation already flagged for tap-to-sample (Sprint 39/44) — assumes the downsampled
  analysis buffer and the displayed preview box share aspect ratio/crop, no correction for
  CameraX's FILL_CENTER possibly cropping Preview and ImageAnalysis differently. This is the first
  time that assumption becomes visually consequential (a positioned overlay snapping to a point on
  screen, not just a numeric debug readout) — unverified on a device, same standing caveat as the
  whole live VBT stack.
  versionCode 60, versionName 0.45.0.
- [x] Sprint 46 — continuous live VBT session, wired end-to-end (v0.46.0): the "full coherent
  journey" request — camera opens, tap once, then every subsequent rep auto-detects with no
  further taps, until the user ends the session.
  **Real scope fork, asked before building** (AskUserQuestion): the rep-summary card's "Mean
  Propulsive Velocity: X.XX m/s" implies calibrated physics, but the live pipeline has never had a
  real pixels-per-meter scale (that only ever comes from post-hoc calibration on a *completed*
  recording — the whole existing offline pipeline: SG smoothing, gyro compensation, dual-marker
  correction, all operate on ONE finished video file, not a continuous multi-rep live stream).
  Three options laid out: (a) live uncalibrated numbers, honestly labelled, buildable now; (b) a
  new one-time inline scale-calibration step before the first rep, for real m/s thereafter; (c)
  segment the continuous recording per rep and run the full offline pipeline + save real video per
  rep — by far the largest build. User picked (a).
  Also flagged: `BarTrailOverlayView` doesn't exist (checked again) — built new this sprint, since
  STEP 4 explicitly needs it and it's squarely in scope now, unlike prior sprints where it was
  correctly out of scope.
  **A real latent bug/rough-edge found while wiring this, not part of the original ask**: read
  `LiftPhaseDetector.kt` before wiring the "no tap between reps" requirement, and confirmed
  `COMPLETE -> READY` already transitions fully automatically (500ms, self-rebaselining) and READY
  already actively watches for the next onset with zero external calls — meaning the old manual
  "START REP" button was only ever load-bearing for the very FIRST rep of a session, not the
  ones after. Replaced it with a single `LaunchedEffect(liveColorLockedOn) { recorder.startRep() }`
  firing once per lock-on — genuinely satisfies "auto-detect every rep, no tap between reps"
  including rep 1, and removes a UI element that was redundant for every rep after the first
  (a real UX improvement discovered via code-reading, not assumed).
  (1) `BarPathCaptureViewModel` — "Continuous live rep session" section: accumulates
  `TrackedFrame`s (reusing the existing domain model, timestamped via `System.currentTimeMillis()`
  since there's no calibrated frame timestamp live) while `phase == MOVING`; on
  `LiveFrameResult.repJustCompleted` (fires with `phase==COMPLETE` — verified before assuming, so
  the accumulate-then-check call order in `onLiveResult` is correct without a special case),
  computes `LiveRepSummary` (mean velocity via the SAME total-displacement/total-time formula
  shape the offline pipeline uses, just unscaled pixels; peak; range-of-motion in px).
  **Deliberately never written to `BarPathAnalysis`/`bar_path_metrics`** — that table's fields are
  real physical units, consumed as such by `ExerciseDetailScreen`'s velocity chart and
  `RepCardGenerator`'s share card; writing fake numbers into real-labelled columns would silently
  corrupt both. Kept in an in-memory `liveSessionReps` list instead (session-lifetime only, matches
  the chosen scope's "no video/no persisted record" limits) — `onSaveRep`/`onDiscardRep` just
  manage that list + clear per-rep accumulation; `onShareLiveRep` explains why sharing isn't wired
  (a real share card built around real m/s would misrepresent a relative number) rather than
  silently no-op'ing. `onEndLiveSession` stops the loop and clears session counters — does not
  navigate away (no such callback exists on this screen) and doesn't touch the separate manual
  RECORD/STOP + offline-analysis flow, which remains fully independent and unaffected.
  (2) `presentation/screens/barpath/LiveTrailOverlay.kt` (new): velocity-colored polyline through
  accumulated MOVING-phase points, reusing `velocityColorArgb` (built for the calibrated offline
  replay screen, 0.2-0.8 m/s anchors) purely as a color-gradient shape — documented as a visual
  approximation, not a claim of matching real velocity zones, since this trail's numbers are the
  same uncalibrated relative readings.
  (3) `presentation/screens/barpath/LiveSessionUi.kt` (new): `LiveSessionTopBar` ("● LIVE" +
  elapsed session timer — deliberately NOT labelled "REC", since no video is saved per rep in this
  mode and that framing would be misleading; rep count; ⊕ RETAP; ✕ END) and `RepSummaryCard`
  (labels every number "rel. speed" / "px", never bare "m/s" or "cm" — the whole point of the
  scope decision above).
  (4) `BarPathCaptureScreen.kt` (`RecordingStep`): `LockOnReticle` now renders unconditionally
  (not gated on `liveColorLockedOn`) — its `reticleState` already defaults to SEARCHING pre-tap
  and its `tapAnchor` fallback already centers on the box, so this doubles as STEP 1's "subtle,
  not intrusive" ambient reticle hint for free, continuing seamlessly into real tracking once a
  tap lands. Removed the old standalone RE-TAP button (superseded by the top bar's). Tap-flash
  timing changed to the literal spec (200ms fade-in / 500ms hold / 500ms fade-out, was a single
  1500ms fade) — a real, deliberate replacement, not additive, since keeping two different flash
  timings for the same visual interaction made no sense.
  KNOWN GAP: this is Option (a) — every velocity/ROM number in this mode is relative, not real
  physics, by deliberate choice. Getting real m/s per rep without re-tapping (option b) or full
  per-rep video + offline pipeline (option c) are real, larger follow-ups if wanted later, not
  silently done here. Unverified on a real device this session, same standing caveat as the whole
  live VBT stack — now covering more surface area (continuous multi-rep cycling, the new trail
  overlay, the auto-startRep timing) than any single prior sprint.
  versionCode 61, versionName 0.46.0.
- [x] Bug fix (v0.46.1): "Recording failed — try again" on the manual RECORD/STOP flow (the
  offline record→calibrate→analyze path, not the continuous live session). Root cause: real race,
  not a device flake. `BarPathVideoRecorder.bindCamera` runs async — `ProcessCameraProvider
  .getInstance` resolves on a later main-executor tick, only then setting `videoCapture` — but the
  RECORD button in `BarPathCaptureScreen` was enabled on `hasPermission` alone. A tap before bind
  completed hit `videoCapture ?: run { onFinalized(null, ...) }` in `startRecording`, which
  `BarPathCaptureViewModel.onRecordingFinished` surfaces as exactly this message.
  Fix: `bindCamera` gained an `onBound: () -> Unit` callback, fired once `videoCapture` is
  genuinely set (both the with-live-analysis and without-analysis bind paths, including the
  high-speed-unavailable retry branch). `BarPathCaptureScreen` tracks `isCameraBound` from it and
  gates the RECORD button on `hasPermission && (isRecording || isCameraBound)`, showing "STARTING
  CAMERA…" in the brief window before bind completes instead of a tappable-but-doomed button.
  versionCode 62, versionName 0.46.1.
- [x] Sprint — Biomechanics Visualizer Phase 1 (v0.47.0), per `docs/biomechanics-spec.md`: a
  proportion-aware stickman animator showing how 4 fixed body archetypes (LONG_FEMUR/
  SHORT_FEMUR/PROPORTIONAL/WIDE_HIP) move differently through a squat. Two real deviations from
  the spec's literal prompts, flagged before building: (1) Compose-only, not Fragments/custom
  `View` — `StickmanCanvasView` became a Compose `Canvas` composable ([StickmanCanvas]), with
  the pure/testable topology (segment groups, draw order, floor-line logic) split into
  [StickmanRenderer], a genuinely Android-free object, same "pure core + thin Compose wrapper"
  shape as the VBT work. (2) domain/model, domain/repository, domain/usecase are FLAT in this
  project (confirmed via directory listing, not the spec's suggested nested
  `domain/biomechanics/`) — followed the established convention instead.
  (1) **Placeholder geometry**: rather than the spec's literal "all nodes at (0.5,0.5)" (would
  render nothing recognizable), wrote a one-time forward-kinematics generator
  (scratchpad `gen_biomechanics.py`, not shipped) converting spec section 7's hand-authored
  hip/knee/torso angle table into real normalized (x,y) node positions per archetype/phase — a
  single sagittal spine centerline (ankle→knee→hip→neck→head) with L/R nodes offset
  symmetrically at each joint height (the standard simplification stylized exercise-form
  diagrams use for depicting forward lean as a centerline shift). WIDE_HIP has no separate
  angle table in the spec (defined by hip width, not femur ratio) — reuses PROPORTIONAL's
  sagittal angles, widened hip half-width only. All 288 node coordinates bounds-checked by the
  generator before being written to the JSON assets. Explicitly a first pass — user chose
  "placeholder now, tune after seeing it render" over hand-authoring angles/ratios upfront.
  (2) **Domain layer**: `Archetype`/`LiftType`/`NodeId`/`NodePosition`/`StickmanKeyframe`/
  `ArchetypeAnimation`/`ArchetypeInfo` — all `@Serializable` directly (no separate DTO/mapper
  layer; these are pure value objects loaded once and cached, matching kotlinx.serialization's
  existing use in `BackupPayload`). Renamed the spec's "LiftPhase" enum to `BiomechanicsPhase`
  — [com.saiyanstrong.domain.util.LiftPhase] already exists (the VBT rep-timing state machine,
  IDLE/SETTLING/READY/MOVING/COMPLETE) — a same-simple-name collision that would force an
  import alias the moment a screen needs both, which Phase 2B (VBT bar-path overlay) explicitly
  will. `BiomechanicsRepository`/`Impl` mirror `ExerciseMediaRepositoryImpl`'s load-once-
  Mutex-cache shape, reading from bundled assets (always present, no network/cache-file
  fallback chain needed) via `kotlinx.serialization.json.Json { ignoreUnknownKeys = true }`
  (matching `BackupSerializer`'s existing convention). `getAnimation` throws
  `IllegalStateException` for the (currently unreachable) DEADLIFT case — Phase 1 ships SQUAT
  only, per the spec's own acceptance criteria; the Lift Selector shows DEADLIFT disabled
  ("SOON") rather than omitting it.
  (3) **Persistence**: `UserPreferencesDataStore` gains `selectedArchetypeName` (String, default
  "PROPORTIONAL") and `biomechanicsDisclaimerShown` (Boolean) — same key/method precedent as
  every other DataStore-backed preference in this file; `UserRepository`/`Impl` convert
  String↔`Archetype` at that boundary (invalid/corrupt names fall back to PROPORTIONAL rather
  than crash).
  (4) **UI**: `ArchetypeSelectionScreen` (2×2 grid, live standing-pose stickman per card,
  first-run disclaimer `AlertDialog`, selection persists, "Compare all four →") →
  `LiftSelectorScreen` → `BiomechanicsVisualizerScreen` (stickman + scrub slider +
  mechanical-facts/irrelevant-cue/stance-cue cards, "Compare with another build") →
  `BiomechanicsCompareScreen` (one screen backs both the 2-up and 4-up entry points, grid
  column count adapts to entry count, one synced slider). `StickmanInterpolator` (new,
  `domain/util/`) is the pure linear keyframe blend from spec section 12's literal formula —
  unit-tested including exact segment-boundary landings and out-of-range clamping. Content
  copy (mechanical facts/stance cue/irrelevant cue) follows spec section 3's mechanical-not-
  prescriptive language policy throughout; LONG_FEMUR's copy is verbatim from the spec's own
  Screen 3 mockup, the other 3 archetypes composed analogously.
  (5) **Nav**: 4 new routes (`biomechanics`, `biomechanics_lift/{archetype}`,
  `biomechanics_visualizer/{archetype}/{lift}`, `biomechanics_compare/{archetypes}/{lift}` —
  comma-joined archetype names in one path segment); new 6th bottom-nav tab "Body"
  (`Icons.Default.Accessibility`) between Exercises and Settings; the 3 non-selection routes
  hide the bottom bar like `bar_path_capture` already does.
  (6) **Tests**: `StickmanRendererTest` (segment-pair coverage matches the spec's literal list
  exactly, draw-order grouping, floor-line placement including the no-foot-node fallback) +
  `StickmanInterpolatorTest` (7 tests: boundary landings, clamping, single/empty-keyframe
  edge cases) — both fully pure, no Robolectric needed, following this project's established
  "pure core, untested Compose shell" split.
  KNOWN GAPS, explicitly deferred: (a) the placeholder geometry has never been seen rendered on
  a device or emulator this session — angles/ratios are a defensible first pass per the forward-
  kinematics model, not a validated one; tune after a real look, per the user's own chosen plan.
  (b) Deadlift keyframes, the Phase 2A femur-ratio continuous slider, and the Phase 2B VBT
  bar-path overlay are all out of scope, per spec sections 10-11 — not started. (c) Coach-tier
  per-athlete archetype assignment (Phase 2C) not started.
  versionCode 63, versionName 0.47.0.
- [x] Bug fix + simplification (v0.47.1): first real-device look at v0.47.0 surfaced a genuine
  bug plus a clunky-visuals complaint. Fixed both at the root rather than patching symptoms.
  **Root cause of "movement isn't correct"**: `StickmanInterpolator` was lerping the 18 baked
  (x,y) `NodePosition`s between keyframes directly. Linearly interpolating the two ENDPOINTS of a
  rotating rigid segment does not trace the segment rotating — it traces a straight line between
  those two endpoints, which means the segment visibly SHRINKS through the middle of any rotation
  and only regains its true length at the keyframes themselves. Every limb was doing this on every
  scrub frame, which is exactly what "movement isn't correct" looks like.
  Real fix, not a patch: geometry is now driven by 3 angles (`PoseAngles`: hip/knee/torso, straight
  from spec section 7's table) run through a proper forward-kinematics engine
  (`domain/util/StickmanKinematics.kt`, ported from the old one-time Python generator into real
  runtime Kotlin) instead of 18 baked coordinates per keyframe. `StickmanInterpolator` now lerps
  the 3 angle scalars (safe — no geometry to distort) and calls kinematics fresh at every progress
  value, so a limb's on-screen length is always exactly `ratio × bodyScale` at every point in the
  animation — it can't warp. Locked in with a real regression test
  (`StickmanKinematicsTest`/`StickmanInterpolatorTest`: hip-to-knee distance asserted constant
  across 11 sampled progress steps, not just the 4 keyframes).
  **"make each limb adjustable"**: new `LimbRatios` data class (thighRatio, shankRatio,
  torsoRatio, headNeckRatio, footLenRatio, shoulderHalfRatio, hipHalfRatio, kneeHalfRatio,
  ankleHalfRatio, barRiseRatio, gripHalfRatio), one per archetype, each a plain named number in
  the JSON asset — this IS what makes "long femur" mean something concrete (bigger thighRatio,
  smaller shankRatio) and what makes every limb independently tunable without touching code.
  `ArchetypeAnimation` gained a `limbRatios` field; `keyframes_squat.json` rewritten to the new
  angles+ratios shape (much smaller file — ~4 numbers/phase instead of 18 coordinate pairs).
  **"simplify... too clunky"**: joint dots shrunk 8dp/12dp → 3dp/5dp; limb stroke 4dp → 3dp; floor
  line changed from dashed to plain solid; removed the redundant wrist-to-BAR limb stub segments
  (the dedicated bar line already terminates exactly at the wrist by construction, so the stub was
  double-drawing a few px under it) — a deliberate simplification away from the spec's literal
  segment list, called out in `StickmanRenderer`'s topology comment and the updated test. Arms
  simplified to a straight shoulder→wrist line with the elbow placed at the exact midpoint (no
  independent upper-arm/forearm ratio, no kink) — the bar itself is now modeled as rigidly
  attached to the upper back via `barRiseRatio`, rotating with torso lean the way a racked bar
  actually does, with the two wrists positioned symmetrically around it via `gripHalfRatio`.
  Removed the now-dead `armRatio` field from `LimbRatios` (the straight-line/midpoint elbow design
  made it unused) rather than ship an unread parameter.
  KNOWN GAP unchanged: still not re-verified on a real device this session — the fix is
  algebraically guaranteed to eliminate the warping (proven by the regression test, not just
  assumed) and the visual simplifications are straightforward, but "looks right on screen" is the
  user's call on the next real look, per the same "tune after seeing it render" plan as before.
  versionCode 64, versionName 0.47.1.
- [x] Sprint — real squat mechanics + Custom Proportions (v0.48.0), per SPEC.md: user tried
  v0.47.1 and asked for two more things via a `/spec` round (clarifying questions asked and
  answered first): (1) the animation still didn't move like a real squat — the bar should stay
  over mid-foot regardless of torso lean, and the hip should actually drop below the knee at
  depth; (2) live, permanent, per-user sliders for each limb, not just JSON tuning.
  (1) **Real depth bug found and fixed** (not just tuned): `StickmanKinematics`'s thigh-angle
  formula damped the knee-driven deviation by ×0.5, which meant even a fully-flexed knee could
  never rotate the thigh past horizontal — the hip could geometrically never drop below the knee
  no matter what angles the JSON specified. This is why "movement isn't perfected" persisted
  after v0.47.1's separate (correct) interpolation fix. Removed the damping (thigh now deviates
  from the shank's angle by the *full* `180° - kneeAngleDeg`, the textbook 2-link hinge-chain
  relationship) and deepened every archetype's PARALLEL/BOTTOM knee angles so real depth is
  actually reached (verified numerically per archetype, not assumed — e.g. LONG_FEMUR BOTTOM
  knee 80°→58°). `PoseAngles.hipAngleDeg` stays in the model (matches spec's authored table,
  available for future use) but is deliberately not fed into this geometry — documented as a
  stated simplification, not silently dropped.
  (2) **Bar-over-mid-foot**: after the angle-driven chain builds the rig, every node from the hip
  up (hip/torso/head/arms/bar) is shifted horizontally so the bar lands exactly on mid-foot — a
  simple translation per the user's own explicit choice over a full IK solve. The foot itself
  (ankle/knee/toe) is never shifted (planted the whole rep). Tradeoff, documented in code: the
  rendered thigh segment can be a few percent off its ratio-defined length after this
  correction — the *only* segment affected, since shank (neither endpoint shifts) and torso/
  head-neck/arms (both endpoints shift by the identical amount, cancelling in the difference)
  all stay provably exactly rigid, locked in by regression tests.
  (3) **Custom Proportions**: new `Archetype.CUSTOM` (5th, permanent option, not a replacement).
  `GetArchetypeAnimationUseCase` composes `BiomechanicsRepository` (PROPORTIONAL's angle
  template + content) with `UserRepository` (the user's saved `LimbRatios`) for CUSTOM, rather
  than either repository depending on the other — cross-repository resolution lives at the
  use-case layer per Clean Architecture. New `customLimbRatiosJson` DataStore key (same
  get/set-through-UserRepository precedent as `selectedArchetypeName`), defaulting to
  PROPORTIONAL's own ratio values until saved. New `CustomProportionsScreen`/`ViewModel`: live
  stickman + scrub slider + 6 sliders (femur/thigh, shin/shank, torso, shoulder width, hip
  width, foot length ratios — `kneeHalfRatio`/`ankleHalfRatio`/`barRiseRatio`/`gripHalfRatio`
  stay fixed, not user-facing proportions). Dragging updates the preview immediately, no
  debounce (pure local computation). Tapping the "Custom" card (added to `archetypes.json`,
  which is why it automatically got a live standing-pose thumbnail on the selection grid for
  free via the existing generic `Archetype.entries` loop) navigates straight to this screen;
  SAVE persists the ratios and opens the full visualizer. "Compare all four" explicitly excludes
  CUSTOM (it's not one of the 4 fixed body types that feature was built to compare).
  (4) Two real test bugs caught and fixed while re-deriving expected values after the shift:
  comparing `L_ANKLE`-to-`L_KNEE` directly (or reading `L_ANKLE.x` alone as "mid-foot") is wrong
  whenever two joints carry *different* half-widths (`ankleHalfRatio` ≠ `kneeHalfRatio`) — the
  true centerline has to be recovered via the L/R midpoint, which is exact by symmetry. Both
  `StickmanKinematicsTest` and `StickmanInterpolatorTest` had this bug; fixed with a shared
  `centerline()` helper rather than papering over it with a looser tolerance.
  KNOWN GAP unchanged: still not re-verified on a real device this session. The depth and
  bar-over-midfoot fixes are proven by regression tests (exact equalities, not just "looks
  plausible"), and the slider-range defaults (§7 of SPEC.md) are a first pass flagged as
  adjustable — real device feedback is still the next step, per the same standing plan.
  versionCode 65, versionName 0.48.0.

## Release rules

- The project now has two build flavors (`github`, `play`) — see "Play Store
  distribution" below for the full scheme. Everyday GitHub releases use the `github`
  flavor exclusively; nothing below changes for that channel except the task names.
- Always build the APK locally (`.\gradlew assembleGithubDebug`) and upload with
  `gh release upload <tag> SaiyanStrong-<tag>-debug.apk --clobber` immediately
  after `gh release create` — do not wait for CI. The flavored debug APK lands at
  `app/build/outputs/apk/github/debug/app-github-debug.apk`.
- Bump `versionCode` (+1) and `versionName` (= release tag without "v") in
  `app/build.gradle.kts` on every release so the in-app updater compares correctly.
  This single version stream is shared by both flavors — never give `play` its own
  versionCode sequence, or Play's own update mechanism and any future github/play
  parity checks will drift.

## Play Store distribution

SaiyanStrong ships on two channels from one codebase: sideloaded APKs via GitHub
Releases (`github` flavor) and the Play Store (`play` flavor). They share the same
`applicationId` (`com.saiyanstrong`) and the same versionCode/versionName stream —
this is the same app, not two separate products.

**What differs between flavors:**
- `BuildConfig.DISTRIBUTION` — `"github"` or `"play"`, gates all self-update UI/logic
  (HomeViewModel, HomeScreen, SettingsScreen, SettingsViewModel).
- `REQUEST_INSTALL_PACKAGES` permission — declared only via
  `app/src/github/AndroidManifest.xml`, never present in a `play` build. Play policy
  forbids apps from self-updating; a Play-distributed APK requesting this permission is
  itself a policy red flag independent of whether the code path is reachable.
- Signing — `github` (and `debug`) use `signingConfigs["release"]` from
  `keystore.properties`. `play` uses `signingConfigs["playRelease"]` from
  `play-keystore.properties` (gitignored, not yet created — falls back to the GitHub
  keystore locally until it exists). **Never let a real Play Console upload happen
  signed with the GitHub keystore** — create the dedicated `play-keystore.properties`
  first.

**Before actually submitting to Play Console** (none of this has been done — this
sprint only prepared the local build/config/docs side):
1. Generate a dedicated upload keystore, create `play-keystore.properties` from it
   (same 4 keys as `keystore.properties`: storeFile/storePassword/keyAlias/keyPassword).
2. Build the App Bundle Play actually wants: `.\gradlew bundlePlayRelease` →
   `app/build/outputs/bundle/playRelease/app-play-release.aab`.
3. Host `PRIVACY_POLICY.md` somewhere with a real URL (GitHub Pages works; Play Console
   wants an actual webpage, not a raw-file link) and paste that URL into Play Console's
   privacy policy field.
4. Fill in the Data Safety form using `PRIVACY_POLICY.md` as the source of truth for
   what's collected/shared.
5. Add an in-app + web-reachable account/data deletion flow before submitting if the
   app will support Google Sign-In in the listed build — Play's Account Deletion
   policy requires this for any app offering sign-in, and SaiyanStrong doesn't have a
   self-service delete-my-backup flow yet (see Sprint 21 KNOWN GAP above).
6. Upload store assets: `store/play_store_icon_512.png` (hi-res icon) + screenshots
   (not yet captured — need a device/emulator) + `store/LISTING.md` copy.
7. Do a real install-and-test pass of a `playRelease` build on a device before
   submitting — this has not been done from Claude Code this session.
