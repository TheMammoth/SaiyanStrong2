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
- [ ] Phase 3 — active workout screen
- [ ] Phase 4 — visualizer
- [ ] Phase 5 — session complete + history
