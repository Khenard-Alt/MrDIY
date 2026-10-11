begin;
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('private-resumes', 'private-resumes', false, 10485760, array['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'])
on conflict (id) do update set public = false, file_size_limit = 10485760,
    allowed_mime_types = array['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'text/plain'];
-- Restrictive guard protects this bucket even when old permissive policies exist.
drop policy if exists "private_resumes_owner_guard" on storage.objects;
create policy "private_resumes_owner_guard" on storage.objects as restrictive for all to public
using (bucket_id not in ('private-resumes', 'resumes') or (auth.uid() is not null and (storage.foldername(name))[1] = auth.uid()::text))
with check (bucket_id not in ('private-resumes', 'resumes') or (auth.uid() is not null and (storage.foldername(name))[1] = auth.uid()::text));
drop policy if exists "private_resumes_owner" on storage.objects;
create policy "private_resumes_owner" on storage.objects for all to authenticated
using (bucket_id in ('private-resumes', 'resumes') and (storage.foldername(name))[1] = auth.uid()::text)
with check (bucket_id in ('private-resumes', 'resumes') and (storage.foldername(name))[1] = auth.uid()::text);
-- Preserve legacy files while revoking anonymous public URLs.
update storage.buckets set public = false where id = 'resumes';
commit;
