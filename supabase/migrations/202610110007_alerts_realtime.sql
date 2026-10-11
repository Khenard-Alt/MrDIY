-- Lets the app receive new notifications instantly (Supabase Realtime) while it is running.
-- Table: public.alerts. Row level security still applies, so each user only receives their own rows.
-- Safe to run more than once.
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'alerts'
    ) then
        alter publication supabase_realtime add table public.alerts;
    end if;
end
$$;
