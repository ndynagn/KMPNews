-- Disposable vanilla PostgreSQL only. Hosted tests use the real Supabase auth schema.
create schema auth;
create table auth.users (id uuid primary key);
create function auth.uid() returns uuid
    language sql stable
    set search_path = ''
    as $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
grant usage on schema auth to anon, authenticated, service_role;
grant execute on function auth.uid() to anon, authenticated, service_role;
