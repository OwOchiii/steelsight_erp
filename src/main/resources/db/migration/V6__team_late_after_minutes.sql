-- How long after a shift starts a worker on this team can check in before being tagged late.
ALTER TABLE team
    ADD COLUMN late_after_minutes integer NOT NULL DEFAULT 15
        CHECK (late_after_minutes BETWEEN 0 AND 240);
