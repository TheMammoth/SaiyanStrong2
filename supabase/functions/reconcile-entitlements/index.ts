// SaiyanStrong Coach Mode — Slice 7: entitlement reconcile.
//
// Scheduled via pg_cron (see supabase/migrations/0004_reconcile_schedule.sql) to run
// daily. Compares Paddle's list of active subscriptions against Supabase profiles with
// coach_entitlement_active=true and flags any mismatch into
// entitlement_reconcile_flags — a safety net for the paddle-webhook function ever
// missing an event (network blip, Paddle retry exhaustion, etc.).
//
// This function only ever reads Paddle and writes flags — it never touches
// profiles.coach_entitlement_active itself. Fixing a flagged mismatch is a manual
// decision, not something this script does automatically.
//
// Deployed with "Verify JWT" ON (the platform default) — unlike paddle-webhook, this
// endpoint isn't called by a third party that can't send a Supabase JWT. The pg_cron
// job authenticates with the project's anon key (public by design, already embedded in
// the Android app itself — RLS, not key secrecy, is what protects data), which
// satisfies Supabase's own JWT check. No custom secret to generate, store, or rotate.
//
// Requires:
//   PADDLE_API_KEY — read-only key (saiyanstrong-reconcile), Paddle account-wide
//   SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY — auto-injected by the platform

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const PADDLE_API_KEY = Deno.env.get("PADDLE_API_KEY")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

interface PaddleSubscription {
  id: string;
  status: string;
  custom_data: { supabase_user_id?: string } | null;
}

async function fetchActivePaddleSubscriptions(): Promise<PaddleSubscription[]> {
  const subs: PaddleSubscription[] = [];
  let after: string | null = null;

  do {
    const url = new URL("https://api.paddle.com/subscriptions");
    url.searchParams.set("status", "active");
    url.searchParams.set("per_page", "100");
    if (after) url.searchParams.set("after", after);

    const res = await fetch(url, {
      headers: { Authorization: `Bearer ${PADDLE_API_KEY}` },
    });
    if (!res.ok) {
      throw new Error(`Paddle API error ${res.status}: ${await res.text()}`);
    }
    const body = await res.json();
    subs.push(...(body.data ?? []));

    const hasMore = body.meta?.pagination?.has_more === true;
    after = hasMore ? body.meta?.pagination?.next ?? null : null;
    // If Paddle reports more pages but the cursor shape doesn't match what's expected,
    // stop rather than loop forever — a partial reconcile is safer than a stuck function.
    if (hasMore && !after) break;
  } while (after);

  return subs;
}

Deno.serve(async () => {
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  let paddleSubs: PaddleSubscription[];
  try {
    paddleSubs = await fetchActivePaddleSubscriptions();
  } catch (e) {
    console.error("failed to fetch Paddle subscriptions", e);
    return new Response("paddle fetch failed", { status: 502 });
  }

  const activePaddleUserIds = new Set(
    paddleSubs
      .map((s) => s.custom_data?.supabase_user_id)
      .filter((id): id is string => !!id),
  );

  const { data: entitledProfiles, error: profilesError } = await supabase
    .from("profiles")
    .select("id, paddle_subscription_id")
    .eq("coach_entitlement_active", true);

  if (profilesError) {
    console.error("failed to read profiles", profilesError);
    return new Response("profiles read failed", { status: 500 });
  }

  const flags: { user_id: string; issue: string; details: unknown }[] = [];

  for (const userId of activePaddleUserIds) {
    if (!entitledProfiles?.some((p) => p.id === userId)) {
      flags.push({
        user_id: userId,
        issue: "paddle_active_supabase_inactive",
        details: { note: "Active Paddle subscription but profiles.coach_entitlement_active is not true" },
      });
    }
  }

  for (const profile of entitledProfiles ?? []) {
    if (!activePaddleUserIds.has(profile.id)) {
      flags.push({
        user_id: profile.id,
        issue: "supabase_active_paddle_inactive",
        details: {
          paddle_subscription_id: profile.paddle_subscription_id,
          note: "profiles.coach_entitlement_active is true but no matching active Paddle subscription found",
        },
      });
    }
  }

  if (flags.length > 0) {
    const { error: insertError } = await supabase.from("entitlement_reconcile_flags").insert(flags);
    if (insertError) {
      console.error("failed to write reconcile flags", insertError);
      return new Response("flag write failed", { status: 500 });
    }
  }

  console.info(
    `reconcile complete: ${paddleSubs.length} active Paddle subscriptions, ` +
      `${entitledProfiles?.length ?? 0} entitled Supabase profiles, ${flags.length} mismatches flagged`,
  );

  return new Response(JSON.stringify({ mismatches: flags.length }), {
    status: 200,
    headers: { "content-type": "application/json" },
  });
});
