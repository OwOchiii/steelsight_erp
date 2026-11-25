-- Sample people and work for trying the pages on a local database (V27). Never run this on production.
--
-- Start the app once first, so the tables and the ADMIN account exist. Then, from the project root:
--   scripts/local.sh seed
-- or by hand:
--   docker exec -i steelsight-local-db psql -v ON_ERROR_STOP=1 -U postgres -d steelsight_local < scripts/dev-sample-data.sql
--
-- Every sample account uses ADMIN's password, so no password is stored here. Dates count from today, so
-- the data always looks current. It matches the practice site of docs/workflow-tutorial.md, section 0.
--
--   ADM02          a second Admin (to decide an appeal ADMIN sent, or vice versa)
--   HSE01, HSE02   HSE officers; HSE01 is on duty 07:00 to 17:00 every day, so violations alert them
--   MGR01          team leader of Team A (WRK01 to WRK03), which owns Area A
--   MGR02          team leader of Team B (WRK04 to WRK06), which owns Area B
--   WRK01..WRK05   site workers; WRK06 is deactivated
--   OFC01          an office worker: no safety score, violations become reminders
--   WRK07          a new joiner who must change the password at first sign-in, with no face photo yet

BEGIN;

DO $$
BEGIN
    IF current_database() <> 'steelsight_local' THEN
        RAISE EXCEPTION 'This script only runs on steelsight_local, not %', current_database();
    END IF;
    IF NOT EXISTS (SELECT 1 FROM app_user WHERE employee_code = 'ADMIN') THEN
        RAISE EXCEPTION 'Start the app once first, so the ADMIN account exists';
    END IF;
    IF EXISTS (SELECT 1 FROM app_user WHERE employee_code = 'WRK01') THEN
        RAISE EXCEPTION 'The sample data is already loaded (scripts/local.sh reset starts again)';
    END IF;
END $$;

