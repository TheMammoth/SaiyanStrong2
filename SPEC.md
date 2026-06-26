# SPEC — SaiyanStrong Logic Hardening + Exercise Library

**Date:** 2026-06-26  
**Status:** Active  
**Scope:** Logic fixes + 150-exercise library + improved picker. Visualizer/SVG deferred.

---

## Objective

Make the core workout loop reliable. A user should be able to:

1. Pick any exercise from a ~150-exercise library
2. Log sets with correct weight and reps
3. Finish a workout and see it saved in history with accurate volume, power, and 1RM
4. Trust the Power Level number and Saiyan stage

No UI redesign. No SVG/anatomy work. Logic and data only.

---

## Work areas (priority order)

### 1. Set logging correctness

**Problem:** Weight input resets unexpectedly; reps or weight might not persist to the ViewModel.

**Fix:**
- `SetInputPanel` in `ActiveWorkoutScreen.kt` uses `remember(initialWeightKg)` which resets when `initialWeightKg` changes. The initial value should only be seeded once per exercise, not on every recomposition.
- After `onLogSet` is called, the weight should carry forward to the next set (not reset to 60.0). Seed from the last logged set for the active exercise.
- `ActiveWorkoutViewModel.onLogSet` must update `exerciseLogs` atomically — verify the `LogSetUseCase` call and the state update happen in the correct order.

### 2. Rest timer

**Problem:** Timer may not start after logging a set, or `onSkipRest` doesn't clear it.

**Fix:**
- Verify `viewModelScope.launch` for the countdown loop is cancelled on `onSkipRest()` and on `onFinishWorkout()`.
- Timer should start automatically when a set is logged (already intended), not require a separate tap.
- `restTimerSecondsRemaining` must reach `null` when the timer expires naturally.

### 3. Power Level + 1RM accuracy

**Problem:** Power level may not update after a session is completed; 1RM display may be stale.

**Fix:**
- `UserRepositoryImpl` must call `DataStore.updateData` (not `edit`) — confirm the `lifetimePowerEarned` accumulates across sessions (not resets).
- `CalculatePowerLevelUseCase.sessionPowerGained` must use the intensity multiplier table from CLAUDE.md.
- `EstimateOneRepMaxUseCase` Epley formula: `weight * (1 + reps/30.0)` — verify no integer division.
- `SessionCompleteViewModel` must `combine` the session flow and the power level flow so both are non-null before display.

### 4. Session save + history

**Problem:** Finished sessions may not appear in history.

**Fix:**
- `SessionRepositoryImpl.saveSession` must use `AppDatabase.withTransaction` and insert in order: session → exercise_logs → set_logs.
- `CompleteSessionUseCase` must pass `totalVolumeKg` and `powerEarned` (computed before calling save, not after).
- `HistoryViewModel` streams `SessionDao.getAllSessions()` — confirm the DAO returns sessions ordered by `date_ms DESC`.
- After `onFinishWorkout`, `ActiveWorkoutViewModel` must not emit `completedSessionId` until `saveSession` actually resolves (i.e., suspend until done).

---

## Exercise library (~150 exercises)

Expand `ExerciseSeeder.DATA` in `data/local/seed/ExerciseSeeder.kt`.

### Categories and muscle mapping

Use the existing `ExerciseCategory` (SQUAT / HINGE / PUSH / PULL) and `MuscleGroup` enum values verbatim. Add no new enums.

Map `svgAssetName` to the nearest existing PNG key:
- Squat-pattern exercises → `"muscle_squat"`
- Hinge-pattern exercises → `"muscle_deadlift"`
- Push (chest/shoulder) → `"muscle_bench"` or `"muscle_ohp"` (use ohp for overhead movements)
- Pull (back/arms) → use `"muscle_deadlift"` (best available back image until new assets arrive)

Map `lottieAsset` to the nearest existing file:
- `"squat_transition.json"` for squats/leg press/lunges
- `"deadlift_transition.json"` for hinges/rows/pulls
- `"bench_transition.json"` for horizontal push
- `"ohp_transition.json"` for vertical push/overhead

### Target exercise list (150 exercises, IDs 1–150)

IDs 1–4 already exist. New entries start at ID 5.

**SQUAT pattern (legs/glutes):**
Front Squat, Goblet Squat, Bulgarian Split Squat, Hack Squat, Leg Press,
Walking Lunges, Reverse Lunges, Step-Ups, Box Jump, Leg Extension,
Leg Curl (Seated), Leg Curl (Lying), Calf Raise (Standing), Calf Raise (Seated),
Smith Machine Squat, Sissy Squat, Sumo Squat, Single-Leg Press, Wall Sit (timed),
Glute Bridge

