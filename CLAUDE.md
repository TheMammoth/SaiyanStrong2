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
```

Room DB version: **5**. Migrations: 1→2 sessions.title, 2→3 set_logs.is_failure,
3→4 exercise re-seed (DELETE FROM exercises), 4→5 templates/template_exercises/
body_weight_logs. Any future schema change requires a Migration, never
`fallbackToDestructiveMigration()` in production.

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
    const val BASE_POWER = 9_001

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
