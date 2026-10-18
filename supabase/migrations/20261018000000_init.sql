-- «Путь» — initial schema (ТЗ п. 6.2, 6.3).
-- Roles: user / editor / reviewer / admin. All access control is enforced server-side via RLS.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------
create type app_role as enum ('user', 'editor', 'reviewer', 'admin');
create type content_status as enum ('draft', 'in_review', 'changes_requested', 'approved', 'published', 'unpublished');
create type ticket_status as enum ('queued', 'sent', 'in_review', 'assigned', 'answered', 'closed');
create type reminder_frequency as enum ('off', 'daily', 'every_3_days', 'weekly');

-- ---------------------------------------------------------------------------
-- Users & roles
-- ---------------------------------------------------------------------------
create table languages (
    code        text primary key,
    name        text not null,
    is_active   boolean not null default true
);
insert into languages (code, name) values ('ru', 'Русский');

create table profiles (
    id            uuid primary key references auth.users (id) on delete cascade,
    display_name  text,
    role          app_role not null default 'user',
    language_code text not null default 'ru' references languages (code),
    created_at    timestamptz not null default now()
);

-- Every auth user (including anonymous guests) gets a profile.
create function handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
    insert into profiles (id) values (new.id) on conflict do nothing;
    insert into notification_settings (user_id) values (new.id) on conflict do nothing;
    return new;
end $$;

create function current_role_is(variadic roles app_role[]) returns boolean
language sql stable security definer set search_path = public as $$
    select exists (select 1 from profiles where id = auth.uid() and role = any (roles));
$$;

create function is_admin() returns boolean language sql stable as $$ select current_role_is('admin') $$;
create function is_staff() returns boolean language sql stable as $$ select current_role_is('admin', 'editor', 'reviewer') $$;

-- ---------------------------------------------------------------------------
-- Content
-- ---------------------------------------------------------------------------
create table categories (
    id            text primary key,
    title         text not null,
    icon          text not null default 'book',
    sort_order    int not null default 0,
    language_code text not null default 'ru' references languages (code)
);

create table routes (
    id            text primary key,
    title         text not null,
    description   text not null,
    icon          text not null default 'route',
    sort_order    int not null default 0,
    is_published  boolean not null default false,
    language_code text not null default 'ru' references languages (code),
    updated_at    timestamptz not null default now()
);

