package orochi.steelsight.ticketing.model;

/** SRS BR-62. CANCELLED: the creator withdrew it while it was still OPEN. */
public enum TicketStatus {
    OPEN, IN_PROGRESS, RESOLVED, CANCELLED;

    public boolean isUnresolved() {
        return this == OPEN || this == IN_PROGRESS;
    }
}
