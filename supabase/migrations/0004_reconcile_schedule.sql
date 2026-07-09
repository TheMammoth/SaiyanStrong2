-- SaiyanStrong Coach Mode — Slice 7: schedule the entitlement reconcile function.
--
-- Runs reconcile-entitlements daily via pg_cron + pg_net. Authenticates with the
-- project's anon key — safe to commit: the anon key is public by design (it ships
-- inside the compiled Android app itself), and reconcile-entitlements is deployed with
-- Supabase's standard "Verify JWT" gate ON, which the anon key satisfies. RLS, not key
-- secrecy, is what protects the actual data; this function only ever reads Paddle and
-- writes to entitlement_reconcile_flags (service-role-only, see 0003) using its own
-- service role key from its environment, never anything derived from this anon key.

create extension if not exists pg_cron with schema extensions;
create extension if not exists pg_net with schema extensions;

select cron.schedule(
  'reconcile-entitlements-daily',
  '0 3 * * *', -- 03:00 UTC daily, low-traffic window
  $$
  select net.http_post(
    url := 'https://hievuesvzojtlvifidel.supabase.co/functions/v1/reconcile-entitlements',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'apikey', 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhpZXZ1ZXN2em9qdGx2aWZpZGVsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIxOTUzNzEsImV4cCI6MjA5Nzc3MTM3MX0.5-oLN49WzzZ3MFbhGB-A9_L-fPGS3wUbG5Ts5qvDknU',
      'Authorization', 'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhpZXZ1ZXN2em9qdGx2aWZpZGVsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODIxOTUzNzEsImV4cCI6MjA5Nzc3MTM3MX0.5-oLN49WzzZ3MFbhGB-A9_L-fPGS3wUbG5Ts5qvDknU'
    ),
    body := '{}'::jsonb
  );
  $$
);
