-- Scope checks the service layer needs beyond V1. As in V1: a request needs permission AND scope.

-- Who can give a shift to this worker. Separate from can_edit_shift, because a new shift has no id yet.
CREATE OR REPLACE FUNCTION can_assign_shift(p_user bigint, p_worker bigint) RETURNS boolean AS $$
SELECT EXISTS (
    SELECT 1
    FROM app_user w
    WHERE w.id = p_worker AND w.is_active AND w.deleted_at IS NULL
      AND (has_permission(p_user, 'SHIFT_UPDATE_ALL')
        OR (has_permission(p_user, 'SHIFT_UPDATE_TEAM')
            AND EXISTS (SELECT 1 FROM team t WHERE t.id = w.team_id AND t.manager_id = p_user))));
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
        OR (has_permission(p_user, 'TICKET_READ_HANDLED') AND u.role_id = c.handler_role_id)));
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
