-- =====================================================================
-- Steelsight ERP - PostgreSQL schema (v3)
-- Written for PostgreSQL 17 and 18, so it also fits Supabase. Needs the pgvector extension.
--
-- The idea in plain words:
--   1. PERMISSION says WHAT a role can do          (TICKET_UPDATE_HANDLED, SHIFT_UPDATE_TEAM ...)
--   2. SCOPE says on WHICH records it can do it    (ticket category -> handler role, worker -> team -> manager)
--   A request is allowed only if BOTH pass.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- needed to stop overlapping shifts
CREATE EXTENSION IF NOT EXISTS vector;       -- pgvector, stores face embeddings

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
                          version        bigint      NOT NULL DEFAULT 0,  -- optimistic lock: a stale edit fails
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
    -- computed on write: handy for "hours this week" on the timetable screen
                       duration_hours numeric GENERATED ALWAYS AS (extract(epoch FROM (end_at - start_at)) / 3600.0) STORED,
                       status     text NOT NULL DEFAULT 'SCHEDULED'
                           CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
                       created_by bigint NOT NULL REFERENCES app_user(id),
                       updated_by bigint REFERENCES app_user(id),
                       version    bigint NOT NULL DEFAULT 0,                 -- optimistic lock: a stale edit fails
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
                           user_id         bigint REFERENCES app_user(id),     -- the worker; NULL = not identified yet, HSE assigns it
                           ppe_linking_id  bigint REFERENCES ppe_linking(id),  -- vest read by OCR; NULL for no vest or an unreadable one
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
                           version         bigint NOT NULL DEFAULT 0,      -- optimistic lock: two HSE officers cannot both decide
                           created_at      timestamptz NOT NULL DEFAULT now(),
                           updated_at      timestamptz NOT NULL DEFAULT now(),
                           CHECK (dispute_status NOT IN ('UPHELD','OVERTURNED')
                               OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    -- only a known worker can dispute
                           CHECK (dispute_status = 'NONE' OR user_id IS NOT NULL)
);

CREATE INDEX idx_violation_link_time ON violation(ppe_linking_id, detected_at DESC);
CREATE INDEX idx_violation_user_time ON violation(user_id, detected_at DESC);
-- the HSE queue of violations nobody has been assigned to yet
CREATE INDEX idx_violation_unidentified ON violation(detected_at DESC) WHERE user_id IS NULL;

-- A vest read names the worker: fill user_id from the link, and refuse a link to someone else
CREATE OR REPLACE FUNCTION violation_set_worker() RETURNS trigger AS $$
DECLARE
  v_user bigint;
BEGIN
  IF NEW.ppe_linking_id IS NOT NULL THEN
    SELECT user_id INTO v_user FROM ppe_linking WHERE id = NEW.ppe_linking_id;
    IF NEW.user_id IS NULL THEN
      NEW.user_id = v_user;
    ELSIF NEW.user_id <> v_user THEN
      RAISE EXCEPTION 'violation user % does not match vest link user %', NEW.user_id, v_user;
    END IF;
  END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_violation_worker BEFORE INSERT OR UPDATE ON violation
    FOR EACH ROW EXECUTE FUNCTION violation_set_worker();
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
                        version     bigint NOT NULL DEFAULT 0,                -- optimistic lock: a stale edit fails
                        created_at  timestamptz NOT NULL DEFAULT now(),
                        updated_at  timestamptz NOT NULL DEFAULT now(),
                        deleted_at  timestamptz,                              -- soft delete
                        -- resolver and time are set exactly when the ticket is resolved
                        CHECK ((status = 'RESOLVED') = (resolver_id IS NOT NULL)),
                        CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

CREATE INDEX idx_ticket_creator  ON ticket(created_by, created_at DESC);
CREATE INDEX idx_ticket_category ON ticket(category_id, status);
-- a violation can have only one live dispute ticket
CREATE UNIQUE INDEX uq_ticket_violation ON ticket(violation_id) WHERE violation_id IS NOT NULL AND deleted_at IS NULL;
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
SELECT (has_permission(p_user, 'TICKET_UPDATE_ALL')
            AND EXISTS (SELECT 1 FROM ticket t WHERE t.id = p_ticket AND t.deleted_at IS NULL))
    OR (has_permission(p_user, 'TICKET_UPDATE_HANDLED')
           AND EXISTS (
        SELECT 1
        FROM ticket t
                 JOIN ticket_category c ON c.id = t.category_id
                 JOIN app_user u        ON u.id = p_user AND u.role_id = c.handler_role_id
        WHERE t.id = p_ticket AND t.deleted_at IS NULL));
$$ LANGUAGE sql STABLE;

