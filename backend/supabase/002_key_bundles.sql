create table if not exists public.key_bundles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  identity_key text not null,
  signed_pre_key text not null,
  signed_pre_key_signature text not null,
  one_time_pre_keys text[] not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.key_bundles enable row level security;

drop policy if exists "key_bundles_read_authenticated" on public.key_bundles;
create policy "key_bundles_read_authenticated"
on public.key_bundles for select
to authenticated
using (true);

drop policy if exists "key_bundles_write_self" on public.key_bundles;
create policy "key_bundles_write_self"
on public.key_bundles for insert
to authenticated
with check (auth.uid() = user_id);

drop policy if exists "key_bundles_update_self" on public.key_bundles;
create policy "key_bundles_update_self"
on public.key_bundles for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);
