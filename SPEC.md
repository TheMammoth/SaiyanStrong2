# SaiyanStrong — Velocity-Based Training (Bar Path + Bar Speed) Spec

## Status: §8 BUILT (v0.23.0) — camera capture + marker tracking shipped, UNVERIFIED against real
footage. See CLAUDE.md "Sprint 26" for the exact known-gap list before trusting any output.

(Replaces the previous "RPE-Based Progression Hints" spec in this file — that feature shipped in
full as v0.21.0; see `CLAUDE.md` progress log, "Sprint 24".)

This is the biggest, most technically ambitious thing in this app so far — genuinely a
multi-session effort, not a sprint. This spec deliberately covers **only the first half**: the
physics/math and the place to store results. The camera capture screen and the actual
color-marker tracking algorithm are **out of scope for this spec** — see §0 for why, and §8 for
where they pick up.

---

## 0. Decisions locked in via clarifying questions

- **Record-then-analyze, not live.** CameraX records the set; analysis happens after, on the
  recorded clip. Far more tractable than real-time tracking, and matches how real camera-based VBT
  products (not accelerometer-based ones) already work.
- **Tracking method: a single colored marker on the bar, classic computer vision** — HSV
  color-blob detection frame-to-frame, no ML model, no training data. No MediaPipe Pose /
  joint-tag tracking for now (may come later — see §8).
- **Persist the results.** New Room table for per-set velocity/bar-path metrics, not a
  view-once-and-discard feature — matches how everything else in this app is tracked historically.
- **Build the math/physics layer first; hold the camera + tracking algorithm for a session where
  you have a real device to test against.** Reasoning: everything in this spec (§2.1–2.3) is pure
  Kotlin math with zero Android dependencies — fully unit-testable right now, the same way the RPE
  chart was in the previous sprint. The camera capture UI and the actual HSV marker-tracking
  algorithm are a completely different kind of risk: color thresholds, lighting, and marker
  visibility only mean anything against **real recorded footage**, which doesn't exist in this
  session. Building that blind and calling it "done" would be dishonest — it would compile, but
  nobody could tell you whether it actually tracks a barbell. This spec explicitly does not build
  that part yet.

**What ships if you approve this spec**: the physics engine that turns *(pixel position, timestamp)
samples of a marker + a real-world scale factor + the set's logged weight* into real velocity/
power/range-of-motion numbers and a training-zone classification, plus a place in the database to
store that per set. **What does not ship yet**: any way to actually produce those pixel samples
from a real video. That's the natural next slice, once you're on a device.

---

## 1. Objective

Give SaiyanStrong a real velocity-based-training (VBT) capability: from a barbell marker's tracked
position over time, compute actual physics — displacement, instantaneous velocity, instantaneous
acceleration, instantaneous force (`F = m·(g + a)`, using the set's already-logged `weightKg` as
mass), instantaneous power (`P = F·v`), and classify the rep into a training zone (Speed /
Speed-Strength / Strength-Speed / Strength / Absolute Strength) the way real strength & conditioning
coaches read bar speed — not just RPE or 1RM percentage.

**Target users**: you, specifically — this is explicitly the feature you've wanted for years,
aimed at applying real applied mechanics to training data instead of just subjective RPE.

---

## 2. Core features & acceptance criteria

### 2.1 Domain models
```kotlin
// domain/model/BarPathSample.kt
data class BarPathSample(val timestampMs: Long, val xPx: Double, val yPx: Double)

// domain/model/VelocityZone.kt — Bryan Mann VBT zone table (widely taught in S&C, generic —
// not lift-specific; documented as an approximation, see §8 for the real long-term answer)
enum class VelocityZone(val label: String, val minMs: Double, val maxMs: Double) {
    ABSOLUTE_STRENGTH("Absolute Strength", 0.0, 0.50),
    STRENGTH_SPEED("Strength-Speed", 0.50, 0.75),
    SPEED_STRENGTH("Speed-Strength", 0.75, 1.00),
    SPEED_ACCEL("Speed (Accelerative)", 1.00, 1.30),
    SPEED_MAX("Speed (Max)", 1.30, Double.MAX_VALUE)
}

// domain/model/BarPathAnalysis.kt
data class BarPathAnalysis(
    val peakVelocityMs: Double,
    val meanConcentricVelocityMs: Double,   // total displacement / total time — the standard MCV metric
    val peakPowerWatts: Double,
    val meanPowerWatts: Double,
    val rangeOfMotionCm: Double,            // top-to-bottom vertical travel
    val barPathDeviationCm: Double,         // horizontal drift, left-right — a real coaching cue
    val velocityZone: VelocityZone
)
```

### 2.2 Physics use case (`domain/usecase/AnalyzeBarPathUseCase.kt`)
Pure function, no Android/DI dependencies beyond `@Inject constructor()`:

```
execute(
    samples: List<BarPathSample>,     // one per tracked video frame, chronological
    pixelsPerMeter: Double,           // calibration scale — how it's derived is out of scope here
    massKg: Double,                   // from SetLog.weightKg
    concentricStartMs: Long,          // window bounds — how these get set is out of scope here
    concentricEndMs: Long
): BarPathAnalysis
```

