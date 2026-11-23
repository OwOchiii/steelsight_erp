-- Violation review, zones with an owning team, the safety score and in-app-first notifications, as the
-- SRS (23/11/2026) and the team's zone decision say:
--   * every violation starts PENDING_REVIEW with no person; an HSE Officer (or the Admin) confirms the
--     person, marks a stranger or dismisses it (BR-48 to BR-53);
--   * a work zone has any number of cameras and belongs to one team; the HSE Officer picks the person
--     from that team first. The gate PPE zone keeps the CAM-01 suggestion (BR-46) and has no team;
--   * the score is 100 - 5 x confirmed violations, for the Site group only, with bonus levels 0 to 3
--     (BR-56 to BR-58);
--   * every notice is saved in the ERP first; a delivery row says whether Zalo still has to send it
--     (BR-15, BR-16). Each new violation alerts the HSE Officers on duty (BR-54).
--
-- The detection app writes violations straight into this table and isn't changed yet, so nothing it
-- writes is dropped or renamed: a person it names becomes the suggestion, and the vest columns stay.

-- ---------------------------------------------------------------------------------------------------
-- Staff group and face consent (BR-06, BR-10, BR-56)
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE app_user
    ADD COLUMN staff_group     text NOT NULL DEFAULT 'SITE' CHECK (staff_group IN ('SITE', 'OFFICE')),
    ADD COLUMN face_consent_at timestamptz;

-- HSE Officers and Admins work from the office side; workers and team leaders are on site.
UPDATE app_user u SET staff_group = 'OFFICE'
FROM role r WHERE r.id = u.role_id AND r.role_name IN ('HSE_OFFICER', 'ADMIN');

-- People enrolled before this release signed the paper form the old way.
UPDATE app_user SET face_consent_at = created_at WHERE face_file_path IS NOT NULL;

-- ---------------------------------------------------------------------------------------------------
-- Zones and cameras (SRS 1.4, CR-03; zone ownership is the team's decision)
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE zone
    ADD COLUMN kind    text NOT NULL DEFAULT 'WORK_AREA' CHECK (kind IN ('GATE_ATTENDANCE', 'GATE_PPE', 'WORK_AREA')),
    ADD COLUMN team_id bigint REFERENCES team(id) ON DELETE SET NULL,
    ADD CONSTRAINT zone_team_only_in_work_areas CHECK (team_id IS NULL OR kind = 'WORK_AREA');
CREATE INDEX idx_zone_team ON zone(team_id);

ALTER TABLE camera
    ADD COLUMN role text NOT NULL DEFAULT 'PPE' CHECK (role IN ('ATTENDANCE', 'PPE'));

-- A camera that has recorded check-ins is the attendance camera, and its zone is the gate.
UPDATE camera c SET role = 'ATTENDANCE' WHERE EXISTS (SELECT 1 FROM check_in ci WHERE ci.camera_id = c.id);
UPDATE zone z SET kind = 'GATE_ATTENDANCE'
WHERE EXISTS (SELECT 1 FROM camera c WHERE c.zone_id = z.id AND c.role = 'ATTENDANCE')
  AND NOT EXISTS (SELECT 1 FROM camera c WHERE c.zone_id = z.id AND c.role = 'PPE');

GRANT SELECT ON zone TO steelsight_detection;   -- already granted in V1; repeated for the new columns

-- ---------------------------------------------------------------------------------------------------
-- Violation review (BR-45 to BR-53, CR-05, CR-06)
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE violation
    ADD COLUMN zone_id               bigint REFERENCES zone(id),
    ADD COLUMN review_status         text NOT NULL DEFAULT 'PENDING_REVIEW'
        CHECK (review_status IN ('PENDING_REVIEW', 'OVERDUE', 'CONFIRMED', 'REMINDER', 'DISMISSED')),
    ADD COLUMN subject               text CHECK (subject IN ('EMPLOYEE', 'STRANGER')),
    ADD COLUMN dismiss_reason        text
        CHECK (dismiss_reason IN ('FALSE_DETECTION', 'OUTSIDE_PERSON', 'ALLOWED_REMOVAL', 'OTHER')),
    ADD COLUMN review_note           text,
    ADD COLUMN suggested_user_id     bigint REFERENCES app_user(id),
    ADD COLUMN suggestion_similarity numeric(4,3) CHECK (suggestion_similarity BETWEEN 0 AND 1),
    ADD COLUMN taken_by              bigint REFERENCES app_user(id),
    ADD COLUMN taken_at              timestamptz,
    ADD COLUMN appeal_decided_by     bigint REFERENCES app_user(id),
    ADD COLUMN appeal_decided_at     timestamptz;

-- reviewed_by/reviewed_at now hold the review decision; an appeal decision has its own columns.
UPDATE violation SET appeal_decided_by = reviewed_by, appeal_decided_at = reviewed_at
WHERE dispute_status IN ('UPHELD', 'OVERTURNED');
UPDATE violation SET reviewed_by = NULL, reviewed_at = NULL;

-- Before this release a violation with a worker counted at once, so those are confirmed.
-- An office employee's violation is a reminder, unless it was already disputed (a reminder can't be).
UPDATE violation v SET review_status = CASE WHEN u.staff_group = 'OFFICE' AND v.dispute_status = 'NONE' THEN 'REMINDER' ELSE 'CONFIRMED' END,
                       subject = 'EMPLOYEE'
