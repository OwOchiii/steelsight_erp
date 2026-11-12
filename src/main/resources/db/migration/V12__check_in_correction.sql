-- What each hand correction of a visit changed. Visits feed the monthly pay report, and a correction
-- overwrites the times and who recorded them, so this is the only record of what they used to be.
CREATE TABLE check_in_correction (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    check_in_id      bigint      NOT NULL REFERENCES check_in(id),
    corrected_by     bigint      NOT NULL REFERENCES app_user(id),
    corrected_at     timestamptz NOT NULL DEFAULT now(),
    old_check_in_at  timestamptz NOT NULL,
    old_check_out_at timestamptz,
    old_note         text,
    old_recorded_by  bigint REFERENCES app_user(id),
    old_auto_closed  boolean     NOT NULL,
    new_check_in_at  timestamptz NOT NULL,
    new_check_out_at timestamptz,
    new_note         text
);
CREATE INDEX idx_check_in_correction_visit ON check_in_correction(check_in_id, corrected_at DESC);
