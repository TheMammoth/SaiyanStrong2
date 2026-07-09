-- SaiyanStrong Coach Mode — Slice 7: reconcile flags table.
--
-- Internal ops table only — no RLS policies at all (RLS enabled, zero grants), so the
-- only way to read or write it is the service role key, which bypasses RLS entirely.
-- Regular users, including coaches, never see this table; it exists purely so a human
-- can inspect drift between Paddle and Supabase after the scheduled reconcile function
-- runs, via the SQL Editor (which also connects as a role that bypasses RLS).

create table if not exists public.entitlement_reconcile_flags (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.profiles(id) on delete set null,
  issue text not null,
  details jsonb,
  created_at timestamptz not null default now()
);

alter table public.entitlement_reconcile_flags enable row level security;
-- Intentionally no policies — default-deny for every role except service_role.
