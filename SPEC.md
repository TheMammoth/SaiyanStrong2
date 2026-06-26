# SaiyanStrong — Sprint 6 Spec: Canvas PowerLevelBar + SessionComplete HUD

## Status: COMPLETE ✓

---

## 1. Objective

Two visual upgrades shipped in Sprint 6:

1. **PowerLevelBar** — replace the `LinearProgressIndicator` with a native Canvas
   segmented power meter (Dragon Ball scouter aesthetic) and a pulsing flame icon
   placeholder (to be swapped for Lottie when the JSON asset lands).

2. **SessionCompleteScreen** — rebuild the post-workout screen as a full HUD layout
   matching the Dragon Ball session-complete aesthetic, with prominent volume display,
   per-exercise stats, estimated 1RM table, and the new PowerLevelBar.

---

## 2. PowerLevelBar — Canvas Segmented Bar

### Layout

```
        [🔥 flame icon — pulsing amber]
┌──────────────────┐  ← top (narrowest, 65% width)
│░░░░░░░░░░░░░░░░░░│
│░░░░░░░░░░░░░░░░░░│
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│  ← active (DangerRed→PowerAmber gradient)
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│
└──────────────────┘  ← bottom (widest, 100% width)
      SUPER SAIYAN
      POWER: 14500
```

### Composable contract

```kotlin
@Composable
fun SegmentedBar(progress: Float, modifier: Modifier = Modifier)

@Composable
fun PowerLevelBar(powerLevel: PowerLevel, modifier: Modifier = Modifier)
```

- `progress`: 0f..1f; drives `litSegments = (progress × 10).roundToInt()`
- `SEGMENT_COUNT = 10`, `GAP_DP = 2.dp`, canvas `60.dp × 200.dp`
- Active brush: `Brush.verticalGradient(DangerRed → PowerAmber)`, key = density
- Inactive brush: `SolidColor(Color(0xFF1A1A1A))`, remembered once
- Width taper: `widthFraction = 1f − 0.35f × i / 9f` (i=0 bottom, i=9 top)
- Flame: `Icons.Filled.LocalFireDepartment`, tint `#F5A623`, alpha 0.5f→1.0f
  via `rememberInfiniteTransition` (800ms, FastOutSlowInEasing, Reverse)

### Dependency added

```toml
compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
```

```kotlin
implementation(libs.compose.material.icons.extended)
```

---

## 3. SessionCompleteScreen — HUD Layout

### Layout

```
┌─────────────────────────────────────────────────┐
│  SAIYAN STRONG                    (NeonGreen)   │
│  SESSION COMPLETE!                               │
└─────────────────────────────────────────────────┘

┌─────────────────────┐  ┌──────────┐
│ TOTAL VOLUME        │  │  🔥      │
│  4.25 t             │  │  ████    │
│                     │  │  ████    │
│ BARBELL SQUAT       │  │  ████    │
│   110 kg ×3         │  │  ░░░░    │
│ DEADLIFT            │  │  ░░░░    │
│   200 kg ×1         │  │ SSJ1     │
│                     │  │ 14500    │
│ SETS      8         │  └──────────┘
│ DURATION  01:02:00  │
└─────────────────────┘

[ POWER: +612 ] [ TIME: 01:02:00 ] [ EXERCISES: 2 ]

┌─────────────────┐  ┌─────────────────┐
│ EST. 1RM        │  │ SETS LOG        │
│ SQUAT  142.8 kg │  │ SQUAT   3 sets  │
│ DEADL  200.0 kg │  │ DEADL   5 sets  │
│                 │  │ VOLUME  4.25 t  │
└─────────────────┘  └─────────────────┘

[ Session title (optional) ]

[ DONE >>> ]
[ DELETE SESSION ]
// EXERCISES: 2  |  SETS: 8  |  +612 POWER //
```

### Card styling

- `OutlinedCard` with `CardDefaults.outlinedCardColors(containerColor = SaiyanGray)`
- Border: `BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))` on hero, `0.3f` on data cards
- All numbers in `FontFamily.Monospace`
- Labels in `TelemetryGreen`, `labelSmall`, `letterSpacing = 2.sp`
- Values in `Color.White`, `FontWeight.Bold`

---

## 4. Files changed

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Added `compose-material-icons-extended` library |
| `app/build.gradle.kts` | Added `implementation(libs.compose.material.icons.extended)` |
| `components/PowerLevelBar.kt` | Full Canvas rewrite of `SegmentedBar`; flame icon placeholder |
| `screens/session_complete/SessionCompleteScreen.kt` | HUD layout with OutlinedCards |

---

## 5. Future work (Sprint 7)

- [ ] Swap flame `Icon` for real Lottie animation when `flame_loop.json` is ready
- [ ] `VisualizerScreen` re-integration or permanent removal decision
- [ ] Strength progress chart (line chart via Canvas on SessionCompleteScreen)
- [ ] HomeScreen quick-stats cards (best lifts this week)