- Converts each sample's `yPx` to a vertical position in meters, oriented so **up is positive**
  (image Y increases downward — this gets inverted, and the inversion is the kind of subtle bug
  this needs a unit test for specifically).
- Filters samples to `[concentricStartMs, concentricEndMs]`.
- Between each consecutive pair: `Δt` (seconds), `Δy` (meters) → instantaneous velocity.
  Between each consecutive velocity pair: instantaneous acceleration → instantaneous force
  (`massKg × (9.81 + a)`) → instantaneous power (`force × velocity`).
- `meanConcentricVelocityMs` = total vertical displacement ÷ total elapsed time (not an average of
  the instantaneous velocities — that's a different, wrong number; this distinction is exactly
  what real VBT devices report and needs to be right).
- `rangeOfMotionCm` = (max height − min height) × 100.
- `barPathDeviationCm` = (max `xPx` − min `xPx`) ÷ `pixelsPerMeter` × 100.
- `velocityZone` = whichever `VelocityZone` band `meanConcentricVelocityMs` falls into.
- **Acceptance** (unit-testable without any camera): feed a synthetic sample list representing a
  bar moving up 0.4m over 0.5s at constant velocity → `meanConcentricVelocityMs` ≈ 0.8 m/s,
  zone = `SPEED_STRENGTH`; a near-zero-velocity synthetic set → `ABSOLUTE_STRENGTH`. Feed a
  sample list with a mid-rep sticking point (deceleration then re-acceleration) → peak velocity
  correctly reflects the fastest instant, not the average.

### 2.3 Persistence (new Room table, DB v7→8)
```
bar_path_metrics : id(PK autoGen), set_log_id(FK→set_logs CASCADE, unique),
                   peak_velocity_ms, mean_concentric_velocity_ms,
                   peak_power_watts, mean_power_watts,
                   range_of_motion_cm, bar_path_deviation_cm,
                   velocity_zone(TEXT)
```
- A **separate table**, not new nullable columns bolted onto `set_logs` — this is a distinct,
  optional, richer bundle of data that will only exist for sets actually recorded with the future
  camera flow; keeping it separate avoids cluttering the hot-path `set_logs` table with columns
  that are `NULL` for nearly every historical row.
- New `BarPathMetricsEntity`/`BarPathMetricsDao` (`insert`, `getForSetLog(setLogId): Flow<BarPathMetricsEntity?>`,
  `deleteForSetLog` — cascades automatically via the FK anyway, listed for symmetry with existing DAOs).
- New `domain/repository/BarPathRepository.kt` + `BarPathRepositoryImpl` — its own repository
  (matching the existing one-repository-per-concern pattern: `TemplateRepository`,
  `ExerciseMediaRepository`, etc.), not bolted onto `SessionRepository`.
- **Acceptance**: inserting a `BarPathMetrics` row for a set, then querying it back, round-trips
  exactly; deleting the parent `SetLog` cascades and removes the `bar_path_metrics` row too
  (same `ForeignKey.CASCADE` pattern already used everywhere else in this schema).

---

## 3. Tech stack additions

| Addition | Purpose |
|---|---|
| None this slice | Everything in §2.1–2.3 is pure Kotlin + Room, already-used dependencies |

**Deliberately not added yet** (belongs to the camera/tracking slice, see §8):
CameraX (`androidx.camera.*`) for recording, and either a hand-rolled HSV blob tracker (pure
Kotlin, works on extracted `Bitmap` frames via `MediaMetadataRetriever`/`MediaCodec`, no native
dependency) or OpenCV for Android (mature, proven, but a large new native/JNI dependency — the
first one in this project). That choice needs a real test video to evaluate honestly, not a guess.

---

## 4. Project structure (new/changed, this slice only)

```
app/src/main/java/com/saiyanstrong/
├── domain/
│   ├── model/
│   │   ├── BarPathSample.kt            ← new
│   │   ├── VelocityZone.kt             ← new
│   │   └── BarPathAnalysis.kt          ← new
│   ├── usecase/
│   │   └── AnalyzeBarPathUseCase.kt    ← new — pure physics, no dependencies
│   └── repository/
│       └── BarPathRepository.kt        ← new
│
└── data/
    ├── local/
    │   ├── entity/BarPathMetricsEntity.kt   ← new
    │   ├── dao/BarPathMetricsDao.kt          ← new
    │   └── AppDatabase.kt                    ← version 7→8, MIGRATION_7_8 (CREATE TABLE only,
    │                                            no data migration needed — brand new table)
    └── repository/
        └── BarPathRepositoryImpl.kt      ← new

app/src/test/java/com/saiyanstrong/domain/usecase/
└── AnalyzeBarPathUseCaseTest.kt         ← new — synthetic sample lists, no Android/camera needed
```

No screens, no ViewModel wiring, no navigation changes this slice — there is nothing to show yet
without a way to produce real `BarPathSample` data.