FROM app_user u WHERE u.id = v.user_id;
UPDATE violation v SET zone_id = c.zone_id FROM camera c WHERE c.id = v.camera_id;

ALTER TABLE violation DROP CONSTRAINT IF EXISTS violation_check;      -- appeal decision needed reviewed_by
ALTER TABLE violation DROP CONSTRAINT IF EXISTS violation_check1;     -- an appeal needs a person: kept below
ALTER TABLE violation
    ALTER COLUMN zone_id SET NOT NULL,
    -- a person is named exactly when a decision names an employee
    ADD CONSTRAINT violation_person_when_decided CHECK (
        (user_id IS NOT NULL) = (review_status IN ('CONFIRMED', 'REMINDER') AND subject = 'EMPLOYEE')),
    ADD CONSTRAINT violation_subject_when_decided CHECK (
        (subject IS NOT NULL) = (review_status IN ('CONFIRMED', 'REMINDER'))),
    ADD CONSTRAINT violation_reason_when_dismissed CHECK (
        (dismiss_reason IS NOT NULL) = (review_status = 'DISMISSED')),
    ADD CONSTRAINT violation_other_reason_written CHECK (
        dismiss_reason IS DISTINCT FROM 'OTHER' OR length(btrim(review_note)) BETWEEN 10 AND 500),
    -- only a confirmed violation of an employee can be appealed (BR-64)
    ADD CONSTRAINT violation_appeal_only_when_confirmed CHECK (
        dispute_status = 'NONE' OR (review_status = 'CONFIRMED' AND user_id IS NOT NULL)),
    ADD CONSTRAINT violation_appeal_decider CHECK (
        dispute_status NOT IN ('UPHELD', 'OVERTURNED') OR (appeal_decided_by IS NOT NULL AND appeal_decided_at IS NOT NULL));

DROP INDEX IF EXISTS idx_violation_unidentified;
CREATE INDEX idx_violation_open ON violation(detected_at) WHERE review_status IN ('PENDING_REVIEW', 'OVERDUE');
CREATE INDEX idx_violation_zone_time ON violation(zone_id, detected_at DESC);

-- The detection app may already send the suggestion itself once it is updated (contract in docs).
GRANT INSERT (suggested_user_id, suggestion_similarity) ON violation TO steelsight_detection;

-- On insert: the zone comes from the camera, and any person the detection app names (directly or
-- through a vest link) is only a suggestion. The old trigger also ran on UPDATE and refused a person
-- other than the vest's wearer, which would stop HSE correcting the camera, so it is replaced.
DROP TRIGGER trg_violation_worker ON violation;
CREATE OR REPLACE FUNCTION violation_set_worker() RETURNS trigger AS $$
DECLARE
    v_link_user bigint;
