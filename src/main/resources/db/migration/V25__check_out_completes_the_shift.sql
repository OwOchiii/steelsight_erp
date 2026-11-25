-- A check-out finishes the shift of its visit: the shift becomes COMPLETED at once, as the check-out
-- notice says ("finishing shift T3 24/11 08:00–17:00"), instead of waiting for the hourly job after the
-- shift ends. The job (SiteJobs.completeEndedShifts) stays for shifts nobody checked out of.
--
-- * Any real check-out counts: the gate's, a visit HSE types in with its check-out, a correction that adds
--   one, or an approved missing check-out. The system's close of a stale visit (auto_closed) doesn't, since
--   nobody checked out; the job completes that shift anyway, as it has ended.
-- * Only a SCHEDULED shift changes; a cancelled one stays cancelled. Locked months are left as they are
--   (BR-70), the same as the job. The version goes up, so a shift form opened before stays stale.
-- * A worker who leaves and comes back during the shift is still attached to it: check_in_attach_shift()
--   takes any shift that isn't CANCELLED.

CREATE FUNCTION check_out_complete_shift() RETURNS trigger AS $$
DECLARE
    v_zone text;
BEGIN
    IF NEW.shift_id IS NULL OR NEW.check_out_at IS NULL OR NEW.auto_closed
       OR (TG_OP = 'UPDATE' AND OLD.check_out_at IS NOT NULL AND NOT OLD.auto_closed) THEN
        RETURN NULL;
    END IF;
    SELECT COALESCE((SELECT time_zone FROM safety_policy LIMIT 1), 'Asia/Ho_Chi_Minh') INTO v_zone;
    UPDATE shift s SET status = 'COMPLETED', version = s.version + 1, updated_at = now()
    WHERE s.id = NEW.shift_id AND s.status = 'SCHEDULED'
      AND NOT EXISTS (SELECT 1 FROM month_lock m
                      WHERE m.month = date_trunc('month', s.start_at AT TIME ZONE v_zone)::date);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

CREATE TRIGGER trg_check_out_complete_shift AFTER INSERT OR UPDATE OF check_out_at, auto_closed ON check_in
    FOR EACH ROW EXECUTE FUNCTION check_out_complete_shift();

-- Shifts already checked out of, that haven't ended yet, are completed now too.
UPDATE shift s SET status = 'COMPLETED', version = s.version + 1, updated_at = now()
WHERE s.status = 'SCHEDULED'
  AND EXISTS (SELECT 1 FROM check_in c WHERE c.shift_id = s.id AND c.check_out_at IS NOT NULL AND NOT c.auto_closed)
  AND NOT EXISTS (SELECT 1 FROM month_lock m, safety_policy p
                  WHERE m.month = date_trunc('month', s.start_at AT TIME ZONE p.time_zone)::date);
