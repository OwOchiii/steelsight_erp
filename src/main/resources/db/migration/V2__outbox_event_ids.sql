-- The detection service's outbox retries until a write is confirmed. If the connection drops after
-- a commit but before the confirmation, the same event arrives twice. Python generates one UUID per
-- event and inserts with ON CONFLICT (event_id) DO NOTHING, so a retry changes nothing.
-- NULL for rows the ERP creates by hand; a unique constraint allows many NULLs.
ALTER TABLE violation   ADD COLUMN event_id uuid, ADD CONSTRAINT uq_violation_event   UNIQUE (event_id);
ALTER TABLE check_in    ADD COLUMN event_id uuid, ADD CONSTRAINT uq_check_in_event    UNIQUE (event_id);
ALTER TABLE ppe_linking ADD COLUMN event_id uuid, ADD CONSTRAINT uq_ppe_linking_event UNIQUE (event_id);

-- check_in and ppe_linking have table-wide INSERT; violation lists its columns
GRANT INSERT (event_id) ON violation TO steelsight_detection;

-- Safety concern: a report of broken safety equipment or a dangerous condition. HSE handles it.
COMMENT ON TABLE ticket_category IS
    'Safety concern = a report of broken safety equipment or a dangerous condition; PPE dispute = a worker contesting a detection.';
