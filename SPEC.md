# SaiyanStrong — Rest Timer Sound Cues + RPE Entry Spec

## Status: BUILT (v0.20.0) — all 4 slices shipped, see CLAUDE.md progress log "Sprint 23"

(Replaces the previous Coach Mode spec in this file — Coach Mode shipped in full; see
`CLAUDE.md` progress log, Sprint "Coach Mode (v0.18.0)" through its Slice 7 reconcile job.)

---

## 0. Decisions locked in via clarifying questions

- **Sound source**: fully synthesized, no bundled audio assets — `ToneGenerator` for the
  tick, a small hand-rolled `AudioTrack` PCM buffer for the gong. No licensing/sourcing
  risk, no new asset pipeline.
- **Mute control**: yes — a Settings → TRAINING toggle ("Rest timer sounds"), DataStore-backed,
  default **on**.
- **RPE picker UI**: a `ModalBottomSheet` (closer to the reference Strong screenshot's layout
  than an inline third cell), not a persistent numpad-adjacent panel — SaiyanStrong doesn't have
  a custom on-screen numpad like Strong's (KG/REPS use the system IME via `BasicTextField`), so
  the reference screenshot's exact layout doesn't map 1:1; a bottom sheet reproduces its content
  (chip row 6–10 in 0.5 steps + explanatory line) without needing a custom keypad.
- **RPE scope**: active workout only (`ActiveWorkoutScreen`) — not the session-detail/History
  edit view added in the previous sprint. Within active workout, RPE is offered both at initial
  entry (`PendingSetRow`) and as an edit on an already-logged set (`CompletedSetRow`) — the latter
  is my own design call, not something asked verbatim, because every other field on a logged row
  (KG, REPS, failure) is already editable in place, and leaving RPE as entry-only would be an odd
  inconsistency. Flagging this so you can trim it if you only want RPE at entry time.

---

## 1. Objective

Two independent, small UX additions to the existing active-workout flow:

1. **Audible rest-timer cues** — a tick at 3 seconds remaining, a gong at 0 — so lifters don't
   have to keep glancing at the screen during rest.
2. **RPE entry per set** — `SetLog.rpe: Float?` and `set_logs.rpe` already exist end-to-end in the
   domain/Room layers and are already threaded through `LogSetUseCase`/`onLogSet` — today the app
   always passes `null`. This closes the UI gap so RPE can actually be recorded.

**Target users**: existing SaiyanStrong users mid-workout — no new user segment, this is a
quality-of-life pass on the core logging loop.

---

## 2. Core features & acceptance criteria

### 2.1 Rest timer sound cues
- Hook into `ActiveWorkoutViewModel.startRestTimerFrom(seconds)` — the existing countdown
  coroutine (`for (secondsLeft in seconds downTo 1) { update; delay(1000) }`):
  - When the loop's `secondsLeft == 3` update fires → play **tick**.
  - When the loop completes naturally (timer reaches 0, not skipped/cancelled) → play **gong**.
- New `util/RestTimerSoundPlayer.kt` (Hilt `@Singleton`), two methods: `playTick()`, `playGong()`.
  - `playTick()`: `ToneGenerator(AudioManager.STREAM_MUSIC, volume).startTone(TONE_PROP_BEEP2, 120)`.
  - `playGong()`: a short (~900ms) procedurally-generated PCM buffer played via `AudioTrack` in
    `STREAM_MUSIC` mode — two sine partials (e.g. 90 Hz + 180 Hz) with an exponential-decay
    amplitude envelope, giving a low "thud/gong" character without any bundled asset.
  - Both respect the device media volume/silent-mode (using `STREAM_MUSIC`, not `STREAM_ALARM`,
    so a silenced phone stays silent — matches user expectation for a workout app, not an alarm).
- Gated by a new Settings toggle (§2.2) — when off, both methods no-op.
- Cancelling the timer early (SKIP, or `onAdjustRestTimer` restarting it) must **not** fire the
  gong — only a countdown that reaches 0 naturally does. This falls out for free: `restTimerJob
  .cancel()` throws `CancellationException` mid-`delay`, so code after the loop never runs.
- **Acceptance**: start a rest timer ≥ 4s, let it run out untouched → hear tick once near the end,
  gong once at expiry. Tap SKIP before it reaches 0 → no gong. Toggle the Settings switch off →
  next timer run is silent.

