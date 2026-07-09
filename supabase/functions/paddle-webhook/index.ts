// SaiyanStrong Coach Mode — Paddle webhook → Supabase entitlement sync.
//
// Deployed as a Supabase Edge Function at /functions/v1/paddle-webhook, registered as
// the webhook destination for the "SaiyanStrong Coach" Paddle product (subscription.
// activated/updated/canceled/past_due events only — see the Paddle notification
// destination "SaiyanStrong Coach entitlement sync").
//
// Requires two secrets (set via `supabase secrets set` or the dashboard, never committed):
//   PADDLE_WEBHOOK_SECRET   — this destination's signing secret (starts with pdl_ntfset_)
//   SUPABASE_SERVICE_ROLE_KEY, SUPABASE_URL — auto-injected by the Supabase platform
//
// Payment reliability rule: after writing the entitlement, re-query the row and verify
// it actually changed before returning success — never trust the write blindly.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const PADDLE_WEBHOOK_SECRET = Deno.env.get("PADDLE_WEBHOOK_SECRET")!;
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const ACTIVE_STATUSES = new Set(["active", "trialing"]);
const RELEVANT_EVENTS = new Set([
  "subscription.activated",
  "subscription.updated",
  "subscription.canceled",
  "subscription.past_due",
]);

async function verifyPaddleSignature(rawBody: string, header: string | null, secret: string): Promise<boolean> {
  if (!header) return false;
  const parts = Object.fromEntries(header.split(";").map((p) => p.split("=") as [string, string]));
  const ts = parts["ts"];
  const h1 = parts["h1"];
  if (!ts || !h1) return false;

  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signatureBuffer = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${ts}:${rawBody}`),
  );
  const computed = Array.from(new Uint8Array(signatureBuffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");

  if (computed.length !== h1.length) return false;
  let diff = 0;
  for (let i = 0; i < computed.length; i++) diff |= computed.charCodeAt(i) ^ h1.charCodeAt(i);
  return diff === 0;
}

Deno.serve(async (req) => {
  const rawBody = await req.text();
  const signatureHeader = req.headers.get("Paddle-Signature");

  if (!(await verifyPaddleSignature(rawBody, signatureHeader, PADDLE_WEBHOOK_SECRET))) {
    return new Response("invalid signature", { status: 401 });
  }

  const event = JSON.parse(rawBody);
  const eventType = event.event_type as string;

  if (!RELEVANT_EVENTS.has(eventType)) {
    return new Response("ignored", { status: 200 });
  }

  const data = event.data;
  const supabaseUserId = data?.custom_data?.supabase_user_id as string | undefined;
  if (!supabaseUserId) {
    return new Response("missing custom_data.supabase_user_id", { status: 400 });
  }

  const isActive = ACTIVE_STATUSES.has(data.status);
  const expiresAt = data.current_billing_period?.ends_at ?? null;
  const subscriptionId = data.id as string;
  const customerId = data.customer_id as string;

  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  const { error: updateError } = await supabase
    .from("profiles")
    .update({
      role: isActive ? "coach" : "free",
      coach_entitlement_active: isActive,
      coach_entitlement_expires_at: expiresAt,
      paddle_subscription_id: subscriptionId,
      paddle_customer_id: customerId,
      updated_at: new Date().toISOString(),
    })
    .eq("id", supabaseUserId);

  if (updateError) {
    console.error("profiles update failed", updateError);
    return new Response("update failed", { status: 500 });
  }

  // Never trust the write blindly — re-query and verify it actually landed.
  const { data: verifyRow, error: verifyError } = await supabase
    .from("profiles")
    .select("coach_entitlement_active, paddle_subscription_id")
    .eq("id", supabaseUserId)
    .single();

  if (verifyError || !verifyRow) {
    console.error("post-write verification query failed", verifyError);
    return new Response("verification failed", { status: 500 });
  }
  if (verifyRow.coach_entitlement_active !== isActive || verifyRow.paddle_subscription_id !== subscriptionId) {
    console.error("post-write verification mismatch", { expected: { isActive, subscriptionId }, got: verifyRow });
    return new Response("verification mismatch", { status: 500 });
  }

  return new Response("ok", { status: 200 });
});