BEGIN
    SELECT zone_id INTO NEW.zone_id FROM camera WHERE id = NEW.camera_id;
    IF NEW.ppe_linking_id IS NOT NULL THEN
        SELECT user_id INTO v_link_user FROM ppe_linking WHERE id = NEW.ppe_linking_id;
    END IF;
    IF NEW.review_status = 'PENDING_REVIEW' THEN
        NEW.suggested_user_id = COALESCE(NEW.suggested_user_id, NEW.user_id, v_link_user);
        NEW.user_id = NULL;
        NEW.subject = NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER trg_violation_worker BEFORE INSERT ON violation
    FOR EACH ROW EXECUTE FUNCTION violation_set_worker();

-- ---------------------------------------------------------------------------------------------------
-- Safety score and bonus level (BR-56 to BR-58)
-- ---------------------------------------------------------------------------------------------------
-- The old score notices (warning and danger levels) are replaced by a notice after each confirmation.
DROP TRIGGER trg_violation_safety_level ON violation;
DROP FUNCTION notify_safety_level();

ALTER TABLE safety_policy DROP CONSTRAINT IF EXISTS safety_policy_check;
ALTER TABLE safety_policy
    DROP COLUMN warning_at,
    DROP COLUMN danger_at,
    DROP COLUMN repeat_limit,
    ADD COLUMN level1_at integer NOT NULL DEFAULT 80,   -- a score at or below this is level 1 (-2%)
    ADD COLUMN level2_at integer NOT NULL DEFAULT 70,   -- level 2 (-4%)
    ADD COLUMN level3_at integer NOT NULL DEFAULT 60,   -- level 3 (-6%), the largest reduction
    ADD CONSTRAINT safety_policy_levels CHECK (0 <= level3_at AND level3_at < level2_at AND level2_at < level1_at AND level1_at < 100);
ALTER TABLE safety_policy ALTER COLUMN points_per_violation SET DEFAULT 5;
UPDATE safety_policy SET points_per_violation = 5;

-- Who has a score: active Site employees with the role WORKER or MANAGER. A month that is locked
-- reads the frozen row of monthly_score instead, so a later change can't alter it (BR-70).
CREATE TABLE monthly_score (
    user_id    bigint  NOT NULL REFERENCES app_user(id),
    month      date    NOT NULL CHECK (extract(day FROM month) = 1),
    violations integer NOT NULL CHECK (violations >= 0),
    score      integer NOT NULL CHECK (score BETWEEN 0 AND 100),
    level      integer NOT NULL CHECK (level BETWEEN 0 AND 3),
    frozen_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, month)
);

CREATE TABLE month_lock (
    month     date        PRIMARY KEY CHECK (extract(day FROM month) = 1),
    locked_at timestamptz NOT NULL DEFAULT now()
);

DROP FUNCTION safety_scores(timestamptz);
CREATE FUNCTION safety_scores(p_at timestamptz DEFAULT now())
    RETURNS TABLE (user_id bigint, score integer, level integer, violations integer) AS $$
WITH month AS (
    SELECT (date_trunc('month', p_at AT TIME ZONE p.time_zone))::date AS first_day,
           (date_trunc('month', p_at AT TIME ZONE p.time_zone)) AT TIME ZONE p.time_zone AS first_moment,
           (date_trunc('month', p_at AT TIME ZONE p.time_zone) + interval '1 month') AT TIME ZONE p.time_zone AS next_month
    FROM safety_policy p
), live AS (
    SELECT u.id,
           COUNT(v.id)::integer AS hits
    FROM app_user u
             JOIN role r ON r.id = u.role_id AND r.role_name IN ('WORKER', 'MANAGER')
             CROSS JOIN month m
             LEFT JOIN violation v ON v.user_id = u.id
                 AND v.review_status = 'CONFIRMED'
                 AND v.dispute_status <> 'OVERTURNED'
                 AND v.detected_at >= m.first_moment AND v.detected_at < m.next_month
    WHERE u.deleted_at IS NULL AND u.staff_group = 'SITE'
      AND NOT EXISTS (SELECT 1 FROM month_lock l, month m2 WHERE l.month = m2.first_day)
    GROUP BY u.id
), counted AS (
    SELECT l.id, GREATEST(0, 100 - p.points_per_violation * l.hits)::integer AS score, l.hits
    FROM live l CROSS JOIN safety_policy p
)
SELECT c.id, c.score,
       CASE WHEN c.score <= p.level3_at THEN 3 WHEN c.score <= p.level2_at THEN 2
            WHEN c.score <= p.level1_at THEN 1 ELSE 0 END,
       c.hits
