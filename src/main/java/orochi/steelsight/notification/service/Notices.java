package orochi.steelsight.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.notification.model.NotificationKind;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.web.SiteTime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The notices about visits and shifts. Each is saved in the ERP first and reaches Zalo through
 * ZaloDispatcher (SRS BR-15). Shift descriptions are written in site time when the notice is made,
 * because a shift can change later.
 */
@Service
@RequiredArgsConstructor
public class Notices {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] WEEKDAY = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

    private final NotificationService notifications;
    private final SiteTime siteTime;

    /** What a shift looked like before an edit, for the change notice. */
    public record ShiftBefore(Long userId, LocalDate day, String text) {
    }

    public void visitRecorded(CheckIn visit) {
        notifications.notify(visit.getUser().getId(), NotificationKind.VISIT_RECORDED, params(
                "recordedBy", visit.getRecordedBy().getFullName(),
                "checkInAt", visit.getCheckInAt().toString(),
                "checkOut", checkOut(visit)), "/attendance/mine");
    }

    public void visitCorrected(CheckIn visit) {
        notifications.notify(visit.getUser().getId(), NotificationKind.VISIT_CORRECTED, params(
                "checkInAt", visit.getCheckInAt().toString(),
                "checkOut", checkOut(visit)), "/attendance/mine");
    }

    public ShiftBefore before(Shift shift) {
        return new ShiftBefore(shift.getUser().getId(), dayOf(shift), describe(shift));
    }

    /** SRS BR-19: a change to a shift of the current or the next week is told at once. */
    public void shiftAdded(Shift shift) {
        if (isCurrentOrNextWeek(dayOf(shift))) {
            notifications.notify(shift.getUser().getId(), NotificationKind.SHIFT_ADDED,
                    params("shift", describe(shift)), "/timetable");
        }
    }

    public void shiftChanged(ShiftBefore before, Shift after) {
        if (!before.userId().equals(after.getUser().getId())) {
            cancelled(before);                           // moved to someone else
            shiftAdded(after);
            return;
        }
        if (isCurrentOrNextWeek(before.day()) || isCurrentOrNextWeek(dayOf(after))) {
            notifications.notify(before.userId(), NotificationKind.SHIFT_CHANGED,
                    params("before", before.text(), "after", describe(after)), "/timetable");
        }
    }

    public void shiftCancelled(Shift shift) {
        cancelled(before(shift));
    }

    /** SRS BR-18: the shifts of next week, or "no shift next week". */
    public void weekSchedule(Long userId, LocalDate monday, List<Shift> shifts) {
        String week = DAY.format(monday) + "–" + DAY.format(monday.plusDays(6));
        if (shifts.isEmpty()) {
            notifications.notify(userId, NotificationKind.WEEKLY_EMPTY, params("week", week), "/timetable");
        } else {
            notifications.notify(userId, NotificationKind.WEEKLY_SCHEDULE, params("week", week,
                    "shifts", shifts.stream().map(this::describe).collect(Collectors.joining("\n"))), "/timetable");
        }
    }

    /** "T2 01/12 07:00–11:30 · Khu vực A" */
    public String describe(Shift shift) {
        LocalDateTime start = siteTime.toLocal(shift.getStartAt());
        LocalDateTime end = siteTime.toLocal(shift.getEndAt());
        return WEEKDAY[start.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue()] + " " + DAY.format(start)
                + " " + TIME.format(start) + "–" + TIME.format(end) + " · " + shift.getZone().getName();
    }

    private void cancelled(ShiftBefore before) {
        if (isCurrentOrNextWeek(before.day())) {
            notifications.notify(before.userId(), NotificationKind.SHIFT_CANCELLED, params("shift", before.text()), "/timetable");
        }
    }

    private boolean isCurrentOrNextWeek(LocalDate day) {
        LocalDate monday = siteTime.mondayOf(siteTime.today());
        return !day.isBefore(monday) && day.isBefore(monday.plusWeeks(2));
    }

    private LocalDate dayOf(Shift shift) {
        return siteTime.toLocal(shift.getStartAt()).toLocalDate();
    }

    private String checkOut(CheckIn visit) {
        return visit.getCheckOutAt() == null ? "" : TIME.format(siteTime.toLocal(visit.getCheckOutAt()));
    }

    private static Map<String, Object> params(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
