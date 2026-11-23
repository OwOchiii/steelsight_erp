package orochi.steelsight.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import orochi.steelsight.web.SiteTime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * Working days and deadlines in site time (SRS BR-25, BR-63). A working day is Monday to Saturday;
 * there is no holiday calendar. A deadline in working days ends at 23:59 of its last day.
 */
@Component
@RequiredArgsConstructor
public class SiteCalendar {

    private final SiteTime siteTime;

    public static boolean isWorkingDay(LocalDate day) {
        return day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /** The day n working days after start (start itself doesn't count). */
    public static LocalDate plusWorkingDays(LocalDate start, int n) {
        LocalDate day = start;
        int left = n;
        while (left > 0) {
            day = day.plusDays(1);
            if (isWorkingDay(day)) {
                left--;
            }
        }
        return day;
    }

    /** 23:59 of the n-th working day after the day of this moment. */
    public OffsetDateTime deadline(OffsetDateTime from, int workingDays) {
        LocalDate last = plusWorkingDays(siteTime.dateOf(from), workingDays);
        return siteTime.startOf(last.plusDays(1)).minusMinutes(1);
    }

    public static LocalDate nthWorkingDay(YearMonth month, int n) {
        LocalDate day = month.atDay(1);
        int seen = isWorkingDay(day) ? 1 : 0;
        while (seen < n) {
            day = day.plusDays(1);
            if (isWorkingDay(day)) {
                seen++;
            }
        }
        return day;
    }

    public static LocalDate lastWorkingDay(YearMonth month) {
        LocalDate day = month.atEndOfMonth();
        while (!isWorkingDay(day)) {
            day = day.minusDays(1);
        }
        return day;
    }

    public YearMonth monthOf(OffsetDateTime moment) {
        return YearMonth.from(siteTime.dateOf(moment));
    }

    public OffsetDateTime startOf(YearMonth month) {
        return siteTime.startOf(month.atDay(1));
    }

    /** Working days after from, up to and including to. */
    public int workingDaysBetween(LocalDate from, LocalDate to) {
        int days = 0;
        for (LocalDate day = from.plusDays(1); !day.isAfter(to); day = day.plusDays(1)) {
            if (isWorkingDay(day)) {
                days++;
            }
        }
        return days;
    }

    public java.time.LocalDateTime local(OffsetDateTime moment) {
        return siteTime.toLocal(moment);
    }

    public LocalDate dateOf(OffsetDateTime moment) {
        return siteTime.dateOf(moment);
    }

    public OffsetDateTime startOfDay(LocalDate day) {
        return siteTime.startOf(day);
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    public LocalDate today() {
        return siteTime.today();
    }
}
