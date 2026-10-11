-- Profile pictures for the admin Applications review.
-- Bucket: private `avatars` (max 2 MB, images only). Files live at <user id>/avatar.jpg.
-- Reason: the app stored the picture only on the phone, so the admin could not show the applicant.
-- Access: each user can read/write only their own folder; the admin reads through signed URLs
-- created with the service-role key on the server.
-- Verification: select id, public, file_size_limit from storage.buckets where id = 'avatars';
begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', false, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = 2097152,
    allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp'];

drop policy if exists "avatars_owner" on storage.objects;
create policy "avatars_owner" on storage.objects for all to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

commit;