**HINGE pattern (posterior chain):**
Romanian Deadlift, Sumo Deadlift, Trap Bar Deadlift, Stiff-Leg Deadlift,
Good Morning, Hip Thrust, Single-Leg Romanian Deadlift, Kettlebell Swing,
Cable Pull-Through, Back Extension (Hyperextension), Seated Leg Curl,
Nordic Hamstring Curl, Glute Ham Raise, Deficit Deadlift, Rack Pull,
Snatch-Grip Deadlift, Block Pull

**PUSH — horizontal (chest/triceps):**
Incline Bench Press, Decline Bench Press, Dumbbell Bench Press,
Incline Dumbbell Press, Cable Chest Fly, Dumbbell Fly, Pec Deck,
Cable Crossover, Push-Up, Close-Grip Bench Press, Dips (Chest),
Landmine Press, Smith Machine Bench Press, Plate Press

**PUSH — vertical (shoulders/triceps):**
Dumbbell Shoulder Press, Arnold Press, Push Press, Behind-the-Neck Press,
Lateral Raise, Front Raise, Cable Lateral Raise, Face Pull,
Upright Row, Tricep Pushdown (Cable), Skull Crusher (EZ Bar),
Overhead Tricep Extension (Dumbbell), Tricep Kickback,
Dips (Triceps), Diamond Push-Up, Single-Arm Cable Pushdown

**PULL — vertical (back/biceps):**
Pull-Up, Chin-Up, Lat Pulldown (Wide), Lat Pulldown (Close),
Cable Row (Seated), Dumbbell Row, Barbell Row (Pendlay), T-Bar Row,
Single-Arm Dumbbell Row, Machine Row, Chest-Supported Row,
Straight-Arm Pulldown, Pullover (Dumbbell), Incline Dumbbell Row

**PULL — arms (biceps/forearms):**
Barbell Curl, Dumbbell Curl, Hammer Curl, Preacher Curl (EZ Bar),
Incline Dumbbell Curl, Cable Curl, Concentration Curl, Spider Curl,
Reverse Curl, Zottman Curl, Machine Curl, Rope Hammer Curl

**Core/Abs (use PUSH category, RECTUS_ABDOMINIS primary):**
Plank (timed), Ab Wheel Rollout, Cable Crunch, Hanging Leg Raise,
Captain's Chair Leg Raise, Decline Sit-Up, Russian Twist,
Decline Crunch, Dragon Flag, Side Plank (timed), Pallof Press,
Landmine Twist, Oblique Crunch

---

## Exercise picker improvements

File: `presentation/screens/workout/ExercisePickerSheet.kt`

**Add:**
1. A `TextField` search bar at the top of the `ModalBottomSheet` — filters by exercise name (case-insensitive substring match)
2. Category chip row below search: ALL / SQUAT / HINGE / PUSH / PULL — tapping a chip filters by `ExerciseCategory`
3. Both filters compose: if a chip is selected AND text is typed, show only exercises matching both
4. The list remains a `LazyColumn` — no grouping headers needed (search + chips replace them)

**ViewModel change:** `ActiveWorkoutViewModel` already exposes `availableExercises`. Filter on the picker side with local `remember` state — no ViewModel change needed.

---

## Boundaries for this spec

| Rule | Detail |
|------|--------|
| **Skip** | SVG / Lottie / anatomy overlay — leave as-is |
| **Skip** | UI visual redesign of any screen |
| **Never** | Add new Room entities, new columns, or schema migrations |
| **Never** | Change `WeightFormatter`, `MuscleGroup`, or `SaiyanStage` enum values |
| **Seeder safety** | All new exercises use `OnConflictStrategy.IGNORE` — safe to re-run |
| **Metric only** | No "lb", no pounds, no imperial anywhere |
| **Test** | After changes, `./gradlew assembleDebug` must pass clean |

---

## Acceptance criteria

- [ ] Log 3 sets on Deadlift → history shows correct total volume in kg
- [ ] Power Level on Home screen increases after completing a session
- [ ] 1RM estimate on Session Complete screen matches Epley formula manually
- [ ] Rest timer counts down to 0 and clears automatically; SKIP clears it immediately
- [ ] Exercise picker shows search bar + category chips; typing "row" shows only row exercises
- [ ] All 150 exercises appear in the picker with correct category and muscle group data
- [ ] `grep -r " lb" app/src` returns zero results
