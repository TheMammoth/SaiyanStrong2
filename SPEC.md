# SaiyanStrong — Cloud Auth + Backup Spec

## Status: IN PROGRESS

---

## 1. Objective

Users currently lose their entire training history (sessions, sets, templates, bodyweight log,
power level) if they lose or wipe their phone — everything lives only in the local Room DB.

Add **optional** cloud auth (Google Sign-In via Supabase) and **backup/restore** (not live sync)
so a signed-in user can recover their data on a new device. The app stays fully usable
offline/logged-out; auth only unlocks backup. This is v1 — no cross-device live sync, no
conflict resolution, backup-restore only.

**Target users:** existing SaiyanStrong users who want their lifting history to survive a phone
loss/reset. No new user-facing concepts beyond "sign in" and "back up."

---

## 2. Core features & acceptance criteria

### 2.1 Google Sign-In (Credential Manager)
- Uses `androidx.credentials` CredentialManager + `googleid` `GetGoogleIdOption`, not the legacy
  `GoogleSignInClient`.
- Web Client ID (from Supabase's Google provider config) is a build config field, never hardcoded.
- On success, the Google ID token is exchanged with Supabase Auth
  (`supabase.auth.signInWith(IDToken)` with provider Google) to establish a Supabase session.
- **Acceptance:** tapping "Sign in with Google" on a real device shows the system account
  picker, completes without a password prompt, and Settings → ACCOUNT shows the signed-in
  email/avatar afterward.

### 2.2 Optional / local-first
- No auth state is required to use any existing feature (workouts, history, templates,
  bodyweight, exercise browser). `AuthRepository.authState` is a `Flow<AuthUser?>`; every
  existing screen is unaffected by it being `null`.
- **Acceptance:** a fresh install with no sign-in behaves identically to v0.13.0 in every screen
  except Settings, which gains an ACCOUNT section.

### 2.3 AuthRepository
- `domain/repository/AuthRepository.kt` (interface) + `data/repository/AuthRepositoryImpl.kt`
  (impl), `@Singleton`, Hilt-bound in a new `di/AuthModule.kt`.
- `authState: Flow<AuthUser?>`, `suspend fun signInWithGoogle(idToken: String): Result<AuthUser>`,
  `suspend fun signOut()`, `currentUserId(): String?`.
- `domain/model/AuthUser.kt`: `id: String, email: String?, displayName: String?, photoUrl: String?`.

### 2.4 Backup now / Restore from backup (Settings → new ACCOUNT section)
- Visible only when signed in. Shows: avatar + email, "last backup: <relative time>" (or "never"),
  BACKUP NOW button, RESTORE FROM BACKUP button, SIGN OUT button.
- Signed-out state shows a single "Sign in with Google" SaiyanButton and one line explaining what
  sign-in unlocks ("back up your training history to the cloud").
- **Acceptance:** BACKUP NOW uploads and immediately updates the "last backup" timestamp.
  RESTORE FROM BACKUP shows the existing `ConfirmDialog` component ("Local data will be replaced
  with your backup from <timestamp>. This cannot be undone.") before doing anything destructive.

### 2.5 Auto-backup after each completed session
- `CompleteSessionUseCase` success enqueues a one-shot `WorkManager` job
  (`BackupWorker`, Hilt-assisted via `HiltWorkerFactory`) with `NetworkType.UNMETERED` constraint
  (Wi-Fi-preferred, per spec — device data is never spent on an auto-backup) and
  `ExistingWorkPolicy.REPLACE` on a fixed unique work name, so rapid consecutive sessions don't
  queue redundant uploads.
- No-op (does not enqueue) when signed out.
- **Acceptance:** finishing a workout while signed in and on Wi-Fi updates the Settings
  "last backup" timestamp within a few seconds without any user action.

### 2.6 Backup payload
- `data/backup/BackupPayload.kt`: `@Serializable` DTO covering **every** table — exercises
  (including user-set `rest_timer_sec` overrides), sessions, exercise_logs, set_logs, templates,
  template_exercises, body_weight_logs, plus `lifetimePowerEarned` and DOTS-formula pref from
  DataStore — everything needed to fully reconstruct app state.
- `data/backup/BackupSerializer.kt`: reads all DAOs + `UserPreferencesDataStore`, builds the DTO,
  serializes with `kotlinx.serialization.json.Json`.
- Envelope fields: `timestampMs: Long`, `appVersionCode: Int`, `payload: BackupPayload`.

### 2.7 Upload target
- Supabase **Storage** (not a Postgres table): private bucket `backups`, one object per user at
  `{userId}/latest.json`, overwritten on every backup. The envelope's own `timestampMs` /
  `appVersionCode` fields are the "versioning" — no need to keep historical objects for v1.
- **RLS / Storage policies:** authenticated users may `select`/`insert`/`update` only objects
  under `{auth.uid()}/...` in the `backups` bucket. No public access.

### 2.8 Restore flow
- `RestoreBackupUseCase`: downloads `{userId}/latest.json`, parses the envelope, and rejects with
  a clear error if `payload.appVersionCode > BuildConfig.VERSION_CODE`
  ("Backup was made with a newer app version — update the app first.") — forward-compatible
  backups are refused, older ones are always accepted.
- On success: `AppDatabase.withTransaction { }` deletes all rows (FK-safe order: set_logs →
  exercise_logs → sessions, template_exercises → templates, body_weight_logs, then exercises) and
  reinserts everything from the payload, then writes DataStore prefs (lifetime power, DOTS
  formula).
- After the transaction commits, the app restarts its nav graph at Home (`NavGraph` pops to
  start destination) so every screen re-reads fresh Flows — no stale in-memory ViewModel state.
- **Acceptance:** sign in on a second/reset device → RESTORE FROM BACKUP → History, Home power
  level, templates, and bodyweight log all match the source device.

### 2.9 Error handling
- Every auth/backup/restore failure (network, Supabase error, version mismatch) surfaces as a
  `Snackbar` on the Settings screen (add a `SnackbarHost` to its `Scaffold` if not already
  present) — never a silent failure, never a crash.

---

## 3. Tech stack additions

All versions added to `gradle/libs.versions.toml` — never hardcoded in `build.gradle.kts`.

| Dependency | Purpose |
|---|---|
| `io.github.jan-tennert.supabase:auth-kt` | Supabase Auth client |
| `io.github.jan-tennert.supabase:storage-kt` | Supabase Storage client |
| `io.ktor:ktor-client-android` | Ktor engine required by supabase-kt |
| `androidx.credentials:credentials` + `credentials-play-services-auth` | Credential Manager |
| `com.google.android.libraries.identity.googleid:googleid` | Google ID token option |
| `androidx.work:work-runtime-ktx` | WorkManager |
| `androidx.hilt:hilt-work` (+ ksp `hilt-compiler`) | Hilt-assisted Worker |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` (+ `kotlin-serialization` plugin) | Backup JSON |

Build config fields (read from **`local.properties`**, same gitignored pattern as
`keystore.properties` — never hardcoded, never committed):
`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_GOOGLE_WEB_CLIENT_ID`.

---

## 4. Project structure (new/changed files)

```
app/src/main/java/com/saiyanstrong/
├── domain/
│   ├── model/
│   │   ├── AuthUser.kt                    ← new
│   │   └── BackupInfo.kt                  ← new (lastBackupAtMs, appVersionCode)
│   ├── repository/
│   │   ├── AuthRepository.kt              ← new
│   │   └── BackupRepository.kt            ← new
│   └── usecase/
│       ├── SignInWithGoogleUseCase.kt     ← new
│       ├── SignOutUseCase.kt              ← new
│       ├── BackupNowUseCase.kt            ← new
│       └── RestoreBackupUseCase.kt        ← new
│
├── data/
│   ├── backup/
│   │   ├── BackupPayload.kt               ← new (@Serializable DTOs, all tables)
│   │   └── BackupSerializer.kt            ← new (DAOs+DataStore → payload → JSON, and back)
│   ├── remote/
│   │   └── SupabaseClientProvider.kt      ← new (builds SupabaseClient from BuildConfig)
│   ├── repository/
│   │   ├── AuthRepositoryImpl.kt          ← new
│   │   └── BackupRepositoryImpl.kt        ← new
│   └── worker/
│       └── BackupWorker.kt                ← new (HiltWorker, one-shot, UNMETERED)
│
├── di/
│   ├── AuthModule.kt                      ← new (SupabaseClient, binds AuthRepository)
│   ├── BackupModule.kt                    ← new (binds BackupRepository)
│   └── RepositoryModule.kt                ← unchanged (existing binds stay)
│
├── util/
│   └── GoogleSignInHelper.kt              ← new (wraps CredentialManager.getCredential)
│
└── presentation/screens/settings/
    ├── SettingsScreen.kt                  ← + ACCOUNT section, SnackbarHost
    └── SettingsViewModel.kt               ← + authState, backupInfo, onSignIn/Out/Backup/Restore

app/src/main/java/com/saiyanstrong/
└── CompleteSessionUseCase.kt / SessionRepositoryImpl.kt  ← enqueue BackupWorker on success (signed-in only)

app/src/main/java/com/saiyanstrong/SaiyanStrongApp.kt     ← implements Configuration.Provider (HiltWorkerFactory)

gradle/libs.versions.toml, app/build.gradle.kts            ← new deps + buildConfigField reads
```

No Room schema/migration changes — backup is fully external to the local DB shape.

---

## 5. Code style (extends existing CLAUDE.md rules)

- Kotlin only, Compose only, `StateFlow` (never `LiveData`) — no exception for this feature.
- Clean Architecture layers hold: `SettingsScreen` → `SettingsViewModel` → use cases →
  `AuthRepository`/`BackupRepository` interfaces; `AuthRepositoryImpl`/`BackupRepositoryImpl` in
  `data/` are never imported directly by the ViewModel.
- `AuthRepositoryImpl`/`BackupRepositoryImpl` are `@Singleton`, injected via Hilt — no manual
  `by lazy {}` construction of the `SupabaseClient`.
- Metric-only rule is unaffected (no weight values are introduced by this feature) but
  `WeightFormatter` remains the only formatter if any weight ever renders on a backup/restore
  screen (it won't in v1 — timestamps and counts only).
- No hardcoded colors — ACCOUNT section reuses existing `SaiyanTheme`/`SaiyanButton`/
  `ConfirmDialog` tokens and components, same as every other Settings row.
- `Result<T>` return type for all suspend auth/backup operations (sign-in, backup, restore) so
  the ViewModel can pattern-match success/failure without exceptions crossing layers.
- Keep every new file under 500 lines; `BackupSerializer` in particular should split
  serialize/deserialize into small per-table private functions rather than one long function.

---

## 6. Testing strategy

This repo has no `app/src/test` source set today — verification is build + manual QA, matching
the existing project convention (see CLAUDE.md `## Build phases — status`).

**Build verification (required before every commit, per CLAUDE.md):**
- `.\gradlew assembleDebug` (PowerShell — the rtk Bash hook rewrites `./gradlew` and hangs)
- `grep -r " lb" app/src` → zero results (unaffected by this feature, re-verify anyway)

**Manual QA checklist (device/emulator with Google Play services):**
1. Fresh install, no sign-in → every existing tab/feature works exactly as v0.13.0.
2. Settings → ACCOUNT → Sign in with Google → system account picker appears → returns to
   Settings showing email/avatar, "last backup: never".
3. Log and finish a workout on Wi-Fi → within ~10s, "last backup" timestamp updates without
   opening Settings manually beforehand.
4. Settings → BACKUP NOW → timestamp updates immediately, no snackbar error.
5. Uninstall app (or clear app data) → reinstall → sign in with the same Google account →
   RESTORE FROM BACKUP → confirm dialog appears → confirm → History/Home/Templates/Bodyweight
   all match the pre-uninstall state.
6. Turn off Wi-Fi (mobile data only) → BACKUP NOW → fails gracefully with a Snackbar, app does
   not crash, "last backup" stays at its last successful value.
7. Sign out → ACCOUNT section reverts to signed-out state; rest of the app still fully usable.
8. Attempt restore of a backup with a higher `appVersionCode` than the installed app (simulate by
   editing the uploaded JSON in Supabase Storage during testing) → clear rejection message via
   Snackbar, no partial/corrupt DB state.

If a `test`/`androidTest` source set gets introduced later, `BackupSerializer` is the first
candidate for a real unit test (serialize → deserialize round-trip equality on a seeded in-memory
Room DB) since it has no Android framework or network dependency once DAOs are faked.

---

## 7. Boundaries

**Always do:**
- Keep the app fully functional signed-out; never gate an existing feature behind auth.
- Read `SUPABASE_URL` / `SUPABASE_ANON_KEY` / `SUPABASE_GOOGLE_WEB_CLIENT_ID` from
  `local.properties` only — never commit them, never hardcode them in Kotlin source.
- Show the existing `ConfirmDialog` before any destructive restore.
- Surface every auth/backup/restore error to the user via Snackbar — never fail silently.
- Update this file's `## Progress log` in CLAUDE.md and bump `versionCode`/`versionName` when
  the feature ships, per standing project rule.

**Ask first about:**
- Whether to keep historical backup versions (multiple objects) instead of a single overwritten
  `latest.json`, if the user later wants "restore from an older backup, not just the newest."
- Whether auto-backup should also trigger on bodyweight log / template save, beyond
  session-complete, if data loss risk there turns out to matter to the user.
- Any change to the Supabase project's schema/policies beyond the single `backups` storage
  bucket described here (e.g. if the user wants richer server-side backup history later).

**Never do:**
- Never make sign-in mandatory to open the app or use any screen.
- Never implement live/background sync in v1 — this is backup-restore only, triggered by an
  explicit action or session-complete, not a continuous sync loop.
- Never store the Supabase anon key or Google Web Client ID as a hardcoded string literal.
- Never restore without going through the confirm dialog first.
- Never leave the local DB in a partially-restored state — restore is one `withTransaction` or
  it fully fails and rolls back.

---

## 8. Open items for implementation phase (not blocking spec approval)

- Exact Supabase Storage bucket RLS policy SQL (to be written and run by the user in the
  Supabase dashboard, since Claude Code has no Supabase project access).
- Whether `BackupWorker` should also retry with backoff on failure (`WorkManager` supports this
  natively via `setBackoffCriteria` — recommend `LINEAR`, 30s initial, since a failed auto-backup
  should quietly retry rather than nag the user).
