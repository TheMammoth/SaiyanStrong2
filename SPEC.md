# SaiyanStrong — Sprint 8 Spec: Icon Polish + Updater Hardening

## Status: COMPLETE ✓  (v0.6.1 – v0.6.4)

---

## 1. Icon gradient background

**Problem:** App icon had a solid black (#0D0D0D) background.

**Fix:**
- Created `res/drawable/ic_launcher_background.xml` — 135° linear gradient:
  `#FFD600` (SSJ gold) → `#FF8F00` (amber) → `#E64A19` (Goku orange)
- Both `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` now
  reference `@drawable/ic_launcher_background` instead of `@color/...`
- Fallback `ic_launcher_background` color updated to `#E64A19`

**Problem:** Gradient was invisible because `ic_launcher_foreground.png` had
the black background baked in as pixels, covering the gradient layer.

**Fix:** PowerShell BFS flood-fill from all 4 corners (threshold < 40 per
channel) made background pixels transparent in all 5 density PNGs. The barbell
and POWER:9001 scouter are preserved and now float on the gradient.

---

## 2. In-app updater hardening

### 2a. Retry on network failure
`checkForUpdate()` in `HomeViewModel` now retries at 0s / 5s / 15s if the
GitHub API call returns null. Covers the common case where Android hasn't
finished connecting to WiFi when the app launches.

### 2b. User-Agent header (root cause fix)
GitHub's REST API returns **403 Forbidden** for requests missing a `User-Agent`
header. Our `HttpURLConnection` didn't send one. The `catch (_: Exception)`
swallowed the resulting error, so the banner never appeared.

**Fix:** Added `setRequestProperty("User-Agent", "SaiyanStrong-Android")` to
`CheckForUpdateUseCase`.

### 2c. Version visible in UI
`BuildConfig.VERSION_NAME` appended to HomeScreen telemetry bar:
```
// POWER LEVEL: 14500  |  v0.6.4 //
```
Lets you confirm at a glance which build is running.

### 2d. versionName / versionCode discipline
- `versionName` must equal the release tag (without "v") in every build
- `versionCode` increments by 1 per release
- The updater comparison `tagName.removePrefix("v") == VERSION_NAME` only
  works correctly when these are kept in sync

---

## 3. Release process (established rule)

1. Make changes, build: `.\gradlew assembleDebug`
2. Commit + push
3. `gh release create vX.Y.Z --title "..." --notes "..."`
4. `cp app/build/outputs/apk/debug/app-debug.apk SaiyanStrong-vX.Y.Z-debug.apk`
5. `gh release upload vX.Y.Z SaiyanStrong-vX.Y.Z-debug.apk --clobber`
6. `rm SaiyanStrong-vX.Y.Z-debug.apk`

Do **not** wait for CI — upload immediately from local build.

---

## 4. Sprint 9 backlog

- [ ] Swap flame `Icon` placeholder for real Lottie animation (when `flame_loop.json` lands)
- [ ] Strength progress line chart on SessionCompleteScreen (Canvas, historical 1RM trend)
- [ ] VisualizerScreen permanent removal or re-integration decision
- [ ] Exercise detail screen (tap exercise name → all-time history for that lift)
- [ ] Notification for rest timer (so screen can be locked during rest)
