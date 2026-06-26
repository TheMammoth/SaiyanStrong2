# SaiyanStrong — Sprint 11 Spec

## Status: IN PROGRESS

---

## Sprint 11 scope (3 reference screenshots)

### 1. Exercise equipment variants
Reference pic 1 shows exercises named as "Bench Press (Barbell)", "Bench Press (Dumbbell)",
"Overhead Press (Barbell)", "Overhead Press (Dumbbell)" as separate entries.

**Changes:**
- Rename key exercises in ExerciseSeeder to follow `"Name (Equipment)"` convention
- Bump DB version 3 → 4 with migration that clears exercises table (re-seeded on next launch)
- Equipment suffixes: (Barbell), (Dumbbell), (Cable), (Machine), (Smith Machine), (Kettlebell)
- Bodyweight exercises keep no suffix: "Pull-Up", "Push-Up", "Dips"

Key renames:
| ID | Before | After |
|----|--------|-------|
| 1 | Barbell Squat | Squat (Barbell) |
| 2 | Deadlift | Deadlift (Barbell) |
| 3 | Bench Press | Bench Press (Barbell) |
| 4 | Overhead Press | Overhead Press (Barbell) |
| 44 | Dumbbell Bench Press | Bench Press (Dumbbell) |
| 45 | Incline Dumbbell Press | Incline Bench Press (Dumbbell) |
| 42 | Incline Bench Press | Incline Bench Press (Barbell) |
| 56 | Dumbbell Shoulder Press | Overhead Press (Dumbbell) |
| 77 | Dumbbell Row | Bent Over Row (Dumbbell) |
| 78 | Pendlay Row | Pendlay Row (Barbell) |
| 80 | Single-Arm Dumbbell Row | One Arm Row (Dumbbell) |
| 86 | Barbell Curl | Curl (Barbell) |
| 87 | Dumbbell Curl | Curl (Dumbbell) |
| 59 | Lateral Raise | Lateral Raise (Dumbbell) |
| 60 | Front Raise | Front Raise (Dumbbell) |
| 25 | Romanian Deadlift | Romanian Deadlift (Barbell) |
| 65 | Skull Crusher | Skull Crusher (Barbell) |

---

### 2. Bottom 5-tab navigation
Reference shows persistent bottom bar: Profile | History | Workout | Exercises | Measure

**Our tabs:** Home | History | Workout | Exercises | Settings

- `NavigationBar` in `NavGraph` top-level `Scaffold.bottomBar`
- Tabs hidden on `ActiveWorkout` and `SessionComplete` routes
- Workout tab → navigate to `Screen.ActiveWorkout`
- Exercises tab → `ExerciseBrowserScreen` (new screen)
- Settings tab → `Screen.Settings` (already exists)
- History tab → `Screen.History`
- Home tab → `Screen.Home`

---

### 3. ExerciseBrowserScreen (Exercises tab)
Standalone screen version of the exercise picker:
- Same search + filter chips as ExercisePickerSheet
- No dialog / overlay — it's a proper nav destination
- Tap on exercise = no-op for now (detail screen is future sprint)
- `ExerciseBrowserViewModel` injects `ExerciseRepository`

---

### 4. ActiveWorkoutScreen: pre-added pending sets
Reference pic 3 shows sets 4, 5, F all pre-visible with input boxes simultaneously.

**Model change:**
- Remove `expandedExerciseId: Int?` from UiState
- Add `pendingSetCounts: Map<Int, Int>` (exerciseId → number of pending rows visible)
- Exercise selected → starts with 1 pending row (`pendingSetCounts[id] = 1`)
- ADD SET → increments count
- Log pending set ✓ → decrements count (and calls `onLogSet`)
- All pending rows shown simultaneously AFTER the rest timer bar (if active)

---

### 5. Completed set ✓ button fix
- ✓ is a **visual indicator only** on completed rows — NOT a delete button
- Row is **tappable** → enters inline edit mode (steppers for KG and REPS appear)
- In edit mode: confirm ✓ saves via `onEditSet`, cancel X exits
- Long-press → delete (via `Modifier.combinedClickable`)
- Add `onEditSet(exerciseId, setIndex, weightKg, reps, isFailure)` to ViewModel

---

### 6. Rest timer adjustment (verify + fix)
- The `-30s / +30s / SKIP` buttons in RestTimerBar call `onAdjustRest` correctly
- Ensure timer bar is visible even while pending set rows are shown
- The pending rows appear BELOW the timer bar (not above)

---

## Files changed / created

| File | Action |
|------|--------|
| `ExerciseSeeder.kt` | Rename exercises to (Equipment) convention |
| `AppDatabase.kt` | Version 3→4, MIGRATION_3_4 |
| `Screen.kt` | Add `Exercises` route |
| `NavGraph.kt` | Bottom nav bar, 5 tabs |
| `ExerciseBrowserViewModel.kt` | New |
| `ExerciseBrowserScreen.kt` | New |
| `ActiveWorkoutViewModel.kt` | pendingSetCounts, onEditSet |
| `ActiveWorkoutScreen.kt` | Multiple pending rows, edit mode, fix ✓ |
| `HomeScreen.kt` | Remove ⚙ header gear (Settings is a tab) |

---

## Sprint 12 backlog
- [ ] Exercise detail screen (tap exercise in browser → detail)
- [ ] Workout naming before session (or name on complete)
- [ ] Lottie flame animation
- [ ] Pre-planned pending sets: persist across rest timer
