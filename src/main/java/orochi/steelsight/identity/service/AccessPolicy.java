package orochi.steelsight.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Permission and scope checks. The rules live in SQL functions so there is only one copy of them. */
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    private final JdbcTemplate jdbc;

    public boolean has(Long actorId, String permission) {
        return ask("SELECT has_permission(?, ?)", actorId, permission);
    }

    public void require(Long actorId, String permission) {
        allow(has(actorId, permission));
    }

    public void requireAny(Long actorId, String... permissions) {
        for (String permission : permissions) {
            if (has(actorId, permission)) {
                return;
            }
        }
        allow(false);
    }

    public boolean canUpdateUser(Long actorId, Long targetUserId) {
        return ask("SELECT can_update_user(?, ?)", actorId, targetUserId);
    }

    public void requireCanUpdateUser(Long actorId, Long targetUserId) {
        allow(canUpdateUser(actorId, targetUserId));
    }

    public void requireCanEditShift(Long actorId, Long shiftId) {
        allow(ask("SELECT can_edit_shift(?, ?)", actorId, shiftId));
    }

    public void requireCanAssignShift(Long actorId, Long workerId) {
        allow(ask("SELECT can_assign_shift(?, ?)", actorId, workerId));
    }

    public boolean canUpdateTicket(Long userId, Long ticketId) {
        return ask("SELECT can_update_ticket(?, ?)", userId, ticketId);
    }

    public void requireCanUpdateTicket(Long actorId, Long ticketId) {
        allow(canUpdateTicket(actorId, ticketId));
    }

    public void requireCanReadTicket(Long actorId, Long ticketId) {
        allow(ask("SELECT can_read_ticket(?, ?)", actorId, ticketId));
    }

    public void requireCanDeleteTicket(Long actorId, Long ticketId) {
        allow(ask("SELECT can_delete_ticket(?, ?)", actorId, ticketId));
    }

    private boolean ask(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }

    private void allow(boolean allowed) {
        if (!allowed) {
            throw new AccessDeniedException("You are not allowed to do this");
        }
    }
}