-- A time at the site, counted in days from today
CREATE FUNCTION pg_temp.at(p_day int, p_time text) RETURNS timestamptz AS $$
    SELECT (((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + p_day) + p_time::time) AT TIME ZONE 'Asia/Ho_Chi_Minh';
$$ LANGUAGE sql STABLE;

-- Some hours ago, but never before this month started, so it counts in this month's score
CREATE FUNCTION pg_temp.recent(p_hours int) RETURNS timestamptz AS $$
    SELECT LEAST(now(), GREATEST(now() - make_interval(hours => p_hours),
           (date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh') + interval '1 minute'));
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.person(p_code text) RETURNS bigint AS $$
    SELECT id FROM app_user WHERE employee_code = p_code;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.place(p_code text) RETURNS bigint AS $$
    SELECT id FROM zone WHERE code = p_code;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.cam(p_code text) RETURNS bigint AS $$
    SELECT id FROM camera WHERE camera_code = p_code;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.category(p_code text) RETURNS bigint AS $$
    SELECT id FROM ticket_category WHERE code = p_code;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.shift_of(p_code text, p_day int) RETURNS bigint AS $$
    SELECT id FROM shift WHERE user_id = pg_temp.person(p_code) AND start_at = pg_temp.at(p_day, '08:00');
$$ LANGUAGE sql STABLE;

-- ---------------------------------------------------------------------------------------------------
-- People and teams
-- ---------------------------------------------------------------------------------------------------
INSERT INTO app_user (role_id, employee_code, full_name, email, phone_number, password_hash, is_active,
                      staff_group, face_consent_at, must_change_password)
SELECT r.id, v.code, v.name, v.email, v.phone, (SELECT password_hash FROM app_user WHERE employee_code = 'ADMIN'),
       v.active, v.grp, CASE WHEN v.grp = 'SITE' THEN now() END, v.code = 'WRK07'
FROM (VALUES
    ('ADM02', 'Duc Pham',    'duc@example.com',   '0900000020', 'ADMIN',       true,  'OFFICE'),
    ('HSE01', 'Hana Tran',   'hana@example.com',  '0900000001', 'HSE_OFFICER', true,  'OFFICE'),
    ('HSE02', 'Huy Pham',    'huy@example.com',   '0900000002', 'HSE_OFFICER', true,  'OFFICE'),
    ('MGR01', 'Minh Le',     'minh@example.com',  '0900000003', 'MANAGER',     true,  'SITE'),
    ('MGR02', 'Mai Nguyen',  'mai@example.com',   '0900000004', 'MANAGER',     true,  'SITE'),
    ('WRK01', 'An Vo',       'an@example.com',    '0900000011', 'WORKER',      true,  'SITE'),
    ('WRK02', 'Binh Do',     NULL,                '0900000012', 'WORKER',      true,  'SITE'),
    ('WRK03', 'Chi Hoang',   'chi@example.com',   '0900000013', 'WORKER',      true,  'SITE'),
    ('WRK04', 'Dung Bui',    NULL,                '0900000014', 'WORKER',      true,  'SITE'),
    ('WRK05', 'Giang Ngo',   'giang@example.com', '0900000015', 'WORKER',      true,  'SITE'),
    ('WRK06', 'Khoa Dang',   NULL,                '0900000016', 'WORKER',      false, 'SITE'),
    ('WRK07', 'Lan Truong',  NULL,                '0900000017', 'WORKER',      true,  'SITE'),
    ('OFC01', 'Oanh Ly',     'oanh@example.com',  '0900000030', 'WORKER',      true,  'OFFICE')
) AS v(code, name, email, phone, role, active, grp)
JOIN role r ON r.role_name = v.role;

INSERT INTO team (name, manager_id) VALUES
    ('Team A', pg_temp.person('MGR01')),
    ('Team B', pg_temp.person('MGR02'));

UPDATE app_user SET team_id = (SELECT id FROM team WHERE name = 'Team A') WHERE employee_code IN ('MGR01', 'WRK01', 'WRK02', 'WRK03', 'WRK07');
UPDATE app_user SET team_id = (SELECT id FROM team WHERE name = 'Team B') WHERE employee_code IN ('MGR02', 'WRK04', 'WRK05', 'WRK06');

-- ---------------------------------------------------------------------------------------------------
-- Zones and cameras (the database gives the codes GATE-IN, GATE-PPE, AREA-A, AREA-B)
-- ---------------------------------------------------------------------------------------------------
INSERT INTO zone (name, kind) VALUES ('Main gate', 'GATE_ATTENDANCE'), ('Gate PPE check', 'GATE_PPE');
INSERT INTO zone (name, kind, team_id) VALUES
    ('Area A - west embankment', 'WORK_AREA', (SELECT id FROM team WHERE name = 'Team A')),
    ('Area B - pier 3',          'WORK_AREA', (SELECT id FROM team WHERE name = 'Team B'));

INSERT INTO camera (camera_code, zone_id, role) VALUES
    ('CAM-01', pg_temp.place('GATE-IN'),  'ATTENDANCE'),
    ('CAM-02', pg_temp.place('GATE-PPE'), 'PPE'),
    ('CAM-03', pg_temp.place('AREA-A'),   'PPE'),
    ('CAM-04', pg_temp.place('AREA-B'),   'PPE');

-- ---------------------------------------------------------------------------------------------------
-- Shifts: 08:00 to 17:00 from three days ago to four days ahead; past ones are completed.
-- HSE01 is on duty 07:00 to 17:00 the same days.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO shift (user_id, zone_id, start_at, end_at, status, created_by)
SELECT pg_temp.person(w.code), pg_temp.place(w.zone), pg_temp.at(d, '08:00'), pg_temp.at(d, '17:00'),
       CASE WHEN d < 0 THEN 'COMPLETED' ELSE 'SCHEDULED' END, pg_temp.person(w.planner)
FROM (VALUES
    ('MGR01', 'AREA-A', 'ADMIN'),
    ('WRK01', 'AREA-A', 'MGR01'),
    ('WRK02', 'AREA-A', 'MGR01'),
    ('WRK03', 'AREA-A', 'MGR01'),
    ('WRK04', 'AREA-B', 'MGR02'),
    ('WRK05', 'AREA-B', 'MGR02'),
    ('OFC01', 'GATE-IN', 'ADMIN')
) AS w(code, zone, planner)
CROSS JOIN generate_series(-3, 4) AS d;

INSERT INTO shift (user_id, zone_id, start_at, end_at, status, created_by)
SELECT pg_temp.person('HSE01'), pg_temp.place('AREA-A'), pg_temp.at(d, '07:00'), pg_temp.at(d, '17:00'),
       CASE WHEN d < 0 THEN 'COMPLETED' ELSE 'SCHEDULED' END, pg_temp.person('ADMIN')
FROM generate_series(-3, 4) AS d;

UPDATE shift SET status = 'CANCELLED'
WHERE user_id = pg_temp.person('WRK05') AND start_at = pg_temp.at(2, '08:00');

-- ---------------------------------------------------------------------------------------------------
-- Visits. Yesterday shows one of each attendance result (Team A, Team B and the office).
-- ---------------------------------------------------------------------------------------------------
INSERT INTO check_in (user_id, shift_id, zone_id, camera_id, check_in_at, check_out_at, face_similarity)
SELECT pg_temp.person(v.code), pg_temp.shift_of(v.code, -1), pg_temp.place('GATE-IN'), pg_temp.cam('CAM-01'),
       pg_temp.at(-1, v.arrived), pg_temp.at(-1, v.left_at), 93.5
FROM (VALUES
    ('WRK01', '07:58', '17:02'),   -- on time
    ('WRK02', '08:20', '17:00'),   -- late 20 min, paid from 08:20 (BR-31)
    ('WRK03', '08:00', '16:30'),   -- left 30 min early (BR-32)
    ('WRK05', '07:55', '18:30'),   -- stayed late: pending overtime 17:20 to 18:30 (BR-34)
    ('MGR01', '07:50', '17:05'),
    ('OFC01', '08:03', '17:00')
) AS v(code, arrived, left_at);

-- WRK04 forgot to check out yesterday: Incomplete, closed by the system 4 hours after the shift (BR-35)
INSERT INTO check_in (user_id, shift_id, zone_id, camera_id, check_in_at, check_out_at, face_similarity,
                      auto_closed, incomplete, incomplete_at)
VALUES (pg_temp.person('WRK04'), pg_temp.shift_of('WRK04', -1), pg_temp.place('GATE-IN'), pg_temp.cam('CAM-01'),
        pg_temp.at(-1, '08:01'), pg_temp.at(-1, '21:00'), 92.0, true, true, pg_temp.at(-1, '23:00'));

INSERT INTO pending_overtime (check_in_id, user_id, shift_id, start_at, end_at, expires_at)
SELECT c.id, c.user_id, c.shift_id, pg_temp.at(-1, '17:20'), pg_temp.at(-1, '18:30'), now() + interval '30 hours'
FROM check_in c WHERE c.user_id = pg_temp.person('WRK05') AND c.shift_id = pg_temp.shift_of('WRK05', -1);

-- Two days ago: everyone on time, and WRK05 entered by hand because the gate camera was down
INSERT INTO check_in (user_id, shift_id, zone_id, camera_id, check_in_at, check_out_at, face_similarity)
SELECT s.user_id, s.id, pg_temp.place('GATE-IN'), pg_temp.cam('CAM-01'), pg_temp.at(-2, '07:55'), pg_temp.at(-2, '17:05'), 95
FROM shift s
WHERE s.start_at = pg_temp.at(-2, '08:00') AND s.status <> 'CANCELLED' AND s.user_id <> pg_temp.person('WRK05');

INSERT INTO check_in (user_id, shift_id, zone_id, method, recorded_by, check_in_at, check_out_at, note)
VALUES (pg_temp.person('WRK05'), pg_temp.shift_of('WRK05', -2), pg_temp.place('GATE-IN'), 'MANUAL', pg_temp.person('HSE01'),
        pg_temp.at(-2, '08:05'), pg_temp.at(-2, '17:05'), 'Gate camera was offline in the morning');

-- On site now: arrived this morning (only those whose arrival time has passed)
INSERT INTO check_in (user_id, shift_id, zone_id, camera_id, check_in_at, face_similarity)
SELECT pg_temp.person(v.code), pg_temp.shift_of(v.code, 0), pg_temp.place('GATE-IN'), pg_temp.cam('CAM-01'),
       pg_temp.at(0, v.arrived), 91
FROM (VALUES ('WRK01', '07:57'), ('WRK02', '08:04'), ('WRK03', '08:12'), ('MGR01', '07:45')) AS v(code, arrived)
WHERE pg_temp.at(0, v.arrived) < now();

-- ---------------------------------------------------------------------------------------------------
-- Violations. Each arrives as the detection app writes it: PENDING_REVIEW with only a suggestion (the
-- trigger fills the zone and alerts HSE). Some are then decided as HSE would.
-- ---------------------------------------------------------------------------------------------------
INSERT INTO violation (camera_id, violation_type, detected_at, confidence, snapshot_path, suggested_user_id, suggestion_similarity, event_id)
VALUES
    (pg_temp.cam('CAM-03'), 'NO_HARD_HAT',          pg_temp.recent(1),  91.2, 'sample/v01.jpg', pg_temp.person('WRK01'), 0.871, gen_random_uuid()),
    (pg_temp.cam('CAM-02'), 'NO_VEST',              pg_temp.recent(2),  88.0, 'sample/v02.jpg', NULL,                    NULL,  gen_random_uuid()),
    (pg_temp.cam('CAM-04'), 'NO_HARD_HAT_AND_VEST', pg_temp.recent(26), 84.5, 'sample/v03.jpg', pg_temp.person('WRK04'), 0.702, gen_random_uuid()),
    (pg_temp.cam('CAM-03'), 'NO_HARD_HAT',          pg_temp.recent(27), 96.8, 'sample/v04.jpg', pg_temp.person('WRK02'), 0.912, gen_random_uuid()),
    (pg_temp.cam('CAM-03'), 'NO_VEST',              pg_temp.recent(50), 93.1, 'sample/v05.jpg', pg_temp.person('WRK02'), 0.884, gen_random_uuid()),
    (pg_temp.cam('CAM-04'), 'NO_VEST',              pg_temp.recent(51), 61.0, 'sample/v06.jpg', NULL,                    NULL,  gen_random_uuid()),
    (pg_temp.cam('CAM-02'), 'NO_HARD_HAT',          pg_temp.recent(28), 90.0, 'sample/v07.jpg', pg_temp.person('OFC01'), 0.801, gen_random_uuid()),
    (pg_temp.cam('CAM-03'), 'NO_HARD_HAT',          pg_temp.recent(29), 92.0, 'sample/v08.jpg', pg_temp.person('WRK03'), 0.850, gen_random_uuid()),
    (pg_temp.cam('CAM-03'), 'NO_VEST',              pg_temp.recent(30), 92.0, 'sample/v09.jpg', pg_temp.person('WRK03'), 0.850, gen_random_uuid()),
    (pg_temp.cam('CAM-03'), 'NO_HARD_HAT',          pg_temp.recent(52), 92.0, 'sample/v10.jpg', pg_temp.person('WRK03'), 0.850, gen_random_uuid()),
    (pg_temp.cam('CAM-03'), 'NO_HARD_HAT',          pg_temp.recent(53), 92.0, 'sample/v11.jpg', pg_temp.person('WRK03'), 0.850, gen_random_uuid());

-- v01, v02: waiting in the Review Queue. v03: not reviewed yesterday, so OVERDUE (BR-53).
UPDATE violation SET review_status = 'OVERDUE' WHERE snapshot_path = 'sample/v03.jpg';

-- v04, v05: confirmed for WRK02 (score 90); v05 is appealed, see the tickets below
UPDATE violation SET user_id = pg_temp.person('WRK02'), subject = 'EMPLOYEE', review_status = 'CONFIRMED',
                     reviewed_by = pg_temp.person('HSE01'), reviewed_at = detected_at + interval '20 minutes'
WHERE snapshot_path IN ('sample/v04.jpg', 'sample/v05.jpg');
UPDATE violation SET dispute_status = 'DISPUTED' WHERE snapshot_path = 'sample/v05.jpg';

-- v06: dismissed as a false detection
UPDATE violation SET review_status = 'DISMISSED', dismiss_reason = 'FALSE_DETECTION',
                     reviewed_by = pg_temp.person('HSE02'), reviewed_at = detected_at + interval '15 minutes'
WHERE snapshot_path = 'sample/v06.jpg';

-- v07: an office employee, so a reminder with no points (BR-51)
UPDATE violation SET user_id = pg_temp.person('OFC01'), subject = 'EMPLOYEE', review_status = 'REMINDER',
                     reviewed_by = pg_temp.person('HSE01'), reviewed_at = detected_at + interval '10 minutes'
WHERE snapshot_path = 'sample/v07.jpg';

-- v08 to v11: four confirmed for WRK03, so a score of 80 and bonus level 1 (BR-57, BR-58)
UPDATE violation SET user_id = pg_temp.person('WRK03'), subject = 'EMPLOYEE', review_status = 'CONFIRMED',
                     reviewed_by = pg_temp.person('HSE01'), reviewed_at = detected_at + interval '20 minutes'
WHERE snapshot_path IN ('sample/v08.jpg', 'sample/v09.jpg', 'sample/v10.jpg', 'sample/v11.jpg');

-- ---------------------------------------------------------------------------------------------------
-- Tickets, one per category, all handled by the Admin (BR-60, BR-61)
-- ---------------------------------------------------------------------------------------------------
-- WRK02's appeal of v05; the Admin has asked HSE01 for the facts
INSERT INTO ticket (category_id, subject, description, created_by, violation_id, created_at)
SELECT pg_temp.category('PPE_APPEAL'), 'Appeal - violation #' || v.id,
       'I was wearing my vest; it was under my raincoat for a moment.', pg_temp.person('WRK02'), v.id, v.reviewed_at + interval '2 hours'
FROM violation v WHERE v.snapshot_path = 'sample/v05.jpg';
INSERT INTO ticket_participant (ticket_id, user_id, asked_by)
SELECT t.id, pg_temp.person('HSE01'), pg_temp.person('ADMIN') FROM ticket t WHERE t.subject LIKE 'Appeal - violation #%';

-- WRK02 says yesterday's check-in was earlier than recorded
INSERT INTO ticket (category_id, subject, description, created_by, shift_id, direction, gate_time)
VALUES (pg_temp.category('ATTENDANCE_ERROR'), 'Check-in recorded late', 'I was at the gate at 08:00 but the camera only saw me later.',
        pg_temp.person('WRK02'), pg_temp.shift_of('WRK02', -1), 'IN', pg_temp.at(-1, '08:00'));

-- WRK04 explains yesterday's missing check-out
INSERT INTO ticket (category_id, subject, description, created_by, shift_id, check_in_id)
SELECT pg_temp.category('MISSING_CHECK_OUT'), 'Forgot to check out', 'I left at 17:00 by the side gate because the main gate was busy.',
       pg_temp.person('WRK04'), c.shift_id, c.id
FROM check_in c WHERE c.user_id = pg_temp.person('WRK04') AND c.incomplete;

-- WRK01 asks for the afternoon of the day after tomorrow off; their team leader has commented
INSERT INTO ticket (category_id, subject, description, created_by, shift_id, leave_type, leave_from, leave_to)
VALUES (pg_temp.category('LEAVE'), 'Afternoon off for a family matter', 'I need to take my child to the hospital in the afternoon.',
        pg_temp.person('WRK01'), pg_temp.shift_of('WRK01', 2), 'PAID', pg_temp.at(2, '13:00'), pg_temp.at(2, '17:00'));
INSERT INTO ticket_comment (ticket_id, author_id, body)
SELECT t.id, pg_temp.person('MGR01'), 'Confirmed with me; the team can cover the afternoon.'
FROM ticket t WHERE t.subject = 'Afternoon off for a family matter';

-- An "other" request the Admin already approved
INSERT INTO ticket (category_id, subject, description, created_by, status, outcome, decision_note,
                    assignee_id, resolver_id, resolved_at, created_at)
VALUES (pg_temp.category('OTHER'), 'New safety boots', 'Mine have split at the toe. Size 42.', pg_temp.person('WRK05'),
        'RESOLVED', 'APPROVED', 'New boots are at the site office; collect them on Monday.',
        pg_temp.person('ADMIN'), pg_temp.person('ADMIN'), pg_temp.at(-1, '10:00'), pg_temp.at(-2, '12:15'));

COMMIT;
