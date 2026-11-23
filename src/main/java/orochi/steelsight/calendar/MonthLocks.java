package orochi.steelsight.calendar;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import orochi.steelsight.exception.BusinessRuleException;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * SRS BR-70: a month is locked at 23:59 of the 2nd working day of the next month. After that nothing of
 * that month can be created or changed (MSG33).
 */
@Component
@RequiredArgsConstructor
public class MonthLocks {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final JdbcTemplate jdbc;
    private final SiteCalendar calendar;

    public boolean isLocked(YearMonth month) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM month_lock WHERE month = ?)", Boolean.class, month.atDay(1)));
    }

    public boolean isLocked(OffsetDateTime moment) {
        return isLocked(calendar.monthOf(moment));
    }

    /** Refuses a change to anything that happened in a locked month. */
    public void requireOpen(OffsetDateTime moment) {
        YearMonth month = calendar.monthOf(moment);
        if (isLocked(month)) {
            throw new BusinessRuleException(String.format("The month %s is locked. No change is possible.", MONTH.format(month)));
        }
    }
}
