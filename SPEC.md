# SaiyanStrong — Standalone Bar Path Analysis (Free-Tier Gated)

## Status: SPEC — not yet built.

(Replaces the previous "VBT Results UI" spec in this file — that shipped as v0.24.0; see
CLAUDE.md "Sprint 27". This spec covers the actual production-grade release of VBT: a
first-class entry point instead of a buried ⋮ menu item, gallery-video import, and the
free/Coach monetization gate.)

---

## 0. Decisions locked in via clarifying questions

- **Entry point**: a card on `HomeScreen` (below `BodyWeightCard`, above the pinned CTA),
  not a new bottom-nav tab. Directly fixes yesterday's real user confusion (couldn't find
  the recording entry point).
- **Freestanding, not set-linked**: user picks *which exercise* the video is for, not a
  specific logged set. No workout session required. `bar_path_metrics` becomes exercise-
  scoped with an optional set link, not exclusively set-scoped.
- **Free limit**: 5 analyses per **calendar month** for non-Coach users, derived live from
  data — not a separately maintained counter (same fix pattern as the Power Level bug,
  CLAUDE.md v0.18.1: counters drift, live queries don't).
- **Unlocked by**: existing Coach entitlement (`IsCoachUseCase` / `is_coach()`), reused
  as-is. No new Paddle product, no new Supabase column. Coach effectively becomes a
  general "Pro" tier for this feature — that's an intentional repositioning, not scope
  creep, since it's the same billing surface already built and live.
- **Video source**: both in-app record (reuse existing `BarPathVideoRecorder`) and gallery
  import (Android Photo Picker, no storage permission) — scoped to *this* standalone flow
  only. The existing set-linked capture flow from the ⋮ menu (Sprint 26, still unverified
  on real footage) is left exactly as-is; not touched by this sprint beyond the shared
  calibration/tracking/results steps it already uses.
- **Weight input**: standalone flow has no set to pull `weightKg` from, so the calibration
  screen gains a plain "weight lifted (kg)" field, same validation pattern as the existing
  reference-length field.
- **Schema**: `bar_path_metrics.set_log_id` becomes nullable (same table, not a parallel
  one) so the existing per-exercise velocity chart in `ExerciseDetailScreen` picks up
  freestanding rows for free, no second query path needed anywhere data is displayed.

---

## 1. Objective

Two real problems from yesterday's device test, fixed together because they're the same
underlying gap:
1. **Discoverability** — bar path capture was 3+ steps deep in an active workout, hidden
   in a ⋮ menu, with zero entry point on Home/Settings. A user actively trying to test it
   couldn't find it.
2. **No way to analyze a video not tied to a live logged set** — e.g. a lift recorded
   earlier, or recorded with the phone's native camera app rather than in-app.

This also introduces the feature's monetization gate: unlimited use requires the existing
Coach entitlement; free users get 5 standalone analyses/month.

---

## 2. Core features & acceptance criteria

### 2.1 Real plumbing change this needs first: `bar_path_metrics` is no longer 1:1 with a set
Today: `set_log_id` is `NOT NULL` + unique-indexed, because only the workout ⋮-menu flow
ever wrote to this table. A freestanding analysis has no set. Schema change (Room v8→9,
**table-recreate migration** — SQLite can't alter a column's `NOT NULL`/index in place):

```
CREATE TABLE bar_path_metrics_new (
  id                          INTEGER PRIMARY KEY AUTOINCREMENT,
  set_log_id                  INTEGER,              -- now nullable
  exercise_id                 INTEGER NOT NULL,      -- new
  created_at_ms                INTEGER NOT NULL,      -- new (was implicit via the set's timestamp)
  peak_velocity_ms             REAL NOT NULL,
  mean_concentric_velocity_ms  REAL NOT NULL,
  peak_power_watts             REAL NOT NULL,
  mean_power_watts             REAL NOT NULL,
  range_of_motion_cm           REAL NOT NULL,
  bar_path_deviation_cm        REAL NOT NULL,
  velocity_zone                TEXT NOT NULL,
  FOREIGN KEY(set_log_id) REFERENCES set_logs(id) ON DELETE CASCADE,
  FOREIGN KEY(exercise_id) REFERENCES exercises(id)
)
```
Backfill for existing rows: `exercise_id` via `set_logs → exercise_logs → exercise_id`
join; `created_at_ms` via the set's `timestamp_ms` (best-effort — it's the moment the set
was logged, close enough for existing rows). Then copy, drop old table, rename, recreate
a **non-unique** index on `set_log_id` and an index on `exercise_id` and `created_at_ms`
(the last one for the monthly-quota range scan).

