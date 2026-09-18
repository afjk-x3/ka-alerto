-- Run once in Supabase's SQL editor (Project → SQL Editor → New query → Run).
--
-- Column names match `data/Event.kt`'s own field names exactly (quoted, so Postgres
-- keeps the camelCase) rather than the Node server's snake_case (`server/src/db.js`
-- maps field names in JS code; PostgREST has no such layer — it matches JSON keys to
-- column names directly). Event is already `@Serializable` and posted as-is
-- (`sync/SupabaseSync.kt`'s encodeEvents) — same "the row is the wire format"
-- principle mesh/MeshProtocol.kt already uses, now with zero mapping code either side.

create table if not exists events (
  id text primary key,
  type text not null,
  lat double precision not null,
  lon double precision not null,
  "featureRef" text,
  severity text,
  "waterLevel" text,
  "authorId" text not null,
  "authorName" text not null,
  "authorRole" text not null,
  "timestampMs" bigint not null,
  "expiresAt" bigint not null,
  origin text not null,
  "hopCount" integer not null default 0,
  note text,
  "disputeReason" text,
  payload text
);

alter table events enable row level security;
create policy "anon read/write" on events for all using (true) with check (true);

-- Report photos, keyed by their content hash (report/PhotoStore.kt). Public bucket +
-- anon key read/write, same no-auth posture as the table above (ground rule 4).
insert into storage.buckets (id, name, public)
values ('photos', 'photos', true)
on conflict (id) do nothing;

create policy "anon photos" on storage.objects for all
  using (bucket_id = 'photos') with check (bucket_id = 'photos');
