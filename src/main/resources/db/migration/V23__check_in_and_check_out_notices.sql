-- SRS 1.2.4 and 3.2.4: after each gate scan the employee gets "Check-in at HH:MM, shift HH:MM-HH:MM" or
-- "Check-out at HH:MM, hours worked N.NN", in the web ERP and on Zalo. The detection app writes check-ins
-- straight into the database, so the notices come from triggers, like the violation alert (V21).
--
-- Only rows written by the detection role count (the WHEN clause sees the calling role, not the function
-- owner): a visit typed in by HSE has its own notice (VISIT_RECORDED), and the ERP's corrections, an
-- approved missing check-out and the automatic close of stale visits must not say "you checked out".
-- The paid hours of a check-out follow the late, early and buffer rules, which are worked out in Java;
-- the minute job adds them before the Zalo message goes out (CheckOutHours).

CREATE FUNCTION check_in_notify() RETURNS trigger AS $$
DECLARE
    v_zone  text;
    v_shift text;
BEGIN
    SELECT time_zone INTO v_zone FROM safety_policy;
    v_zone := COALESCE(v_zone, 'Asia/Ho_Chi_Minh');
    IF NEW.shift_id IS NOT NULL THEN
        SELECT to_char(s.start_at AT TIME ZONE v_zone, 'HH24:MI') || '-' || to_char(s.end_at AT TIME ZONE v_zone, 'HH24:MI')
        INTO v_shift FROM shift s WHERE s.id = NEW.shift_id;
    END IF;
    INSERT INTO notification (user_id, kind, params, link)
    VALUES (NEW.user_id, 'CHECK_IN',
            jsonb_build_object('checkInId', NEW.id, 'checkInAt', NEW.check_in_at, 'shift', v_shift),
            '/attendance/mine');
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER trg_check_in_notify AFTER INSERT ON check_in
    FOR EACH ROW WHEN (current_user = 'steelsight_detection')
    EXECUTE FUNCTION check_in_notify();

CREATE FUNCTION check_out_notify() RETURNS trigger AS $$
BEGIN
    INSERT INTO notification (user_id, kind, params, link)
    VALUES (NEW.user_id, 'CHECK_OUT',
            jsonb_build_object('checkInId', NEW.id, 'shiftId', NEW.shift_id, 'checkOutAt', NEW.check_out_at),
            '/attendance/mine');
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER trg_check_out_notify AFTER UPDATE OF check_out_at ON check_in
    FOR EACH ROW WHEN (current_user = 'steelsight_detection'
                       AND OLD.check_out_at IS NULL AND NEW.check_out_at IS NOT NULL AND NOT NEW.auto_closed)
    EXECUTE FUNCTION check_out_notify();