-- A manager can edit a shift only for workers in a team the manager runs
CREATE OR REPLACE FUNCTION can_edit_shift(p_user bigint, p_shift bigint) RETURNS boolean AS $$
SELECT (has_permission(p_user, 'SHIFT_UPDATE_ALL')
            AND EXISTS (SELECT 1 FROM shift s WHERE s.id = p_shift))
    OR (has_permission(p_user, 'SHIFT_UPDATE_TEAM')
           AND EXISTS (
        SELECT 1
        FROM shift s
                 JOIN app_user w ON w.id = s.user_id
                 JOIN team t     ON t.id = w.team_id AND t.manager_id = p_user
        WHERE s.id = p_shift));
$$ LANGUAGE sql STABLE;

-- A manager can edit workers in a team the manager runs (not themselves); USER_MANAGE covers everyone
CREATE OR REPLACE FUNCTION can_update_user(p_user bigint, p_target bigint) RETURNS boolean AS $$
SELECT (has_permission(p_user, 'USER_MANAGE')
            AND EXISTS (SELECT 1 FROM app_user w WHERE w.id = p_target AND w.deleted_at IS NULL))
    OR (has_permission(p_user, 'USER_UPDATE_TEAM')
           AND EXISTS (
        SELECT 1
        FROM app_user w
                 JOIN team t ON t.id = w.team_id AND t.manager_id = p_user
        WHERE w.id = p_target AND w.id <> p_user AND w.deleted_at IS NULL));
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
                                                               ('TICKET_READ_ALL',        'TICKET',    'READ',   'See every ticket'),
                                                               ('TICKET_UPDATE_ALL',      'TICKET',    'UPDATE', 'Reply, assign, resolve any ticket'),
                                                               ('TICKET_DELETE_ALL',      'TICKET',    'DELETE', 'Soft-delete any ticket'),
                                                               ('TICKET_DELETE_HANDLED',  'TICKET',    'DELETE', 'Soft-delete tickets in my role''s categories'),
                                                               ('SHIFT_READ_OWN',         'SHIFT',     'READ',   'See my own timetable'),
                                                               ('SHIFT_READ_TEAM',        'SHIFT',     'READ',   'See the timetable of my team'),
                                                               ('SHIFT_UPDATE_TEAM',      'SHIFT',     'UPDATE', 'Create and edit shifts for my team'),
                                                               ('SHIFT_READ_ALL',         'SHIFT',     'READ',   'See every timetable'),
                                                               ('SHIFT_UPDATE_ALL',       'SHIFT',     'UPDATE', 'Create and edit any shift'),
                                                               ('VIOLATION_READ_OWN',     'VIOLATION', 'READ',   'See my own PPE violations'),
                                                               ('VIOLATION_READ_ALL',     'VIOLATION', 'READ',   'See all PPE violations'),
                                                               ('VIOLATION_REVIEW',       'VIOLATION', 'REVIEW', 'Decide a disputed violation'),
                                                               ('USER_MANAGE',            'USER',      'MANAGE', 'Create users, change roles'),
                                                               ('USER_UPDATE_TEAM',       'USER',      'UPDATE', 'Edit workers on my team; no role changes or new accounts'),
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
          ('MANAGER',     'SHIFT_READ_TEAM'), ('MANAGER', 'SHIFT_UPDATE_TEAM'), ('MANAGER', 'USER_UPDATE_TEAM')
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
-- Check-in table
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
                          auto_closed     boolean NOT NULL DEFAULT false,     -- check_out_at is an estimate, the worker never checked out

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

-- The shift and vest link on a visit must belong to the worker on that visit
CREATE OR REPLACE FUNCTION check_in_same_worker() RETURNS trigger AS $$
BEGIN
  IF NEW.shift_id IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM shift WHERE id = NEW.shift_id AND user_id = NEW.user_id) THEN
    RAISE EXCEPTION 'shift % does not belong to user %', NEW.shift_id, NEW.user_id;
  END IF;
  IF NEW.ppe_linking_id IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM ppe_linking WHERE id = NEW.ppe_linking_id AND user_id = NEW.user_id) THEN
    RAISE EXCEPTION 'vest link % does not belong to user %', NEW.ppe_linking_id, NEW.user_id;
  END IF;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_in_worker BEFORE INSERT OR UPDATE OF user_id, shift_id, ppe_linking_id ON check_in
    FOR EACH ROW EXECUTE FUNCTION check_in_same_worker();

-- Closes visits nobody checked out of, otherwise uq_check_in_open blocks the worker's next check-in.
-- Closes at shift end + p_grace, or check-in + p_no_shift_limit when there is no shift.
-- The detection service calls this on a timer and before each check-in, because it stays up when the ERP sleeps.
CREATE OR REPLACE FUNCTION close_stale_check_ins(
    p_grace          interval DEFAULT interval '2 hours',
    p_no_shift_limit interval DEFAULT interval '16 hours') RETURNS integer AS $$
