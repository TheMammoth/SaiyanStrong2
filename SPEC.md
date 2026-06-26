# SaiyanStrong — Sprint 5 Spec: UX Overhaul

## Decisions from user review

| Decision | Choice |
|----------|--------|
| Add Set entry point | Inside exercise card, Hevy-style `+ ADD SET` button |
| Visualizer | **Removed** from active workout screen (files stay, not rendered) |
| Extra fix | Quick-delete a logged set (× button on each set row) |

---

## 1. Objective

The active workout screen is hard to use because:
- After adding an exercise there is no visible "Add Set" action
- The `Begin Set` button in the Visualizer is not intuitive
- Users cannot delete a mistakenly-logged set mid-workout

Sprint 5 fixes all three without changing any DB schema, navigation, or Dragon Ball theme.

---

## 2. Active Workout Screen — new layout

```
┌─ SAIYAN STRONG ──────────────── AMBER HEADER ─┐
│  [BARBELL SQUAT — 2 SETS]                      │
└─────────────────────────────────────────────────┘

┌─ BARBELL SQUAT ─────────────────────────────────┐
│  SET   PREV           KG     REPS    ×           │
│   1    95 kg × 5     100      5      ×           │
│   2    100 kg × 5    100      5      ×           │
│                                                   │
│  ⏱ 62s  [-30s] [+30s] [SKIP]                    │
│                                                   │
│  [F]   [ + ADD SET >>>                        ]  │
└───────────────────────────────────────────────────┘

┌─ DEADLIFT ──────────────────────────────────────┐
│  NO SETS LOGGED                                  │
│  [F]   [ + ADD SET >>>                        ]  │
└───────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────┐
│ [+ EXERCISE]  [LOG]  [FINISH]                    │
│ // SETS: 2  |  VOL: 200 kg //                    │
└──────────────────────────────────────────────────┘
```

Key points:
- `+ ADD SET` is **always visible** at the bottom of every exercise card
- Tapping `+ ADD SET` expands an inline weight/reps input **within that card**
- Only one card's input is expanded at a time — tapping another collapses the first
- `[F]` failure toggle lives next to `+ ADD SET` on the same row
- Rest timer shows inside the card that just logged a set, above `+ ADD SET`
- Set rows show a small `×` icon; tapping it deletes the set in memory
- **Visualizer removed** from `ActiveWorkoutScreen`

---

## 3. Inline set input (expanded state)

When `+ ADD SET` is tapped the bottom of the card expands:

```
┌──────────────────────────────────────────────────┐
│  [-5]  [ 100 kg ]  [+5]  │  [-]  [5]  [+]       │
│  [F]   [ LOG SET >>>                           ]  │
└──────────────────────────────────────────────────┘
```

- Pre-filled with last logged weight/reps for that exercise (or 60.0 / 5 if none)
- `LOG SET >>>` calls `onLogSet(exerciseId, weightKg, reps, null, isFailure)`
- After logging, input collapses and new set row appears; rest timer starts

---

## 4. Files to change

| File | Change |
|------|--------|
| `ActiveWorkoutViewModel.kt` | `activeExerciseId` → `expandedExerciseId: Int?`; add `restTimerForExerciseId: Int?`; `onAddSetClicked(exerciseId)` expands that card; `onLogSet` gains `exerciseId: Int`; add `onDeleteSet(exerciseId: Int, setIndex: Int)` |
| `ActiveWorkoutScreen.kt` | Remove `VisualizerScreen`, `VisualizerViewModel`, all visualizer state; `ExerciseLogCard` gets inline expanded input + `+ ADD SET` + `×` on set rows |

**No DB, navigation, theme, or other-screen changes.**

---

## 5. ViewModel contract

```kotlin
data class ActiveWorkoutUiState(
    val exerciseLogs: List<ExerciseLog> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val exerciseUsageCounts: Map<Int, Int> = emptyMap(),
    val previousPerformance: Map<Int, List<SetLog>> = emptyMap(),
    val expandedExerciseId: Int? = null,
    val restTimerForExerciseId: Int? = null,
    val restTimerSecondsRemaining: Int? = null,
    val isExercisePickerVisible: Boolean = false,
    val completedSessionId: Long? = null
)

fun onAddSetClicked(exerciseId: Int)
fun onLogSet(exerciseId: Int, weightKg: Double, reps: Int, rpe: Float?, isFailure: Boolean)
fun onDeleteSet(exerciseId: Int, setIndex: Int)
```

---

## 6. Set deletion rules

- Delete from in-memory `exerciseLogs` only — no DB write (session not yet saved)
- Remaining sets renumbered: setNumber = index + 1
- Card stays even if all sets deleted (user can re-add)

---

## 7. Boundaries

**DO:** Keep all color tokens, WeightFormatter, rest timer coroutine, ExercisePickerSheet, Visualizer source files (just stop rendering them).

**DON'T:** Change DB schema, navigation, History/Home screens, or add dependencies.

---

## 8. Acceptance criteria

- [ ] Every exercise card shows `+ ADD SET` even with no sets logged
- [ ] Tapping `+ ADD SET` expands inline weight/reps input in that card only
- [ ] Only one card's input open at a time
- [ ] `LOG SET >>>` adds row and starts rest timer inside that card
- [ ] `×` on a set row removes it and renumbers
- [ ] Visualizer gone from workout screen, no blank space left
- [ ] Theme, fonts, colors unchanged
