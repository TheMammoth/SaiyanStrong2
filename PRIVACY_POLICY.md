# SaiyanStrong Privacy Policy

*Last updated: 2026-07-08*

SaiyanStrong ("the app") is a powerlifting/bodybuilding training tracker. This policy explains what
data the app collects, why, and what control you have over it.

## The short version

- **Local-first.** All your training data lives on your device by default. Nothing leaves your
  phone unless you explicitly sign in and back up.
- **No ads. No trackers. No analytics SDKs.** SaiyanStrong doesn't run any advertising network,
  behavioral analytics, or third-party tracking library. There is nothing in this app whose
  purpose is to profile you or serve you ads.
- **Optional cloud backup.** If you choose to sign in with Google, your workout data can be backed
  up to a private cloud storage bucket so you don't lose it if you lose your phone. This is opt-in
  — the app is fully functional if you never sign in.
- **Nothing is sold.** SaiyanStrong does not sell, rent, or share your data with third parties for
  advertising or marketing purposes.

## What data the app stores, and where

### On your device (always)

Everything you log in the app — workout sessions, sets (weight, reps, RPE), exercise history,
templates, bodyweight entries, and your Power Level progress — is stored in a local database on
your device using Android's Room persistence library. This data never leaves your device unless
you turn on cloud backup (below). Uninstalling the app deletes this data permanently.

The app also stores a small set of local preferences on-device (your default rest timer, which
DOTS formula you use, whether you've completed the onboarding walkthrough) using Android
DataStore. These are not sent anywhere.

### In the cloud (only if you sign in)

Signing in is done through **Google Sign-In** (via Android's Credential Manager). SaiyanStrong
receives only the basic profile info Google provides during sign-in: your email address, display
name, and profile photo URL. SaiyanStrong never sees or stores your Google password.

If you tap **Backup Now** in Settings, or after you finish a workout while signed in and connected
to Wi-Fi, the app uploads a single JSON snapshot of your local database — the same workout,
template, bodyweight, and preference data described above — to a private storage bucket hosted on
**Supabase** (the backend infrastructure provider SaiyanStrong uses for authentication and
storage). This bucket is access-controlled so that only your signed-in account can read or write
your own backup file — no other user, and no unauthenticated request, can access it.

Restoring a backup downloads that same file back to your device and replaces your local database
with it, after you confirm the action.

## What the app does *not* do

- It does not run ads or ad SDKs.
- It does not use analytics, crash-reporting, or behavioral-tracking SDKs.
- It does not access your contacts, location, camera, microphone, SMS, or call log — the app
  never requests any of those permissions.
- It does not read or write files outside its own private app storage.
- It does not share your data with data brokers or advertisers.

## Permissions the app requests, and why

| Permission | Why |
|---|---|
| Internet access | Required for cloud backup/restore, Google Sign-In, and (GitHub-distributed builds only) checking for app updates and loading exercise reference photos. |
| Background work scheduling (via WorkManager) | Used only to run the optional auto-backup job after a workout finishes, and only when you're signed in. |
| Install unknown apps *(GitHub-distributed build only)* | Used only by the in-app updater to install a new APK version you've explicitly downloaded from this app's GitHub Releases page. **This permission is not present in the Play Store build of the app at all** — the Play version relies entirely on Play Store's own update mechanism. |

## Your choices and control

- **Don't want cloud backup?** Don't sign in. The app works fully offline and nothing is ever
  uploaded.
- **Want to stop backing up?** Sign out from Settings at any time; no further automatic backups
  will be made.
- **Want your local data gone?** Uninstall the app — the local database is deleted with it.
- **Want your cloud backup deleted?** Contact us (see below) and we will delete the backup file
  associated with your account. We're working on adding a self-service "delete my cloud data"
  option directly in the app.

## Children's privacy

SaiyanStrong is a general-audience fitness tracking tool and is not directed at children. We do
not knowingly collect data from children under the age required by applicable law in your region.

## Changes to this policy

If this policy changes, the "Last updated" date at the top of this file will change accordingly.
Material changes will also be reflected in the app's release notes.

## Contact

Questions, requests, or concerns about your data can be raised via the project's GitHub Issues
page: https://github.com/TheMammoth/SaiyanStrong2/issues
