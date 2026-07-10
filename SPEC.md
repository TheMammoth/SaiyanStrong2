# SaiyanStrong — RPE-Based Progression Hints Spec

## Status: BUILT (v0.21.0) — all 3 slices shipped + unit tests added, see CLAUDE.md "Sprint 24"

(Replaces the previous "Rest Timer Sounds + RPE Entry" spec in this file — that feature shipped
in full as v0.20.0; see `CLAUDE.md` progress log, "Sprint 23".)

---

## 0. Decisions locked in via clarifying questions

- **Placement**: active workout only, appended to the existing PREVIOUS column
  (`PendingSetRow`/`CompletedSetRow` in `ActiveWorkoutScreen.kt`) — exactly where a lifter looks
  right before logging the next set. Not added to the exercise detail RECORDS/CHARTS tabs.
- **Calculation basis**: a real %1RM-by-RPE lookup table (the widely published
  Tuchscherer/RTS-style RPE chart used across powerlifting autoregulation — public training
  methodology, not proprietary code or copyrighted text), not an ad-hoc threshold table. This
  estimates a "true" 1RM from *any* logged (reps, weight, RPE) triple, not just AMRAP sets like
  the existing Epley formula assumes.
- **Suggestion type**: weight **or** reps, whichever the RPE data implies is the gentler/safer
  next step (a real coach doesn't always say "add weight" — sometimes "one more rep" is the
  correct call).

---

## 1. Objective

Today, `PendingSetRow`/`CompletedSetRow` show a plain "PREVIOUS" readout (last time's weight ×
reps) with no interpretation. Since RPE entry now exists (v0.20.0) but nothing reads it back, this
closes that loop: use last session's logged RPE for the same exercise/set-slot to give a short,
concrete autoregulation hint — "you had room, push a bit more" vs "you were maxed out, hold or
ease off" — the way an experienced lifter or coach reads their own RPE log.

**Target users**: existing users who've started logging RPE (opt-in from v0.20.0) — no hint shows
for sets logged without an RPE, since there's no basis for one.

**Worked example (from your prompt)**: last week, set 1 of Bench Press was `100kg × 5 @ RPE 9`
(1 rep in reserve). This week, PREVIOUS for that same set slot should read something like:

```
PREVIOUS
100kg × 5 @9
→ try 6 reps
```

— because at RPE 9 for 5 reps, the chart says there's exactly one more rep available before
true failure; the smallest safe progression is "the same weight, one more rep," not necessarily
more weight.

---

## 2. Core features & acceptance criteria

### 2.1 RPE → %1RM chart (`domain/util/RpeChart.kt` or similar — pure, no dependencies)
- A 12×9 lookup table: reps 1–12 (rows) × RPE 10.0 down to 6.0 in 0.5 steps (9 columns) → % of
  1RM. Values match the standard published RTS/Tuchscherer chart (e.g. 5 reps @ RPE9 ≈ 83.7%,
  5 reps @ RPE10 ≈ 86.3%).
- `fun percentOf1Rm(reps: Int, rpe: Float): Double` — reps clamped to `1..12` (beyond 12 the
  chart's accuracy craters for a compound barbell lift; clamping to 12 is an explicit, documented
  approximation, not silently wrong), RPE clamped to `6f..10f` and snapped to the nearest 0.5
  (matches the RPE bottom sheet's own granularity exactly, so no interpolation is needed for
  values the picker can actually produce).
- `fun estimateTrue1Rm(weightKg: Double, reps: Int, rpe: Float): Double = weightKg /
  percentOf1Rm(reps, rpe)`.
- **Acceptance**: `estimateTrue1Rm(100.0, 5, 9f)` ≈ `119.5` (100 / 0.837); feeding that back —
  `100.0 * percentOf1Rm(5, 9f)` — round-trips to ≈100.0.

### 2.2 Suggestion rule (`domain/usecase/SuggestNextLoadUseCase.kt`)
Given the previous set's `(weightKg, reps, rpe)` for the same exercise/set-slot:

| Last RPE | Reps-in-reserve implied | Suggestion |
|---|---|---|
| ≤ 8.0 | ≥ 2 | **More weight, same reps.** Target a new weight so the *same reps* land at RPE 9 next time: `targetWeight = round(estimate1Rm × percentOf1Rm(reps, 9.0), toNearestStep)`. |
| 8.5 or 9.0 | 1–1.5 | **Depends on rep count.** If `reps ≤ 6`: **one more rep, same weight** (the gentler progression at lower rep counts). If `reps > 6`: **a small weight bump** toward RPE 9.5 at the same reps (adding a whole rep at already-high rep counts is the bigger relative jump, not the safer one). |
| 9.5 | 0.5 | **Hold.** Same weight, same reps — you were right at the edge, repeat it to confirm before pushing further. |
| 10.0 | 0 | **Ease off.** Suggest the same weight for one fewer rep, or note it plainly as a max-effort set — no "push more" hint at true failure. |

- No previous RPE recorded for that set slot (pre-v0.20.0 history, or user skipped RPE) →
  no hint, falls back to today's plain PREVIOUS text exactly as it is now.
- Weight is rounded to the exercise's existing step convention (`2.0kg` for
  Dumbbell/Kettlebell exercises, `2.5kg` otherwise — the same `stepKg` logic
  `ExerciseLogCard`/`ActiveWorkoutScreen` already compute from the exercise name).
- **Acceptance**: the worked example in §1 (`100kg×5 @9`) produces "try 6 reps," not a weight
  change, since reps(5) ≤ 6. A set logged `100kg×8 @9` produces a small weight-bump suggestion
  instead, since reps(8) > 6.

