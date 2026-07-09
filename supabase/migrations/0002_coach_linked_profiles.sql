-- SaiyanStrong Coach Mode — Slice 3: linked-profile visibility.
-- Builds on 0001_coach_mode.sql. Adds email to profiles — needed so a coach's invite
-- list and an athlete's "Linked Coaches" list can show a human-readable identity instead
-- of a raw UUID. Google Sign-In doesn't populate display_name, and profiles has no
-- client-writable columns since the Slice 1 security fix, so the only safe way to get
-- email into profiles is server-side, via the existing sign-up trigger.

alter table public.profiles add column if not exists email text;

-- Backfill any profile created before this column existed (just the one test row from
-- Slice 1 verification).
update public.profiles p
set email = u.email
from auth.users u
where p.id = u.id and p.email is null;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email) values (new.id, new.email);
  return new;
end;
$$;
-- on_auth_user_created (the trigger itself) is untouched — CREATE OR REPLACE above just
-- updates this function's body in place.

-- A coach and their actively-linked athletes may see each other's basic identity. Kept
-- separate from profiles_select_own so it's easy to reason about: this is the ONLY policy
-- that grants visibility across different users' rows, and it's conditioned entirely on
-- an active coach_athletes link existing in either direction.
create policy "profiles_select_linked"
on public.profiles for select
to authenticated
using (
  exists (
    select 1 from public.coach_athletes
    where status = 'active'
      and ((coach_id = auth.uid() and athlete_id = profiles.id)
        or (athlete_id = auth.uid() and coach_id = profiles.id))
  )
);

-- The app queries this view (not the raw profiles table) when looking up a linked
-- party's identity, so entitlement/billing columns (coach_entitlement_active,
-- paddle_subscription_id, etc.) never reach a linked coach/athlete even though the RLS
-- policy above grants row-level access to the full profiles row. security_invoker means
-- the view enforces the QUERYING user's RLS on the underlying table (Postgres 15+,
-- Supabase default) rather than the view owner's — without it this view would silently
-- bypass RLS entirely, which would be a much bigger hole than the one it's meant to close.
create or replace view public.linked_profile_public
with (security_invoker = true)
as select id, email, display_name from public.profiles;

grant select on public.linked_profile_public to authenticated;
