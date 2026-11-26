-- Harborline Health analyst portal: schema, roles, row-level security and RPCs.
--
-- Lives in the same Postgres database as the knowledgeDrop service, but in its own
-- schema (insurance_portal). Hibernate only manages the public tables, so the two
-- never touch each other. Safe to re-run: every statement is idempotent.
--
-- Expects psql variables (passed by docker-compose's db-init service):
--   auth_password   password for the portal_authenticator login role (PostgREST)
--   jwt_secret      HS256 secret shared with PostgREST (PGRST_JWT_SECRET)
--   admin_user      username of the first admin (created only if missing)
--   admin_password  password of the first admin

\set ON_ERROR_STOP on
\set QUIET on
set client_min_messages = warning;

begin;

create extension if not exists pgcrypto;
create extension if not exists citext;

create schema if not exists insurance_portal;
create schema if not exists insurance_portal_private;

-- ---------------------------------------------------------------------------
-- Database roles (cluster-wide, so guarded with IF NOT EXISTS checks)
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (select 1 from pg_roles where rolname = 'portal_anon') then
    create role portal_anon nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'portal_user') then
    create role portal_user nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'portal_admin') then
    create role portal_admin nologin;
  end if;
  if not exists (select 1 from pg_roles where rolname = 'portal_authenticator') then
    create role portal_authenticator login noinherit;
  end if;
end
$$;

alter role portal_authenticator with login noinherit password :'auth_password';
grant portal_anon, portal_user, portal_admin to portal_authenticator;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------
create table if not exists insurance_portal.analysts (
  id            uuid primary key default gen_random_uuid(),
  username      citext not null unique,
  display_name  text not null,
  department    text,
  role          text not null default 'user' check (role in ('admin', 'user')),
  password_hash text not null,
  active        boolean not null default true,
  created_at    timestamptz not null default now()
);

create table if not exists insurance_portal.settings (
  key        text primary key,
  value      text not null,
  updated_at timestamptz not null default now()
);

-- Private config (JWT secret). This schema is not exposed by PostgREST.
create table if not exists insurance_portal_private.config (
  key   text primary key,
  value text not null
);

-- ---------------------------------------------------------------------------
-- Helpers that read the JWT claims PostgREST puts on each request
-- ---------------------------------------------------------------------------
create or replace function insurance_portal_private.jwt_claims()
returns json
language sql stable
as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), ''), '{}')::json;
$$;

-- The caller's analyst id, or null when the account is missing or deactivated.
-- SECURITY DEFINER so it can check analysts.active regardless of the caller's grants.
create or replace function insurance_portal_private.current_analyst_id()
returns uuid
language sql stable security definer
set search_path = insurance_portal, insurance_portal_private, public
as $$
  select a.id
  from insurance_portal.analysts a
  where a.id = nullif(insurance_portal_private.jwt_claims() ->> 'sub', '')::uuid
    and a.active;
$$;

create table if not exists insurance_portal.questions (
  id         uuid primary key default gen_random_uuid(),
  analyst_id uuid not null default insurance_portal_private.current_analyst_id()
             references insurance_portal.analysts (id),
  question   text not null,
  k          int not null default 5 check (k >= 1),
  answer     text,
  citations  jsonb not null default '[]'::jsonb,
  status     text not null check (status in ('answered', 'error')),
  error      text,
  created_at timestamptz not null default now()
);
create index if not exists questions_analyst_created_idx
  on insurance_portal.questions (analyst_id, created_at desc);

create table if not exists insurance_portal.bookmarks (
  id          uuid primary key default gen_random_uuid(),
  question_id uuid not null references insurance_portal.questions (id) on delete cascade,
  analyst_id  uuid not null default insurance_portal_private.current_analyst_id()
              references insurance_portal.analysts (id),
  note        text,
  created_at  timestamptz not null default now(),
  unique (question_id, analyst_id)
);

-- Local record of which analyst uploaded each knowledgeDrop document.
create table if not exists insurance_portal.document_owners (
  document_id uuid primary key,
  filename    text not null,
  analyst_id  uuid default insurance_portal_private.current_analyst_id()
              references insurance_portal.analysts (id),
  uploaded_at timestamptz not null default now(),
  deleted_at  timestamptz,
  deleted_by  uuid references insurance_portal.analysts (id)
);
create index if not exists document_owners_filename_idx
  on insurance_portal.document_owners (filename) where deleted_at is null;

