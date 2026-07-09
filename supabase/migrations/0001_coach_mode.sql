-- SaiyanStrong Coach Mode — Slice 1: schema + RLS.
-- Run once in the Supabase SQL Editor for the barbell-io project
-- (https://hievuesvzojtlvifidel.supabase.co). Pure SQL, no app code depends on this yet.
--
-- IMPORTANT — read before modifying: this Supabase project is NOT dedicated solely to
-- SaiyanStrong. It already contained a `profiles` table (Supabase's standard "User
-- Management" quickstart shape: id/display_name/role default 'free'/created_at, with a
-- handle_new_user() trigger on auth.users) plus five other empty tables — athletes,
-- custom_exercises, gym_sessions, training_plans, workout_sessions — that this migration
-- does NOT touch at all. Their origin is unconfirmed (possibly leftover scaffolding from
-- an earlier, unrelated session); all were empty (0 rows) when discovered, so there was
-- no data-loss risk, but their *purpose* is still unknown, so Coach Mode only ever
-- extends `profiles` additively and never renames/repurposes anything that was already
-- there. If those five tables turn out to matter for something else, this migration
-- doesn't conflict with them — it never references them.
--
-- Design notes (read before modifying):
--   * profiles already existed with an overly-permissive "own profile - update" RLS
--     policy (using/with check: auth.uid() = id, no column restrictions) — meaning any
--     signed-in user could already set role='admin' on themselves directly via the
--     client, independent of anything in this migration. That policy is dropped below.
--     role / coach_entitlement_* / paddle_* now have NO client-writable path at all —
--     only the paddle-webhook Edge Function (service role key, bypasses RLS) writes them.
--   * coach_athletes and coach_pushed_templates each have a BEFORE UPDATE trigger that
--     enforces the ONE transition the non-owning party (athlete) is allowed to make
--     (revoke a link / accept a template) and rejects anything else, including
--     reassigning coach_id — RLS's using/with check alone can't express "this column
--     must not change" across old vs new rows, so a trigger does that part.
--   * coach_invite_codes is never exposed to athlete SELECT — redemption only happens
--     through redeem_invite_code(), a security-definer RPC, so codes can't be
--     enumerated by querying the table directly.

-- ── profiles (pre-existing — extended additively, not recreated) ───────────────
alter table public.profiles add column if not exists coach_entitlement_active boolean not null default false;
alter table public.profiles add column if not exists coach_entitlement_expires_at timestamptz;
alter table public.profiles add column if not exists paddle_subscription_id text;
alter table public.profiles add column if not exists paddle_customer_id text;
alter table public.profiles add column if not exists updated_at timestamptz not null default now();

-- Existing constraint only allowed ('free','premium','admin'); add 'coach' without
-- removing anything already in use by whatever else may reference this table.
alter table public.profiles drop constraint if exists profiles_role_check;
alter table public.profiles add constraint profiles_role_check
  check (role = any (array['free', 'premium', 'admin', 'coach']::text[]));

-- Close the pre-existing self-elevation hole described above. profiles becomes
-- read-only from the client (webhook uses the service role key, which bypasses RLS).
drop policy if exists "own profile - update" on public.profiles;
-- "own profile - select" (pre-existing) is left as-is — harmless, unrelated to entitlement.

-- handle_new_user()/on_auth_user_created (pre-existing) already does
-- `insert into public.profiles (id) values (new.id)`, which is fully compatible with
-- the new nullable/defaulted columns above — intentionally left untouched.

-- ── is_coach(): the single entitlement check, used everywhere ──────────────────
create or replace function public.is_coach(uid uuid)
returns boolean
language sql
security definer
stable
set search_path = public
as $$
  select exists (
    select 1 from public.profiles
    where id = uid
      and role = 'coach'
      and coach_entitlement_active = true
      and (coach_entitlement_expires_at is null or coach_entitlement_expires_at > now())
  );
$$;

grant execute on function public.is_coach(uuid) to authenticated;

-- ── coach_invite_codes ───────────────────────────────────────────────────────
create table if not exists public.coach_invite_codes (
  code text primary key,
  coach_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz,
  revoked boolean not null default false
);

alter table public.coach_invite_codes enable row level security;

create policy "invite_codes_coach_select_own"
on public.coach_invite_codes for select
to authenticated
using (coach_id = auth.uid());

create policy "invite_codes_coach_insert"
on public.coach_invite_codes for insert
to authenticated
with check (coach_id = auth.uid() and public.is_coach(auth.uid()));

create policy "invite_codes_coach_update_own"
on public.coach_invite_codes for update
to authenticated
using (coach_id = auth.uid())
with check (coach_id = auth.uid());

-- ── coach_athletes ───────────────────────────────────────────────────────────
create table if not exists public.coach_athletes (
  id uuid primary key default gen_random_uuid(),
  coach_id uuid not null references public.profiles(id) on delete cascade,
  athlete_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'active' check (status in ('active', 'revoked')),
  linked_at timestamptz not null default now(),
  revoked_at timestamptz,
  unique (coach_id, athlete_id)
);

alter table public.coach_athletes enable row level security;

create policy "coach_athletes_coach_select"
on public.coach_athletes for select
to authenticated
using (coach_id = auth.uid());

create policy "coach_athletes_athlete_select"
on public.coach_athletes for select
to authenticated
using (athlete_id = auth.uid());

-- Athletes may attempt to update only their own rows; the trigger below restricts
-- WHAT they're allowed to change (revoke only, nothing else).
create policy "coach_athletes_athlete_update"
on public.coach_athletes for update
to authenticated
using (athlete_id = auth.uid());

create or replace function public.enforce_coach_athletes_athlete_revoke_only()
returns trigger
language plpgsql
as $$
begin
  if auth.uid() = old.athlete_id then
    if new.coach_id <> old.coach_id or new.athlete_id <> old.athlete_id then
      raise exception 'athletes may not reassign a coach link';
    end if;
    if old.status <> 'active' or new.status <> 'revoked' then
      raise exception 'athletes may only revoke an active link';
    end if;
    new.revoked_at := now();
  end if;
  return new;
end;
$$;

drop trigger if exists coach_athletes_before_update on public.coach_athletes;
create trigger coach_athletes_before_update
  before update on public.coach_athletes
  for each row execute function public.enforce_coach_athletes_athlete_revoke_only();

-- ── redeem_invite_code(): the only way a coach_athletes row gets created ────────
create or replace function public.redeem_invite_code(invite_code text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_coach_id uuid;
begin
  select coach_id into v_coach_id
  from public.coach_invite_codes
  where code = invite_code
    and revoked = false
    and (expires_at is null or expires_at > now());

  if v_coach_id is null then
    return false;
  end if;

  if v_coach_id = auth.uid() then
    return false; -- can't coach yourself
  end if;

  insert into public.coach_athletes (coach_id, athlete_id, status)
  values (v_coach_id, auth.uid(), 'active')
  on conflict (coach_id, athlete_id)
  do update set status = 'active', revoked_at = null, linked_at = now();

  return true;
end;
$$;

grant execute on function public.redeem_invite_code(text) to authenticated;

-- ── coach_pushed_templates ───────────────────────────────────────────────────
create table if not exists public.coach_pushed_templates (
  id uuid primary key default gen_random_uuid(),
  coach_id uuid not null references public.profiles(id) on delete cascade,
  athlete_id uuid not null references public.profiles(id) on delete cascade,
  name text not null,
  exercise_ids integer[] not null,
  created_at timestamptz not null default now(),
  accepted boolean not null default false
);

alter table public.coach_pushed_templates enable row level security;

create policy "pushed_templates_coach_select"
on public.coach_pushed_templates for select
to authenticated
using (coach_id = auth.uid());

create policy "pushed_templates_coach_insert"
on public.coach_pushed_templates for insert
to authenticated
with check (
  coach_id = auth.uid()
  and public.is_coach(auth.uid())
  and exists (
    select 1 from public.coach_athletes ca
    where ca.coach_id = auth.uid()
      and ca.athlete_id = coach_pushed_templates.athlete_id
      and ca.status = 'active'
  )
);

create policy "pushed_templates_athlete_select"
on public.coach_pushed_templates for select
to authenticated
using (athlete_id = auth.uid());

-- Athletes may attempt to update only their own rows; the trigger below restricts
-- WHAT they're allowed to change (accept only, nothing else).
create policy "pushed_templates_athlete_update"
on public.coach_pushed_templates for update
to authenticated
using (athlete_id = auth.uid());

create or replace function public.enforce_pushed_templates_athlete_accept_only()
returns trigger
language plpgsql
as $$
begin
  if auth.uid() = old.athlete_id then
    if new.coach_id <> old.coach_id
       or new.athlete_id <> old.athlete_id
       or new.name <> old.name
       or new.exercise_ids <> old.exercise_ids then
      raise exception 'athletes may only accept a pushed template, not modify it';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists pushed_templates_before_update on public.coach_pushed_templates;
create trigger pushed_templates_before_update
  before update on public.coach_pushed_templates
  for each row execute function public.enforce_pushed_templates_athlete_accept_only();

-- ── Storage: coach read access to a linked athlete's backup ─────────────────────
-- Additive alongside the existing backups_select_own/insert_own/update_own policies
-- from the auth/backup sprint (permissive policies for the same action OR together,
-- so this only ever ADDS access, never narrows the athlete's own access to their file).
-- Read-only — coaches never get insert/update on an athlete's backup.
create policy "backups_coach_select_linked_athlete"
on storage.objects for select
to authenticated
using (
  bucket_id = 'backups'
  and exists (
    select 1 from public.coach_athletes
    where coach_id = auth.uid()
      and athlete_id::text = (storage.foldername(name))[1]
      and status = 'active'
  )
);
