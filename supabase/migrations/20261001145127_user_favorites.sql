create table public.user_favorites (
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    article_id text not null check (article_id <> ''),
    added_at timestamptz not null default now(),
    title text,
    url text,
    summary text,
    image_url text,
    source_id text,
    source_name text,
    published_at_epoch_milliseconds bigint,
    primary key (user_id, article_id)
);

-- The article snapshot survives feed eviction. Ties use article_id for stable pagination.
create index user_favorites_user_added_idx
    on public.user_favorites (user_id, added_at desc, article_id desc);

alter table public.user_favorites enable row level security;

-- Remove project default grants before exposing the minimum client operations.
revoke all on public.user_favorites from public, anon, authenticated;
grant select, delete on public.user_favorites to authenticated;
grant insert (
    user_id, article_id, title, url, summary, image_url,
    source_id, source_name, published_at_epoch_milliseconds
) on public.user_favorites to authenticated;
grant all on public.user_favorites to service_role;

create policy user_favorites_select_own
    on public.user_favorites for select to authenticated
    using ((select auth.uid()) = user_id);

create policy user_favorites_insert_own
    on public.user_favorites for insert to authenticated
    with check ((select auth.uid()) = user_id);

create policy user_favorites_delete_own
    on public.user_favorites for delete to authenticated
    using ((select auth.uid()) = user_id);

comment on table public.user_favorites is
    'Private article snapshots owned by an Auth user. Clients can add, read and remove, but cannot update.';
comment on column public.user_favorites.added_at is
    'Server-assigned save time. Client INSERT privileges exclude this column.';
