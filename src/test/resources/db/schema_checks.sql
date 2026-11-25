-- Checks the database rules of the migrations (V1 to V26). Run by SchemaChecksSqlTest during mvn verify.
-- Everything rolls back. Any failed check stops with an error; success ends with "ALL CHECKS PASSED".
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
  v_worker bigint; v_other bigint; v_zone bigint; v_cam bigint; v_gate bigint;
  v_viol bigint; v_shift bigint; v_user bigint; v_closed integer; v_auto boolean;
BEGIN
  INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
  SELECT id, 'T1', 'Test One', 'x' FROM role WHERE role_name = 'WORKER' RETURNING id INTO v_worker;
  INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
  SELECT id, 'T2', 'Test Two', 'x' FROM role WHERE role_name = 'WORKER' RETURNING id INTO v_other;
  INSERT INTO app_user (role_id, employee_code, full_name, password_hash, staff_group)
  SELECT id, 'T3', 'Test Officer', 'x', 'OFFICE' FROM role WHERE role_name = 'HSE_OFFICER';
  INSERT INTO zone (name) VALUES ('Test zone') RETURNING id INTO v_zone;
  INSERT INTO camera (camera_code, zone_id) VALUES ('CAM-T', v_zone) RETURNING id INTO v_cam;

  -- V26: the vest tables, burst alerts and the per-team late tolerance are gone (CR-01, CR-02, BR-31)
  ASSERT to_regclass('public.ppe_vest') IS NULL AND to_regclass('public.ppe_linking') IS NULL
         AND to_regclass('public.safety_alert') IS NULL, 'the vest and burst alert tables should be gone';
  ASSERT NOT EXISTS (SELECT 1 FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND (column_name = 'ppe_linking_id' OR (table_name = 'team' AND column_name = 'late_after_minutes'))),
    'no column should point at a vest link, and teams have no late tolerance';

  -- V26: each zone gets the next free code of its kind, and it never changes (SRS 3.2.23)
  ASSERT (SELECT code FROM zone WHERE id = v_zone) ~ '^AREA-[A-Z0-9]+$', 'a work area gets an AREA code';
  INSERT INTO zone (name, kind) VALUES ('Test gate', 'GATE_PPE') RETURNING id INTO v_gate;
  ASSERT (SELECT code FROM zone WHERE id = v_gate) LIKE 'GATE-PPE%', 'a gate PPE zone gets a GATE-PPE code';
  BEGIN
    UPDATE zone SET code = 'AREA-ZZ' WHERE id = v_zone;
    RAISE EXCEPTION 'CHECK_FAILED: a zone code was changed';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM LIKE 'CHECK_FAILED%' THEN RAISE; END IF;
  END;
  UPDATE zone SET name = 'Test zone renamed' WHERE id = v_zone;   -- the name stays the Admin's to change

  -- generated column
  INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
  VALUES (v_worker, v_zone, now() - interval '56 hours', now() - interval '48 hours', v_other)
  RETURNING id INTO v_shift;
  ASSERT (SELECT duration_hours FROM shift WHERE id = v_shift) = 8, 'duration_hours should be 8';

  -- BR-48: a violation starts PENDING_REVIEW with no person, and the zone comes from the camera
  INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
  VALUES (v_cam, 'NO_VEST', now(), 's1.jpg') RETURNING id, user_id INTO v_viol, v_user;
  ASSERT v_user IS NULL, 'a new violation has no person';
  ASSERT (SELECT review_status FROM violation WHERE id = v_viol) = 'PENDING_REVIEW', 'a new violation waits for review';
  ASSERT (SELECT zone_id FROM violation WHERE id = v_viol) = v_zone, 'the zone comes from the camera';

  -- a pending violation cannot be appealed
  BEGIN
    UPDATE violation SET dispute_status = 'DISPUTED' WHERE id = v_viol;
    RAISE EXCEPTION 'CHECK_FAILED: a pending violation was appealed';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- a person can't be put on a violation without a decision
  BEGIN
    UPDATE violation SET user_id = v_other WHERE id = v_viol;
    RAISE EXCEPTION 'CHECK_FAILED: a person without a decision was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- HSE confirms the person
  UPDATE violation SET user_id = v_other, subject = 'EMPLOYEE', review_status = 'CONFIRMED', reviewed_at = now() WHERE id = v_viol;

  -- a dismissal needs its reason, and Other needs 10 to 500 characters (BR-50)
  BEGIN
    INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path, review_status)
    VALUES (v_cam, 'NO_VEST', now(), 's6.jpg', 'DISMISSED');
    RAISE EXCEPTION 'CHECK_FAILED: a dismissal without a reason was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  BEGIN
    INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path, review_status, dismiss_reason, review_note)
    VALUES (v_cam, 'NO_VEST', now(), 's7.jpg', 'DISMISSED', 'OTHER', 'short');
    RAISE EXCEPTION 'CHECK_FAILED: Other with a short reason was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- the person the detection app suggests, or names, is only a suggestion (BR-47, BR-48)
  INSERT INTO violation (suggested_user_id, camera_id, violation_type, detected_at, snapshot_path)
  VALUES (v_worker, v_cam, 'NO_HARD_HAT', now(), 's2.jpg') RETURNING id, user_id INTO v_viol, v_user;
  ASSERT v_user IS NULL, 'a suggestion does not decide the person';
  ASSERT (SELECT suggested_user_id FROM violation WHERE id = v_viol) = v_worker, 'the suggestion is kept';
  INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
  VALUES (v_other, v_cam, 'NO_HARD_HAT_AND_VEST', now(), 's3.jpg') RETURNING id INTO v_viol;
  ASSERT (SELECT suggested_user_id FROM violation WHERE id = v_viol) = v_other, 'a named person is the suggestion';

  -- a READY embedding must have a vector
  BEGIN
    INSERT INTO face_embedding (user_id, source_file_path, status) VALUES (v_worker, 'f.jpg', 'READY');
    RAISE EXCEPTION 'CHECK_FAILED: READY row without an embedding was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO face_embedding (user_id, source_file_path) VALUES (v_worker, 'f.jpg');

  -- BR-35: a forgotten check-out is flagged Incomplete and closed 4 hours after the shift, so the worker
  -- can check in again; the check-out time is never paid
  INSERT INTO check_in (user_id, shift_id, check_in_at, face_similarity)
  VALUES (v_worker, v_shift, now() - interval '56 hours', 90);
  v_closed = close_stale_check_ins();
  ASSERT v_closed = 1, 'one visit should be closed by the system';
  SELECT auto_closed INTO v_auto FROM check_in WHERE user_id = v_worker;
  ASSERT v_auto, 'visit should be flagged closed by the system';
  ASSERT (SELECT incomplete FROM check_in WHERE user_id = v_worker), 'visit should be Incomplete attendance';
  INSERT INTO check_in (user_id, face_similarity) VALUES (v_worker, 91);
  ASSERT close_stale_check_ins() = 0, 'a current visit should stay open';
  UPDATE check_in SET check_out_at = now() + interval '1 minute' WHERE user_id = v_worker AND check_out_at IS NULL;

  -- BR-30: a check-in is attached to the person's shift up to 60 minutes before it starts
  INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
  VALUES (v_other, v_zone, now() + interval '50 minutes', now() + interval '5 hours', v_other) RETURNING id INTO v_shift;
  INSERT INTO check_in (user_id, face_similarity) VALUES (v_other, 92) RETURNING shift_id INTO v_user;
  ASSERT v_user = v_shift, 'a check-in 50 minutes early belongs to the shift';
  UPDATE check_in SET check_out_at = now() + interval '1 minute' WHERE user_id = v_other AND check_out_at IS NULL;

  -- every table the outbox writes to refuses a repeated event
  ASSERT (SELECT count(*) FROM pg_constraint
          WHERE conname IN ('uq_violation_event', 'uq_check_in_event')) = 2,
    'outbox tables should have unique event ids';

  -- the detection role can do its job
  SET LOCAL ROLE steelsight_detection;
  PERFORM id, face_file_path FROM app_user;
  PERFORM employee_code, full_name FROM app_user;          -- V13: names for the gate screen
  PERFORM zalo_chat_id FROM app_user;                      -- V18: gate check-in messages on Zalo
  PERFORM code, name, kind FROM zone;                       -- V26: camera settings name the zone by its code
  ASSERT (SELECT count(*) FROM face_embedding) = 1, 'detection should see embeddings';
  UPDATE face_embedding SET status = 'READY', model_name = 'buffalo_l', embedding = array_fill(0.1, ARRAY[512])::vector;
  INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
  VALUES (v_cam, 'NO_VEST', now(), 's4.jpg');
  INSERT INTO check_in (user_id, face_similarity) VALUES (v_other, 88);
  PERFORM close_stale_check_ins();

  -- a retried outbox event changes nothing
  INSERT INTO violation (event_id, camera_id, violation_type, detected_at, snapshot_path)
  VALUES ('00000000-0000-0000-0000-000000000001', v_cam, 'NO_VEST', now(), 's5.jpg')
  ON CONFLICT (event_id) DO NOTHING;
  INSERT INTO violation (event_id, camera_id, violation_type, detected_at, snapshot_path)
  VALUES ('00000000-0000-0000-0000-000000000001', v_cam, 'NO_VEST', now(), 's5.jpg')
  ON CONFLICT (event_id) DO NOTHING;
  ASSERT (SELECT count(*) FROM violation WHERE event_id = '00000000-0000-0000-0000-000000000001') = 1,
    'a retried violation should be ignored';

  -- and nothing else
  BEGIN
    PERFORM password_hash FROM app_user;
    RAISE EXCEPTION 'CHECK_FAILED: detection read password_hash';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  BEGIN
    PERFORM email FROM app_user;
    RAISE EXCEPTION 'CHECK_FAILED: detection read email';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  BEGIN
    PERFORM phone_number FROM app_user;
    RAISE EXCEPTION 'CHECK_FAILED: detection read phone_number';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  BEGIN
    UPDATE violation SET dispute_status = 'OVERTURNED';
    RAISE EXCEPTION 'CHECK_FAILED: detection changed a dispute';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  BEGIN
    PERFORM 1 FROM ticket;
    RAISE EXCEPTION 'CHECK_FAILED: detection read tickets';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
  RESET ROLE;

  -- BR-54: each violation the detection app writes alerts the HSE Officers (nobody is on duty here, so all of them and the Admins)
  ASSERT (SELECT count(*) FROM notification WHERE kind = 'VIOLATION_ALERT') > 0, 'a new violation should alert someone';

  -- every notice gets a delivery row: SKIPPED without a linked Zalo chat (BR-16)
  ASSERT NOT EXISTS (SELECT 1 FROM notification n LEFT JOIN notification_delivery d ON d.notification_id = n.id
                     WHERE d.notification_id IS NULL), 'every notice should have a delivery row';

  RAISE NOTICE 'ALL CHECKS PASSED';
END $$;

ROLLBACK;
