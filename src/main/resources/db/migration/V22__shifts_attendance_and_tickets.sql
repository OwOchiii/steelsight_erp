-- Shifts, attendance and tickets as the SRS (23/11/2026) says:
--   * a shift is REGULAR or OVERTIME, and overtime needs a reason (BR-21, BR-23);
--   * a check-in is attached to its shift at most 60 minutes before the start (BR-30);
--   * a visit with no check-out is flagged Incomplete at 23:00 and closed 4 hours after the shift,
--     without guessing a check-out time (BR-35);
--   * time after the shift end plus 20 minutes is pending overtime until the Admin adds an overtime
--     shift within 48 hours (BR-34, BR-37);
--   * tickets have the five categories of BR-60, an outcome, the fields of each category, and can be
--     cancelled; the Admin can ask an HSE Officer to comment (SRS 3.2.22).
--
-- As in V21, nothing the detection app writes is dropped or renamed. It closes stale visits by calling
-- close_stale_check_ins(), which is rewritten here with the same signature.

-- ---------------------------------------------------------------------------------------------------
-- Shifts
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE shift
    ADD COLUMN kind   text NOT NULL DEFAULT 'REGULAR' CHECK (kind IN ('REGULAR', 'OVERTIME')),
    ADD COLUMN reason text,
    ADD CONSTRAINT shift_overtime_has_reason CHECK (kind <> 'OVERTIME' OR length(btrim(reason)) BETWEEN 10 AND 500),
    -- BR-21: 30 minutes to 12 hours. Older shifts aren't checked, so NOT VALID.
    ADD CONSTRAINT shift_length CHECK (end_at - start_at BETWEEN interval '30 minutes' AND interval '12 hours') NOT VALID;

-- ---------------------------------------------------------------------------------------------------
-- Attendance
-- ---------------------------------------------------------------------------------------------------
-- incomplete: no check-out 20 minutes after the shift end, seen at 23:00 (BR-35). auto_closed now means
-- closed by the system; the check_out_at it writes is only the moment of closing, never paid.
ALTER TABLE check_in
    ADD COLUMN incomplete    boolean NOT NULL DEFAULT false,
    ADD COLUMN incomplete_at timestamptz,
    ADD CONSTRAINT check_in_incomplete_when CHECK (incomplete = (incomplete_at IS NOT NULL));

-- Visits the old rule closed with an estimated check-out were never explained: they are incomplete.
UPDATE check_in SET incomplete = true, incomplete_at = COALESCE(check_out_at, now())
WHERE auto_closed AND method = 'FACE';

ALTER TABLE check_in_correction
    ADD COLUMN ticket_id bigint;   -- FK added below, once ticket has its new columns

-- BR-30: a check-in goes with the person's own shift if it is at most 60 minutes before the start and
-- before the end. BR-35 / SRS 3.2.35: a visit still open from an earlier day is closed by the system.
CREATE OR REPLACE FUNCTION check_in_attach_shift() RETURNS trigger AS $$
DECLARE
    v_zone text;
BEGIN
    SELECT time_zone INTO v_zone FROM safety_policy;
    UPDATE check_in o
    SET check_out_at = GREATEST(NEW.check_in_at, o.check_in_at + interval '1 minute'), auto_closed = true,
        incomplete = true, incomplete_at = COALESCE(o.incomplete_at, now())
    WHERE o.user_id = NEW.user_id AND o.check_out_at IS NULL
      AND (o.check_in_at AT TIME ZONE v_zone)::date < (NEW.check_in_at AT TIME ZONE v_zone)::date;
    IF NEW.shift_id IS NULL THEN
        SELECT s.id INTO NEW.shift_id
        FROM shift s
        WHERE s.user_id = NEW.user_id AND s.status <> 'CANCELLED'
          AND NEW.check_in_at >= s.start_at - interval '60 minutes' AND NEW.check_in_at < s.end_at
        ORDER BY s.start_at
        LIMIT 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER trg_check_in_attach_shift BEFORE INSERT ON check_in
    FOR EACH ROW EXECUTE FUNCTION check_in_attach_shift();

