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
Hilt                     2.51.1
KSP                      2.1.0-1.0.29
Lottie Compose           6.6.0
Kotlin Coroutines        1.8.1
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
│   │   │   ├── AppDatabase.kt
│   │   │   ├── dao/
│   │   │   │   ├── ExerciseDao.kt
│   │   │   │   ├── SessionDao.kt
│   │   │   │   ├── ExerciseLogDao.kt
│   │   │   │   └── SetLogDao.kt
│   │   │   ├── entity/
│   │   │   │   ├── ExerciseEntity.kt
│   │   │   │   ├── SessionEntity.kt
│   │   │   │   ├── ExerciseLogEntity.kt
│   │   │   │   └── SetLogEntity.kt
│   │   │   └── seed/
│   │   │       └── ExerciseSeeder.kt
│   │   ├── datastore/
│   │   │   └── UserPreferencesDataStore.kt
│   │   ├── mapper/
│   │   │   ├── ExerciseMapper.kt
│   │   │   ├── SessionMapper.kt
│   │   │   └── SetLogMapper.kt
│   │   └── repository/
│   │       ├── ExerciseRepositoryImpl.kt
│   │       ├── SessionRepositoryImpl.kt
│   │       └── UserRepositoryImpl.kt
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
│   │   │   └── PowerLevel.kt        ← includes SaiyanStage enum
│   │   ├── repository/
│   │   │   ├── ExerciseRepository.kt
│   │   │   ├── SessionRepository.kt
│   │   │   └── UserRepository.kt
│   │   └── usecase/
│   │       ├── LogSetUseCase.kt
│   │       ├── CompleteSessionUseCase.kt
│   │       ├── CalculatePowerLevelUseCase.kt
│   │       ├── EstimateOneRepMaxUseCase.kt
│   │       └── GetEvolutionStageUseCase.kt
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
│   │   │   ├── PowerLevelBar.kt
│   │   │   └── SaiyanButton.kt
│   │   └── screens/
│   │       ├── home/
│   │       │   ├── HomeScreen.kt
│   │       │   └── HomeViewModel.kt
│   │       ├── workout/
│   │       │   ├── ActiveWorkoutScreen.kt
│   │       │   ├── ActiveWorkoutViewModel.kt
│   │       │   └── ExercisePickerSheet.kt
│   │       ├── visualizer/
│   │       │   ├── VisualizerScreen.kt
│   │       │   ├── VisualizerViewModel.kt
│   │       │   ├── VisualizerState.kt
│   │       │   └── ParticleTendrilCanvas.kt
│   │       ├── session_complete/
│   │       │   ├── SessionCompleteScreen.kt
│   │       │   └── SessionCompleteViewModel.kt
│   │       └── history/
│   │           ├── HistoryScreen.kt
│   │           └── HistoryViewModel.kt
│   │
│   └── util/
│       └── WeightFormatter.kt
│
└── res/
    ├── assets/
    │   └── lottie/                  ← squat_transition.json, deadlift_transition.json, etc.
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
exercises         : id(PK), name, category, primary_muscles(CSV), secondary_muscles(CSV),
                    lottie_asset, svg_asset_name

sessions          : id(PK autoGen), date_ms, duration_ms, total_volume_kg,
                    power_earned, notes

exercise_logs     : id(PK autoGen), session_id(FK→sessions CASCADE),
                    exercise_id(FK→exercises), order_index

set_logs          : id(PK autoGen), exercise_log_id(FK→exercise_logs CASCADE),
                    set_number, weight_kg, reps, rpe(nullable), volume_kg,
                    timestamp_ms
```

Room DB version: **1**. Any future schema change requires a Migration, never
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

## Build phases — current task

**PHASE 1 (current):** Data foundation. Build in this order:

1. `gradle/libs.versions.toml` + `app/build.gradle.kts` — add all deps
2. All 4 Room entities in `data/local/entity/`
3. All 4 DAOs in `data/local/dao/`
4. `AppDatabase.kt` referencing all entities and DAOs
5. `di/DatabaseModule.kt` — provides DB + all DAOs as singletons
6. `ExerciseSeeder.kt` with Big 4 data
7. `SaiyanStrongApp.kt` — @HiltAndroidApp, seeds on first run
8. `MainActivity.kt` — minimal, just `setContent { SaiyanTheme { } }`
9. `util/WeightFormatter.kt`
10. `di/RepositoryModule.kt` stub (can be empty binds for now)

**Verify Phase 1:** `./gradlew assembleDebug` must pass clean.
Open Database Inspector on device/emulator — `exercises` table must have 4 rows.
`grep -r " lb" app/src` must return zero results.

**PHASE 2 (next):** Domain layer — all models, repository interfaces, use cases.
**PHASE 3:** Active Workout screen + ViewModel (set logging, rest timer).
**PHASE 4:** Visualizer state machine + screen.
**PHASE 5:** Session Complete + History screens.

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

## Release rules

- Always build APK locally (`.\gradlew assembleDebug`) and upload with
  `gh release upload <tag> SaiyanStrong-<tag>-debug.apk --clobber` immediately
  after `gh release create` — do not wait for CI.
- Bump `versionCode` (+1) and `versionName` (= release tag without "v") in
  `app/build.gradle.kts` on every release so the in-app updater compares correctly.
