-- A worker's safety score is worked out from their violations, never stored. That way it returns to
-- 100 on the 1st with no job running (the ERP can be asleep), and an overturned violation gives its
-- points back by itself.

-- One row: the rules of the score. The time zone decides when a month starts, and should match
-- steelsight.time-zone in application.yaml.
CREATE TABLE safety_policy (
    id                   boolean  PRIMARY KEY DEFAULT true CHECK (id),
    points_per_violation integer  NOT NULL DEFAULT 10 CHECK (points_per_violation > 0),
    warning_at           integer  NOT NULL DEFAULT 70,
    danger_at            integer  NOT NULL DEFAULT 40,
    repeat_limit         interval NOT NULL DEFAULT interval '30 minutes',
    time_zone            text     NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    CHECK (0 <= danger_at AND danger_at < warning_at AND warning_at < 100)
);
INSERT INTO safety_policy DEFAULT VALUES;

-- The score of everyone with the WORKER role, in the month of p_at. Other roles have no row, so their
-- score is NULL. The same worker and type counts once per block of repeat_limit, because a camera can
-- report someone standing in view over and over.
CREATE FUNCTION safety_scores(p_at timestamptz DEFAULT now())
    RETURNS TABLE (user_id bigint, score integer, level text) AS $$
WITH month AS (
    SELECT (date_trunc('month', p_at AT TIME ZONE p.time_zone)) AT TIME ZONE p.time_zone AS first_moment,
           (date_trunc('month', p_at AT TIME ZONE p.time_zone) + interval '1 month') AT TIME ZONE p.time_zone AS next_month
    FROM safety_policy p
), counted AS (
    SELECT u.id,
           COUNT(DISTINCT (v.violation_type, date_bin(p.repeat_limit, v.detected_at, timestamptz 'epoch')))
               FILTER (WHERE v.id IS NOT NULL) AS hits
    FROM app_user u
             JOIN role r ON r.id = u.role_id AND r.role_name = 'WORKER'
             CROSS JOIN safety_policy p
             CROSS JOIN month m
             LEFT JOIN violation v ON v.user_id = u.id
                 AND v.dispute_status <> 'OVERTURNED'
                 AND v.detected_at >= m.first_moment AND v.detected_at < m.next_month
    WHERE u.deleted_at IS NULL
    GROUP BY u.id
), scored AS (
    SELECT c.id, GREATEST(0, 100 - p.points_per_violation * c.hits)::integer AS score
    FROM counted c CROSS JOIN safety_policy p
)
SELECT s.id, s.score,
       CASE WHEN s.score <= p.danger_at THEN 'DANGER' WHEN s.score <= p.warning_at THEN 'WARNING' ELSE 'SAFE' END
FROM scored s CROSS JOIN safety_policy p;
$$ LANGUAGE sql STABLE;

-- A report from the detection software that too many violations are happening. notified is false for
-- a report that arrived while HSE had just been told, so a long bad spell doesn't flood the bell.
CREATE TABLE safety_alert (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id        uuid        NOT NULL UNIQUE,
    violation_count integer     NOT NULL CHECK (violation_count > 0),
    window_seconds  integer     NOT NULL CHECK (window_seconds > 0),
    detected_at     timestamptz NOT NULL,
    camera_code     text,       -- as sent: a mistyped code must not lose a safety alert
    notified        boolean     NOT NULL,
    received_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_safety_alert_notified ON safety_alert(received_at DESC) WHERE notified;

-- The words are not stored: they are shown in the language of whoever reads them.
CREATE TABLE notification (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint NOT NULL REFERENCES app_user(id),
    kind            text   NOT NULL CHECK (kind IN ('SAFETY_WARNING', 'SAFETY_DANGER', 'HSE_VIOLATION_ALERT')),
    score           integer,                                -- safety notices: the score when it was sent
    period          date,                                   -- safety notices: the 1st of that month
    safety_alert_id bigint REFERENCES safety_alert(id),     -- HSE alerts
    read_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CHECK ((kind = 'HSE_VIOLATION_ALERT') = (safety_alert_id IS NOT NULL)),
    CHECK ((kind = 'HSE_VIOLATION_ALERT') = (period IS NULL)),
    CHECK ((kind = 'HSE_VIOLATION_ALERT') = (score IS NULL))
);
CREATE INDEX idx_notification_user ON notification(user_id, created_at DESC);
CREATE INDEX idx_notification_unread ON notification(user_id) WHERE read_at IS NULL;
-- each level reaches a worker once a month
CREATE UNIQUE INDEX uq_notification_level ON notification(user_id, kind, period) WHERE period IS NOT NULL;

-- A trigger, because the detection software writes violations straight to the database and the ERP
-- may be asleep. It runs as the table owner, because the detection role can't write notifications.
CREATE FUNCTION notify_safety_level() RETURNS trigger AS $$
DECLARE
    v_score integer;
    v_level text;
    v_zone  text;
BEGIN
    IF NEW.user_id IS NULL OR NEW.dispute_status = 'OVERTURNED' THEN
        RETURN NULL;
    END IF;
    SELECT s.score, s.level INTO v_score, v_level FROM safety_scores(now()) s WHERE s.user_id = NEW.user_id;
    IF v_level IS NULL OR v_level = 'SAFE' THEN
        RETURN NULL;
    END IF;
    SELECT time_zone INTO v_zone FROM safety_policy;
    INSERT INTO notification (user_id, kind, score, period)
    VALUES (NEW.user_id, 'SAFETY_' || v_level, v_score, date_trunc('month', now() AT TIME ZONE v_zone)::date)
    ON CONFLICT (user_id, kind, period) WHERE period IS NOT NULL DO NOTHING;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER trg_violation_safety_level AFTER INSERT OR UPDATE OF user_id, dispute_status ON violation
    FOR EACH ROW EXECUTE FUNCTION notify_safety_level();

-- How unsafe each time slot was: shifts with the same start and end, across the site. One camera covers
-- the site, so a violation belongs to every slot running when it happened, even with no worker named.
CREATE FUNCTION safety_time_slots(p_from timestamptz, p_to timestamptz)
    RETURNS TABLE (start_at timestamptz, end_at timestamptz, workers integer, worker_hours numeric,
                   violations integer, per_100_hours numeric) AS $$
WITH slot AS (
    SELECT s.start_at, s.end_at, COUNT(*)::integer AS workers, SUM(s.duration_hours) AS worker_hours
    FROM shift s
    WHERE s.status <> 'CANCELLED' AND s.start_at >= p_from AND s.start_at < p_to AND s.start_at <= now()
    GROUP BY s.start_at, s.end_at
), hit AS (
    SELECT slot.start_at, slot.end_at, slot.workers, slot.worker_hours,
           COUNT(DISTINCT (COALESCE(v.user_id, 0), v.violation_type, date_bin(p.repeat_limit, v.detected_at, timestamptz 'epoch')))
               FILTER (WHERE v.id IS NOT NULL)::integer AS violations
    FROM slot
             CROSS JOIN safety_policy p
             LEFT JOIN violation v ON v.detected_at >= slot.start_at AND v.detected_at < slot.end_at
                 AND v.dispute_status <> 'OVERTURNED'
    GROUP BY slot.start_at, slot.end_at, slot.workers, slot.worker_hours
)
SELECT h.start_at, h.end_at, h.workers, h.worker_hours, h.violations,
       round(100.0 * h.violations / NULLIF(h.worker_hours, 0), 2)
FROM hit h
ORDER BY 6 DESC NULLS LAST, h.start_at DESC;
$$ LANGUAGE sql STABLE;