create table lessons (
    id            text primary key,
    category_id   text not null references categories (id),
    title         text not null,
    summary       text not null,
    minutes       int not null default 5 check (minutes between 1 and 60),
    blocks        jsonb not null default '[]',   -- same block format as the app's content.json
    quiz          jsonb not null default '[]',
    related       text[] not null default '{}',
    audio_url     text,
    status        content_status not null default 'draft',
    editor_id     uuid references profiles (id),  -- ответственный редактор
    reviewer_id   uuid references profiles (id),  -- проверяющий специалист
    reviewer_name text,                            -- публичное имя проверяющего
    reviewed_at   date,
    published_at  timestamptz,
    correction_note text,                          -- порядок/описание последних исправлений
    language_code text not null default 'ru' references languages (code),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create table route_lessons (
    route_id  text not null references routes (id) on delete cascade,
    lesson_id text not null references lessons (id) on delete cascade,
    position  int not null,
    primary key (route_id, lesson_id)
);

create table sources (
    id        bigint generated always as identity primary key,
    title     text not null,
    reference text not null default '',
    url       text
);

create table lesson_sources (
    lesson_id text not null references lessons (id) on delete cascade,
    source_id bigint not null references sources (id) on delete restrict,
    position  int not null default 0,
    primary key (lesson_id, source_id)
);

create table faqs (
    id            text primary key,
    question      text not null,
    answer        text not null,
    category_id   text not null references categories (id),
    popular       boolean not null default false,
    sources       jsonb not null default '[]',
    status        content_status not null default 'draft',
    editor_id     uuid references profiles (id),
    reviewer_id   uuid references profiles (id),
    reviewer_name text,
    reviewed_at   date,
    updated_at    timestamptz not null default now()
);

-- Review workflow history (статусы проверки материалов).
create table review_events (
    id          bigint generated always as identity primary key,
    entity      text not null check (entity in ('lesson', 'faq')),
    entity_id   text not null,
    from_status content_status,
    to_status   content_status not null,
    actor_id    uuid references profiles (id),
    comment     text,
    created_at  timestamptz not null default now()
);

-- Religious content may only be published after review by a different person than the editor.
create function enforce_review_workflow() returns trigger
language plpgsql set search_path = public as $$
begin
    new.updated_at := now();
    if new.status = 'published' and old.status is distinct from 'published' then
        if old.status not in ('approved', 'unpublished') then
            raise exception 'Материал можно опубликовать только после одобрения проверяющим';
        end if;
        if new.reviewer_id is null or new.reviewed_at is null then
            raise exception 'Не указан проверяющий или дата проверки';
        end if;
        new.published_at := now();
    end if;
    if new.status = 'approved' and old.status is distinct from 'approved' then
        if not current_role_is('reviewer', 'admin') then
            raise exception 'Одобрить материал может только проверяющий специалист';
        end if;
        if new.editor_id = auth.uid() then
            raise exception 'Редактор не может сам одобрить свой материал';
        end if;
        new.reviewer_id := coalesce(new.reviewer_id, auth.uid());
        new.reviewed_at := coalesce(new.reviewed_at, current_date);
    end if;
    -- Any edit of a published text sends it back to review.
    if old.status = 'published' and new.status = 'published' and
       (to_jsonb(new) - 'updated_at' - 'published_at') is distinct from (to_jsonb(old) - 'updated_at' - 'published_at') and
       not current_role_is('admin') then
        raise exception 'Опубликованный материал нельзя изменить без повторной проверки — снимите с публикации или создайте исправление';
    end if;
    if new.status is distinct from old.status then
        insert into review_events (entity, entity_id, from_status, to_status, actor_id)
        values (case when tg_table_name = 'lessons' then 'lesson' else 'faq' end, new.id, old.status, new.status, auth.uid());
    end if;
    return new;
end $$;

create trigger lessons_review before update on lessons for each row execute function enforce_review_workflow();
create trigger faqs_review before update on faqs for each row execute function enforce_review_workflow();

-- ---------------------------------------------------------------------------
-- Learning progress
-- ---------------------------------------------------------------------------
create table progress (
    user_id      uuid not null default auth.uid() references profiles (id) on delete cascade,
    lesson_id    text not null references lessons (id) on delete cascade,
    completed_at timestamptz not null default now(),
    quiz_score   int,
    primary key (user_id, lesson_id)
);

create table saved_lessons (
    user_id    uuid not null default auth.uid() references profiles (id) on delete cascade,
    lesson_id  text not null references lessons (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, lesson_id)
);

create table user_routes (
    user_id    uuid primary key default auth.uid() references profiles (id) on delete cascade,
    route_id   text references routes (id) on delete set null,
    updated_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Questions & answers
-- ---------------------------------------------------------------------------
create table questions (
    id               uuid primary key default gen_random_uuid(),
    client_id        uuid not null unique,          -- id generated on the device (idempotent retries)
    user_id          uuid not null default auth.uid() references profiles (id) on delete cascade,
    body             text not null check (length(body) between 10 and 2000),
    category_id      text references categories (id),
    lesson_id        text references lessons (id) on delete set null,
    status           ticket_status not null default 'sent',
    requires_scholar boolean not null default false, -- нужен квалифицированный специалист (фетва)
    answer           text,
    answered_by      uuid references profiles (id),
    answered_at      timestamptz,
    clarity_rating   int check (clarity_rating between 1 and 5),
    created_at       timestamptz not null default now()
);
create index questions_status_idx on questions (status, created_at);

-- ---------------------------------------------------------------------------
-- Notifications
-- ---------------------------------------------------------------------------
create table notification_settings (
    user_id       uuid primary key references auth.users (id) on delete cascade,
    reminders     reminder_frequency not null default 'off',
    new_materials boolean not null default true,
    answers       boolean not null default true,
    route_updates boolean not null default true
);

create table push_tokens (
    token      text primary key,
    user_id    uuid not null default auth.uid() references profiles (id) on delete cascade,
    platform   text not null check (platform in ('android', 'ios')),
    updated_at timestamptz not null default now()
);

create table broadcasts (
    id         bigint generated always as identity primary key,
    kind       text not null check (kind in ('new_materials', 'route_updates')),
    title      text not null,
    body       text not null,
    created_by uuid references profiles (id),
    sent_at    timestamptz,
    created_at timestamptz not null default now()
);

create trigger on_auth_user_created after insert on auth.users for each row execute function handle_new_user();

-- ---------------------------------------------------------------------------
-- Audit log for critical actions (ТЗ п. 5)
-- ---------------------------------------------------------------------------
create table audit_log (
    id         bigint generated always as identity primary key,
    actor_id   uuid,
    action     text not null,
    entity     text not null,
    entity_id  text,
    before     jsonb,
    after      jsonb,
    created_at timestamptz not null default now()
);

create function audit() returns trigger
language plpgsql security definer set search_path = public as $$
begin
    insert into audit_log (actor_id, action, entity, entity_id, before, after)
    values (
        auth.uid(), lower(tg_op), tg_table_name,
        coalesce((to_jsonb(new) ->> 'id'), (to_jsonb(old) ->> 'id')),
        case when tg_op <> 'INSERT' then to_jsonb(old) end,
        case when tg_op <> 'DELETE' then to_jsonb(new) end
    );
    return coalesce(new, old);
end $$;

create trigger audit_lessons after insert or update or delete on lessons for each row execute function audit();
create trigger audit_faqs after insert or update or delete on faqs for each row execute function audit();
create trigger audit_routes after insert or update or delete on routes for each row execute function audit();
create trigger audit_broadcasts after insert or update on broadcasts for each row execute function audit();
-- Only admins may change roles (a policy cannot compare with the old row, so a trigger does it).
create function guard_role_change() returns trigger
language plpgsql set search_path = public as $$
begin
    if new.role is distinct from old.role and not is_admin() then
        raise exception 'Менять роль может только администратор';
    end if;
    return new;
end $$;
create trigger profiles_guard_role before update on profiles for each row execute function guard_role_change();

create trigger audit_roles after update of role on profiles for each row execute function audit();

-- ---------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------
alter table languages enable row level security;
alter table profiles enable row level security;
alter table categories enable row level security;
alter table routes enable row level security;
alter table lessons enable row level security;
alter table route_lessons enable row level security;
alter table sources enable row level security;
alter table lesson_sources enable row level security;
alter table faqs enable row level security;
alter table review_events enable row level security;
alter table progress enable row level security;
alter table saved_lessons enable row level security;
alter table user_routes enable row level security;
alter table questions enable row level security;
alter table notification_settings enable row level security;
alter table push_tokens enable row level security;
alter table broadcasts enable row level security;
alter table audit_log enable row level security;

-- Public, published content is readable by everyone (guest mode works without login).
create policy "read languages" on languages for select using (true);
create policy "read categories" on categories for select using (true);
create policy "read published routes" on routes for select using (is_published or is_staff());
create policy "read published lessons" on lessons for select using (status = 'published' or is_staff());
create policy "read route lessons" on route_lessons for select using (true);
create policy "read sources" on sources for select using (true);
create policy "read lesson sources" on lesson_sources for select using (true);
create policy "read published faqs" on faqs for select using (status = 'published' or is_staff());

-- Content editing: staff only (the review trigger enforces who may approve/publish).
create policy "staff write categories" on categories for all using (is_staff()) with check (is_staff());
create policy "staff write routes" on routes for all using (is_staff()) with check (is_staff());
create policy "staff write lessons" on lessons for all using (is_staff()) with check (is_staff());
create policy "staff write route lessons" on route_lessons for all using (is_staff()) with check (is_staff());
create policy "staff write sources" on sources for all using (is_staff()) with check (is_staff());
create policy "staff write lesson sources" on lesson_sources for all using (is_staff()) with check (is_staff());
create policy "staff write faqs" on faqs for all using (is_staff()) with check (is_staff());
create policy "staff read review events" on review_events for select using (is_staff());

-- Profiles: own profile; admins manage roles.
create policy "own profile" on profiles for select using (id = auth.uid() or is_admin());
create policy "update own profile" on profiles for update using (id = auth.uid()) with check (id = auth.uid());
create policy "admin manage profiles" on profiles for update using (is_admin()) with check (is_admin());

-- Personal progress: owner only. No other user can ever read it (no public ratings).
create policy "own progress" on progress for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own saved" on saved_lessons for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own route" on user_routes for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own notification settings" on notification_settings for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy "own push tokens" on push_tokens for all using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Questions: the author creates and reads; staff answer.
create policy "ask question" on questions for insert with check (user_id = auth.uid() and answer is null and status = 'sent');
create policy "read own or staff" on questions for select using (user_id = auth.uid() or is_staff());
create policy "staff answer" on questions for update using (is_staff()) with check (is_staff());

create policy "admin broadcasts" on broadcasts for all using (is_admin()) with check (is_admin());
create policy "admin audit" on audit_log for select using (is_admin());

-- ---------------------------------------------------------------------------
-- RPCs used by the app
-- ---------------------------------------------------------------------------
create function rate_answer(p_client_id uuid, p_rating int) returns void
language sql security definer set search_path = public as $$
    update questions set clarity_rating = p_rating
    where client_id = p_client_id and user_id = auth.uid() and answer is not null and p_rating between 1 and 5;
$$;

create function set_saved_lessons(lesson_ids text[]) returns void
language plpgsql security definer set search_path = public as $$
begin
    delete from saved_lessons where user_id = auth.uid() and lesson_id <> all (lesson_ids);
    insert into saved_lessons (user_id, lesson_id)
    select auth.uid(), l from unnest(lesson_ids) l
    where exists (select 1 from lessons where id = l)
    on conflict do nothing;
end $$;

-- Right to erasure (ТЗ 6.3): removes the auth user; everything personal cascades.
create function delete_my_account() returns void
language plpgsql security definer set search_path = public, auth as $$
begin
    if auth.uid() is null then raise exception 'not authenticated'; end if;
    delete from auth.users where id = auth.uid();
end $$;

-- Published content in the exact JSON shape of the app's assets/content.json.
create function content_bundle() returns jsonb
language sql stable security definer set search_path = public as $$
select jsonb_build_object(
    'version', extract(epoch from coalesce((select max(updated_at) from lessons where status = 'published'), now()))::bigint,
    'routes', coalesce((
        select jsonb_agg(jsonb_build_object('id', r.id, 'title', r.title, 'description', r.description, 'icon', r.icon,
            'lessons', (select coalesce(jsonb_agg(rl.lesson_id order by rl.position), '[]') from route_lessons rl
                        join lessons l on l.id = rl.lesson_id and l.status = 'published' where rl.route_id = r.id))
            order by r.sort_order)
        from routes r where r.is_published), '[]'),
    'categories', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'title', title, 'icon', icon) order by sort_order) from categories), '[]'),
    'lessons', coalesce((
        select jsonb_agg(jsonb_build_object(
            'id', l.id, 'category', l.category_id, 'title', l.title, 'summary', l.summary, 'minutes', l.minutes,
            'blocks', l.blocks, 'quiz', l.quiz, 'related', to_jsonb(l.related), 'updated', l.updated_at::date,
            'sources', (select coalesce(jsonb_agg(jsonb_build_object('title', s.title, 'ref', s.reference) order by ls.position), '[]')
                        from lesson_sources ls join sources s on s.id = ls.source_id where ls.lesson_id = l.id),
            'review', jsonb_build_object('status', 'verified', 'reviewer', l.reviewer_name, 'date', l.reviewed_at,
                        'editor', (select display_name from profiles where id = l.editor_id))))
        from lessons l where l.status = 'published'), '[]'),
    'faqs', coalesce((
        select jsonb_agg(jsonb_build_object('id', f.id, 'question', f.question, 'answer', f.answer, 'category', f.category_id,
            'popular', f.popular, 'sources', f.sources,
            'review', jsonb_build_object('status', 'verified', 'reviewer', f.reviewer_name, 'date', f.reviewed_at)))
        from faqs f where f.status = 'published'), '[]')
);
$$;

