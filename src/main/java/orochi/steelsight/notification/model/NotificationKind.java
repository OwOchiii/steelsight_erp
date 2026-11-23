package orochi.steelsight.notification.model;

import java.util.List;

/**
 * The kinds of notice (SRS 3.2.4). Each names the parameters its message takes, in order: the message
 * notice.KIND gets them as {0}, {1}, ... A parameter called "type" is a violation type and is shown with
 * its label; one ending in "At" is a time and is shown in site time.
 */
public enum NotificationKind {
    CHECK_IN("checkInAt", "shift"),
    CHECK_OUT("checkOutAt", "hours"),
    VISIT_RECORDED("recordedBy", "checkInAt", "checkOut"),
    VISIT_CORRECTED("checkInAt", "checkOut"),
    WEEKLY_SCHEDULE("week", "shifts"),
    WEEKLY_EMPTY("week"),
    MONTH_SCHEDULE("month", "shifts"),
    MONTH_EMPTY("month"),
    SHIFT_ADDED("shift"),
    SHIFT_CHANGED("before", "after"),
    SHIFT_CANCELLED("shift"),
    VIOLATION_ALERT("type", "zone", "detectedAt", "suggested"),
    VIOLATION_CONFIRMED("type", "zone", "detectedAt", "score", "level", "appealUntil"),
    LEVEL_WORSE("level", "reduction"),
    REMINDER("type", "zone", "detectedAt"),
    APPEAL_DECIDED("outcome", "note"),
    TICKET_CREATED("subject", "creator"),
    TICKET_COMMENT("subject", "author"),
    TICKET_DECIDED("subject", "outcome"),
    ASKED_FOR_FACTS("subject"),
    INCOMPLETE_ATTENDANCE("checkInAt"),
    OVERDUE_VIOLATIONS("count"),
    PENDING_OVERTIME_LIST("count"),
    OPEN_TICKETS("count"),
    OLD_TICKETS("count"),
    LEGACY_ALERT,
    LEGACY_SCORE("score");

    private final List<String> params;

    NotificationKind(String... params) {
        this.params = List.of(params);
    }

    public List<String> params() {
        return params;
    }
}
