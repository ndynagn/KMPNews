-- Execute as postgres with ON_ERROR_STOP. All fixtures and assertions roll back.
-- No email, password, token, or existing user data is read or returned.
begin;
do $$
begin
    perform set_config('kmpnews.test_user_a', gen_random_uuid()::text, true);
    perform set_config('kmpnews.test_user_b', gen_random_uuid()::text, true);
end;
$$;

insert into auth.users (id) values
    (current_setting('kmpnews.test_user_a')::uuid),
    (current_setting('kmpnews.test_user_b')::uuid);

set local role authenticated;
do $$
declare
    owner_id uuid := current_setting('kmpnews.test_user_a')::uuid;
    other_id uuid := current_setting('kmpnews.test_user_b')::uuid;
    affected integer;
begin
    perform set_config('request.jwt.claim.sub', owner_id::text, true);
    insert into public.user_favorites (article_id, title) values ('fixture-article', 'Saved title');

    if (select count(*) from public.user_favorites) <> 1 then
        raise exception 'Owner cannot read their saved article';
    end if;
    if not exists (
        select 1 from public.user_favorites
        where user_id = owner_id and added_at = now() and title = 'Saved title' and summary is null
    ) then
        raise exception 'Owner/default timestamp/snapshot/null semantics failed';
    end if;

    begin
        insert into public.user_favorites (article_id) values ('fixture-article');
        raise exception 'Duplicate article accepted';
    exception when unique_violation then null;
    end;

    -- Data API resolution=ignore-duplicates must preserve the first snapshot and timestamp.
    insert into public.user_favorites (user_id, article_id, title)
        values (owner_id, 'fixture-article', 'Must not replace')
        on conflict (user_id, article_id) do nothing;
    if (select title from public.user_favorites where article_id = 'fixture-article') <> 'Saved title' then
        raise exception 'Duplicate changed the snapshot';
    end if;

    begin
        insert into public.user_favorites (user_id, article_id) values (other_id, 'forged-owner');
        raise exception 'Cross-user insertion accepted';
    exception when insufficient_privilege then null;
    end;

    begin
        insert into public.user_favorites (article_id, added_at) values ('forged-time', now());
        raise exception 'Client-controlled timestamp accepted';
    exception when insufficient_privilege then null;
    end;

    begin
        update public.user_favorites set user_id = other_id where article_id = 'fixture-article';
        raise exception 'Client UPDATE accepted';
    exception when insufficient_privilege then null;
    end;

    begin
        insert into public.user_favorites (article_id) values ('');
        raise exception 'Empty article ID accepted';
    exception when check_violation then null;
    end;

    perform set_config('request.jwt.claim.sub', other_id::text, true);
    if exists (select 1 from public.user_favorites) then
        raise exception 'Another user can read the first user snapshot';
    end if;
    delete from public.user_favorites where user_id = owner_id;
    get diagnostics affected = row_count;
    if affected <> 0 then
        raise exception 'Another user can delete the first user snapshot';
    end if;
    insert into public.user_favorites (article_id) values ('fixture-article');

    perform set_config('request.jwt.claim.sub', '', true);
    perform set_config('request.jwt.claims', '{}', true);
    if exists (select 1 from public.user_favorites) then
        raise exception 'Missing identity can read favorites';
    end if;
    begin
        insert into public.user_favorites (user_id, article_id) values (owner_id, 'missing-identity');
        raise exception 'Missing identity can insert favorites';
    exception when insufficient_privilege then null;
    end;

    perform set_config('request.jwt.claim.sub', owner_id::text, true);
    delete from public.user_favorites where article_id = 'fixture-article';
    get diagnostics affected = row_count;
    if affected <> 1 then
        raise exception 'Owner cannot delete their article';
    end if;
end;
$$;

set local role anon;
do $$
declare
    statement text;
begin
    foreach statement in array array[
        'select * from public.user_favorites',
        'insert into public.user_favorites (article_id) values (''guest'')',
        'update public.user_favorites set title = ''guest''',
        'delete from public.user_favorites'
    ] loop
        begin
            execute statement;
            raise exception 'Guest operation accepted: %', statement;
        exception when insufficient_privilege then null;
        end;
    end loop;
end;
$$;

reset role;
do $$
begin
    if not exists (
        select 1 from public.user_favorites
        where user_id = current_setting('kmpnews.test_user_b')::uuid and article_id = 'fixture-article'
    ) then
        raise exception 'Deleting one user favorite removed another user favorite';
    end if;

    delete from auth.users where id = current_setting('kmpnews.test_user_b')::uuid;
    if exists (
        select 1 from public.user_favorites where user_id = current_setting('kmpnews.test_user_b')::uuid
    ) then
        raise exception 'Account deletion left orphan favorites';
    end if;
end;
$$;
rollback;
