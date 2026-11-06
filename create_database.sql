-- =====================================================================
-- Steelsight ERP - PostgreSQL 18 schema (v2)
-- Tested on PostgreSQL 18.6. Uses one PostgreSQL 18 feature: a VIRTUAL generated column
-- (shift.duration_hours). On PostgreSQL 17 or older, change VIRTUAL to STORED.
--
-- The idea in plain words:
--   1. PERMISSION says WHAT a role can do          (TICKET_UPDATE_HANDLED, SHIFT_UPDATE_TEAM ...)
--   2. SCOPE says on WHICH records it can do it    (ticket category -> handler role, worker -> team -> manager)
--   A request is allowed only if BOTH pass.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- needed to stop overlapping shifts

-- Keeps updated_at correct without app code
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- 1. Roles and permissions  (one row = one action, not a bundle of flags)
-- ---------------------------------------------------------------------
CREATE TABLE role (
                      id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      role_name text NOT NULL UNIQUE                  -- WORKER, HSE_OFFICER, MANAGER, ADMIN
);

CREATE TABLE permission (
                            id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                            code        text NOT NULL UNIQUE,               -- what Spring loads as an authority
                            module      text NOT NULL,                      -- TICKET, SHIFT, VIOLATION, USER
                            action      text NOT NULL,                      -- READ, CREATE, UPDATE, DELETE, REVIEW, MANAGE
                            description text
);

CREATE TABLE role_permission (
                                 role_id       bigint NOT NULL REFERENCES role(id)       ON DELETE CASCADE,
                                 permission_id bigint NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
                                 PRIMARY KEY (role_id, permission_id)            -- also blocks duplicates
);

-- ---------------------------------------------------------------------
-- 2. Users, teams, zones
-- ---------------------------------------------------------------------
CREATE TABLE zone (
                      id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      name text NOT NULL UNIQUE                       -- Zone A, Zone B ...
);

CREATE TABLE team (
                      id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      name       text NOT NULL UNIQUE,
                      manager_id bigint                               -- FK added below (users do not exist yet)
);

CREATE TABLE app_user (                           -- "user" is a reserved word in PostgreSQL
                          id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          role_id        bigint NOT NULL REFERENCES role(id),
                          team_id        bigint REFERENCES team(id),      -- workers belong to a team; the team has a manager
                          employee_code  text   NOT NULL UNIQUE,          -- used on the login page
                          full_name      text   NOT NULL,
                          email          text   UNIQUE,
                          phone_number   text,
                          password_hash  text   NOT NULL,
                          face_file_path text,
                          is_active      boolean     NOT NULL DEFAULT true,
                          created_at     timestamptz NOT NULL DEFAULT now(),
                          updated_at     timestamptz NOT NULL DEFAULT now(),
                          deleted_at     timestamptz                      -- soft delete
);

ALTER TABLE team
    ADD CONSTRAINT fk_team_manager FOREIGN KEY (manager_id) REFERENCES app_user(id);

CREATE INDEX idx_user_team ON app_user(team_id);
CREATE TRIGGER trg_user_updated BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- 3. Shift timetable  (a day off is simply "no row")
-- ---------------------------------------------------------------------
CREATE TABLE shift (
                       id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       user_id    bigint NOT NULL REFERENCES app_user(id),   -- the worker
                       zone_id    bigint NOT NULL REFERENCES zone(id),
                       start_at   timestamptz NOT NULL,
                       end_at     timestamptz NOT NULL,
    -- computed when read, not stored: handy for "hours this week" on the timetable screen
                       duration_hours numeric GENERATED ALWAYS AS (extract(epoch FROM (end_at - start_at)) / 3600.0) VIRTUAL,
                       status     text NOT NULL DEFAULT 'SCHEDULED'
                           CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
                       created_by bigint NOT NULL REFERENCES app_user(id),
                       updated_by bigint REFERENCES app_user(id),
                       created_at timestamptz NOT NULL DEFAULT now(),
                       updated_at timestamptz NOT NULL DEFAULT now(),
                       CHECK (end_at > start_at),
    -- one worker cannot have two overlapping active shifts
                       EXCLUDE USING gist (user_id WITH =, tstzrange(start_at, end_at) WITH &&)
    WHERE (status <> 'CANCELLED')
);

CREATE INDEX idx_shift_user_start ON shift(user_id, start_at);
CREATE TRIGGER trg_shift_updated BEFORE UPDATE ON shift
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- 4. PPE: vests, vest-to-worker links, cameras, violations
-- ---------------------------------------------------------------------
CREATE TABLE ppe_vest (
                          id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          vest_code text NOT NULL UNIQUE
);

