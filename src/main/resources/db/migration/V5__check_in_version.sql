-- Optimistic lock for corrections: a stale edit fails instead of overwriting.
ALTER TABLE check_in ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- The detection service and close_stale_check_ins() update check_in with plain SQL. Count those
-- changes too, so a correction form opened before an automatic check-out can't overwrite it.
CREATE OR REPLACE FUNCTION bump_check_in_version() RETURNS trigger AS $$
BEGIN
    IF NEW.version = OLD.version THEN
        NEW.version := OLD.version + 1;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_in_version BEFORE UPDATE ON check_in
    FOR EACH ROW EXECUTE FUNCTION bump_check_in_version();