-- Name/department lookup every analyst may read (e.g. to show document owners).
-- Runs as its owner, so it exposes only these columns of analysts and nothing else.
create or replace view insurance_portal.analyst_directory as
  select id, display_name, department from insurance_portal.analysts;

-- ---------------------------------------------------------------------------
-- JWT signing (HS256) with pgcrypto, so no pgjwt extension is needed
-- ---------------------------------------------------------------------------
create or replace function insurance_portal_private.base64url(data bytea)
returns text
language sql immutable
as $$
  select translate(encode(data, 'base64'), E'+/=\n', '-_');
$$;

create or replace function insurance_portal_private.sign_jwt(payload json, secret text)
returns text
language sql immutable
as $$
  with parts as (
    select insurance_portal_private.base64url(convert_to('{"alg":"HS256","typ":"JWT"}', 'utf8'))
           || '.'
           || insurance_portal_private.base64url(convert_to(payload::text, 'utf8')) as signable
  )
  select signable || '.' || insurance_portal_private.base64url(hmac(signable, secret, 'sha256'))
  from parts;
$$;

create or replace function insurance_portal_private.require_admin()
returns void
language plpgsql stable
as $$
begin
  if coalesce(insurance_portal_private.jwt_claims() ->> 'role', '') <> 'portal_admin'
     or insurance_portal_private.current_analyst_id() is null then
    raise insufficient_privilege using message = 'Admins only';
  end if;
end
$$;

-- ---------------------------------------------------------------------------
-- RPCs exposed by PostgREST at /rpc/<name>
-- ---------------------------------------------------------------------------
create or replace function insurance_portal.login(username text, password text)
returns json
language plpgsql security definer
set search_path = insurance_portal, insurance_portal_private, public
as $$
declare
  a insurance_portal.analysts;
  secret text;
begin
  select * into a
  from insurance_portal.analysts
  where analysts.username = login.username and analysts.active;

  if not found or a.password_hash <> crypt(login.password, a.password_hash) then
    raise insufficient_privilege using message = 'Invalid username or password';
  end if;

  select value into secret from insurance_portal_private.config where key = 'jwt_secret';

  return json_build_object(
    'token', insurance_portal_private.sign_jwt(
      json_build_object(
        'role', case when a.role = 'admin' then 'portal_admin' else 'portal_user' end,
        'sub', a.id,
        'name', a.display_name,
        'exp', extract(epoch from now() + interval '8 hours')::bigint
      ),
      secret
    )
  );
end
$$;

-- The knowledgeDrop client token for any signed-in, active analyst. The browser needs
-- it to call the service; it is never shown in the UI to non-admins.
create or replace function insurance_portal.kd_token()
returns text
language plpgsql stable security definer
set search_path = insurance_portal, insurance_portal_private, public
as $$
begin
  if insurance_portal_private.current_analyst_id() is null then
    raise insufficient_privilege using message = 'Not signed in';
  end if;
  return (select value from insurance_portal.settings where key = 'kd_client_token');
end
$$;

create or replace function insurance_portal.change_my_password(old_password text, new_password text)
returns void
language plpgsql security definer
set search_path = insurance_portal, insurance_portal_private, public
as $$
declare
  me uuid := insurance_portal_private.current_analyst_id();
begin
  if me is null then
    raise insufficient_privilege using message = 'Not signed in';
  end if;
  if length(coalesce(new_password, '')) < 8 then
    raise exception 'New password must be at least 8 characters' using errcode = '22023';
  end if;
  if not exists (
    select 1 from insurance_portal.analysts
    where id = me and password_hash = crypt(old_password, password_hash)
  ) then
    raise insufficient_privilege using message = 'Current password is incorrect';
  end if;
  update insurance_portal.analysts
     set password_hash = crypt(new_password, gen_salt('bf', 10))
   where id = me;
end
$$;

create or replace function insurance_portal.create_analyst(
  username text, display_name text, department text, role text, password text)
