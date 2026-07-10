# SaiyanStrong — VBT Results UI Spec

## Status: BUILT (v0.24.0) — see CLAUDE.md "Sprint 27". Tracking pipeline itself still unverified
against real footage (Sprint 26's known gap is unchanged by this sprint).

(Replaces the previous "Bar Path Camera Capture" spec in this file — that shipped as v0.23.0; see
CLAUDE.md "Sprint 26". This spec is the missing display half: `BarPathAnalysis` data has been
saveable since then, but nothing shows it anywhere.)

---

## 0. Decisions locked in via clarifying questions

- **Both screens get it**: `SessionCompleteScreen` shows the immediate result on the set just
  recorded; `ExerciseDetailScreen` gets a velocity-over-time chart, matching how e1RM/weight/
  volume are already tracked historically — that's the actual point of recording this at all.
- **Inline UI**: a small badge on a tracked set, tap to expand full metrics — matching the
  existing RPE-chip "tap to reveal" interaction language already in `SessionCompleteScreen`'s set
  rows, not a permanently-expanded second line on every row.

---

## 1. Objective

Close the loop opened by the last two sprints: `BarPathAnalysis` (velocity, power, ROM, bar path
deviation, training zone) has been computed and persisted per set since v0.22.0/v0.23.0, but no
screen reads it back. This surfaces it in the two places that already show everything else about
a lift — right after logging it, and as a trend over time.

---

## 2. Core features & acceptance criteria

### 2.1 Real plumbing gap this needs first: `ExerciseSetHistory` has no `SetLog` id
`SessionRepository.getExerciseHistory(exerciseId)` — the source of every chart on
`ExerciseDetailScreen` — is built from `SetLogDao.getHistoryForExercise`'s `SetWithDate`
projection, which never selected the set's own `id`. Without it there's no way to join a history
row against `bar_path_metrics` (keyed by `set_log_id`). Fix, additive only:
- `SetWithDate` gains `val id: Long` (from `sl.id` in the existing join query).
- `ExerciseSetHistory` domain model gains `val setLogId: Long`.
- `SessionRepositoryImpl.getExerciseHistory` threads it through.
No other caller of `ExerciseSetHistory` breaks — it's an added field, not a changed one.

### 2.2 Batch lookup, not N individual subscriptions
Both screens need "bar path data for several sets at once," not one set at a time. Add to
`BarPathRepository`:
```kotlin
fun getBarPathMetricsForSets(setLogIds: List<Long>): Flow<Map<Long, BarPathAnalysis>>
```
backed by a new `BarPathMetricsDao.getForSetLogIds(setLogIds: List<Long>): Flow<List<BarPathMetricsEntity>>`
(`WHERE set_log_id IN (:setLogIds)`). Avoids each set row independently subscribing to its own
Flow, and avoids re-querying per row on every recomposition.

### 2.3 SessionCompleteScreen — inline badge + expandable detail
- `SessionCompleteViewModel`: combine the existing session flow with
  `barPathRepository.getBarPathMetricsForSets(allSetIdsInSession)`, expose
  `barPathBySetId: Map<Long, BarPathAnalysis>` in `SessionCompleteUiState`.
- `ExerciseResultCard`/`EditableResultSetRow` (`SessionResultsSection.kt`): a set row whose
  `set.id` has an entry in the map gets a small velocity-zone badge (e.g. "⚡ Speed-Strength")
  next to its SET number. Tapping it expands an inline detail block below that row — peak/mean
  velocity, peak/mean power, ROM, bar path deviation — collapses again on a second tap. No new
  bottom sheet; reuses the same "expand below the row" pattern the KG/REPS steppers already use.
- **Acceptance**: a set with no recorded bar-path data renders exactly as it does today, zero
  visual change. A tracked set shows the badge; tapping expands/collapses the detail block.

### 2.4 ExerciseDetailScreen — velocity trend chart
- `ExerciseDetailViewModel`: combine `sessionRepository.getExerciseHistory(exerciseId)` (now
  carrying `setLogId`) with `barPathRepository.getBarPathMetricsForSets(allSetLogIdsInHistory)`.
  For each session that has at least one tracked set, take the tracked set with the highest
  weight that session (same "best of session" convention `weightChart`/`e1RmChart` already use)
  and plot its `meanConcentricVelocityMs` → new `velocityChart: List<ChartPoint>` in
  `ExerciseDetailUiState`, reusing the existing `ChartPoint` type — no new chart data shape needed.
- `ChartsTab`: reuses the existing generic `ChartCard`/`DetailLineChart` composables verbatim —
  add `item { ChartCard("BAR SPEED (MEAN VELOCITY, M/S)", uiState.velocityChart) }`, shown only
  when `velocityChart.size >= 2` (most exercises won't have tracked sets at all yet, and the
  existing empty-state message is written for the *whole* charts tab, not per-card — this one
  quietly omits itself rather than showing a confusing "not enough data" card for a feature most
  lifts haven't used).
- **Acceptance**: an exercise with zero or one tracked sessions shows the existing three charts
  unchanged, no velocity card. An exercise with ≥2 tracked sessions shows a fourth chart card
  identical in style to the other three.

---

## 3. Tech stack additions

None — reuses existing Room/Flow/Compose patterns, the existing `ChartPoint`/`ChartCard`/
`DetailLineChart` components, and the existing `BarPathRepository`/`BarPathAnalysis` from the
prior two sprints.

---

## 4. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── data/local/dao/
│   ├── SetLogDao.kt                    ← getHistoryForExercise query adds sl.id; SetWithDate.id
│   └── BarPathMetricsDao.kt            ← + getForSetLogIds(List<Long>)
├── domain/
│   ├── model/ExerciseSetHistory.kt     ← + setLogId: Long
│   └── repository/BarPathRepository.kt ← + getBarPathMetricsForSets(List<Long>)
├── data/repository/
│   ├── SessionRepositoryImpl.kt        ← getExerciseHistory threads setLogId through
│   └── BarPathRepositoryImpl.kt        ← + getBarPathMetricsForSets impl
│
└── presentation/screens/
    ├── session_complete/
    │   ├── SessionCompleteViewModel.kt      ← + barPathBySetId in UiState
    │   └── SessionResultsSection.kt         ← badge + expandable detail per tracked set
    └── exercises/
        ├── ExerciseDetailViewModel.kt       ← + velocityChart in UiState
        └── ExerciseDetailScreen.kt          ← ChartsTab gains the 4th ChartCard, conditionally
```

No Room schema change (this is all read-side), no new screens, no navigation changes.

---

## 5. Code style (extends existing CLAUDE.md rules)

- Reuses `ChartPoint`/`ChartCard`/`DetailLineChart` as-is — no parallel chart type introduced for
  velocity data.
- Batch-fetch pattern (`Map<Long, BarPathAnalysis>` keyed by `setLogId`) over per-row Flow
  subscriptions, consistent with how `previousPerformance: Map<Int, List<SetLog>>` already works
  in `ActiveWorkoutUiState`.
- "Best of session" selection (highest-weight tracked set) matches the exact convention
  `weightChart`/`e1RmChart` already use in `ExerciseDetailViewModel` — no new aggregation rule
  invented.

---

## 6. Testing strategy

No device/emulator this session, same as every UI-only sprint so far — verified via
`assembleGithubDebug` compiling clean and a manual reasoning pass over the acceptance criteria
above. The one genuinely pure-logic piece — "pick the tracked set with the highest weight per
session" — is a good, cheap candidate for a unit test if a data-mapping function is extracted
rather than inlined into the `combine` block; will extract it as a small private function and add
1–2 unit tests for it, continuing this project's pattern of testing pure logic where it's cheap
to (RPE chart, bar path physics).

---

## 7. Boundaries

**Always do:**
- Zero visual change for sets/exercises with no recorded bar-path data — this is purely additive.
- Reuse existing chart/badge visual language — no new color tokens, no new chart component.

**Ask first about:**
- Nothing anticipated — this is UI wiring over already-built, already-tested data (RPE chart
  precedent, bar path physics precedent), not new algorithmic risk like the last two sprints.

**Never do:**
- Never fabricate or interpolate a velocity chart point for a session that has no tracked set —
  sessions without recorded data simply don't contribute a point, same as how `weightChart` only
  has a point per session that actually has sets.

---

## 8. Notes

This is UI-only wiring over already-verified logic (the RPE-chart/bar-path-physics unit tests
from the last two sprints don't change), so it carries much less risk than the camera/tracking
sprint. The one still-true caveat from before: whatever numbers this displays are only as
trustworthy as the marker-tracking pipeline that produced them, which remains unverified against
real footage.
