package orochi.steelsight.attendance.service;

import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.CheckInMethod;

import java.time.OffsetDateTime;

/**
 * How a visit went against its planned shift. Worked out when read, never stored, so a corrected
 * check-in or an edited shift changes it straight away.
 *
 * @param requiredCheckOut the shift end; null when the visit has no planned shift
 * @param minutesLate      minutes after the shift started, 0 within the 5-minute grace (BR-31)
 * @param minutesEarly     minutes before requiredCheckOut, 0 if they stayed or are still on site
 * @param tooShort         recorded in and out within the minimum visit, so it doesn't count as attending a shift
 */
public record VisitStatus(CheckIn visit, OffsetDateTime requiredCheckOut, long minutesLate, long minutesEarly,
                          boolean tooShort) {

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

    /** Closed by the system: the worker never checked out, and the check-out time isn't real (SRS BR-35). */
    public boolean isMissingCheckOut() {
        return visit.isAutoClosed();
    }

    /** Flagged Incomplete attendance and not explained yet: counts 0 hours (SRS BR-35, BR-67). */
    public boolean isNeedsReview() {
        return visit.isIncomplete();
    }

    public boolean isOnTime() {
        return hasShift() && !isLate() && !isEarly() && !isOnSite() && !isMissingCheckOut();
    }
}
