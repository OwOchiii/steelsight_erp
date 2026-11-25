-- The data model of SRS 3.1.5 (CR-01, CR-02, CR-23):
--   * vest numbers are gone (CR-01): the tables ppe_vest and ppe_linking and the columns
--     check_in.ppe_linking_id and violation.ppe_linking_id are dropped. The detection app stopped
--     writing them on 23/11/2026 (docs/detection-app-contract.md);
--   * burst alerts are gone (CR-02): the table safety_alert is dropped with the alert API. Every
--     violation already alerts the HSE Officers on duty on its own (V21 trigger);
--   * the late tolerance is 5 minutes for the whole site (BR-31), so team.late_after_minutes goes;
--   * each zone gets a fixed code (GATE-IN, GATE-PPE, AREA-A, AREA-B ...) that the desktop app's camera
--     settings name the zone by (SRS 3.2.23, 3.2.30). The name stays the Admin's to change.

-- ---------------------------------------------------------------------------------------------------
-- Vest numbers (CR-01)
-- ---------------------------------------------------------------------------------------------------
-- The visit trigger also checked that a vest link belonged to the worker. Its trigger lists
-- ppe_linking_id, so it is dropped before the column and made again for the shift alone.
DROP TRIGGER trg_check_in_worker ON check_in;
CREATE OR REPLACE FUNCTION check_in_same_worker() RETURNS trigger AS $$
BEGIN
    IF NEW.shift_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM shift WHERE id = NEW.shift_id AND user_id = NEW.user_id) THEN
        RAISE EXCEPTION 'shift % does not belong to user %', NEW.shift_id, NEW.user_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_check_in_worker BEFORE INSERT OR UPDATE OF user_id, shift_id ON check_in
    FOR EACH ROW EXECUTE FUNCTION check_in_same_worker();

-- The new-violation trigger read the vest's wearer as a suggestion. Without vests, a suggestion comes
-- only from the detection app (suggested_user_id) or a person it names directly (user_id).
CREATE OR REPLACE FUNCTION violation_set_worker() RETURNS trigger AS $$
BEGIN
    SELECT zone_id INTO NEW.zone_id FROM camera WHERE id = NEW.camera_id;
    IF NEW.review_status = 'PENDING_REVIEW' THEN
        NEW.suggested_user_id = COALESCE(NEW.suggested_user_id, NEW.user_id);
        NEW.user_id = NULL;
        NEW.subject = NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp;

-- Their indexes, column grants and the detection role's policies on the two tables go with them.
ALTER TABLE check_in  DROP COLUMN ppe_linking_id;
ALTER TABLE violation DROP COLUMN ppe_linking_id;
DROP TABLE ppe_linking;
DROP TABLE ppe_vest;

-- ---------------------------------------------------------------------------------------------------
-- Burst alerts (CR-02). V21 already removed notification.safety_alert_id.
-- ---------------------------------------------------------------------------------------------------
DROP TABLE safety_alert;

-- ---------------------------------------------------------------------------------------------------
-- Late tolerance (BR-31): 5 minutes for every team, fixed in AttendanceRules
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE team DROP COLUMN late_after_minutes;

-- ---------------------------------------------------------------------------------------------------
-- Zone codes (SRS 3.2.23): given when a zone is added and never changed. The first free code of the kind:
-- GATE-IN, GATE-IN-2 ...; GATE-PPE, GATE-PPE-2 ...; AREA-A to AREA-Z, then AREA-27 and on. The database
-- gives it, so the ERP, the setup script and the tests all get the same codes.
-- ---------------------------------------------------------------------------------------------------
ALTER TABLE zone ADD COLUMN code text;

CREATE FUNCTION zone_next_code(p_kind text) RETURNS text AS $$
DECLARE
    v_code text;
BEGIN
    FOR n IN 1..10000 LOOP
        v_code := CASE p_kind
                      WHEN 'GATE_ATTENDANCE' THEN 'GATE-IN'  || CASE WHEN n = 1 THEN '' ELSE '-' || n END
                      WHEN 'GATE_PPE'        THEN 'GATE-PPE' || CASE WHEN n = 1 THEN '' ELSE '-' || n END
                      ELSE 'AREA-' || CASE WHEN n <= 26 THEN chr(64 + n) ELSE n::text END
                  END;
        IF NOT EXISTS (SELECT 1 FROM zone WHERE code = v_code) THEN
            RETURN v_code;
        END IF;
    END LOOP;
    RAISE EXCEPTION 'no free zone code for %', p_kind;
END;
$$ LANGUAGE plpgsql;

-- Zones made before this release, in the order they were added.
DO $$
DECLARE
    z record;
BEGIN
    FOR z IN SELECT id, kind FROM zone ORDER BY id LOOP
        UPDATE zone SET code = zone_next_code(z.kind) WHERE id = z.id;
    END LOOP;
END $$;

ALTER TABLE zone
    ALTER COLUMN code SET NOT NULL,
    ADD CONSTRAINT zone_code_key UNIQUE (code),
    ADD CONSTRAINT zone_code_format CHECK (code ~ '^[A-Z0-9-]{2,20}$');

-- A new zone with no code gets the next one; the unique key still decides if two are added at once.
-- The code never changes afterwards, because the desktop app's camera settings name the zone by it.
CREATE FUNCTION zone_code() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.code := COALESCE(NEW.code, zone_next_code(NEW.kind));
    ELSIF NEW.code IS DISTINCT FROM OLD.code THEN
        RAISE EXCEPTION 'the code of zone % cannot change', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_zone_code BEFORE INSERT OR UPDATE OF code ON zone
    FOR EACH ROW EXECUTE FUNCTION zone_code();
