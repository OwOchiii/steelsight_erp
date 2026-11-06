-- Sample people and work for trying the pages on a local database. Never run this on production.
--
-- Start the app once first, so the tables and the ADMIN account exist. Then, from the project root:
--   docker exec -i steelsight-db psql -v ON_ERROR_STOP=1 -U postgres -d steelsight_local < scripts/dev-sample-data.sql
--
-- Every sample account uses ADMIN's password, so no password is stored here. Dates count from today.
--   HSE01, HSE02  HSE officers
--   MGR01         Manager of Team A (WRK01 to WRK03), late after 15 minutes
--   MGR02         Manager of Team B (WRK04 to WRK06), late after 30 minutes
--   WRK01..WRK06  Workers; WRK06 is inactive

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
        RAISE EXCEPTION 'The sample data is already loaded';
    END IF;
END $$;

-- A time at the site, counted in days from today
CREATE FUNCTION pg_temp.at(p_day int, p_time text) RETURNS timestamptz AS $$
    SELECT (((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + p_day) + p_time::time) AT TIME ZONE 'Asia/Ho_Chi_Minh';
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.person(p_code text) RETURNS bigint AS $$
    SELECT id FROM app_user WHERE employee_code = p_code;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.place(p_name text) RETURNS bigint AS $$
    SELECT id FROM zone WHERE name = p_name;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.cam(p_code text) RETURNS bigint AS $$
    SELECT id FROM camera WHERE camera_code = p_code;
$$ LANGUAGE sql STABLE;

CREATE FUNCTION pg_temp.category(p_name text) RETURNS bigint AS $$
    SELECT id FROM ticket_category WHERE category_name = p_name;
$$ LANGUAGE sql STABLE;

-- People and teams
INSERT INTO app_user (role_id, employee_code, full_name, email, phone_number, password_hash, is_active)
SELECT r.id, v.code, v.name, v.email, v.phone, (SELECT password_hash FROM app_user WHERE employee_code = 'ADMIN'), v.active
FROM (VALUES
    ('HSE01', 'Hana Tran',   'hana@example.com',  '0900000001', 'HSE_OFFICER', true),
    ('HSE02', 'Huy Pham',    'huy@example.com',   '0900000002', 'HSE_OFFICER', true),
    ('MGR01', 'Minh Le',     'minh@example.com',  '0900000003', 'MANAGER',     true),
    ('MGR02', 'Mai Nguyen',  'mai@example.com',   '0900000004', 'MANAGER',     true),
    ('WRK01', 'An Vo',       'an@example.com',    '0900000011', 'WORKER',      true),
    ('WRK02', 'Binh Do',     NULL,                '0900000012', 'WORKER',      true),
    ('WRK03', 'Chi Hoang',   'chi@example.com',   NULL,         'WORKER',      true),
    ('WRK04', 'Dung Bui',    NULL,                NULL,         'WORKER',      true),
    ('WRK05', 'Giang Ngo',   'giang@example.com', '0900000015', 'WORKER',      true),
    ('WRK06', 'Khoa Dang',   NULL,                NULL,         'WORKER',      false)
) AS v(code, name, email, phone, role, active)
JOIN role r ON r.role_name = v.role;

INSERT INTO team (name, manager_id, late_after_minutes) VALUES
    ('Team A', pg_temp.person('MGR01'), 15),
    ('Team B', pg_temp.person('MGR02'), 30);

UPDATE app_user SET team_id = (SELECT id FROM team WHERE name = 'Team A') WHERE employee_code IN ('WRK01', 'WRK02', 'WRK03');
UPDATE app_user SET team_id = (SELECT id FROM team WHERE name = 'Team B') WHERE employee_code IN ('WRK04', 'WRK05', 'WRK06');

-- Places and cameras
INSERT INTO zone (name) VALUES ('Furnace Bay'), ('Rolling Mill'), ('Warehouse');
INSERT INTO camera (camera_code, zone_id) VALUES
    ('CAM-FB1', pg_temp.place('Furnace Bay')),
    ('CAM-RM1', pg_temp.place('Rolling Mill')),
    ('CAM-WH1', pg_temp.place('Warehouse'));

-- Shifts: 08:00 to 17:00 from three days ago to four days ahead. Past ones are completed.
INSERT INTO shift (user_id, zone_id, start_at, end_at, status, created_by)
SELECT pg_temp.person(w.code), pg_temp.place(w.zone), pg_temp.at(d, '08:00'), pg_temp.at(d, '17:00'),
       CASE WHEN d < 0 THEN 'COMPLETED' ELSE 'SCHEDULED' END, pg_temp.person(w.manager)
FROM (VALUES
    ('WRK01', 'Furnace Bay',  'MGR01'),
    ('WRK02', 'Furnace Bay',  'MGR01'),
    ('WRK03', 'Rolling Mill', 'MGR01'),
    ('WRK04', 'Rolling Mill', 'MGR02'),
    ('WRK05', 'Warehouse',    'MGR02')
) AS w(code, zone, manager)
CROSS JOIN generate_series(-3, 4) AS d;

UPDATE shift SET status = 'CANCELLED'
WHERE user_id = pg_temp.person('WRK05') AND start_at = pg_temp.at(2, '08:00');

-- Officers and managers have timetables of their own too
INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
SELECT pg_temp.person(c), pg_temp.place('Warehouse'), pg_temp.at(1, '09:00'), pg_temp.at(1, '18:00'), pg_temp.person('ADMIN')
FROM unnest(ARRAY['HSE01', 'MGR01']) AS c;

-- Yesterday's visits, one for each attendance result
INSERT INTO check_in (user_id, shift_id, zone_id, camera_id, check_in_at, check_out_at, face_similarity, auto_closed)
SELECT pg_temp.person(v.code), s.id, s.zone_id, c.id, pg_temp.at(-1, v.arrived), pg_temp.at(-1, v.left_at), 93.5, v.auto
FROM (VALUES
    ('WRK01', '08:00', '17:00', false),   -- on time
    ('WRK02', '08:15', '17:15', false),   -- on time, made up 15 minutes
    ('WRK03', '09:00', '17:15', false),   -- late, stayed until the 15-minute limit
    ('WRK04', '09:00', '17:00', false),   -- late and early: Team B must stay until 17:30
    ('WRK05', '08:20', '17:00', true)     -- never checked out, so 17:00 is an estimate
) AS v(code, arrived, left_at, auto)
JOIN shift s ON s.user_id = pg_temp.person(v.code) AND s.start_at = pg_temp.at(-1, '08:00')
JOIN camera c ON c.zone_id = s.zone_id;

-- Two days ago: everyone on time, plus one entered by hand
INSERT INTO check_in (user_id, shift_id, zone_id, camera_id, check_in_at, check_out_at, face_similarity)
SELECT s.user_id, s.id, s.zone_id, c.id, pg_temp.at(-2, '07:55'), pg_temp.at(-2, '17:05'), 95
FROM shift s JOIN camera c ON c.zone_id = s.zone_id
WHERE s.start_at = pg_temp.at(-2, '08:00') AND s.user_id <> pg_temp.person('WRK05');

INSERT INTO check_in (user_id, shift_id, zone_id, method, recorded_by, check_in_at, check_out_at, note)
SELECT s.user_id, s.id, s.zone_id, 'MANUAL', pg_temp.person('HSE01'), pg_temp.at(-2, '08:05'), pg_temp.at(-2, '17:05'), 'Gate camera was offline'
FROM shift s
WHERE s.start_at = pg_temp.at(-2, '08:00') AND s.user_id = pg_temp.person('WRK05');

-- On site now, including one late arrival
INSERT INTO check_in (user_id, shift_id, zone_id, camera_id, check_in_at, face_similarity)
SELECT s.user_id, s.id, s.zone_id, c.id, LEAST(pg_temp.at(0, v.arrived), now()), 91
FROM (VALUES ('WRK01', '07:58'), ('WRK02', '08:05'), ('WRK03', '08:40')) AS v(code, arrived)
JOIN shift s ON s.user_id = pg_temp.person(v.code) AND s.start_at = pg_temp.at(0, '08:00')
JOIN camera c ON c.zone_id = s.zone_id;

-- Violations with a known worker, in each dispute state
INSERT INTO violation (user_id, camera_id, violation_type, detected_at, confidence, snapshot_path, dispute_status, reviewed_by, reviewed_at)
VALUES
    (pg_temp.person('WRK01'), pg_temp.cam('CAM-FB1'), 'NO_VEST',     pg_temp.at(-1, '10:12'), 91.20, 'sample/1.jpg', 'DISPUTED',   NULL, NULL),
    (pg_temp.person('WRK04'), pg_temp.cam('CAM-RM1'), 'NO_HARNESS',  pg_temp.at(-1, '14:30'), 84.00, 'sample/2.jpg', 'DISPUTED',   NULL, NULL),
    (pg_temp.person('WRK02'), pg_temp.cam('CAM-FB1'), 'NO_HARD_HAT', pg_temp.at(-1, '09:45'), 96.75, 'sample/3.jpg', 'NONE',       NULL, NULL),
    (pg_temp.person('WRK03'), pg_temp.cam('CAM-RM1'), 'NO_GLOVES',   pg_temp.at(-3, '11:05'), 88.10, 'sample/4.jpg', 'UPHELD',     pg_temp.person('HSE01'), pg_temp.at(-2, '09:00')),
    (pg_temp.person('WRK05'), pg_temp.cam('CAM-WH1'), 'NO_VEST',     pg_temp.at(-3, '15:20'), 62.40, 'sample/5.jpg', 'OVERTURNED', pg_temp.person('HSE02'), pg_temp.at(-2, '10:00'));

-- Enough violations with no worker to fill more than one page
INSERT INTO violation (camera_id, violation_type, detected_at, confidence, snapshot_path)
SELECT pg_temp.cam(CASE i % 3 WHEN 0 THEN 'CAM-FB1' WHEN 1 THEN 'CAM-RM1' ELSE 'CAM-WH1' END),
       (ARRAY['NO_HARD_HAT', 'NO_VEST', 'NO_GLOVES', 'NO_HARNESS'])[i % 4 + 1],
       now() - i * interval '5 hours', 70 + i, 'sample/unknown-' || i || '.jpg'
FROM generate_series(1, 23) AS i;

-- The tickets behind those disputes
INSERT INTO ticket (category_id, subject, description, created_by, violation_id, status, resolver_id, resolved_at, created_at)
SELECT pg_temp.category('PPE dispute'), v.subject, v.reason, x.user_id, x.id, v.status,
       CASE WHEN v.status = 'RESOLVED' THEN x.reviewed_by END,
       CASE WHEN v.status = 'RESOLVED' THEN x.reviewed_at END,
       x.detected_at + interval '3 hours'
FROM (VALUES
    ('sample/1.jpg', 'Dispute: No vest',    'I was wearing it. The vest was covered by my jacket for a moment.', 'OPEN'),
    ('sample/2.jpg', 'Dispute: No harness', 'I was on the ground at the time, so no harness was needed.',        'OPEN'),
    ('sample/4.jpg', 'Dispute: No gloves',  'I had only just taken them off to use the tablet.',                 'RESOLVED'),
    ('sample/5.jpg', 'Dispute: No vest',    'That is a visitor in the picture, not me.',                         'RESOLVED')
) AS v(path, subject, reason, status)
JOIN violation x ON x.snapshot_path = v.path;

INSERT INTO ticket_comment (ticket_id, author_id, body)
SELECT t.id, x.reviewed_by, v.body
FROM (VALUES
    ('sample/4.jpg', 'The footage shows gloves off near the furnace. Keep them on in this zone.'),
    ('sample/5.jpg', 'Confirmed this was a visitor. The violation is cancelled.')
) AS v(path, body)
JOIN violation x ON x.snapshot_path = v.path
JOIN ticket t ON t.violation_id = x.id;

-- Everyday tickets for each handling role
INSERT INTO ticket (category_id, subject, description, created_by, zone_id, assignee_id, status, resolver_id, resolved_at, created_at)
VALUES
    (pg_temp.category('Safety concern'), 'Loose guard rail on level 2', 'The rail moves when you lean on it.', pg_temp.person('WRK03'),
     pg_temp.place('Rolling Mill'), NULL, 'OPEN', NULL, NULL, pg_temp.at(-1, '11:00')),
    (pg_temp.category('Safety concern'), 'Oil spill near the gate', 'Slippery patch beside the loading door.', pg_temp.person('WRK05'),
     pg_temp.place('Warehouse'), pg_temp.person('HSE02'), 'IN_PROGRESS', NULL, NULL, pg_temp.at(-2, '13:30')),
    (pg_temp.category('Safety concern'), 'Fire extinguisher missing', 'The bracket by the east door was empty.', pg_temp.person('WRK01'),
     pg_temp.place('Furnace Bay'), pg_temp.person('HSE01'), 'RESOLVED', pg_temp.person('HSE01'), pg_temp.at(-2, '16:00'), pg_temp.at(-3, '09:30')),
    (pg_temp.category('Shift or timetable issue'), 'Swap Friday shift', 'Can I swap with Binh this Friday?', pg_temp.person('WRK01'),
     NULL, NULL, 'OPEN', NULL, NULL, pg_temp.at(-1, '16:40')),
    (pg_temp.category('Shift or timetable issue'), 'Wrong zone on my timetable', 'I should be in Rolling Mill next week.', pg_temp.person('WRK04'),
     NULL, pg_temp.person('MGR02'), 'IN_PROGRESS', NULL, NULL, pg_temp.at(-2, '08:50')),
    (pg_temp.category('Equipment request'), 'New safety boots', 'Mine have split at the toe. Size 42.', pg_temp.person('WRK02'),
     NULL, NULL, 'OPEN', NULL, NULL, pg_temp.at(-1, '12:15')),
    (pg_temp.category('Account or access'), 'Cannot sign in on the tablet', 'It says my code is wrong.', pg_temp.person('WRK05'),
     NULL, NULL, 'OPEN', NULL, NULL, pg_temp.at(-1, '07:50'));

INSERT INTO ticket_comment (ticket_id, author_id, body)
SELECT t.id, pg_temp.person('HSE01'), 'Replaced the extinguisher and checked the rest of the bay.'
FROM ticket t WHERE t.subject = 'Fire extinguisher missing';

COMMIT;
