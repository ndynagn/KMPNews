-- Run in an isolated test database or before first deployment. Always rolls back.
begin;
select pg_advisory_xact_lock(875001);
delete from private.news_request_attempts;
do $$
declare
    result jsonb;
    current_time_utc timestamptz;
begin
    if has_function_privilege('anon', 'public.reserve_news_request()', 'EXECUTE') or
       has_function_privilege('authenticated', 'public.reserve_news_request()', 'EXECUTE') then
        raise exception 'Client role can reserve budget';
    end if;
    if has_table_privilege('anon', 'private.news_request_attempts', 'SELECT') or
       has_table_privilege('authenticated', 'private.news_request_attempts', 'INSERT') then
        raise exception 'Client role can access attempts';
    end if;
    for i in 1..25 loop
        result := public.reserve_news_request();
        if result->>'allowed' <> 'true' then raise exception 'Early burst rejection'; end if;
    end loop;
    result := public.reserve_news_request();
    if result->>'code' <> 'RateLimitExceeded' or (result->>'retry_after')::integer not between 1 and 900 then
        raise exception 'Burst boundary failed';
    end if;
    if (select count(*) from private.news_request_attempts) <> 25 then raise exception 'Rejected call counted'; end if;
    delete from private.news_request_attempts;
    current_time_utc := clock_timestamp();
    insert into private.news_request_attempts select current_time_utc - interval '1 hour' from generate_series(1,180);
    result := public.reserve_news_request();
    if result->>'code' <> 'ApiLimitExceeded' then raise exception 'Daily boundary failed'; end if;
    delete from private.news_request_attempts;
    insert into private.news_request_attempts select current_time_utc - interval '24 hours' from generate_series(1,180);
    result := public.reserve_news_request();
    if result->>'allowed' <> 'true' then raise exception 'Expired day did not release capacity'; end if;
    delete from private.news_request_attempts;
    insert into private.news_request_attempts select current_time_utc - interval '15 minutes' from generate_series(1,25);
    result := public.reserve_news_request();
    if result->>'allowed' <> 'true' then raise exception 'Expired burst did not release capacity'; end if;
end;
$$;
set local role service_role;
select public.reserve_news_request()->>'allowed' as service_role_allowed;
rollback;