The old unique index on `set_log_id` is dropped on purpose: "one bar-path row per set" is
now an **app-layer invariant**, not a DB one — `BarPathRepositoryImpl.saveBarPathMetrics`
(the existing set-linked path) does a delete-then-insert inside one call instead of
relying on `OnConflictStrategy.REPLACE` + a unique index. This is a real, deliberate
simplification, not an oversight — SQLite partial unique indexes aren't expressible via
Room's `@Index` annotation, and inventing a workaround there is worse than just enforcing
it in the one place that writes set-linked rows.

`BarPathMetricsEntity` gains `exerciseId: Long` (was implicit) and `createdAtMs: Long`;
`setLogId` becomes `Long?`.

### 2.2 Repository/DAO additions
```kotlin
// BarPathMetricsDao
@Query("SELECT * FROM bar_path_metrics WHERE exercise_id = :exerciseId AND set_log_id IS NULL ORDER BY created_at_ms DESC")
fun getFreestandingForExercise(exerciseId: Int): Flow<List<BarPathMetricsEntity>>

@Query("SELECT COUNT(*) FROM bar_path_metrics WHERE set_log_id IS NULL AND created_at_ms >= :monthStartMs")
fun countFreestandingSince(monthStartMs: Long): Flow<Int>
```
```kotlin
// BarPathRepository — additive, existing set-linked methods unchanged
suspend fun saveFreestandingBarPathMetrics(exerciseId: Int, weightKg: Double, analysis: BarPathAnalysis)
fun getFreestandingAnalysesForExercise(exerciseId: Int): Flow<List<BarPathAnalysis>>
fun getFreestandingCountThisMonth(): Flow<Int>
```
New `domain/usecase/GetBarPathQuotaUseCase.kt` (mirrors `IsCoachUseCase`'s "one shared
check" precedent):
```kotlin
data class BarPathQuota(val usedThisMonth: Int, val limit: Int = 5, val isUnlimited: Boolean)
class GetBarPathQuotaUseCase @Inject constructor(
    private val barPathRepository: BarPathRepository,
    private val isCoachUseCase: IsCoachUseCase
) {
    fun execute(): Flow<BarPathQuota> = combine(
        barPathRepository.getFreestandingCountThisMonth(),
        flow { emit(isCoachUseCase.execute()) }
    ) { count, isCoach -> BarPathQuota(count, isUnlimited = isCoach) }
}
```

### 2.3 Home card
New `HomeScreen` composable `BarPathCard`, placed after `BodyWeightCard`:
- Title "BAR PATH ANALYSIS", subtitle either "X/5 analyses this month" (free) or
  "UNLIMITED · COACH" (entitled) — amber for the free count when `used >= limit`, neon
  green otherwise, matching existing token usage elsewhere on this screen.
- "NEW ANALYSIS" button:
  - Quota available → opens the existing `ExercisePickerSheet` (already used by
    `ActiveWorkoutScreen`/`ExercisePickerSheet.kt`, reused verbatim) to pick which
    exercise the video is for, then navigates to `BarPathCapture` in standalone mode.
  - Quota exhausted, not Coach → `ConfirmDialog`-style upsell ("Free limit reached — 5/5
    this month. Coach unlocks unlimited analyses.") with an UPGRADE button that opens the
    same Paddle web-checkout URL `CoachSettingsScreen` already opens (reuse, no new
    checkout surface).
- `HomeViewModel` gains `barPathQuota: StateFlow<BarPathQuota>` from
  `GetBarPathQuotaUseCase` — same pattern as the existing `dotsScore`/`bodyWeightLogs`
  StateFlows already on this ViewModel.

### 2.4 Capture flow — standalone mode
`Screen.BarPathCapture` route changes from `bar_path_capture/{setLogId}/{weightKg}` to
`bar_path_capture?exerciseId={exerciseId}&setLogId={setLogId}&weightKg={weightKg}`:
- From the workout ⋮ menu (existing, unchanged behavior): `exerciseId` + real `setLogId`
  + real `weightKg` — calibration screen behaves exactly as it does today, no weight
  field shown.
- From the Home card (new): `exerciseId` + `setLogId=-1` (sentinel, matches the existing
  `templateId=-1`/`repeatLast` convention already used by `ActiveWorkout`'s route) +
  `weightKg=-1`. `BarPathCaptureViewModel` treats `setLogId <= 0` as "standalone."

`RecordingStep` gains a source choice **only when standalone** (`isStandalone` from the
ViewModel): a small row above the existing RECORD button — "RECORD" (unchanged, existing
`BarPathVideoRecorder`) or "IMPORT FROM GALLERY" (new). Gallery import uses
`ActivityResultContracts.PickVisualMedia` filtered to `VisualMediaType.VideoOnly` (Android
Photo Picker — no `READ_MEDIA_VIDEO`/storage permission needed on API 33+, and it's
supported via Google Play Services back to API 26 through the same contract). The picked
`content://` Uri is copied to `cacheDir/bar_path/` via a new small
`util/barpath/BarPathVideoImporter.kt` (mirrors `SessionShareImageSaver`'s existing
cache-copy pattern) so it becomes a real file path for `MediaMetadataRetriever`/
`BarPathFrameTracker`, which both already expect a path, not a Uri.

`CalibrationStep` gains a "Weight lifted (kg)" `OutlinedTextField`, shown only when
`isStandalone` — reuses the exact validation shape `referenceLengthCm` already has
(`toDoubleOrNull()`, must be > 0, inline error message).

`onConfirmCalibration`/`onSave` in the ViewModel branch on `isStandalone`:
- standalone → `barPathRepository.saveFreestandingBarPathMetrics(exerciseId, weightKg, analysis)`
- set-linked (existing) → `barPathRepository.saveBarPathMetrics(setLogId, analysis)` (unchanged)

### 2.5 ExerciseDetailScreen velocity chart picks up freestanding rows for free
`ExerciseDetailViewModel`'s existing `buildVelocityChart` (Sprint 27) combines session
history + set-linked bar-path metrics. Extend the same `combine` block to also collect
`barPathRepository.getFreestandingAnalysesForExercise(exerciseId)` and merge those in as
additional `ChartPoint`s (`dateMs = createdAtMs`), then sort the merged list by date. No
new chart component, no new screen — the "BAR SPEED" card just has more points on it once
freestanding data exists for that exercise.

**Acceptance**: an exercise with only freestanding (no set-linked) tracked analyses still
shows the chart once it has ≥2 points total, matching the existing `size >= 2` gate.

---

## 3. Tech stack additions

None. `PickVisualMedia` is part of `androidx.activity` (already a dependency, ships
`ActivityResultContracts`). Reuses `BarPathVideoRecorder`, `BarPathFrameTracker`,
`MarkerColorMatcher`, `AnalyzeBarPathUseCase`, `ExercisePickerSheet`, `ConfirmDialog`,
`IsCoachUseCase`, and the Paddle checkout URL logic from `CoachSettingsScreen`, all
verbatim.

---

## 4. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── data/local/
│   ├── AppDatabase.kt                    ← v8→9, MIGRATION_8_9 (table recreate)
│   ├── entity/BarPathMetricsEntity.kt    ← + exerciseId, createdAtMs; setLogId nullable
│   └── dao/BarPathMetricsDao.kt          ← + getFreestandingForExercise, countFreestandingSince
├── domain/
│   ├── usecase/GetBarPathQuotaUseCase.kt ← new
│   └── repository/BarPathRepository.kt   ← + saveFreestanding.../getFreestanding...
├── data/repository/BarPathRepositoryImpl.kt  ← delete-then-insert for set-linked save;
│                                                new freestanding methods
├── util/barpath/BarPathVideoImporter.kt  ← new, content:// → cache file copy
│
└── presentation/
    ├── screens/home/
    │   ├── HomeScreen.kt                 ← + BarPathCard
    │   └── HomeViewModel.kt              ← + barPathQuota StateFlow
    ├── screens/barpath/
    │   ├── BarPathCaptureScreen.kt       ← RecordingStep source choice, weight field
    │   └── BarPathCaptureViewModel.kt    ← isStandalone branch, nav args change
    ├── screens/exercises/ExerciseDetailViewModel.kt ← buildVelocityChart merges freestanding
    └── navigation/
        ├── Screen.kt                     ← BarPathCapture route gains exerciseId + optional args
        └── NavGraph.kt                   ← wires new nav args; Home → ExercisePickerSheet → capture

data/backup/BackupPayload.kt / BackupSerializer.kt ← BarPathMetricsDto: setLogId nullable,
                                                       + exerciseId, createdAtMs (versioned,
                                                       old backups still decode via defaults)
```

---

## 5. Code style (extends existing CLAUDE.md rules)

- "One shared check" precedent (`IsCoachUseCase`) extends into `GetBarPathQuotaUseCase` —
  quota/entitlement logic lives in exactly one place, never re-implemented inline in
  `HomeViewModel` or the capture flow.
- Derive-live-not-maintain-a-counter precedent (Power Level v0.18.1 fix) applies directly
  to the monthly quota: `COUNT(*) WHERE created_at_ms >= monthStart`, not a DataStore
  counter that could drift from deleted/edited rows.
- Reuse over duplication: `ExercisePickerSheet`, `ConfirmDialog`, the Paddle checkout URL,
  `BarPathVideoRecorder`, `BarPathFrameTracker`, `AnalyzeBarPathUseCase` are all reused
  as-is — this sprint is new wiring around existing, already-built pieces.

---

## 6. Testing strategy

- `GetBarPathQuotaUseCase`: pure combine logic, unit-testable with a fake
  `BarPathRepository`/`IsCoachUseCase` — 2-3 tests (under limit, at limit, coach override).
- `BarPathVideoImporter`'s file-copy is Android-dependent (Uri/ContentResolver) — no unit
  test, same category as `BarPathFrameTracker`/`BarPathVideoRecorder` from Sprint 26.
- Migration 8→9 correctness (backfill join) is the highest-risk pure-logic piece here —
  worth a Room migration test (`MigrationTestHelper`) if time allows, otherwise verify by
  hand: install a pre-migration debug build with real logged bar-path data, upgrade, and
  confirm `exercise_id`/`created_at_ms` backfilled correctly via a DB inspector.
- Everything else: same as prior VBT sprints — `assembleGithubDebug` compiling clean,
  manual reasoning pass. **Real device test still required and still owed** from
  yesterday: confirm the fixed calibration-screen scroll bug actually resolves, then test
  gallery import + standalone flow end to end before trusting any of this in production.

---

## 7. Boundaries

**Always do:**
- Leave the existing set-linked ⋮-menu capture flow's behavior unchanged for a user who
  enters it that way — this sprint only adds a second entry path and extends storage.
- Keep the quota check server-of-truth-free (derived from local Room data + the existing
  `is_coach()` entitlement check) — no new backend surface for this specific feature.

**Ask first about:**
- Whether the free-tier count should also count set-linked (⋮-menu) analyses toward the
  monthly 5, or only freestanding ones as currently scoped. Current spec: **freestanding
  only** — the ⋮-menu flow predates monetization and gating it retroactively would be a
  surprising behavior change for existing users. Flag if this should be revisited.

**Never do:**
- Never let the quota check block the app when offline/query-fails — default to allowing
  the analysis (fail open) rather than fail closed on a local Room read, since this is a
  local quota, not a payment gate.
- Never duplicate the Coach entitlement check outside `GetBarPathQuotaUseCase` /
  `IsCoachUseCase`.

---

## 8. Notes

This closes the loop the user hit directly: "I don't see where the option to upload the
video is." After this ships, Home has a visible, always-there entry point, and video
doesn't have to come from the in-app camera. The underlying marker-tracking pipeline
itself is **still unverified against real footage** (Sprint 26's open item) — this sprint
doesn't change that risk, it changes how many ways there are to trigger the same
unverified pipeline. Real-device testing (now unblocked by today's scroll-bug fix) is
still the next real milestone, independent of this sprint's scope.
