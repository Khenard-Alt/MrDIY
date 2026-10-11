-- 1) public.push_tokens: phone tokens used to send a notification to a user's tray even when the app is closed.
--    The app registers/removes its own token; the admin server (service role) reads them to send.
-- 2) public.notification_emails: one row per email the admin server sends to an applicant,
--    so HR can confirm that an email was sent (or why it failed). Only the service role can read or write it.
-- Verification: select * from public.push_tokens; select * from public.notification_emails order by created_at desc;
begin;

create table if not exists public.push_tokens (
    token text primary key,
    user_id text not null,
    platform text not null default 'android',
    updated_at timestamptz not null default now()
);
create index if not exists push_tokens_user_idx on public.push_tokens (user_id);

alter table public.push_tokens enable row level security;

drop policy if exists "Users manage own push tokens" on public.push_tokens;
create policy "Users manage own push tokens" on public.push_tokens
    for all to authenticated
    using (auth.uid()::text = user_id)
    with check (auth.uid()::text = user_id);

create table if not exists public.notification_emails (
    id uuid primary key default gen_random_uuid(),
    user_id text,
    application_id text,
    to_email text not null,
    subject text not null,
    status text not null check (status in ('sent', 'failed')),
    error text,
    created_at timestamptz not null default now()
);
create index if not exists notification_emails_application_idx on public.notification_emails (application_id, created_at desc);

-- No policies on purpose: with row level security on, only the service role (admin server) can use it.
alter table public.notification_emails enable row level security;

commit;