### 2.3 UI — PREVIOUS column gets a second line
- `ActiveWorkoutScreen.kt`: both `PendingSetRow` and `CompletedSetRow` already receive
  `previousSet: SetLog?` for their slot. Compute the suggestion (pure function call, no new
  ViewModel state needed — it's a deterministic function of `previousSet` alone) and, when present,
  render it as a small second line under the existing "100kg × 5 @9" text — `NeonGreen`,
  `→ try 6 reps` / `→ try 102.5kg` / `→ hold` / `→ ease off` — reusing the exact visual weight
  the app already gives to "this is actionable" text (matches how `RpeChip`'s set state reads).
- No change to `ExerciseDetailScreen`, `HistoryScreen`, `SessionCompleteScreen`, or any existing
  e1RM/DOTS calculation — those keep using the existing Epley-based `EstimateOneRepMaxUseCase`
  untouched; this is a new, additive, active-workout-only signal.
- **Acceptance**: an exercise with no logged history (first time ever) shows the existing "—"
  PREVIOUS text, unchanged. An exercise with history but no RPE on the relevant set shows the
  existing plain weight×reps text, unchanged. Only a set with a previous RPE gains the second line.

---

## 3. Tech stack additions

None — pure Kotlin math + existing Compose UI. No new dependencies, no schema changes (this
reads `SetLog.rpe`, which already exists and is already populated since v0.20.0).

---

## 4. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── domain/
│   ├── util/
│   │   └── RpeChart.kt                 ← new — pure lookup table + percentOf1Rm()/estimateTrue1Rm()
│   ├── model/
│   │   └── LoadSuggestion.kt           ← new — sealed class: MoreWeight(kg), MoreReps(reps), Hold, EaseOff
│   └── usecase/
│       └── SuggestNextLoadUseCase.kt   ← new — pure function: (previousSet: SetLog, stepKg: Double)
│                                          -> LoadSuggestion?
│
└── presentation/screens/workout/
    └── ActiveWorkoutScreen.kt          ← PendingSetRow/CompletedSetRow call SuggestNextLoadUseCase.
                                          (or a small local composable helper, since it's a pure
                                          function with no DI needs) and render the second line
```

`SuggestNextLoadUseCase` deliberately takes no repository/DI dependencies (pure function of its
arguments) — Hilt-injectable per convention, but trivially unit-testable without a container if a
test source set is ever added.

---

## 5. Code style (extends existing CLAUDE.md rules)

- `RpeChart`/`SuggestNextLoadUseCase` are pure functions — no side effects, no `Flow`, no
  suspend — consistent with how `CalculatePowerLevelUseCase`/`EstimateOneRepMaxUseCase` are
  already written.
- `LoadSuggestion` as a `sealed class` (`data class`/`data object` variants), matching the
  project's stated style preference for `when` over `if/else` chains when the UI renders it.
- No hardcoded colors — the suggestion line reuses `NeonGreen`/`Color.White.copy(alpha=...)`.
- Reuses the exact existing `stepKg` derivation (`Dumbbell`/`Kettlebell` → 2.0, else 2.5) already
  duplicated in `ExerciseLogCard` — no new constant introduced.

---

## 6. Testing strategy

No `app/src/test` source set exists in this project (same reality as every prior sprint) — this
would be an unusually good candidate for one, since `RpeChart`/`SuggestNextLoadUseCase` are pure
functions with no Android dependencies, but adding a test source set is out of scope unless you
want it added as part of this slice. Flagging as a question below rather than assuming either way.

Manual verification (build + by-hand table checks, no device needed for the math itself):
1. Compute `estimateTrue1Rm`/`percentOf1Rm` for a handful of known chart values by hand, confirm
   they match §2.1.
2. Walk through §2's rule table for each RPE band (≤8, 8.5–9 at low/high reps, 9.5, 10) and
   confirm the exact hint text matches the table.
3. `assembleGithubDebug` build check (same as every prior sprint this session).

Device-needed manual QA (flagged, not assumed done): log a set with RPE, finish the workout,
start a new workout with the same exercise, confirm the second PREVIOUS line appears with the
expected suggestion.

---

## 7. Boundaries

**Always do:**
- Only ever suggest based on an actual logged RPE — never fabricate or assume one.
- Keep the existing plain PREVIOUS text as a fallback in every case where no suggestion applies —
  never regress the current behavior for RPE-less history.
- Round suggested weights to the exercise's existing step convention, never a raw unrounded float.

**Ask first about:**
- Whether to add an `app/src/test` source set for this (see §6) — it's the first genuinely
  test-friendly pure-function code in the project, but introducing a test source set is a small
  standing infrastructure decision beyond this one feature.
- Exact wording/tone for the four hint variants (`try 102.5kg`, `try 6 reps`, `hold`, `ease off`)
  — happy to adjust once you see them rendered.

**Never do:**
- Never suggest a weight/rep jump that ignores the exercise's rounding step.
- Never touch the existing Epley-based e1RM calculations used elsewhere (History, Exercise
  Records, Home DOTS/big-three) — this is additive, not a replacement.

---

## 8. Suggested incremental slices

1. `RpeChart.kt` (table + lookup functions) — verifiable by hand against known values, no UI yet.
2. `LoadSuggestion.kt` + `SuggestNextLoadUseCase.kt` — the rule table from §2.2, unit-testable in
   isolation if a test source set is added (see Boundaries).
3. `ActiveWorkoutScreen.kt` wiring — second PREVIOUS line in `PendingSetRow`/`CompletedSetRow`.

Each independently buildable/verifiable, own commit, per your established convention.
