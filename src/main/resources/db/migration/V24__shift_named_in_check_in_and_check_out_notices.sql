-- The gate's check-in and check-out notices name the shift they start and finish by its weekday, date
-- and time, as the shift notices do ("T3 24/11 08:00–17:00"): "Check-in at ..., starting shift ..." and
-- "Check-out at ..., finishing shift ..., hours worked ...". The label is written when the notice is made,
-- because a shift can change later. A visit with no shift has no label and gets its own wording.

CREATE FUNCTION shift_label(p_shift_id bigint) RETURNS text AS $$
    SELECT (ARRAY['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'])[extract(isodow FROM s.start_at AT TIME ZONE z.tz)::int]
           || ' ' || to_char(s.start_at AT TIME ZONE z.tz, 'DD/MM HH24:MI')
           || '–' || to_char(s.end_at AT TIME ZONE z.tz, 'HH24:MI')
    FROM shift s
             CROSS JOIN (SELECT COALESCE((SELECT time_zone FROM safety_policy LIMIT 1), 'Asia/Ho_Chi_Minh') AS tz) z
    WHERE s.id = p_shift_id;
$$ LANGUAGE sql STABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION check_in_notify() RETURNS trigger AS $$
BEGIN
    INSERT INTO notification (user_id, kind, params, link)
    VALUES (NEW.user_id, 'CHECK_IN',
            jsonb_build_object('checkInId', NEW.id, 'checkInAt', NEW.check_in_at, 'shift', shift_label(NEW.shift_id)),
            '/attendance/mine');
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION check_out_notify() RETURNS trigger AS $$
BEGIN
    INSERT INTO notification (user_id, kind, params, link)
    VALUES (NEW.user_id, 'CHECK_OUT',
            jsonb_build_object('checkInId', NEW.id, 'shiftId', NEW.shift_id, 'checkOutAt', NEW.check_out_at,
                               'shift', shift_label(NEW.shift_id)),
            '/attendance/mine');
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- Notices already made read the same way: the label of the shift of their visit, when it still exists.
UPDATE notification n SET params = n.params || jsonb_build_object('shift', shift_label(ci.shift_id))
FROM check_in ci
WHERE n.kind IN ('CHECK_IN', 'CHECK_OUT') AND ci.id = (n.params ->> 'checkInId')::bigint;