FROM counted c CROSS JOIN safety_policy p
UNION ALL
SELECT s.user_id, s.score, s.level, s.violations
FROM monthly_score s JOIN month m ON s.month = m.first_day
WHERE EXISTS (SELECT 1 FROM month_lock l WHERE l.month = m.first_day);
$$ LANGUAGE sql STABLE;

-- How unsafe each time slot was. Every violation that wasn't dismissed or overturned counts, named or
-- not, because the slot is about the site, not a person.
CREATE OR REPLACE FUNCTION safety_time_slots(p_from timestamptz, p_to timestamptz)
    RETURNS TABLE (start_at timestamptz, end_at timestamptz, workers integer, worker_hours numeric,
                   violations integer, per_100_hours numeric) AS $$
WITH slot AS (
    SELECT s.start_at, s.end_at, COUNT(*)::integer AS workers, SUM(s.duration_hours) AS worker_hours
    FROM shift s
    WHERE s.status <> 'CANCELLED' AND s.start_at >= p_from AND s.start_at < p_to AND s.start_at <= now()
    GROUP BY s.start_at, s.end_at
), hit AS (
    SELECT slot.start_at, slot.end_at, slot.workers, slot.worker_hours,
           COUNT(v.id)::integer AS violations
    FROM slot
             LEFT JOIN violation v ON v.detected_at >= slot.start_at AND v.detected_at < slot.end_at
                 AND v.review_status <> 'DISMISSED' AND v.dispute_status <> 'OVERTURNED'
    GROUP BY slot.start_at, slot.end_at, slot.workers, slot.worker_hours
)
SELECT h.start_at, h.end_at, h.workers, h.worker_hours, h.violations,
       round(100.0 * h.violations / NULLIF(h.worker_hours, 0), 2)
FROM hit h
ORDER BY 6 DESC NULLS LAST, h.start_at DESC;
$$ LANGUAGE sql STABLE;

-- ---------------------------------------------------------------------------------------------------
-- Notifications: saved first, delivered on Zalo after (BR-15, BR-16, BR-17)
-- ---------------------------------------------------------------------------------------------------
-- A notice stores its kind and parameters, not words, so each page shows it in the reader's language
-- and the Zalo copy is written in Vietnamese when it is sent. link is the page the notice opens.
DROP INDEX IF EXISTS uq_notification_level;
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_kind_check;
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_check;
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_check1;
ALTER TABLE notification DROP CONSTRAINT IF EXISTS notification_check2;
ALTER TABLE notification
    ADD COLUMN params jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN link   text;
UPDATE notification SET params = jsonb_build_object('score', score) WHERE score IS NOT NULL;
-- Burst alerts are no longer sent (CR-02); their old notices stay readable as plain alerts.
UPDATE notification SET kind = 'LEGACY_ALERT' WHERE kind = 'HSE_VIOLATION_ALERT';
UPDATE notification SET kind = 'LEGACY_SCORE' WHERE kind IN ('SAFETY_WARNING', 'SAFETY_DANGER');
ALTER TABLE notification
    DROP COLUMN score,
    DROP COLUMN period,
    DROP COLUMN safety_alert_id,
    ADD CONSTRAINT notification_kind_known CHECK (kind ~ '^[A-Z_]+$');

CREATE TABLE notification_delivery (
    notification_id bigint      PRIMARY KEY REFERENCES notification(id) ON DELETE CASCADE,
    channel         text        NOT NULL DEFAULT 'ZALO' CHECK (channel = 'ZALO'),
    status          text        NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    attempts        integer     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz,
    sent_at         timestamptz,
    last_error      text,
    CHECK ((status = 'PENDING') = (next_attempt_at IS NOT NULL)),
    CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);
CREATE INDEX idx_delivery_due ON notification_delivery(next_attempt_at) WHERE status = 'PENDING';