CREATE TABLE ppe_linking (                        -- who wore which vest, and when
                             id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             ppe_vest_id bigint NOT NULL REFERENCES ppe_vest(id),
                             user_id     bigint NOT NULL REFERENCES app_user(id),
                             linked_at   timestamptz NOT NULL DEFAULT now(),
                             unlinked_at timestamptz,                        -- NULL = still active
                             CHECK (unlinked_at IS NULL OR unlinked_at > linked_at)
);
-- a vest has at most one active wearer, and a worker at most one active vest
CREATE UNIQUE INDEX uq_link_active_vest ON ppe_linking(ppe_vest_id) WHERE unlinked_at IS NULL;
CREATE UNIQUE INDEX uq_link_active_user ON ppe_linking(user_id)     WHERE unlinked_at IS NULL;

CREATE TABLE camera (
                        id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        camera_code text   NOT NULL UNIQUE,             -- CAM-B2
                        zone_id     bigint NOT NULL REFERENCES zone(id)
);

CREATE TABLE violation (
                           id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           ppe_linking_id  bigint NOT NULL REFERENCES ppe_linking(id),  -- worker = linking.user_id
                           camera_id       bigint NOT NULL REFERENCES camera(id),
                           violation_type  text   NOT NULL,                -- NO_HARD_HAT, NO_VEST, NO_GLOVES, NO_HARNESS
                           detected_at     timestamptz NOT NULL,           -- when the camera saw it
                           confidence      numeric(5,2) CHECK (confidence BETWEEN 0 AND 100),
                           snapshot_path   text   NOT NULL,                -- camera image shown to the worker
                           box_x numeric(5,2), box_y numeric(5,2),         -- detection box, % of the image
                           box_w numeric(5,2), box_h numeric(5,2),
    -- the machine is right until the worker disputes; HSE then decides
                           dispute_status  text NOT NULL DEFAULT 'NONE'
                               CHECK (dispute_status IN ('NONE','DISPUTED','UPHELD','OVERTURNED')),
                           reviewed_by     bigint REFERENCES app_user(id),
                           reviewed_at     timestamptz,
                           created_at      timestamptz NOT NULL DEFAULT now(),
                           updated_at      timestamptz NOT NULL DEFAULT now(),
                           CHECK (dispute_status NOT IN ('UPHELD','OVERTURNED')
                               OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
);

CREATE INDEX idx_violation_link_time ON violation(ppe_linking_id, detected_at DESC);
CREATE TRIGGER trg_violation_updated BEFORE UPDATE ON violation
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- 5. Tickets  (each category belongs to the role that handles it)
-- ---------------------------------------------------------------------
CREATE TABLE ticket_category (
                                 id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 category_name   text   NOT NULL UNIQUE,
                                 handler_role_id bigint NOT NULL REFERENCES role(id)   -- <- the scope rule lives here
);

CREATE TABLE ticket (
                        id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        category_id bigint NOT NULL REFERENCES ticket_category(id),
                        subject     text   NOT NULL,
                        description text   NOT NULL,
                        created_by  bigint NOT NULL REFERENCES app_user(id),  -- the worker who opened it
                        assignee_id bigint REFERENCES app_user(id),           -- who is working on it
                        resolver_id bigint REFERENCES app_user(id),           -- who closed it
                        violation_id bigint REFERENCES violation(id),         -- set when disputing a PPE detection
                        status      text NOT NULL DEFAULT 'OPEN'
                            CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED')),
                        resolved_at timestamptz,
                        created_at  timestamptz NOT NULL DEFAULT now(),
                        updated_at  timestamptz NOT NULL DEFAULT now(),
                        deleted_at  timestamptz,                              -- soft delete
                        CHECK (status <> 'RESOLVED' OR (resolver_id IS NOT NULL AND resolved_at IS NOT NULL))
);

CREATE INDEX idx_ticket_creator  ON ticket(created_by, created_at DESC);
CREATE INDEX idx_ticket_category ON ticket(category_id, status);
CREATE TRIGGER trg_ticket_updated BEFORE UPDATE ON ticket
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE ticket_comment (
                                id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                ticket_id  bigint NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
                                author_id  bigint NOT NULL REFERENCES app_user(id),
                                body       text   NOT NULL,
                                created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_comment_ticket ON ticket_comment(ticket_id, created_at);

-- ---------------------------------------------------------------------
-- 6. Rule helpers: "permission AND scope"
--    Spring can call these, or copy the same logic into a service method.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION has_permission(p_user bigint, p_code text) RETURNS boolean AS $$
SELECT EXISTS (
    SELECT 1
    FROM app_user u
             JOIN role_permission rp ON rp.role_id = u.role_id
             JOIN permission p       ON p.id = rp.permission_id
    WHERE u.id = p_user AND u.is_active AND u.deleted_at IS NULL AND p.code = p_code);
$$ LANGUAGE sql STABLE;

-- HSE can edit a ticket only if its category is handled by HSE, a manager only for the manager's categories ...
CREATE OR REPLACE FUNCTION can_update_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT has_permission(p_user, 'TICKET_UPDATE_HANDLED')
           AND EXISTS (
        SELECT 1
        FROM ticket t
                 JOIN ticket_category c ON c.id = t.category_id
                 JOIN app_user u        ON u.id = p_user AND u.role_id = c.handler_role_id
        WHERE t.id = p_ticket AND t.deleted_at IS NULL);
$$ LANGUAGE sql STABLE;

-- A manager can edit a shift only for workers in a team the manager runs
CREATE OR REPLACE FUNCTION can_edit_shift(p_user bigint, p_shift bigint) RETURNS boolean AS $$
SELECT has_permission(p_user, 'SHIFT_UPDATE_TEAM')
           AND EXISTS (
        SELECT 1
        FROM shift s
                 JOIN app_user w ON w.id = s.user_id
                 JOIN team t     ON t.id = w.team_id AND t.manager_id = p_user
        WHERE s.id = p_shift);
$$ LANGUAGE sql STABLE;

-- ---------------------------------------------------------------------
-- 7. Seed data
-- ---------------------------------------------------------------------
INSERT INTO role (role_name) VALUES ('WORKER'), ('HSE_OFFICER'), ('MANAGER'), ('ADMIN');

INSERT INTO permission (code, module, action, description) VALUES
                                                               ('TICKET_CREATE',          'TICKET',    'CREATE', 'Open a ticket in any category'),
                                                               ('TICKET_READ_OWN',        'TICKET',    'READ',   'See tickets I opened'),
                                                               ('TICKET_READ_HANDLED',    'TICKET',    'READ',   'See tickets in categories my role handles'),
                                                               ('TICKET_UPDATE_HANDLED',  'TICKET',    'UPDATE', 'Reply, assign, resolve tickets in my role''s categories'),
                                                               ('TICKET_DELETE_HANDLED',  'TICKET',    'DELETE', 'Soft-delete tickets in my role''s categories'),
                                                               ('SHIFT_READ_OWN',         'SHIFT',     'READ',   'See my own timetable'),
                                                               ('SHIFT_READ_TEAM',        'SHIFT',     'READ',   'See the timetable of my team'),
                                                               ('SHIFT_UPDATE_TEAM',      'SHIFT',     'UPDATE', 'Create and edit shifts for my team'),
                                                               ('VIOLATION_READ_OWN',     'VIOLATION', 'READ',   'See my own PPE violations'),
                                                               ('VIOLATION_READ_ALL',     'VIOLATION', 'READ',   'See all PPE violations'),
                                                               ('VIOLATION_REVIEW',       'VIOLATION', 'REVIEW', 'Decide a disputed violation'),
                                                               ('USER_MANAGE',            'USER',      'MANAGE', 'Create users, change roles'),
                                                               ('ROLE_MANAGE',            'USER',      'MANAGE', 'Edit roles and permissions');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
          ('WORKER',      'TICKET_CREATE'), ('WORKER', 'TICKET_READ_OWN'),
          ('WORKER',      'SHIFT_READ_OWN'), ('WORKER', 'VIOLATION_READ_OWN'),

          ('HSE_OFFICER', 'TICKET_CREATE'), ('HSE_OFFICER', 'TICKET_READ_OWN'), ('HSE_OFFICER', 'SHIFT_READ_OWN'),
          ('HSE_OFFICER', 'TICKET_READ_HANDLED'), ('HSE_OFFICER', 'TICKET_UPDATE_HANDLED'), ('HSE_OFFICER', 'TICKET_DELETE_HANDLED'),
          ('HSE_OFFICER', 'VIOLATION_READ_ALL'), ('HSE_OFFICER', 'VIOLATION_REVIEW'),

          ('MANAGER',     'TICKET_CREATE'), ('MANAGER', 'TICKET_READ_OWN'), ('MANAGER', 'SHIFT_READ_OWN'),
          ('MANAGER',     'TICKET_READ_HANDLED'), ('MANAGER', 'TICKET_UPDATE_HANDLED'), ('MANAGER', 'TICKET_DELETE_HANDLED'),
          ('MANAGER',     'SHIFT_READ_TEAM'), ('MANAGER', 'SHIFT_UPDATE_TEAM')
     ) AS v(role_name, code)
         JOIN role r       ON r.role_name = v.role_name
         JOIN permission p ON p.code = v.code;

-- Admin gets everything
INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r CROSS JOIN permission p WHERE r.role_name = 'ADMIN';

-- Which role handles which ticket category
INSERT INTO ticket_category (category_name, handler_role_id)
SELECT v.name, r.id
FROM (VALUES
          ('PPE dispute',             'HSE_OFFICER'),
          ('Safety concern',          'HSE_OFFICER'),
          ('Shift or timetable issue','MANAGER'),
          ('Equipment request',       'MANAGER'),
          ('Account or access',       'ADMIN')
     ) AS v(name, role_name)
         JOIN role r ON r.role_name = v.role_name;

-- =====================================================================
-- Steelsight ERP - check-in table (additive migration)
-- Run AFTER steelsight_schema.sql. Works on PostgreSQL 17 and 18 (so also on Supabase).
--
-- One row = one visit to site: face check-in, then check-out.
-- The detection service creates the row (method FACE) and links the vest;
-- HSE can add or correct a row by hand (method MANUAL).
-- =====================================================================

CREATE TABLE check_in (
                          id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          user_id         bigint NOT NULL REFERENCES app_user(id),
                          shift_id        bigint REFERENCES shift(id),        -- planned shift this visit belongs to (may be NULL)
                          zone_id         bigint REFERENCES zone(id),
                          camera_id       bigint REFERENCES camera(id),       -- camera that recognised the face (NULL for MANUAL)
                          ppe_linking_id  bigint REFERENCES ppe_linking(id),  -- vest the worker wore at check-in

                          check_in_at     timestamptz NOT NULL DEFAULT now(),
                          check_out_at    timestamptz,                        -- NULL = still on site

                          method          text NOT NULL DEFAULT 'FACE'
                              CHECK (method IN ('FACE', 'MANUAL')),
                          face_similarity numeric(5,2)                        -- InsightFace match score, 0-100
                              CHECK (face_similarity BETWEEN 0 AND 100),
                          snapshot_path   text,                               -- face image taken at the gate
                          recorded_by     bigint REFERENCES app_user(id),     -- who typed it in (MANUAL only)
                          note            text,

                          created_at      timestamptz NOT NULL DEFAULT now(),
                          updated_at      timestamptz NOT NULL DEFAULT now(),

                          CHECK (check_out_at IS NULL OR check_out_at > check_in_at),
    -- a face check-in must say how sure the match was; a manual one must say who did it
                          CHECK (method <> 'FACE'   OR face_similarity IS NOT NULL),
                          CHECK (method <> 'MANUAL' OR recorded_by IS NOT NULL)
);

-- A worker can only be checked in once at a time
CREATE UNIQUE INDEX uq_check_in_open ON check_in(user_id) WHERE check_out_at IS NULL;

CREATE INDEX idx_check_in_user_time ON check_in(user_id, check_in_at DESC);
CREATE INDEX idx_check_in_shift     ON check_in(shift_id);

CREATE TRIGGER trg_check_in_updated BEFORE UPDATE ON check_in
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ---------------------------------------------------------------------
-- Permissions for the new table (same style as the rest of the schema)
-- ---------------------------------------------------------------------
INSERT INTO permission (code, module, action, description) VALUES
                                                               ('CHECKIN_READ_OWN',  'CHECKIN', 'READ',   'See my own check-ins'),
                                                               ('CHECKIN_READ_TEAM', 'CHECKIN', 'READ',   'See check-ins of my team'),
                                                               ('CHECKIN_READ_ALL',  'CHECKIN', 'READ',   'See all check-ins'),
                                                               ('CHECKIN_EDIT',      'CHECKIN', 'UPDATE', 'Add or correct a check-in by hand');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
          ('WORKER',      'CHECKIN_READ_OWN'),
          ('HSE_OFFICER', 'CHECKIN_READ_OWN'), ('HSE_OFFICER', 'CHECKIN_READ_ALL'), ('HSE_OFFICER', 'CHECKIN_EDIT'),
          ('MANAGER',     'CHECKIN_READ_OWN'), ('MANAGER',     'CHECKIN_READ_TEAM')
     ) AS v(role_name, code)
         JOIN role r       ON r.role_name = v.role_name
         JOIN permission p ON p.code = v.code;

INSERT INTO role_permission (role_id, permission_id)   -- Admin gets the new ones too
SELECT r.id, p.id FROM role r CROSS JOIN permission p
WHERE r.role_name = 'ADMIN' AND p.module = 'CHECKIN';

-- ---------------------------------------------------------------------
-- SUPABASE ONLY (safe to skip on a local database)
-- Supabase exposes every table in the "public" schema through its public
-- Data API. Turning on Row Level Security with NO policies blocks that API
-- completely, while your Spring Boot and Python services keep working,
-- because they connect as the table owner role.
-- ---------------------------------------------------------------------
DO $$
DECLARE t record;
BEGIN
FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' LOOP
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
END LOOP;
END $$;