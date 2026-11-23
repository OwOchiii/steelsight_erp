package orochi.steelsight.ppe.model;

/** SRS BR-48, BR-53: every violation starts PENDING_REVIEW; at 23:00 an undecided one becomes OVERDUE. */
public enum ReviewStatus {
    PENDING_REVIEW, OVERDUE, CONFIRMED, REMINDER, DISMISSED;

    public boolean isOpen() {
        return this == PENDING_REVIEW || this == OVERDUE;
    }
}