-- Every notice gets its delivery row in the same transaction: PENDING for someone with a linked Zalo
-- chat, SKIPPED for everyone else (BR-16). Old notices were never meant for Zalo.
CREATE FUNCTION notification_add_delivery() RETURNS trigger AS $$
BEGIN
    INSERT INTO notification_delivery (notification_id, status, next_attempt_at)
    SELECT NEW.id,
           CASE WHEN u.zalo_chat_id IS NOT NULL THEN 'PENDING' ELSE 'SKIPPED' END,
           CASE WHEN u.zalo_chat_id IS NOT NULL THEN now() END
    FROM app_user u WHERE u.id = NEW.user_id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER trg_notification_delivery AFTER INSERT ON notification
    FOR EACH ROW EXECUTE FUNCTION notification_add_delivery();

-- ---------------------------------------------------------------------------------------------------
-- Violation alert routing (BR-26, BR-54, BR-55): in the database, because the detection app writes
-- violations directly and the ERP may be asleep. One alert per violation, never a burst.
-- ---------------------------------------------------------------------------------------------------
CREATE FUNCTION violation_alert_hse() RETURNS trigger AS $$
DECLARE
    v_params jsonb;
BEGIN
    SELECT jsonb_build_object(
               'violationId', NEW.id,
               'type', NEW.violation_type,
               'zone', z.name,
               'camera', c.camera_code,
               'detectedAt', NEW.detected_at,
               'receivedAt', now(),
               'suggested', s.full_name,
               'suggestedCode', s.employee_code)
    INTO v_params
    FROM camera c JOIN zone z ON z.id = c.zone_id
             LEFT JOIN app_user s ON s.id = NEW.suggested_user_id
    WHERE c.id = NEW.camera_id;

    INSERT INTO notification (user_id, kind, params, link)
    SELECT u.id, 'VIOLATION_ALERT', v_params, '/violations/' || NEW.id
    FROM app_user u JOIN role r ON r.id = u.role_id
    WHERE u.deleted_at IS NULL AND u.is_active AND r.role_name = 'HSE_OFFICER'
      AND EXISTS (SELECT 1 FROM shift s
                  WHERE s.user_id = u.id AND s.status <> 'CANCELLED'
                    AND NEW.detected_at >= s.start_at AND NEW.detected_at < s.end_at);
    IF NOT FOUND THEN
        INSERT INTO notification (user_id, kind, params, link)
        SELECT u.id, 'VIOLATION_ALERT', v_params || jsonb_build_object('nobodyOnDuty', true), '/violations/' || NEW.id
        FROM app_user u JOIN role r ON r.id = u.role_id
        WHERE u.deleted_at IS NULL AND u.is_active AND r.role_name IN ('HSE_OFFICER', 'ADMIN');
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER trg_violation_alert AFTER INSERT ON violation
    FOR EACH ROW WHEN (NEW.review_status = 'PENDING_REVIEW')
    EXECUTE FUNCTION violation_alert_hse();

-- ---------------------------------------------------------------------------------------------------
-- Settings, report exports and scheduled job runs
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE company_setting (
    id                          boolean PRIMARY KEY DEFAULT true CHECK (id),
    half_day_unexplained        boolean NOT NULL DEFAULT false,   -- BR-36
    appeal_working_days         integer NOT NULL DEFAULT 3 CHECK (appeal_working_days BETWEEN 1 AND 10),
    explanation_working_days    integer NOT NULL DEFAULT 3 CHECK (explanation_working_days BETWEEN 1 AND 10),
    version                     bigint  NOT NULL DEFAULT 0
);
INSERT INTO company_setting DEFAULT VALUES;

CREATE TABLE report_export (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES app_user(id),
    month       date        NOT NULL CHECK (extract(day FROM month) = 1),
    scope       text        NOT NULL,                       -- SITE or the team name
    provisional boolean     NOT NULL,
    exported_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_export_time ON report_export(exported_at DESC);

-- One row per run of a scheduled job and the time it was due, so a job never runs twice for the same
-- time and a run missed while the ERP slept is caught up once (SRS 3.2.38, RE-08).
CREATE TABLE job_run (
    job           text        NOT NULL,
    scheduled_for timestamptz NOT NULL,
    started_at    timestamptz NOT NULL DEFAULT now(),
    finished_at   timestamptz,
    result        text,
    PRIMARY KEY (job, scheduled_for)
);
