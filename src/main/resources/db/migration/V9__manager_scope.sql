-- Managers and HSE can look at every worker. Managers handle tickets only from the teams they run:
-- a ticket from someone with no team goes to an admin. HSE still handles all of its categories.
INSERT INTO permission (code, module, action, description) VALUES
    ('USER_READ_ALL',      'USER',   'READ',   'See the details and safety score of every worker'),
    ('TICKET_READ_TEAM',   'TICKET', 'READ',   'See tickets my team opened, in my role''s categories'),
    ('TICKET_UPDATE_TEAM', 'TICKET', 'UPDATE', 'Reply, assign, resolve tickets my team opened, in my role''s categories'),
    ('TICKET_DELETE_TEAM', 'TICKET', 'DELETE', 'Soft-delete tickets my team opened, in my role''s categories');

DELETE FROM role_permission rp
USING role r, permission p
WHERE rp.role_id = r.id AND rp.permission_id = p.id AND r.role_name = 'MANAGER'
  AND p.code IN ('TICKET_READ_HANDLED', 'TICKET_UPDATE_HANDLED', 'TICKET_DELETE_HANDLED');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
          ('MANAGER',     'USER_READ_ALL'), ('MANAGER', 'TICKET_READ_TEAM'),
          ('MANAGER',     'TICKET_UPDATE_TEAM'), ('MANAGER', 'TICKET_DELETE_TEAM'),
          ('HSE_OFFICER', 'USER_READ_ALL'),
          ('ADMIN',       'USER_READ_ALL'), ('ADMIN', 'TICKET_READ_TEAM'),
          ('ADMIN',       'TICKET_UPDATE_TEAM'), ('ADMIN', 'TICKET_DELETE_TEAM')
     ) AS v(role_name, code)
         JOIN role r       ON r.role_name = v.role_name
         JOIN permission p ON p.code = v.code;

-- The ticket is in a category this user's role handles, and its creator is on a team this user runs.
CREATE FUNCTION ticket_from_my_team(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (
    SELECT 1
    FROM ticket t
             JOIN ticket_category c ON c.id = t.category_id
             JOIN app_user me       ON me.id = p_user AND me.role_id = c.handler_role_id
             JOIN app_user creator  ON creator.id = t.created_by
             JOIN team tm           ON tm.id = creator.team_id AND tm.manager_id = p_user
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
        OR (has_permission(p_user, 'TICKET_READ_HANDLED') AND u.role_id = c.handler_role_id)
        OR (has_permission(p_user, 'TICKET_READ_TEAM') AND ticket_from_my_team(p_user, p_ticket))));
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
        WHERE t.id = p_ticket AND t.deleted_at IS NULL))
    OR (has_permission(p_user, 'TICKET_UPDATE_TEAM') AND ticket_from_my_team(p_user, p_ticket));
$$ LANGUAGE sql STABLE;

CREATE OR REPLACE FUNCTION can_delete_ticket(p_user bigint, p_ticket bigint) RETURNS boolean AS $$
SELECT EXISTS (
    SELECT 1
    FROM ticket t
             JOIN ticket_category c ON c.id = t.category_id
             JOIN app_user u        ON u.id = p_user
    WHERE t.id = p_ticket AND t.deleted_at IS NULL
      AND (has_permission(p_user, 'TICKET_DELETE_ALL')
        OR (has_permission(p_user, 'TICKET_DELETE_HANDLED') AND u.role_id = c.handler_role_id)
        OR (has_permission(p_user, 'TICKET_DELETE_TEAM') AND ticket_from_my_team(p_user, p_ticket))));
$$ LANGUAGE sql STABLE;
