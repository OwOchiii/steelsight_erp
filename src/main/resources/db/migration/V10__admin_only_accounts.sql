-- Only admins change accounts. Managers organise shifts for the teams they run, but no longer edit
-- anyone's details.
DELETE FROM role_permission rp
USING permission p
WHERE rp.permission_id = p.id AND p.code = 'USER_UPDATE_TEAM';

DELETE FROM permission WHERE code = 'USER_UPDATE_TEAM';

CREATE OR REPLACE FUNCTION can_update_user(p_user bigint, p_target bigint) RETURNS boolean AS $$
SELECT has_permission(p_user, 'USER_MANAGE')
   AND EXISTS (SELECT 1 FROM app_user w WHERE w.id = p_target AND w.deleted_at IS NULL);
$$ LANGUAGE sql STABLE;

-- Set when an admin chooses someone's password, on a new account or a reset. They must pick their own
-- at the next sign-in, so the password the admin knows stops working. Existing accounts start false.
ALTER TABLE app_user ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
