-- Tickets go to HSE (safety and disputes) or to admin (everything else). Team leaders no longer handle
-- tickets, so the team-ticket rules from V9 go. Each team leader leads at most one team. Leaders and
-- admins can export a monthly report for accounting.

-- Admin takes over the categories managers used to handle, and gets leave requests.
UPDATE ticket_category SET handler_role_id = (SELECT id FROM role WHERE role_name = 'ADMIN')
WHERE category_name IN ('Shift or timetable issue', 'Equipment request');

INSERT INTO ticket_category (category_name, handler_role_id)
SELECT 'Leave request', id FROM role WHERE role_name = 'ADMIN';

-- A manager can no longer work on these, so open ones go back to the queue for an admin to pick up.
UPDATE ticket t SET assignee_id = NULL
FROM app_user u JOIN role r ON r.id = u.role_id
WHERE u.id = t.assignee_id AND r.role_name = 'MANAGER' AND t.status <> 'RESOLVED';

-- The permission checks as they were before V9, without the team branch.
CREATE OR REPLACE FUNCTION can_read_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (
    SELECT 1
    FROM ticket t
             JOIN ticket_category c ON c.id = t.category_id
             JOIN app_user u        ON u.id = p_user
    WHERE t.id = p_ticket AND t.deleted_at IS NULL
      AND (has_permission(p_user, 'TICKET_READ_ALL')
        OR (has_permission(p_user, 'TICKET_READ_OWN') AND t.created_by = p_user)
        OR (has_permission(p_user, 'TICKET_READ_HANDLED') AND u.role_id = c.handler_role_id)));
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_update_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT (has_permission(p_user, 'TICKET_UPDATE_ALL')
            AND EXISTS (SELECT 1 FROM ticket t WHERE t.id = p_ticket AND t.deleted_at IS NULL))
    OR (has_permission(p_user, 'TICKET_UPDATE_HANDLED')
           AND EXISTS (
        SELECT 1
        FROM ticket t
                 JOIN ticket_category c ON c.id = t.category_id
                 JOIN app_user u        ON u.id = p_user AND u.role_id = c.handler_role_id
        WHERE t.id = p_ticket AND t.deleted_at IS NULL));
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_delete_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (
    SELECT 1
    FROM ticket t
             JOIN ticket_category c ON c.id = t.category_id
             JOIN app_user u        ON u.id = p_user
    WHERE t.id = p_ticket AND t.deleted_at IS NULL
      AND (has_permission(p_user, 'TICKET_DELETE_ALL')
        OR (has_permission(p_user, 'TICKET_DELETE_HANDLED') AND u.role_id = c.handler_role_id)));
$$ LANGUAGE sql STABLE;

DROP FUNCTION ticket_from_my_team(bigint, bigint);

DELETE FROM role_permission rp
USING permission p
WHERE rp.permission_id = p.id AND p.code IN ('TICKET_READ_TEAM', 'TICKET_UPDATE_TEAM', 'TICKET_DELETE_TEAM');
DELETE FROM permission WHERE code IN ('TICKET_READ_TEAM', 'TICKET_UPDATE_TEAM', 'TICKET_DELETE_TEAM');

-- One team per leader.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM team WHERE manager_id IS NOT NULL GROUP BY manager_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'A manager leads more than one team. Give each of those teams its own leader, then run this again.';
    END IF;
END $$;
CREATE UNIQUE INDEX uq_team_manager ON team(manager_id) WHERE manager_id IS NOT NULL;

-- The monthly report for accounting: a leader exports their own team, an admin everyone. Admin holds
-- every permission, as in V1, so it gets the team one too; the report checks REPORT_EXPORT_ALL first.
INSERT INTO permission (code, module, action, description) VALUES
    ('REPORT_EXPORT_TEAM', 'REPORT', 'READ', 'Export the monthly report of the team I lead'),
    ('REPORT_EXPORT_ALL',  'REPORT', 'READ', 'Export the monthly report of every worker');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES ('MANAGER', 'REPORT_EXPORT_TEAM'), ('ADMIN', 'REPORT_EXPORT_ALL'), ('ADMIN', 'REPORT_EXPORT_TEAM')) AS v(role_name, code)
         JOIN role r       ON r.role_name = v.role_name
         JOIN permission p ON p.code = v.code;
