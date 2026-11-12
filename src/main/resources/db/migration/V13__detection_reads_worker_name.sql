-- The gate check-in screen in the detection app shows each worker's name next to their check-in
-- and check-out times, so a worker can see the visit was recorded for them. Before this, the
-- detection role could read only the employee code.
--
-- The detection_read policy on app_user from V1 already allows SELECT for this role, so a column
-- GRANT is all ADR-004 needs here. password_hash, email and phone_number stay unreadable.
GRANT SELECT (full_name) ON app_user TO steelsight_detection;