### 2.2 Mute toggle
- `UserPreferencesDataStore`: new `restTimerSoundsEnabled` (`booleanPreferencesKey`, default
  `true`), following the exact existing pattern of `useFemaleDotsFormula`/`defaultRestSeconds`.
- `UserRepository`/`Impl`: `getRestTimerSoundsEnabled(): Flow<Boolean>` +
  `setRestTimerSoundsEnabled(enabled: Boolean)`.
- `SettingsScreen` TRAINING section gains a "Rest timer sounds" row (label + `Switch`), next to
  the existing "Default rest timer −15s/+15s" row.
- `ActiveWorkoutViewModel` collects this as a `StateFlow<Boolean>` (same shape as
  `defaultRestSeconds`) and checks it before calling `RestTimerSoundPlayer`.

### 2.3 RPE entry at set logging
- New `presentation/components/RpeBottomSheet.kt`: `ModalBottomSheet` showing the reference
  screenshot's explanatory line ("RPE is a way to measure the difficulty of a set. Tap a number
  to select an RPE value.") plus a wrapped row of selectable chips: 6, 6.5, 7, 7.5, 8, 8.5, 9,
  9.5, 10 (matches the reference exactly), plus a "NO RPE" / clear option. Selected chip
  highlighted `NeonGreen`, matching existing chip-selection language elsewhere in the app
  (ExercisePickerSheet's sort chips).
- `PendingSetRow` (ActiveWorkoutScreen.kt): add a small tappable "RPE" affordance below the
  SET/KG/REPS row (own slim row, ~24dp, matching the existing "expand below the row" pattern
  already used for KG/REPS steppers) — shows "+ RPE" (dim, unset) or "RPE 8.5" (`NeonGreen`, set).
  Tapping opens `RpeBottomSheet`; selecting a value updates local pending-row state so it's
  included in the `onLogSet(kg, reps, rpe, isFailure)` call already wired end-to-end today (only
  the `rpe` argument is currently hardcoded `null` in `PendingSetRow.logSet()`).
- `CompletedSetRow`: same affordance, editable after the fact — extends
  `onEditSet: (Int, Int, Double, Int, Boolean) -> Unit` to
  `(Int, Int, Double, Int, Float?, Boolean) -> Unit`, threading through
  `ActiveWorkoutViewModel.onEditSet` to `SetLog.copy(rpe = ...)`.
- **Acceptance**: logging a set with RPE 8.5 persists `set_logs.rpe = 8.5` for that row (visible
  via the exercise detail HISTORY tab, which already reads the `rpe` column — no changes needed
  there); a set logged with no RPE stores `null`, unchanged from today's behavior.

---

## 3. Tech stack additions

| Addition | Purpose |
|---|---|
| `android.media.ToneGenerator` (platform API, no dependency) | Tick sound |
| `android.media.AudioTrack` (platform API, no dependency) | Procedurally synthesized gong sound |
| No new Gradle dependencies, no new permissions | Both APIs are already available on minSdk 26 with no manifest changes |

---

## 4. Project structure (new/changed)

```
app/src/main/java/com/saiyanstrong/
├── util/
│   └── RestTimerSoundPlayer.kt         ← new — @Singleton, playTick()/playGong(), ToneGenerator +
│                                          AudioTrack-synthesized gong, both gated by an enabled
│                                          flag passed in per-call (no internal DataStore read —
│                                          keeps it a pure player, ViewModel owns the setting)
│
├── data/datastore/
│   └── UserPreferencesDataStore.kt     ← + restTimerSoundsEnabled key/flow/setter
├── domain/repository/
│   └── UserRepository.kt               ← + getRestTimerSoundsEnabled()/setRestTimerSoundsEnabled()
├── data/repository/
│   └── UserRepositoryImpl.kt           ← + passthrough to the DataStore methods above
│
├── presentation/components/
│   └── RpeBottomSheet.kt               ← new — ModalBottomSheet, chip row 6–10 step 0.5 + clear
│
├── presentation/screens/workout/
│   ├── ActiveWorkoutViewModel.kt       ← restTimerSoundsEnabled StateFlow; startRestTimerFrom()
│   │                                      calls RestTimerSoundPlayer at secondsLeft==3 and on
│   │                                      natural expiry; onEditSet gains an rpe parameter
│   └── ActiveWorkoutScreen.kt          ← PendingSetRow + CompletedSetRow gain the RPE affordance
│                                          row + RpeBottomSheet wiring
│
└── presentation/screens/settings/
    ├── SettingsScreen.kt               ← + "Rest timer sounds" Switch row in TRAINING section
    └── SettingsViewModel.kt            ← + restTimerSoundsEnabled StateFlow + toggle handler
```

No Room schema change — `set_logs.rpe` already exists (has since Phase 1). No new Android
dependencies, no new permissions.

---

## 5. Code style (extends existing CLAUDE.md rules)

- `RestTimerSoundPlayer` takes an `enabled: Boolean` parameter on each play call rather than
  reading DataStore itself, keeping it a stateless utility — the ViewModel (which already
  collects `StateFlow`s the same way for `defaultRestSeconds`) is the single place that knows the
  current setting.
- RPE values are `Float?` end-to-end, matching the existing `SetLog.rpe: Float?` — the bottom
  sheet only ever emits one of the nine reference values (6.0–10.0 step 0.5) or `null`, no free
  text entry, so no new validation logic is needed at the boundary.
- No hardcoded colors — RPE chip selection state uses `NeonGreen`/`Color.White.copy(alpha=...)`
  exactly like existing chip components.
- `StateFlow` everywhere, `collectAsStateWithLifecycle()` in Compose — same as every other screen.

---

## 6. Testing strategy

Same reality as the rest of this project: no `app/src/test` source set, no device/emulator access
this session. Verification is build success + manual QA checklist (flagged, not assumed done):

1. Start a rest timer, let it run to completion untouched → tick near the end, gong at expiry.
2. Start a rest timer, tap SKIP before expiry → no gong.
3. Toggle "Rest timer sounds" off in Settings → next timer run (tick and gong) is silent; toggle
   back on → sound returns without restarting the app.
4. Log a set, tap the RPE affordance, pick 8.5 → set row shows "RPE 8.5"; finish the workout →
   open that exercise's HISTORY tab and confirm the same RPE value is shown for that set.
5. Log a set with no RPE → confirm nothing regresses (unaffected, same as today).
6. Edit an already-logged set's RPE via `CompletedSetRow` → confirm the change persists after
   FINISH (session save reads the in-memory `ExerciseLog.sets`, which already includes `rpe` in
   `SetLog` — no repository change needed for the happy path, this is UI-state plumbing only).

---

## 7. Boundaries

**Always do:**
- Keep both sound cues on `STREAM_MUSIC` so device silent mode / media-volume-zero is respected
  — never use `STREAM_ALARM` to force sound through a silenced phone.
- Respect the mute toggle for both the tick and the gong — no cue that ignores it.
- Keep RPE optional — never require a value before a set can be logged (matches how `rpe` has
  always been nullable in the domain model).

**Ask first about:**
- Whether the "editable after logging" half of §2.3 (RPE on `CompletedSetRow`, not just
  `PendingSetRow`) is wanted, since it was my own extrapolation from the existing edit-everything
  pattern rather than something explicitly requested — easy to drop if you only want RPE at
  initial entry.
- Exact tick/gong tone character (frequencies, duration) — the plan above is a reasonable first
  pass with no assets to reference; happy to adjust after you hear it once it's built (no way to
  preview audio without a device/emulator this session).

**Never do:**
- Never fetch or bundle third-party audio assets without your explicit sign-off (moot here since
  everything's synthesized, but stating it since audio was the topic).
- Never make the rest-timer cues bypass silent mode.

---

## 8. Suggested incremental slices

1. `RestTimerSoundPlayer` (tick + gong synthesis) + wiring into
   `ActiveWorkoutViewModel.startRestTimerFrom` — testable by ear alone, no RPE dependency.
2. Mute toggle: DataStore key, `UserRepository` methods, Settings row, ViewModel wiring.
3. `RpeBottomSheet` component + `PendingSetRow` entry wiring (the core ask).
4. `CompletedSetRow` RPE editing (the extrapolated half — pending your confirmation in
   Boundaries above; skip this slice entirely if you only want entry-time RPE).

Each slice independently buildable/verifiable, own commit, per your established convention this
project.