-- BR-35, called by the detection app and by the 23:00 job: a visit open 20 minutes after its shift end
-- is flagged Incomplete; one open 4 hours after the shift end is closed, so the next scan is a check-in.
-- A visit with no shift closes once its day has passed. Same signature as V15.
DROP FUNCTION close_stale_check_ins(text);
CREATE FUNCTION close_stale_check_ins(p_time_zone text DEFAULT 'Asia/Ho_Chi_Minh') RETURNS integer AS $$
DECLARE
    v_closed integer;
BEGIN
    UPDATE check_in c SET incomplete = true, incomplete_at = now()
    FROM shift s
    WHERE s.id = c.shift_id AND s.status <> 'CANCELLED' AND c.check_out_at IS NULL AND NOT c.incomplete
      AND s.end_at + interval '20 minutes' <= now();

    WITH closed AS (
        UPDATE check_in c
        SET check_out_at = d.close_at, auto_closed = true,
            incomplete = true, incomplete_at = COALESCE(c.incomplete_at, now())
        FROM (
            SELECT o.id,
                   CASE WHEN s.id IS NOT NULL THEN GREATEST(s.end_at, o.check_in_at) + interval '4 hours'
                        ELSE (((o.check_in_at AT TIME ZONE p_time_zone)::date + 1)::timestamp) AT TIME ZONE p_time_zone
                   END AS close_at
            FROM check_in o
                     LEFT JOIN shift s ON s.id = o.shift_id AND s.status <> 'CANCELLED'
            WHERE o.check_out_at IS NULL
        ) d
        WHERE c.id = d.id AND d.close_at <= now()
        RETURNING c.id
    )
    SELECT count(*)::integer INTO v_closed FROM closed;
    RETURN v_closed;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

