package orochi.steelsight.attendance.service;

import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.CheckInMethod;

import java.time.OffsetDateTime;

/**
 * How a visit went against its planned shift. Worked out when read, never stored, so a corrected
 * check-in or an edited shift changes it straight away.
 *
 * @param requiredCheckOut when the worker may leave; null when the visit has no planned shift
 * @param minutesLate      minutes after the shift started, 0 if within the grace period
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

    /** The worker never checked out, so the recorded check-out is only an estimate. */
    public boolean isMissingCheckOut() {
        return visit.isAutoClosed();
    }

    /**
     * Checked in at the gate, never checked out, and no vest number to recognise them by on the way out.
     * It can be an honest miss, so hours stay as they are until someone looks at it and corrects the visit.
     */
    public boolean isNeedsReview() {
        return visit.isAutoClosed() && visit.getPpeLinking() == null && visit.getMethod() == CheckInMethod.FACE;
    }

    public boolean isOnTime() {
        return hasShift() && !isLate() && !isEarly() && !isOnSite() && !isMissingCheckOut();
    }
}
