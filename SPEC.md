# SaiyanStrong — Coach Mode Spec

## Status: DRAFT — awaiting confirmation before any code changes

---

## 0. Decisions locked in via clarifying questions

- **Entitlement pattern**: designed fresh (no access to Zona X's actual code), matching the
  shape described — a `profiles.role` column + **one** `is_coach()` check, enforced at the
  database level via RLS (not just app-side), never re-implemented inline anywhere.
- **Webhook hosting**: a Supabase Edge Function (Deno), co-located with the entitlements table.
- **Paddle product setup**: done together via browser automation against your logged-in Paddle
  dashboard, same pattern as the Supabase/Google Cloud setup earlier in this project.
- **Reconcile script**: a scheduled Supabase Edge Function (cron), not a manual script.

---

## 1. Objective

Add a paid "Coach" tier: a signed-in user can become a coach, invite athletes (existing regular
users) to link their account, see an aggregated dashboard of those athletes' training, view an
athlete's history/records/charts read-only, and push workout templates to them. Monetized via a
~€12/month Paddle subscription purchased on the web — **the app never processes payment**, it only
reads an entitlement flag that a webhook keeps in sync.

**Target users**: personal trainers / coaches who already use SaiyanStrong themselves and want to
manage multiple athletes from one place, plus their athletes (regular SaiyanStrong users who opt
in to being coached).

---

## 2. Core features & acceptance criteria

### 2.1 Entitlement — single source of truth
- New Postgres table `profiles` (one row per `auth.users` id): `role` (`'athlete'` | `'coach'`),
  `coach_entitlement_active`, `coach_entitlement_expires_at`, `paddle_subscription_id`,
  `paddle_customer_id`.
- New Postgres function `is_coach(uid uuid) returns boolean` — the **only** place entitlement
  logic is expressed. Used directly inside RLS policies for every coach-only table/action (invite
  code creation, template push), so the database itself enforces it, not just the app.
- Client-side mirror: one `CoachEntitlementRepository` + `IsCoachUseCase`, exposed as
  `StateFlow<Boolean>`. Every screen/ViewModel that needs to gate coach UI calls this one use
  case — **no screen re-implements the check.**
- **Acceptance**: revoking `coach_entitlement_active` in the database (simulating subscription
  expiry) immediately blocks coach-only RLS actions server-side, independent of what the client
  app believes or caches.

### 2.2 Athlete linking (invite code + explicit consent)
- Coach generates an invite code (`coach_invite_codes` table: short human-typeable code, tied to
  `coach_id`, revocable, optional expiry).
- Athlete enters the code in-app → sees an **explicit consent screen** naming the coach and
  stating exactly what becomes visible (their workout history, exercise records, charts, weekly
  volume, Power Level — not live location, not real-time, just their existing backup snapshot)
  before anything links.
- On accept: a redeem RPC (`redeem_invite_code(code)`, `security definer`) validates the code and
  inserts a row into `coach_athletes` (`coach_id`, `athlete_id`, `status='active'`). Athletes never
  get direct table access to `coach_invite_codes` — redemption only happens through this RPC, so
  codes can't be enumerated by querying the table directly.
- **Revocable**: Settings → "Linked Coaches" (athlete side) lists active links with a REVOKE
  button (sets `status='revoked'`) — reuses the existing `ConfirmDialog` component. The moment
  status flips, the coach's read access to that athlete's backup is gone (see 2.4).
- **Acceptance**: an athlete who never entered a code stays completely invisible to every coach;
  revoking a link immediately removes the coach's ability to read that athlete's backup file.

### 2.3 Coach dashboard
- New `CoachDashboardScreen`: list of linked athletes, each row showing last-session date, this
  week's volume, current Power Level + Saiyan stage, and a red-flag indicator (amber/red,
  `DangerRed` token) when no session in 7+ days.
- **Data source is the existing backup mechanism, not a new live-sync table**: for each linked
  athlete, the coach's client downloads `backups/{athleteId}/latest.json` (permitted only via a
  new storage RLS policy gated on an active `coach_athletes` row — see 2.4) and decodes it with
  the **existing** `BackupSerializer.decode()` from the auth/backup sprint. Dashboard stats
  (weekly volume, last session, Power Level/stage) are computed by reusing the **existing** pure
  calculation logic (`CalculatePowerLevelUseCase`, the week-stats logic already in
  `HomeViewModel`) against the decoded payload instead of local Room DAOs — no duplicate stats
  logic, no new "live session" Postgres table.
