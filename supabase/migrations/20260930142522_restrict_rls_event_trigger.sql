-- Dashboard creates this event trigger helper; local databases may not have it.
-- Event triggers need no client EXECUTE grant. Keep automatic RLS enabled.
do $$
begin
    if pg_catalog.to_regprocedure('public.rls_auto_enable()') is not null then
        revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
    end if;
end;
$$;
