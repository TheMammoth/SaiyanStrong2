# SaiyanStrong — Sprint 9 Spec: SessionCompleteScreen Full HUD Rebuild

## Reference
Target layout: 3-column neon green HUD with flame power bar (see reference image).

## Status: IN PROGRESS

---

## Layout (top-level)

```
Column {
    "SAIYAN STRONG"        ← amber, bold, large, centered
    "Session Complete!"    ← amber, medium, centered

    Row {
        HudPanel(weight=0.30) { LeftPanel }
        HudPanel(weight=0.40) { CenterPanel }
        HudPanel(weight=0.30) { RightPanel }
    }

    Image(ic_launcher_foreground, ~180dp, centered)  ← decorative barbell
}
```

---

## HudPanel composable

- Background: MatteBlack (#0D0D0D)
- Border: 1.5dp NeonGreen, RoundedCornerShape(8dp)
- Glow: drawBehind secondary rect at alpha 0.2f, spread 4dp
- Padding: 8dp inside

---

## Left Panel

### StrengthLineChart (Canvas, height ~140dp)

Data: last 8 weeks of volumeKg from weeklyBars.
- Y-axis: TelemetryGreen labels, 8sp
- X-axis: week date labels, 8sp
- Line: NeonGreen, 2dp stroke
- Dots: NeonGreen filled, 4dp radius
- Grid: NeonGreen alpha 0.12f

### ExerciseStatsTable

Headers: LIFT | EST. 1RM | REPS
Rows per exercise: name (≤12 chars) | estOneRmKg (WeightFormatter) | totalReps
Font: monospace 10sp; alternating row tint NeonGreen alpha 0.05f

---

## Center Panel

Inner Row split ~55/45:

### Left half — Volume + Stats + Mini Chart

Volume hero box (inner border NeonGreen alpha 0.4f):
- "TOTAL VOLUME:"   → TelemetryGreen monospace 11sp
- "[X,XXX kg]"      → NeonGreen FontWeight.Black 28sp (comma-separated)

Stats (white monospace 10sp):
- "Max [ExerciseName]: [weight]"
- "Total Reps: [n]"
- "Duration: [Xh Xm]"
- "Muscle Fatigue: [Low/Med/High]"  (Low <10 sets, Med <20, High ≥20)

Strength Progress mini chart (height ~56dp):
- Label: "Strength Progress: +X% Week/Week"  amber 9sp
- Canvas: same line as left panel but compact; NeonGreen fill gradient below line

### Right half — Power Section

(top→bottom, centered)
1. Flame icon: Icons.Filled.LocalFireDepartment, 48dp, #F5A623, pulsing alpha 0.5→1.0
2. PowerLevelBar (existing component), height=180dp
3. "Level [n] ([StageName])"  → NeonGreen 10sp
4. "POWER LEVEL"              → amber 9sp letter-spacing

---

## Right Panel

### Estimated 1RM Table

Header: "EST. 1RM" | "KG"  → NeonGreen 11sp bold
Rows: exerciseName | estOneRmKg (WeightFormatter.formatOneRm)
0.5dp divider lines NeonGreen alpha 0.25f

### Time Spent Table (below a Spacer(8dp))

Header: "EXERCISE" | "TIME"  → NeonGreen 11sp bold
Rows: exerciseName | "~[n]m" (totalSets * 3 min proxy)

---

## ViewModel additions (SessionCompleteViewModel)

New data class (in same file):
```kotlin
data class ExerciseRow(
    val name: String,
    val bestWeightKg: Double,
    val estOneRmKg: Double,
    val totalReps: Int,
    val totalSets: Int
)
```

New StateFlows injected from SessionRepository.getAllSessions():
- `weeklyBars: StateFlow<List<WeekBar>>`  (reuse HomeViewModel buildWeekBars logic)
- `strengthProgressPct: StateFlow<Float>` (thisWeek vol - prevWeek vol / prevWeek * 100)
- `exerciseRows: StateFlow<List<ExerciseRow>>`  (derived from session)

---

## Acceptance criteria

- [ ] 3-column HUD layout visible on 6" device (landscape natural fit; portrait scrollable)
- [ ] NeonGreen glowing borders on all 3 panels
- [ ] Line chart renders from real session history
- [ ] Volume and 1RM use kg (WeightFormatter), never lb
- [ ] PowerLevelBar + flame correct position in center-right
- [ ] Bottom barbell image renders
- [ ] assembleDebug SUCCESSFUL

---

## Files to change

1. `SessionCompleteViewModel.kt` — add weeklyBars, exerciseRows, strengthProgressPct
2. `SessionCompleteScreen.kt` — full rewrite to 3-column HUD

---

## Sprint 10 backlog

- [ ] Lottie flame animation (replace Icon placeholder)
- [ ] Exercise detail screen
- [ ] Rest timer push notification
- [ ] Portrait/landscape adaptive layout
