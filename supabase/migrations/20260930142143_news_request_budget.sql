create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
grant usage on schema private to service_role;

create table private.news_request_attempts (
    reserved_at timestamptz not null
);
create index news_request_attempts_reserved_at_idx on private.news_request_attempts (reserved_at);
alter table private.news_request_attempts enable row level security;
revoke all on private.news_request_attempts from public, anon, authenticated;
grant select, insert, delete on private.news_request_attempts to service_role;

-- Invoker privileges are sufficient: only service_role can execute or access the table.
create function public.reserve_news_request()
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
    current_time_utc timestamptz;
    daily_count integer;
    burst_count integer;
    daily_oldest timestamptz;
    burst_oldest timestamptz;
    daily_wait integer := 0;
    burst_wait integer := 0;
begin
    -- Every caller shares this transaction lock; read the clock AFTER obtaining it.
    perform pg_catalog.pg_advisory_xact_lock(875001);
    current_time_utc := pg_catalog.clock_timestamp();
    delete from private.news_request_attempts where reserved_at <= current_time_utc - interval '24 hours';

    select count(*), min(reserved_at),
        count(*) filter (where reserved_at > current_time_utc - interval '15 minutes'),
        min(reserved_at) filter (where reserved_at > current_time_utc - interval '15 minutes')
    into daily_count, daily_oldest, burst_count, burst_oldest
    from private.news_request_attempts;

    if daily_count >= 180 then
        daily_wait := greatest(1, ceil(extract(epoch from daily_oldest + interval '24 hours' - current_time_utc))::integer);
    end if;
    if burst_count >= 25 then
        burst_wait := greatest(1, ceil(extract(epoch from burst_oldest + interval '15 minutes' - current_time_utc))::integer);
    end if;
    if daily_wait > 0 or burst_wait > 0 then
        return pg_catalog.jsonb_build_object(
            'allowed', false,
            'code', case when daily_wait > 0 then 'ApiLimitExceeded' else 'RateLimitExceeded' end,
            'retry_after', greatest(daily_wait, burst_wait)
        );
    end if;

    insert into private.news_request_attempts (reserved_at) values (current_time_utc);
    return pg_catalog.jsonb_build_object('allowed', true);
end;
$$;
revoke all on function public.reserve_news_request() from public, anon, authenticated;
grant execute on function public.reserve_news_request() to service_role;
comment on function public.reserve_news_request() is
    'Atomically reserves one NewsData attempt: 180 per rolling day, 25 per rolling 15 minutes. No refunds.';
