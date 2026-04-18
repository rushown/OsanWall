-- OsanWall core schema (free-first friendly)
-- Works on vanilla Postgres/Supabase free tier.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  handle text unique not null check (char_length(handle) between 3 and 30),
  display_name text not null check (char_length(display_name) between 1 and 60),
  avatar_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.motes (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(body) between 1 and 240),
  x double precision not null,
  y double precision not null,
  vibe_key text not null default 'unknown',
  alpha real not null default 1.0 check (alpha >= 0 and alpha <= 1),
  status text not null default 'visible' check (status in ('visible', 'ghosted', 'archived')),
  interactions integer not null default 0,
  unique_interactions integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_decay_at timestamptz not null default now()
);

create index if not exists idx_motes_created_at on public.motes (created_at desc);
create index if not exists idx_motes_status on public.motes (status);
create index if not exists idx_motes_vibe on public.motes (vibe_key);
create index if not exists idx_motes_xy on public.motes (x, y);

create table if not exists public.spatial_anchors (
  id uuid primary key default gen_random_uuid(),
  mote_id uuid not null references public.motes(id) on delete cascade,
  cell_x integer not null,
  cell_y integer not null,
  created_at timestamptz not null default now(),
  unique (mote_id, cell_x, cell_y)
);

create index if not exists idx_spatial_anchor_cell on public.spatial_anchors (cell_x, cell_y);

-- Auto-update updated_at
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_profiles_updated_at on public.profiles;
create trigger trg_profiles_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists trg_motes_updated_at on public.motes;
create trigger trg_motes_updated_at
before update on public.motes
for each row execute function public.set_updated_at();

-- RLS
alter table public.profiles enable row level security;
alter table public.motes enable row level security;
alter table public.spatial_anchors enable row level security;

-- Profiles: public read, owner write
drop policy if exists "profiles_read_all" on public.profiles;
create policy "profiles_read_all"
on public.profiles for select
to authenticated
using (true);

drop policy if exists "profiles_insert_self" on public.profiles;
create policy "profiles_insert_self"
on public.profiles for insert
to authenticated
with check (auth.uid() = id);

drop policy if exists "profiles_update_self" on public.profiles;
create policy "profiles_update_self"
on public.profiles for update
to authenticated
using (auth.uid() = id)
with check (auth.uid() = id);

-- Motes: public read visible/ghosted, owner full control
drop policy if exists "motes_read_public" on public.motes;
create policy "motes_read_public"
on public.motes for select
to authenticated
using (status in ('visible', 'ghosted') or auth.uid() = author_id);

drop policy if exists "motes_insert_self" on public.motes;
create policy "motes_insert_self"
on public.motes for insert
to authenticated
with check (auth.uid() = author_id);

drop policy if exists "motes_update_self" on public.motes;
create policy "motes_update_self"
on public.motes for update
to authenticated
using (auth.uid() = author_id)
with check (auth.uid() = author_id);

drop policy if exists "motes_delete_self" on public.motes;
create policy "motes_delete_self"
on public.motes for delete
to authenticated
using (auth.uid() = author_id);

-- Anchors follow parent mote ownership/readability
drop policy if exists "anchors_read_by_mote_policy" on public.spatial_anchors;
create policy "anchors_read_by_mote_policy"
on public.spatial_anchors for select
to authenticated
using (
  exists (
    select 1 from public.motes m
    where m.id = spatial_anchors.mote_id
      and (m.status in ('visible', 'ghosted') or m.author_id = auth.uid())
  )
);

drop policy if exists "anchors_write_by_owner" on public.spatial_anchors;
create policy "anchors_write_by_owner"
on public.spatial_anchors for all
to authenticated
using (
  exists (
    select 1 from public.motes m
    where m.id = spatial_anchors.mote_id
      and m.author_id = auth.uid()
  )
)
with check (
  exists (
    select 1 from public.motes m
    where m.id = spatial_anchors.mote_id
      and m.author_id = auth.uid()
  )
);
