package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import orochi.steelsight.notification.service.NotificationService;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.ticketing.service.TicketService;

/** The waiting-work counts beside sidebar links, as ${@navCounts.queue(userId)}. */
@Component
@RequiredArgsConstructor
public class NavCounts {

    private final TicketService tickets;
    private final ViolationService violations;
    private final NotificationService notifications;

    public long unread(Long userId) {
        return notifications.countUnread(userId);
    }

    public long queue(Long userId) {
        return tickets.countUnresolvedInQueue(userId);
    }

    public long disputes(Long userId) {
        return violations.countDisputed(userId);
    }
}