---

## 5. Code style (extends existing CLAUDE.md rules)

- `AnalyzeBarPathUseCase` pure/no-dependency, same convention as `CalculatePowerLevelUseCase`/
  `SuggestNextLoadUseCase`.
- `VelocityZone` as an `enum class` carrying its own band bounds — same `SaiyanStage`-style pattern
  already used for Power Level stages (threshold data living on the enum itself, not a separate
  lookup).
- Migration is additive-only (`CREATE TABLE`, no `ALTER`/data backfill) — never
  `fallbackToDestructiveMigration()`, consistent with every prior migration in this project.
- `BarPathRepository` follows the exact existing repository-interface-in-domain /
  impl-in-data pattern, never imported directly by a ViewModel (there isn't one yet).

---

## 6. Testing strategy

This is the second slice in the project with real unit tests (after the RPE chart/suggestion
logic). Since there's no camera output to feed it yet, tests use **hand-constructed synthetic
`BarPathSample` lists** with known ground-truth physics (e.g. "10 samples, evenly spaced 33ms
apart, moving up exactly 0.04m per sample" → known velocity), verifying:
1. Constant-velocity synthetic rep → correct `meanConcentricVelocityMs` and matching `velocityZone`.
2. A synthetic sticking point (deceleration mid-rep, re-acceleration) → `peakVelocityMs` reflects
   the fastest instant, not an average.
3. Image-coordinate inversion is correct (Y increasing downward in pixel space → position
   increasing *upward* in real-world meters) — this is exactly the kind of sign-flip bug that's
   invisible until you stare at a real video and wonder why the numbers are backwards.
4. `rangeOfMotionCm`/`barPathDeviationCm` match hand-computed values for a known sample set.
5. FK cascade: deleting a `SetLog` with `bar_path_metrics` attached removes the metrics row (Room
   in-memory or instrumented DB test — flagged as needing a device/emulator if it ends up
   requiring `AndroidJUnit4`; if it can be done as a plain Robolectric-free logic check instead,
   prefer that to stay in the no-device-needed test tier).

No device/emulator needed for items 1–4. Item 5 may need one depending on how Room's in-memory
test database behaves in this environment — flagged honestly rather than assumed.

---

## 7. Boundaries

**Always do:**
- Keep `AnalyzeBarPathUseCase` a pure function — no camera, no I/O, no Android imports.
- Use `meanConcentricVelocityMs` as *total displacement ÷ total time*, never as an average of
  instantaneous velocities — these produce different numbers and only one is the real MCV metric
  coaches expect.
- Additive-only migration — never destructive, matching every migration so far in this project.

**Ask first about:**
- Whether the FK-cascade test (§6 item 5) needs an instrumented/device-backed test — will report
  back honestly once I try to write it, rather than guessing now.
- The generic `VelocityZone` table (§2.1) is population-level, not personalized or lift-specific —
  real load-velocity relationships differ meaningfully between squat/bench/deadlift. Flagging this
  now, not silently shipping a table that quietly under- or over-estimates zones for your specific
  lifts (see §8 for the actual fix).

**Never do:**
- Never fake or interpolate camera/tracking data to make this slice "look done" — if there's no
  real video, there's no real `BarPathSample` list, and nothing here pretends otherwise.
- Never build the camera/CV slice (§8) blind and claim it's verified — that needs your real device
  and a real recorded lift, explicitly deferred per your own choice in §0.

---

## 8. Where this picks up next (out of scope for this spec, listed so nothing gets lost)

1. **Calibration UI**: user taps two points on a known-length reference (bar sleeve, a plate of
   known diameter) in the first video frame → `pixelsPerMeter`. A one-time-per-recording step.
2. **CameraX recording screen**: record the set, save the clip.
3. **Marker tracking**: extract frames (`MediaMetadataRetriever` or `MediaCodec`) → HSV
   color-blob detection per frame → a `List<BarPathSample>`. This is the part that genuinely needs
   a real device + real footage to tune (marker color choice, lighting robustness, frame sampling
   rate) — the actual R&D of this feature.
4. **Rep-window detection**: automatically or manually mark `concentricStartMs`/`concentricEndMs`
   per rep within a set (multi-rep sets need per-rep windows, not one analysis for the whole clip).
5. **UI to show results**: a velocity/power graph + numbers per set, wired into
   `SessionCompleteScreen`/`ExerciseDetailScreen` alongside existing e1RM/RPE data.
6. **The real long-term upgrade over §2.1's generic zone table**: once enough velocity-tagged sets
   exist for a given exercise, fit a **personal load-velocity profile** (linear regression of
   velocity vs. %1RM across the user's own logged sets) instead of relying on population-level
   Bryan Mann bands — this would let the app estimate e1RM from bar speed alone, a genuinely
   different and arguably more accurate method than the existing Epley formula, and personalized to
   the individual lifter rather than a generic chart. Real VBT research direction, not fantasy —
   just meaningfully more work than this slice, and needs a real velocity dataset to fit against
   first.
