# SaiyanStrong — Sprint 10 Spec: ActiveWorkoutScreen layout rebuild

## Reference: provided screenshot

## Status: COMPLETE — v0.8.0

---

## Differences between reference and current UI

| Area | Reference | Current (before this sprint) |
|------|-----------|------------------------------|
| Top bar | ← icon · timer · FINISH right | No top bar |
| Header | Workout name bold + elapsed | "SAIYAN STRONG" static |
| Exercise name | Accent color, 16sp | Uppercase white/green, smaller |
| Columns | SET / PREVIOUS / KG / REPS / ✓ | SET / PREV / KG / REPS / × |
| Completed rows | **Solid green bg**, filled ✓ button | Faint tint, × delete |
| Rest label between sets | "4:00" teal text between each row | Nothing |
| Active timer | **Full-width solid colored bar** | Small amber bordered row with buttons |
| Pending set | Inline table row with input boxes | Collapsed panel, hidden |
| Add set button | `ADD SET (1:30)` text button | `+ ADD SET >>>` SaiyanButton |
| Add exercise | Bottom text button | Row button with LOG/FINISH |

---

## Layout spec

```
Column {
    // TOP BAR
    Row(SaiyanGray bg) {
        Text("←", white)
        Spacer(weight=1)
        Text(elapsed "M:SS", white)
        Spacer(weight=1)
        TextButton("FINISH", NeonGreen)
    }

    // WORKOUT TITLE
    Column(padding=16) {
        Text("TRAINING SESSION", white, bold, 20sp)
        Text(elapsed, white alpha 0.6, 13sp)
    }

    // EXERCISE CARDS
    LazyColumn(weight=1f) {
        items(exerciseLogs) { log ->
            ExerciseLogCard(log, ...)
        }
        item { TextButton("ADD EXERCISE", NeonGreen) }
    }

    // TELEMETRY
    Text("// SETS: N | VOL: X //", TelemetryGreen, black bg)
}
```

---

## ExerciseLogCard layout spec

```
Column(SaiyanGray bg, NeonGreen border) {
    Row {
        Text(exerciseName, NeonGreen, 16sp, bold)
        Spacer
    }

    // Headers
    Row { "SET" | "PREVIOUS" | "KG" | "REPS" | "✓" }  ← TelemetryGreen

    forEach(completedSets, index) {
        CompletedSetRow(set, previousSet)   ← NeonGreen bg alpha 0.18f
        if (not last OR rest timer running OR pending open) {
            RestLabel("1:30")  ← TelemetryGreen, 10sp, centered
        }
    }

    // Active rest timer (if running)
    if (restSecondsRemaining != null) {
        RestTimerBar(seconds)  ← full-width, PowerAmber solid bg
    }

    // Pending set row (if expanded)
    if (isExpanded) {
        PendingSetRow(...)
    }

    // ADD SET
    TextButton("ADD SET (1:30)", NeonGreen, centered)
}
```

---

## Component details

### CompletedSetRow
- Background: `NeonGreen.copy(alpha = 0.15f)`, RoundedCornerShape(3.dp)
- ✓ button: `Color(0xFF2E7D32)` green filled, white "✓" text → tapping = delete

### RestLabel
- Text `"1:30"`, color = `TelemetryGreen`, 10sp, centered, full width

### RestTimerBar
- Full-width `Row`, background = `PowerAmber`, padding vertical 10dp
- Centered large `Text(M:SS)`, white, 22sp, bold
- Small row below: `-30s` · `+30s` · `SKIP` TextButtons in white

### PendingSetRow
- Background: dark (`Color(0xFF111111)`)
- SET column: next set number
- PREVIOUS: previousSet?.let { "Xkg × N" } ?: "—"
- KG: `[-] [value] [+]` inline (weightStepKg steps)
- REPS: `[-] [value] [+]` inline
- ✓: NeonGreen filled square → logs the set

### AddExerciseButton
- Centered `TextButton`, text "ADD EXERCISE", NeonGreen, 15sp bold

---

## ViewModel addition
```kotlin
private val _elapsedSeconds = MutableStateFlow(0)
val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()
// ticks every second in init coroutine
```

---

## Files changed
1. `ActiveWorkoutViewModel.kt` — add elapsedSeconds StateFlow
2. `ActiveWorkoutScreen.kt` — full rewrite

---

## Sprint 11 backlog
- [ ] Lottie flame (SessionComplete)
- [ ] Exercise detail screen
- [ ] Rest timer notification
- [ ] Pre-planned pending sets (add multiple sets upfront)