WITH closed AS (
    UPDATE check_in c
    SET check_out_at = d.close_at, auto_closed = true
    FROM (
        SELECT o.id,
               GREATEST(COALESCE(s.end_at + p_grace, o.check_in_at + p_no_shift_limit),
                        o.check_in_at + interval '1 minute') AS close_at   -- still after check_in_at for a late arrival
        FROM check_in o
                 LEFT JOIN shift s ON s.id = o.shift_id
        WHERE o.check_out_at IS NULL
    ) d
    WHERE c.id = d.id AND d.close_at < now()
    RETURNING c.id
)
SELECT count(*)::integer FROM closed;
$$ LANGUAGE sql;

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
-- Face embeddings  (one row per worker, also the enrolment queue)
--   1. The ERP saves the photo, sets app_user.face_file_path and upserts a PENDING row.
--   2. The detection service picks up PENDING rows, computes the embedding and sets READY or FAILED.
--   3. The detection service loads READY rows of active workers into memory to match faces.
-- Biometric data: delete the row when a worker is soft-deleted or withdraws consent.
-- ---------------------------------------------------------------------
CREATE TABLE face_embedding (
                                id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                user_id          bigint NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
                                source_file_path text   NOT NULL,                -- the photo this row was made from
                                status           text   NOT NULL DEFAULT 'PENDING'
                                    CHECK (status IN ('PENDING','READY','FAILED')),
                                embedding        vector(512),                    -- InsightFace output
                                model_name       text,                           -- embeddings from different models do not compare
                                error_message    text,                           -- e.g. no face found in the photo
                                created_at       timestamptz NOT NULL DEFAULT now(),
                                updated_at       timestamptz NOT NULL DEFAULT now(),
                                CHECK (status <> 'READY' OR (embedding IS NOT NULL AND model_name IS NOT NULL))
);

CREATE TRIGGER trg_face_embedding_updated BEFORE UPDATE ON face_embedding
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Soft-deleting a worker erases the embedding in the same transaction
CREATE OR REPLACE FUNCTION app_user_erase_embedding() RETURNS trigger AS $$
BEGIN
DELETE FROM face_embedding WHERE user_id = NEW.id;
RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_erase_embedding AFTER UPDATE OF deleted_at ON app_user
    FOR EACH ROW WHEN (OLD.deleted_at IS NULL AND NEW.deleted_at IS NOT NULL)
    EXECUTE FUNCTION app_user_erase_embedding();

-- ---------------------------------------------------------------------
-- Database role for the detection service: only what it needs, no password hashes
-- Created without login, so no password sits in this file. Run this by hand once:
--   ALTER ROLE steelsight_detection LOGIN PASSWORD '<a strong password>';
-- The ERP connects as the table owner, because it runs the migrations.
-- ---------------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'steelsight_detection') THEN
    CREATE ROLE steelsight_detection NOLOGIN;
  END IF;
END $$;

GRANT USAGE ON SCHEMA public TO steelsight_detection;

GRANT SELECT ON zone, camera, ppe_vest, shift TO steelsight_detection;
GRANT SELECT (id, employee_code, face_file_path, is_active, deleted_at) ON app_user TO steelsight_detection;
GRANT SELECT, INSERT ON ppe_linking TO steelsight_detection;
GRANT UPDATE (unlinked_at) ON ppe_linking TO steelsight_detection;
GRANT SELECT, INSERT ON check_in TO steelsight_detection;
GRANT UPDATE (check_out_at, auto_closed, ppe_linking_id) ON check_in TO steelsight_detection;
GRANT SELECT ON violation TO steelsight_detection;
GRANT INSERT (user_id, ppe_linking_id, camera_id, violation_type, detected_at, confidence,
              snapshot_path, box_x, box_y, box_w, box_h) ON violation TO steelsight_detection;
GRANT SELECT ON face_embedding TO steelsight_detection;
GRANT UPDATE (status, embedding, model_name, error_message) ON face_embedding TO steelsight_detection;

REVOKE EXECUTE ON FUNCTION close_stale_check_ins(interval, interval) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION close_stale_check_ins(interval, interval) TO steelsight_detection;

-- RLS is switched on by afterMigrate__enable_rls.sql, so each grant above also needs a policy
CREATE POLICY detection_read ON zone           FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_read ON camera         FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_read ON ppe_vest       FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_read ON shift          FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_read ON app_user       FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_read ON ppe_linking    FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_add  ON ppe_linking    FOR INSERT TO steelsight_detection WITH CHECK (true);
CREATE POLICY detection_edit ON ppe_linking    FOR UPDATE TO steelsight_detection USING (true) WITH CHECK (true);
CREATE POLICY detection_read ON check_in       FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_add  ON check_in       FOR INSERT TO steelsight_detection WITH CHECK (true);
CREATE POLICY detection_edit ON check_in       FOR UPDATE TO steelsight_detection USING (true) WITH CHECK (true);
CREATE POLICY detection_read ON violation      FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_add  ON violation      FOR INSERT TO steelsight_detection WITH CHECK (true);
CREATE POLICY detection_read ON face_embedding FOR SELECT TO steelsight_detection USING (true);
CREATE POLICY detection_edit ON face_embedding FOR UPDATE TO steelsight_detection USING (true) WITH CHECK (true);
