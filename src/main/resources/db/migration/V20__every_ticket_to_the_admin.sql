-- Every ticket goes to the Admin, as the SRS says (UC-24, BR-61, 3.1.3):
--   * every category is handled by ADMIN, including PPE appeals and safety concerns, which went to HSE;
--   * HSE no longer has a ticket queue. An HSE Officer gives the Admin the facts of a shift instead;
--   * an Admin cannot handle a ticket they opened themselves, so another Admin handles it (UC-24 EX-01);
--   * the team leader of the person who opened a ticket can read it and comment, but not decide it.
--
-- VIOLATION_REVIEW stays with HSE for naming the worker on a violation. Deciding an appeal is now a
-- ticket action, checked with can_update_ticket on the appeal's ticket (ViolationService.decide).

UPDATE ticket_category SET handler_role_id = (SELECT id FROM role WHERE role_name = 'ADMIN');

-- Open tickets an HSE Officer had taken go back to the Admin queue.
UPDATE ticket t SET assignee_id = NULL, status = 'OPEN'
FROM app_user u JOIN role r ON r.id = u.role_id
WHERE u.id = t.assignee_id AND r.role_name = 'HSE_OFFICER' AND t.status <> 'RESOLVED';

DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND r.role_name = 'HSE_OFFICER'
  AND p.code IN ('TICKET_READ_HANDLED', 'TICKET_UPDATE_HANDLED', 'TICKET_DELETE_HANDLED');

UPDATE permission SET description = 'Name the worker on a PPE violation' WHERE code = 'VIOLATION_REVIEW';

-- Read and comment only; V11 removed the old TICKET_READ_TEAM, which also let a leader decide.
INSERT INTO permission (code, module, action, description) VALUES
    ('TICKET_READ_TEAM', 'TICKET', 'READ', 'See and comment on tickets opened by the team I lead');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role r JOIN permission p ON p.code = 'TICKET_READ_TEAM'
WHERE r.role_name IN ('MANAGER', 'ADMIN');

-- True when p_user leads the team of the person who opened the ticket.
CREATE FUNCTION ticket_from_my_team(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT has_permission(p_user, 'TICKET_READ_TEAM') AND EXISTS (
    SELECT 1
    FROM ticket t
             JOIN app_user c ON c.id = t.created_by
             JOIN team tm    ON tm.id = c.team_id AND tm.manager_id = p_user
    WHERE t.id = p_ticket AND t.deleted_at IS NULL);
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_read_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (
    SELECT 1
    FROM ticket t
             JOIN ticket_category c ON c.id = t.category_id
             JOIN app_user u        ON u.id = p_user
    WHERE t.id = p_ticket AND t.deleted_at IS NULL
      AND (has_permission(p_user, 'TICKET_READ_ALL')
        OR (has_permission(p_user, 'TICKET_READ_OWN') AND t.created_by = p_user)
        OR (has_permission(p_user, 'TICKET_READ_HANDLED') AND u.role_id = c.handler_role_id)))
    OR ticket_from_my_team(p_user, p_ticket);
$$ LANGUAGE sql STABLE;

-- Nobody handles a ticket they opened.
CREATE OR REPLACE FUNCTION can_update_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (SELECT 1 FROM ticket t WHERE t.id = p_ticket AND t.deleted_at IS NULL AND t.created_by <> p_user)
   AND (has_permission(p_user, 'TICKET_UPDATE_ALL')
    OR (has_permission(p_user, 'TICKET_UPDATE_HANDLED')
           AND EXISTS (
        SELECT 1
        FROM ticket t
                 JOIN ticket_category c ON c.id = t.category_id
                 JOIN app_user u        ON u.id = p_user AND u.role_id = c.handler_role_id
        WHERE t.id = p_ticket)));
$$ LANGUAGE sql STABLE;

-- The person who opened it, whoever handles it, and the team leader of the person who opened it.
CREATE FUNCTION can_comment_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (SELECT 1 FROM ticket t WHERE t.id = p_ticket AND t.deleted_at IS NULL AND t.created_by = p_user)
    OR can_update_ticket(p_user, p_ticket)
    OR ticket_from_my_team(p_user, p_ticket);
$$ LANGUAGE sql STABLE;