returns uuid
language plpgsql security definer
set search_path = insurance_portal, insurance_portal_private, public
as $$
declare
  new_id uuid;
begin
  perform insurance_portal_private.require_admin();
  if length(coalesce(password, '')) < 8 then
    raise exception 'Password must be at least 8 characters' using errcode = '22023';
  end if;
  insert into insurance_portal.analysts (username, display_name, department, role, password_hash)
  values (create_analyst.username, create_analyst.display_name,
          nullif(create_analyst.department, ''), create_analyst.role,
          crypt(create_analyst.password, gen_salt('bf', 10)))
  returning id into new_id;
  return new_id;
end
$$;

create or replace function insurance_portal.reset_password(analyst_id uuid, password text)
returns void
language plpgsql security definer
set search_path = insurance_portal, insurance_portal_private, public
as $$
begin
  perform insurance_portal_private.require_admin();
  if length(coalesce(password, '')) < 8 then
    raise exception 'Password must be at least 8 characters' using errcode = '22023';
  end if;
  update insurance_portal.analysts
     set password_hash = crypt(reset_password.password, gen_salt('bf', 10))
   where id = reset_password.analyst_id;
end
$$;

create or replace function insurance_portal.set_active(analyst_id uuid, active boolean)
returns void
language plpgsql security definer
set search_path = insurance_portal, insurance_portal_private, public
as $$
begin
  perform insurance_portal_private.require_admin();
  if set_active.analyst_id = insurance_portal_private.current_analyst_id() and not set_active.active then
    raise exception 'You cannot deactivate your own account' using errcode = '22023';
  end if;
  update insurance_portal.analysts
     set active = set_active.active
   where id = set_active.analyst_id;
end
$$;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
grant usage on schema insurance_portal to portal_anon, portal_user, portal_admin;
-- Needed so column defaults and policies can call the private helper functions.
grant usage on schema insurance_portal_private to portal_user, portal_admin;
revoke all on insurance_portal_private.config from public;

revoke execute on all functions in schema insurance_portal from public;
revoke execute on all functions in schema insurance_portal_private from public;
grant execute on function insurance_portal_private.jwt_claims() to portal_user, portal_admin;
grant execute on function insurance_portal_private.current_analyst_id() to portal_user, portal_admin;
grant execute on function insurance_portal_private.require_admin() to portal_admin;

grant execute on function insurance_portal.login(text, text) to portal_anon, portal_user, portal_admin;
grant execute on function insurance_portal.kd_token() to portal_user, portal_admin;
grant execute on function insurance_portal.change_my_password(text, text) to portal_user, portal_admin;
grant execute on function insurance_portal.create_analyst(text, text, text, text, text) to portal_admin;
grant execute on function insurance_portal.reset_password(uuid, text) to portal_admin;
grant execute on function insurance_portal.set_active(uuid, boolean) to portal_admin;

-- analysts: admins read everything except password_hash; edits go through RPCs,
-- apart from profile fields admins may PATCH directly.
revoke all on insurance_portal.analysts from portal_anon, portal_user, portal_admin;
grant select (id, username, display_name, department, role, active, created_at)
  on insurance_portal.analysts to portal_admin;
grant update (display_name, department, role) on insurance_portal.analysts to portal_admin;

revoke all on insurance_portal.analyst_directory from portal_anon, portal_user, portal_admin;
grant select on insurance_portal.analyst_directory to portal_user, portal_admin;

-- settings (holds the knowledgeDrop client token): admins only.
revoke all on insurance_portal.settings from portal_anon, portal_user, portal_admin;
grant select, insert, update, delete on insurance_portal.settings to portal_admin;

revoke all on insurance_portal.questions from portal_anon, portal_user, portal_admin;
grant select, insert, delete on insurance_portal.questions to portal_user, portal_admin;

revoke all on insurance_portal.bookmarks from portal_anon, portal_user, portal_admin;
grant select, insert, update, delete on insurance_portal.bookmarks to portal_user, portal_admin;

revoke all on insurance_portal.document_owners from portal_anon, portal_user, portal_admin;
grant select, insert, update on insurance_portal.document_owners to portal_user, portal_admin;

