package orochi.steelsight.attendance.service;

import orochi.steelsight.attendance.model.CheckIn;

import java.time.OffsetDateTime;

/**
 * How a visit went against its planned shift. Worked out when read, never stored, so a corrected
 * check-in or an edited shift changes it straight away.
 *
 * @param requiredCheckOut when the worker may leave; null when the visit has no planned shift
 * @param minutesLate      minutes after the shift started, 0 if within the grace period
 * @param minutesEarly     minutes before requiredCheckOut, 0 if they stayed or are still on site
 */
public record VisitStatus(CheckIn visit, OffsetDateTime requiredCheckOut, long minutesLate, long minutesEarly) {

    public boolean hasShift() {
        return requiredCheckOut != null;
    }

    public boolean isLate() {
        return minutesLate > 0;
    }

    public boolean isEarly() {
        return minutesEarly > 0;
    }

    public boolean isOnSite() {
        return visit.getCheckOutAt() == null;
    }

    /** The worker never checked out, so the recorded check-out is only an estimate. */
    public boolean isMissingCheckOut() {
        return visit.isAutoClosed();
    }

    public boolean isOnTime() {
        return hasShift() && !isLate() && !isEarly() && !isOnSite() && !isMissingCheckOut();
    }
}
