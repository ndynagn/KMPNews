-- Synthetic Storage metadata only; all fixtures and assertions roll back. No real objects are touched.
begin;
select set_config('kmpnews.avatar_owner', gen_random_uuid()::text, true),
       set_config('kmpnews.avatar_other', gen_random_uuid()::text, true);
set local role authenticated;
do $$
declare
    fixture_owner text := current_setting('kmpnews.avatar_owner');
    fixture_other text := current_setting('kmpnews.avatar_other');
    affected integer;
begin
    perform set_config('request.jwt.claim.sub', fixture_owner, true);
    insert into storage.objects(bucket_id, name) values ('avatars', fixture_owner || '/aaaa.jpg');
    if not exists(select 1 from storage.objects where bucket_id='avatars' and name=fixture_owner || '/aaaa.jpg') then
        raise exception 'Owner cannot read avatar';
    end if;
    begin
        insert into storage.objects(bucket_id, name) values ('avatars', fixture_other || '/bbbb.jpg');
        raise exception 'Cross-user upload accepted';
    exception when insufficient_privilege then null;
    end;
    begin
        insert into storage.objects(bucket_id, name) values ('avatars', fixture_owner || '/aaaa.png');
        raise exception 'Invalid path accepted';
    exception when insufficient_privilege then null;
    end;
    update storage.objects set name=fixture_owner || '/cccc.jpg' where name=fixture_owner || '/aaaa.jpg';
    get diagnostics affected = row_count;
    if affected <> 0 then raise exception 'Overwrite unexpectedly allowed'; end if;
    perform set_config('request.jwt.claim.sub', fixture_other, true);
    if exists(select 1 from storage.objects where bucket_id='avatars' and name=fixture_owner || '/aaaa.jpg') then
        raise exception 'Another user can read avatar';
    end if;

    delete from storage.objects where bucket_id = 'avatars' and name = fixture_owner || '/aaaa.jpg';
    get diagnostics affected = row_count;
    if affected <> 0 then raise exception 'Another user can delete avatar'; end if;
end $$;
set local role anon;
do $$
declare
    affected integer;
begin
    if exists(select 1 from storage.objects where bucket_id='avatars') then
        raise exception 'Guest can read avatars';
    end if;
    begin
        insert into storage.objects(bucket_id, name) values ('avatars', current_setting('kmpnews.avatar_owner') || '/dddd.jpg');
        raise exception 'Guest can upload avatar';
    exception when insufficient_privilege then null;
    end;

    delete from storage.objects
    where bucket_id = 'avatars' and name = current_setting('kmpnews.avatar_owner') || '/aaaa.jpg';
    get diagnostics affected = row_count;
    if affected <> 0 then raise exception 'Guest can delete avatar'; end if;
end $$;
set local role authenticated;
do $$
declare
    fixture_owner text := current_setting('kmpnews.avatar_owner');
    affected integer;
begin
    perform set_config('request.jwt.claim.sub', fixture_owner, true);
    delete from storage.objects where bucket_id = 'avatars' and name = fixture_owner || '/aaaa.jpg';
    get diagnostics affected = row_count;
    if affected <> 1 then raise exception 'Owner cannot delete avatar'; end if;
end $$;
rollback;
