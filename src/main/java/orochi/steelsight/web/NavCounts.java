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

    /** Violations waiting for review: PENDING_REVIEW or OVERDUE (SRS 3.2.16). Appeals count in the ticket queue. */
    public long review(Long userId) {
        return violations.countOpen(userId);
    }
}
