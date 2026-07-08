-- Run once in the Supabase SQL Editor for the `backups` storage bucket.
-- Restricts each authenticated user to objects under their own uid folder:
-- backups/{auth.uid()}/latest.json

create policy "backups_select_own"
on storage.objects for select
to authenticated
using (
  bucket_id = 'backups'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "backups_insert_own"
on storage.objects for insert
to authenticated
with check (
  bucket_id = 'backups'
  and (storage.foldername(name))[1] = auth.uid()::text
);

create policy "backups_update_own"
on storage.objects for update
to authenticated
using (
  bucket_id = 'backups'
  and (storage.foldername(name))[1] = auth.uid()::text
);
