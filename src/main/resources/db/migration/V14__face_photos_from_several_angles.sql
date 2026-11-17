-- A worker can have face photos from several angles, not just one looking at the camera.
-- The gate camera often sees a face turned to one side, and ArcFace scores a turned face much
-- lower against a photo taken straight on. With a LEFT and a RIGHT photo as well, the detection
-- service compares each face against every angle of a worker and keeps the best score.
--
-- Each angle is its own row and its own place in the enrolment queue (ADR-011), so no new
-- embedding column is needed. app_user.face_file_path stays the FRONT photo. The LEFT and RIGHT
-- photos are kept only in source_file_path. Existing rows become FRONT.
--
-- The detection role's existing GRANT SELECT ON face_embedding covers the new column. Its UPDATE
-- grant (status, embedding, model_name, error_message) does not, so it cannot change a row's pose.
-- The soft-delete trigger from V1 already deletes every row of the worker (ADR-007).
ALTER TABLE face_embedding
    ADD COLUMN pose text NOT NULL DEFAULT 'FRONT'
        CHECK (pose IN ('FRONT', 'LEFT', 'RIGHT'));

ALTER TABLE face_embedding DROP CONSTRAINT face_embedding_user_id_key;
ALTER TABLE face_embedding ADD CONSTRAINT face_embedding_user_pose_key UNIQUE (user_id, pose);
