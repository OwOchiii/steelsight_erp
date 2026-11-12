-- Checks the rules in db/migration/V1__init_schema.sql. Run by SchemaChecksSqlTest during mvn verify.
-- Everything rolls back. Any failed check stops with an error; success ends with "ALL CHECKS PASSED".
\set ON_ERROR_STOP on
BEGIN;

DO $$
DECLARE
  v_worker bigint; v_other bigint; v_zone bigint; v_cam bigint; v_vest bigint; v_link bigint;
  v_viol bigint; v_shift bigint; v_user bigint; v_closed integer; v_auto boolean;
BEGIN
  INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
  SELECT id, 'T1', 'Test One', 'x' FROM role WHERE role_name = 'WORKER' RETURNING id INTO v_worker;
  INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
  SELECT id, 'T2', 'Test Two', 'x' FROM role WHERE role_name = 'WORKER' RETURNING id INTO v_other;
  INSERT INTO zone (name) VALUES ('Test zone') RETURNING id INTO v_zone;
  INSERT INTO camera (camera_code, zone_id) VALUES ('CAM-T', v_zone) RETURNING id INTO v_cam;
  INSERT INTO ppe_vest (vest_code) VALUES ('V-T') RETURNING id INTO v_vest;
  INSERT INTO ppe_linking (ppe_vest_id, user_id) VALUES (v_vest, v_worker) RETURNING id INTO v_link;

  -- generated column
  INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by)
  VALUES (v_worker, v_zone, now() - interval '56 hours', now() - interval '48 hours', v_other)
  RETURNING id INTO v_shift;
  ASSERT (SELECT duration_hours FROM shift WHERE id = v_shift) = 8, 'duration_hours should be 8';

  -- a violation with no vest is allowed and shows as unidentified
  INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
  VALUES (v_cam, 'NO_VEST', now(), 's1.jpg') RETURNING id, user_id INTO v_viol, v_user;
  ASSERT v_user IS NULL, 'violation without a vest should be unidentified';

  -- an unidentified violation cannot be disputed
  BEGIN
    UPDATE violation SET dispute_status = 'DISPUTED' WHERE id = v_viol;
    RAISE EXCEPTION 'CHECK_FAILED: unidentified violation was disputed';
  EXCEPTION WHEN check_violation THEN NULL;
  END;

  -- HSE can name the worker with no vest link
  UPDATE violation SET user_id = v_other WHERE id = v_viol;

  -- a vest read fills in the worker
  INSERT INTO violation (ppe_linking_id, camera_id, violation_type, detected_at, snapshot_path)
  VALUES (v_link, v_cam, 'NO_HARD_HAT', now(), 's2.jpg') RETURNING user_id INTO v_user;
  ASSERT v_user = v_worker, 'worker should come from the vest link';

  -- a link to a different worker is refused
  BEGIN
    INSERT INTO violation (user_id, ppe_linking_id, camera_id, violation_type, detected_at, snapshot_path)
    VALUES (v_other, v_link, v_cam, 'NO_GLOVES', now(), 's3.jpg');
    RAISE EXCEPTION 'CHECK_FAILED: mismatched worker was accepted';
  EXCEPTION WHEN raise_exception THEN
    IF SQLERRM LIKE 'CHECK_FAILED%' THEN RAISE; END IF;
  END;

  -- a READY embedding must have a vector
  BEGIN
    INSERT INTO face_embedding (user_id, source_file_path, status) VALUES (v_worker, 'f.jpg', 'READY');
    RAISE EXCEPTION 'CHECK_FAILED: READY row without an embedding was accepted';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  INSERT INTO face_embedding (user_id, source_file_path) VALUES (v_worker, 'f.jpg');

  -- a forgotten check-out closes, and the worker can check in again
  INSERT INTO check_in (user_id, shift_id, check_in_at, face_similarity)
  VALUES (v_worker, v_shift, now() - interval '56 hours', 90);
  v_closed = close_stale_check_ins();
  ASSERT v_closed = 1, 'one visit should auto-close';
  SELECT auto_closed INTO v_auto FROM check_in WHERE user_id = v_worker;
  ASSERT v_auto, 'visit should be flagged auto_closed';
  INSERT INTO check_in (user_id, face_similarity) VALUES (v_worker, 91);
  ASSERT close_stale_check_ins() = 0, 'a current visit should stay open';
  UPDATE check_in SET check_out_at = now() + interval '1 minute' WHERE user_id = v_worker AND check_out_at IS NULL;

  -- every table the outbox writes to refuses a repeated event
  ASSERT (SELECT count(*) FROM pg_constraint
          WHERE conname IN ('uq_violation_event', 'uq_check_in_event', 'uq_ppe_linking_event')) = 3,
    'outbox tables should have unique event ids';

  -- the detection role can do its job
  SET LOCAL ROLE steelsight_detection;
  PERFORM id, face_file_path FROM app_user;
  PERFORM employee_code, full_name FROM app_user;          -- V13: names for the gate screen
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
    PERFORM email, phone_number FROM app_user;
    RAISE EXCEPTION 'CHECK_FAILED: detection read contact details';
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

  RAISE NOTICE 'ALL CHECKS PASSED';
END $$;

ROLLBACK;