REVOKE EXECUTE ON FUNCTION close_stale_check_ins(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION close_stale_check_ins(text) TO steelsight_detection;

-- BR-34, BR-37: time after the shift end plus 20 minutes, without an overtime shift. Paid at 0% until
-- the Admin adds an overtime shift that covers it; cancelled after 48 hours or on the 2nd of the next
-- month, whichever is first.
CREATE TABLE pending_overtime (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    check_in_id       bigint      NOT NULL UNIQUE REFERENCES check_in(id),
    user_id           bigint      NOT NULL REFERENCES app_user(id),
    shift_id          bigint      NOT NULL REFERENCES shift(id),
    start_at          timestamptz NOT NULL,
    end_at            timestamptz NOT NULL,
    status            text        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VALID', 'CANCELLED')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    expires_at        timestamptz NOT NULL,
    decided_at        timestamptz,
    overtime_shift_id bigint REFERENCES shift(id),
    leader_note       text CHECK (length(leader_note) <= 500),
    CHECK (end_at > start_at),
    CHECK ((status = 'PENDING') = (decided_at IS NULL)),
    CHECK ((status = 'VALID') = (overtime_shift_id IS NOT NULL))
);
CREATE INDEX idx_pending_overtime_open ON pending_overtime(expires_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------------------------------
-- Tickets
-- ---------------------------------------------------------------------------------------------------
-- code says what a category does; the five of BR-60 have one. The others stay for the tickets that
-- use them, but can't be chosen for a new ticket.
ALTER TABLE ticket_category
    ADD COLUMN code   text UNIQUE CHECK (code IN ('PPE_APPEAL', 'ATTENDANCE_ERROR', 'MISSING_CHECK_OUT', 'LEAVE', 'OTHER')),
    ADD COLUMN active boolean NOT NULL DEFAULT true;

UPDATE ticket_category SET category_name = 'PPE appeal', code = 'PPE_APPEAL' WHERE category_name = 'PPE dispute';
UPDATE ticket_category SET category_name = 'Attendance error', code = 'ATTENDANCE_ERROR' WHERE category_name = 'Attendance correction';
UPDATE ticket_category SET code = 'LEAVE' WHERE category_name = 'Leave request';
UPDATE ticket_category SET code = 'OTHER' WHERE category_name = 'Other';
INSERT INTO ticket_category (category_name, handler_role_id, code)
SELECT 'Missing check-out', id, 'MISSING_CHECK_OUT' FROM role WHERE role_name = 'ADMIN'
ON CONFLICT (category_name) DO UPDATE SET code = 'MISSING_CHECK_OUT';
UPDATE ticket_category SET active = false WHERE code IS NULL;

ALTER TABLE ticket DROP CONSTRAINT IF EXISTS ticket_status_check;
ALTER TABLE ticket
    ADD CONSTRAINT ticket_status_check CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED')),
    ADD COLUMN outcome       text CHECK (outcome IN ('APPROVED', 'REJECTED', 'UPHELD', 'OVERTURNED')),
    ADD COLUMN decision_note text,
    ADD COLUMN hse_facts     text CHECK (length(hse_facts) <= 1000),
    ADD COLUMN cancelled_at  timestamptz,
    ADD COLUMN shift_id      bigint REFERENCES shift(id),
    ADD COLUMN check_in_id   bigint REFERENCES check_in(id),
    ADD COLUMN direction     text CHECK (direction IN ('IN', 'OUT')),
    ADD COLUMN gate_time     timestamptz,
    ADD COLUMN leave_type    text CHECK (leave_type IN ('PAID', 'UNPAID')),
    ADD COLUMN leave_from    timestamptz,
    ADD COLUMN leave_to      timestamptz,
    ADD CONSTRAINT ticket_outcome_only_when_resolved CHECK (outcome IS NULL OR status = 'RESOLVED'),
    ADD CONSTRAINT ticket_cancelled_when CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    ADD CONSTRAINT ticket_leave_times CHECK (leave_from IS NULL OR (leave_to IS NOT NULL AND leave_to > leave_from));

-- Resolved disputes keep their outcome.
UPDATE ticket t SET outcome = v.dispute_status
FROM violation v WHERE v.id = t.violation_id AND t.status = 'RESOLVED' AND v.dispute_status IN ('UPHELD', 'OVERTURNED');

-- MSG45: one open ticket per violation, or per shift and category.
CREATE UNIQUE INDEX uq_ticket_open_per_shift ON ticket(category_id, shift_id)
    WHERE shift_id IS NOT NULL AND status IN ('OPEN', 'IN_PROGRESS') AND deleted_at IS NULL;
CREATE INDEX idx_ticket_shift ON ticket(shift_id);
CREATE INDEX idx_ticket_check_in ON ticket(check_in_id);

ALTER TABLE check_in_correction
    ADD CONSTRAINT check_in_correction_ticket_fk FOREIGN KEY (ticket_id) REFERENCES ticket(id);

-- An HSE Officer the Admin asked for the facts may read and comment on that ticket (SRS 3.1.3).
CREATE TABLE ticket_participant (
    ticket_id bigint      NOT NULL REFERENCES ticket(id) ON DELETE CASCADE,
    user_id   bigint      NOT NULL REFERENCES app_user(id),
    asked_by  bigint      NOT NULL REFERENCES app_user(id),
    asked_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (ticket_id, user_id)
);

CREATE OR REPLACE FUNCTION can_read_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (
    SELECT 1
    FROM ticket t
             JOIN ticket_category c ON c.id = t.category_id
             JOIN app_user u        ON u.id = p_user
    WHERE t.id = p_ticket AND t.deleted_at IS NULL
      AND (has_permission(p_user, 'TICKET_READ_ALL')
        OR (has_permission(p_user, 'TICKET_READ_OWN') AND t.created_by = p_user)
        OR (has_permission(p_user, 'TICKET_READ_HANDLED') AND u.role_id = c.handler_role_id)
        OR EXISTS (SELECT 1 FROM ticket_participant p WHERE p.ticket_id = t.id AND p.user_id = p_user)))
    OR ticket_from_my_team(p_user, p_ticket);
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_comment_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (SELECT 1 FROM ticket t WHERE t.id = p_ticket AND t.deleted_at IS NULL AND t.created_by = p_user)
    OR can_update_ticket(p_user, p_ticket)
    OR ticket_from_my_team(p_user, p_ticket)
    OR EXISTS (SELECT 1 FROM ticket_participant p JOIN ticket t ON t.id = p.ticket_id
               WHERE p.ticket_id = p_ticket AND p.user_id = p_user AND t.deleted_at IS NULL);
$$ LANGUAGE sql STABLE;