- Freshness caveat this implies and that the UI must be honest about: dashboard data is only as
  current as the athlete's last successful backup (which happens after each completed session on
  Wi-Fi, or on manual Backup Now) — not real-time. Label it accordingly (e.g. "as of &lt;backup
  timestamp&gt;") rather than implying live sync.
- **Acceptance**: an athlete who hasn't opened the app in 8 days shows the red flag; one active
  yesterday does not.

### 2.4 Read-only athlete detail view
- Tapping an athlete opens a read-only screen reusing the same visual language as
  `ExerciseDetailScreen`'s CHARTS/RECORDS tabs and `HistoryScreen`'s session cards, but sourced
  from the decoded backup payload (in-memory) instead of Room DAOs. No local persistence of
  athlete data on the coach's device beyond an in-memory cache for the session.
- New storage RLS policy on the `backups` bucket: a coach may `select` (read-only —
  **no insert/update**) `{athleteId}/latest.json` only where an `coach_athletes` row exists with
  `coach_id = auth.uid() and athlete_id = {athleteId} and status = 'active'`.
- **Acceptance**: a coach cannot read any athlete's backup they aren't actively linked to, even by
  guessing/crafting the storage path — enforced by Postgres RLS, not app-side trust.

### 2.5 Template push
- Coach builds or selects a template (reusing the existing `WorkoutTemplate` exercise-list shape)
  and pushes it to a specific linked athlete → new row in `coach_pushed_templates`
  (`coach_id`, `athlete_id`, `name`, `exercise_ids`, `accepted=false`).
- Athlete's `WorkoutLandingScreen` "MY TEMPLATES" grid shows pushed-but-not-yet-accepted templates
  with a **"FROM COACH"** badge (`PowerAmber` token, matching the app's existing badge language).
  Tapping it copies the template into the athlete's local Room `templates` table (via the existing
  `TemplateRepository.saveTemplate`) and marks `accepted=true` — after that it behaves exactly
  like any other local template.
- **Acceptance**: a pushed template never silently appears in the athlete's local template list
  without them tapping to accept it first.

### 2.6 Payments (Paddle, web-only)
- A static checkout page (new `paddle/coach-checkout.html`, or hosted the same way as the privacy
  policy) using Paddle.js for the "SaiyanStrong Coach" (~€12/month) product/price created in your
  Paddle dashboard.
- In-app: Settings → "Coach Mode" section shows either "BECOME A COACH →" (opens the checkout page
  in a Custom Tab, github flavor) or, on the **play flavor**, see the risk flagged in Boundaries
  §7 below — this is **not** settled yet and needs your call before building it.
- The app **never** touches Paddle's API or card details directly — it only ever reads
  `profiles.coach_entitlement_active`.

### 2.7 Webhook + reconciliation (Supabase Edge Functions)
- `supabase/functions/paddle-webhook/index.ts`: receives Paddle subscription events
  (`subscription.created`, `.updated`, `.canceled`), **verifies the Paddle webhook signature**,
  updates the matching `profiles` row's entitlement fields, then — per your stated Zona X rule —
  **re-queries that row after the write and verifies it actually changed** before returning 200 to
  Paddle (so a silent no-op write, e.g. wrong customer-id match, gets caught and logged/alerted
  rather than swallowed).
- `supabase/functions/reconcile-entitlements/index.ts`: scheduled (pg_cron or Supabase Scheduled
  Functions, e.g. hourly), calls the Paddle API for all active subscriptions for the "SaiyanStrong
  Coach" product, compares against `profiles` rows with `coach_entitlement_active=true`, and
  corrects/logs any drift in either direction (entitled-but-not-subscribed, or
  subscribed-but-not-entitled).
- **Acceptance**: manually flipping a Paddle test subscription's status gets reflected in
  `profiles` within one webhook round-trip; deliberately corrupting a `profiles` row out-of-band
  gets self-healed by the next reconcile run.

---

## 3. Tech stack additions

| Addition | Purpose |
|---|---|
| Supabase Postgres tables: `profiles`, `coach_invite_codes`, `coach_athletes`, `coach_pushed_templates` | Relational data this feature needs beyond the existing JSON-backup mechanism |
| Postgres function `is_coach(uuid)` + RPC `redeem_invite_code(text)` | Single-source-of-truth entitlement check + safe code redemption without exposing the codes table |
| New storage RLS policy on the existing `backups` bucket | Coach read access to a linked athlete's backup, scoped to active consent |
| Supabase Edge Functions (Deno/TypeScript): `paddle-webhook`, `reconcile-entitlements` | The only backend/server code this project will have — everything else is client + Supabase managed services |
| Paddle.js checkout page (static HTML, hosted alongside `PRIVACY_POLICY.md`) | Out-of-app payment, per your instruction that the app never processes payment |
| No new Android dependencies | Coach screens reuse existing Compose components/patterns; androidx.browser (Custom Tabs) may be needed to open the checkout page cleanly — small, standard addition if so |

---

## 4. Project structure (new/changed, client side)

```
app/src/main/java/com/saiyanstrong/
├── domain/
│   ├── model/
│   │   ├── CoachProfile.kt            ← new
│   │   ├── AthleteSummary.kt          ← new (dashboard row: id, name/email, lastSessionAtMs,
│   │   │                                 weeklyVolumeKg, powerLevel, stage, isStale)
│   │   ├── InviteCode.kt              ← new
│   │   └── CoachTemplate.kt           ← new
│   ├── repository/
│   │   └── CoachRepository.kt         ← new (entitlement, invite codes, athlete list + detail,
│   │                                     template push) — one interface, keeps ViewModels off
│   │                                     the data layer per existing Clean Architecture rule
│   └── usecase/
│       ├── IsCoachUseCase.kt          ← new — THE single entitlement check
│       ├── GenerateInviteCodeUseCase.kt
│       ├── RedeemInviteCodeUseCase.kt
│       ├── RevokeCoachLinkUseCase.kt
│       ├── GetAthleteDashboardUseCase.kt   ← downloads+decodes athlete backups, computes stats
│       │                                     by reusing CalculatePowerLevelUseCase etc.
│       └── PushTemplateToAthleteUseCase.kt
│
├── data/
│   └── repository/
│       └── CoachRepositoryImpl.kt     ← new — Supabase Postgres queries/RPCs + reuses
│                                         BackupSerializer.decode() for athlete payloads
│
└── presentation/screens/coach/
    ├── CoachSettingsScreen.kt         ← "Become a coach" / already-a-coach state, in Settings
    ├── CoachDashboardScreen.kt        ← athlete list, new bottom-tab-adjacent entry point only
    │                                     visible when IsCoachUseCase is true
    ├── InviteCodeScreen.kt            ← coach: generate/share code
    ├── RedeemCodeScreen.kt            ← athlete: enter code
    ├── CoachConsentScreen.kt          ← athlete: explicit consent before linking
    ├── AthleteDetailScreen.kt         ← coach: read-only history/records/charts
    └── TemplatePushScreen.kt          ← coach: build/select template, pick athlete, push

supabase/
├── migrations/
│   └── 0001_coach_mode.sql            ← new tables, is_coach(), redeem_invite_code(), RLS
└── functions/
    ├── paddle-webhook/index.ts        ← new
    └── reconcile-entitlements/index.ts ← new

paddle/
└── coach-checkout.html                ← new, static Paddle.js checkout page
```

No changes to the existing Room schema — Coach mode is entirely additive on top of the existing
local-first architecture; athlete-side local data is untouched except for template acceptance
(reuses `TemplateRepository.saveTemplate`, already exists).

---

## 5. Code style (extends existing CLAUDE.md rules)

- Same Clean Architecture layering as the rest of the app: Coach screens → Coach ViewModels →
  use cases → `CoachRepository` interface; `CoachRepositoryImpl` lives in `data/` and is never
  imported directly by presentation code.
- `IsCoachUseCase` is the **only** entitlement check anywhere in the client — grep for
  `role.*coach` or `coach_entitlement` outside `IsCoachUseCase`/`CoachRepositoryImpl` should
  return nothing once this ships.
- All new Compose screens use existing tokens only (`NeonGreen`/`PowerAmber`/`DangerRed`/
  `SaiyanGray`/`TelemetryGreen`/`MatteBlack`) — the red-flag indicator uses `DangerRed`, the "FROM
  COACH" badge uses `PowerAmber`, matching how badges/warnings already read elsewhere in the app.
- `Result<T>` for every suspend Coach operation, same convention established in the auth/backup
  sprint.
- SQL migrations are plain, reviewable `.sql` files under `supabase/migrations/`, applied via the
  Supabase SQL Editor the same way the `backups` bucket RLS was — no ORM/ migration framework
  introduced.
- Edge Functions: plain Deno/TypeScript, no framework — verify the Paddle webhook signature using
  Paddle's documented HMAC scheme before trusting any payload.

---

## 6. Testing strategy

Same reality as the rest of this project: no `app/src/test` source set, no device access this
session. Verification is build success + manual QA checklists, plus these Coach-specific checks:

**Database/RLS (run directly in Supabase SQL Editor, no app needed):**
1. As a non-coach user, attempt to insert into `coach_invite_codes` directly → rejected by RLS.
2. As athlete A, attempt to `select` `backups/{athleteB}/latest.json` with no `coach_athletes` row
   → rejected.
3. Link A→coach C, then read `backups/{A}/latest.json` as C → succeeds. Revoke the link → same
   read now fails.
4. Flip `profiles.coach_entitlement_active` to false for C → C can no longer insert invite codes
   or push templates, even mid-session.

**Manual app QA (needs a device — flagged honestly, not assumed done):**
1. Two test accounts: one becomes a coach (manually flip entitlement in the DB for testing, since
   real Paddle checkout needs a live test transaction), one stays an athlete.
2. Coach generates a code; athlete redeems it and sees the consent screen before anything links.
3. Coach dashboard shows the athlete with correct last-session/volume/stage after the athlete logs
   a workout and backs up.
4. Athlete lets 7+ days pass (or the test manipulates `latest.json`'s embedded timestamp) → red
   flag appears.
5. Coach pushes a template → athlete sees "FROM COACH" badge → accepts → template behaves
   normally afterward.
6. Athlete revokes the coach link → coach dashboard no longer shows that athlete (or shows it as
   inaccessible) on next refresh.
7. Paddle test-mode subscription created/canceled → webhook updates `profiles` → app reflects the
   change without a manual DB edit.

---

## 7. Boundaries

**Always do:**
- Enforce entitlement and consent **in the database via RLS**, never trust the client alone.
- Show the explicit consent screen before any athlete-coach link is created — no silent linking.
- Keep the "as of &lt;timestamp&gt;" framing on coach-viewed athlete data — never imply live sync.
- Verify the Paddle webhook signature before trusting any webhook payload.
- Re-query and verify after every entitlement write in the webhook handler (your stated rule).

**Ask first about:**
- **⚠️ Play Store payments policy — this is a real open risk, not a formality.** Google Play's
  Payments policy generally requires Google Play Billing for subscriptions that unlock
  functionality *within* an app distributed via Play, with only narrow exceptions (e.g. "reader
  apps," specific multi-platform allowances, or regional User Choice Billing pilots). Simply
  showing "manage on web" instead of a purchase button on the play flavor does **not** by itself
  make an external-payment-unlocked feature compliant — the risk is that Play could reject or
  later remove the *entire app* for policy violation, not just flag the Coach feature. Before
  building the play-flavor Coach entry point as "link out to web," I'd want your explicit call on
  one of: (a) Play flavor hides Coach mode's purchase path entirely and only *displays* Coach
  features if entitlement was already granted via another channel (closer to how some
  subscription apps handle Play compliance — no purchase surface in the Play build at all), or
  (b) eventually implement real Google Play Billing for the Play channel specifically, or (c) you
  research/confirm your specific case qualifies for a policy exception. I am not qualified to make
  this legal/policy call for you, and getting it wrong risks the whole app's Play listing, not
  just this feature — so this is a real blocker for the play-flavor half of §2.6, not the
  github-flavor half.
- Paddle price/currency/trial details beyond "~€12/month" (exact price, trial period, annual
  option) — confirm before creating the live product.
- Whether coach dashboard data should eventually become near-real-time (e.g., athlete
  auto-backing-up more aggressively when coached) — out of scope for this spec, flagged as a
  possible v2.

**Never do:**
- Never let the client-side entitlement check be the only gate — RLS enforces it too.
- Never expose the `coach_invite_codes` table to direct client SELECT — redemption only through
  the `redeem_invite_code` RPC.
- Never let a coach read/write an athlete's backup without an active, athlete-initiated
  `coach_athletes` link.
- Never process a card number or payment detail inside the Android app.
- Never skip the webhook signature verification, even in a rush.

---

## 8. Suggested incremental slices (per your "commits per slice" instruction)

1. Supabase schema: `profiles`, `coach_invite_codes`, `coach_athletes`, `coach_pushed_templates`,
   `is_coach()`, `redeem_invite_code()`, RLS policies, new `backups` bucket coach-read policy —
   pure SQL, no app code yet, verified via the SQL Editor test checklist in §6.
2. Client entitlement plumbing: `IsCoachUseCase`, `CoachRepository`/`Impl` (entitlement query
   only), Settings "Coach Mode" section showing current status (no purchase flow yet — that's
   gated on the Play-policy decision above).
3. Invite + consent + link: generate/redeem code, consent screen, `coach_athletes` link,
   Settings "Linked Coaches" revoke UI.
4. Coach dashboard: athlete list, stats via decoded backups, red-flag logic.
5. Read-only athlete detail view (history/records/charts).
6. Template push + athlete-side "FROM COACH" acceptance flow.
7. Paddle checkout page + webhook Edge Function + reconcile Edge Function (built together since
   they share the entitlement-write logic) — this slice is where the Play-policy decision must
   already be settled.

Each slice is independently buildable/testable and gets its own commit, per your instruction.