-- Anonymised usage statistics for the admin panel (ТЗ п. 12). No per-user rows leave this function.
create function admin_stats() returns jsonb
language plpgsql stable security definer set search_path = public as $$
begin
    if not is_admin() then raise exception 'Недостаточно прав'; end if;
    return jsonb_build_object(
        'users_total', (select count(*) from profiles),
        'users_completed_first_lesson', (select count(distinct user_id) from progress),
        'retention_d7', (select count(distinct p.user_id) from progress p join profiles u on u.id = p.user_id
                         where p.completed_at >= u.created_at + interval '7 days'),
        'retention_d30', (select count(distinct p.user_id) from progress p join profiles u on u.id = p.user_id
                          where p.completed_at >= u.created_at + interval '30 days'),
        'questions_total', (select count(*) from questions),
        'questions_answered', (select count(*) from questions where status = 'answered'),
        'avg_answer_hours', (select round(avg(extract(epoch from answered_at - created_at) / 3600)::numeric, 1) from questions where answered_at is not null),
        'avg_clarity', (select round(avg(clarity_rating)::numeric, 2) from questions where clarity_rating is not null)
    );
end $$;

revoke all on function admin_stats() from anon;
revoke all on function delete_my_account() from anon;
grant execute on function content_bundle() to anon, authenticated;