-- ---------------------------------------------------------------------------
-- Row-level security
-- ---------------------------------------------------------------------------
alter table insurance_portal.analysts enable row level security;
alter table insurance_portal.questions enable row level security;
alter table insurance_portal.bookmarks enable row level security;
alter table insurance_portal.document_owners enable row level security;

drop policy if exists analysts_admin_all on insurance_portal.analysts;
create policy analysts_admin_all on insurance_portal.analysts
  for all to portal_admin using (true) with check (true);

-- Questions: users see and write only their own; admins see everyone's but still
-- create questions only as themselves.
drop policy if exists questions_user_own on insurance_portal.questions;
create policy questions_user_own on insurance_portal.questions
  for all to portal_user
  using (analyst_id = insurance_portal_private.current_analyst_id())
  with check (analyst_id = insurance_portal_private.current_analyst_id());

drop policy if exists questions_admin_read on insurance_portal.questions;
create policy questions_admin_read on insurance_portal.questions
  for select to portal_admin using (true);

drop policy if exists questions_admin_insert on insurance_portal.questions;
create policy questions_admin_insert on insurance_portal.questions
  for insert to portal_admin
  with check (analyst_id = insurance_portal_private.current_analyst_id());

drop policy if exists questions_admin_delete on insurance_portal.questions;
create policy questions_admin_delete on insurance_portal.questions
  for delete to portal_admin using (true);

-- Bookmarks: users only on questions they can see (their own); admins on any.
drop policy if exists bookmarks_user_own on insurance_portal.bookmarks;
create policy bookmarks_user_own on insurance_portal.bookmarks
  for all to portal_user
  using (analyst_id = insurance_portal_private.current_analyst_id())
  with check (
    analyst_id = insurance_portal_private.current_analyst_id()
    and exists (select 1 from insurance_portal.questions q where q.id = question_id)
  );

drop policy if exists bookmarks_admin_read on insurance_portal.bookmarks;
create policy bookmarks_admin_read on insurance_portal.bookmarks
  for select to portal_admin using (true);

drop policy if exists bookmarks_admin_write_own on insurance_portal.bookmarks;
create policy bookmarks_admin_write_own on insurance_portal.bookmarks
  for insert to portal_admin
  with check (analyst_id = insurance_portal_private.current_analyst_id());

drop policy if exists bookmarks_admin_update_own on insurance_portal.bookmarks;
create policy bookmarks_admin_update_own on insurance_portal.bookmarks
  for update to portal_admin
  using (analyst_id = insurance_portal_private.current_analyst_id())
  with check (analyst_id = insurance_portal_private.current_analyst_id());

drop policy if exists bookmarks_admin_delete on insurance_portal.bookmarks;
create policy bookmarks_admin_delete on insurance_portal.bookmarks
  for delete to portal_admin using (true);

-- Document owners: the whole library is shared, so everyone reads every row.
-- New rows are always recorded as the uploader; anyone may mark a row deleted.
drop policy if exists owners_read on insurance_portal.document_owners;
create policy owners_read on insurance_portal.document_owners
  for select to portal_user, portal_admin using (true);

drop policy if exists owners_insert on insurance_portal.document_owners;
create policy owners_insert on insurance_portal.document_owners
  for insert to portal_user, portal_admin
  with check (analyst_id = insurance_portal_private.current_analyst_id());

drop policy if exists owners_update on insurance_portal.document_owners;
create policy owners_update on insurance_portal.document_owners
  for update to portal_user, portal_admin
  using (true)
  with check (deleted_by is null or deleted_by = insurance_portal_private.current_analyst_id());

-- ---------------------------------------------------------------------------
-- Seed data
-- ---------------------------------------------------------------------------
insert into insurance_portal_private.config (key, value)
values ('jwt_secret', :'jwt_secret')
on conflict (key) do update set value = excluded.value;

-- First admin: created only if the username is free, so a password changed in the
-- UI is never reset by re-running this script.
insert into insurance_portal.analysts (username, display_name, department, role, password_hash)
values (:'admin_user', 'Portal Administrator', 'IT', 'admin',
        crypt(:'admin_password', gen_salt('bf', 10)))
on conflict (username) do nothing;

commit;

-- Ask a running PostgREST to reload its schema cache.
notify pgrst, 'reload schema';
