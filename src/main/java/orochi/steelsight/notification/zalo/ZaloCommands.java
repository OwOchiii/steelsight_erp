package orochi.steelsight.notification.zalo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.service.AttendanceCalculator;
import orochi.steelsight.attendance.service.ShiftAttendance;
import orochi.steelsight.attendance.service.ShiftAttendance.Flag;
import orochi.steelsight.calendar.MonthLocks;
import orochi.steelsight.ppe.service.SafetyScoreService;
import orochi.steelsight.web.SiteTime;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Commands a linked person sends to the bot, answered with their own data only: the chat decides whose
 * account it is. Zalo shows plain text, so each shift is one line instead of a grid. A team addition,
 * not in the SRS (team decision 24/11/2026).
 *
 * /help, /lich [tuantoi], /chamcong [thangtruoc]; anything else gets the list of commands.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ZaloCommands {

    private static final Locale SITE = Locale.forLanguageTag("vi");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final String[] WEEKDAY = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
    /** The flags worth a word in a one-line summary, in the order they are shown. */
    private static final List<Flag> SHOWN = List.of(Flag.ABSENT, Flag.LATE, Flag.EARLY, Flag.INCOMPLETE, Flag.HALF_DAY,
            Flag.TO_REVIEW, Flag.PAID_LEAVE, Flag.UNPAID_LEAVE, Flag.ON_SITE, Flag.VALID_OT, Flag.PENDING_OT, Flag.ON_TIME);

    private final AttendanceCalculator calculator;
    private final SafetyScoreService scores;
    private final MonthLocks locks;
    private final SiteTime siteTime;
    private final MessageSource messages;

    @Value("${steelsight.base-url:}")
    private String baseUrl;

    /** The reply to one message of a linked person. */
    public String reply(Long userId, String message) {
        String command = plain(message);
        return switch (command) {
            case "/lich", "/schedule" -> schedule(userId, siteTime.mondayOf(siteTime.today()));
            case "/lichtuantoi", "/lichtuansau", "/schedulenext" -> schedule(userId, siteTime.mondayOf(siteTime.today()).plusWeeks(1));
            case "/chamcong", "/attendance" -> attendance(userId, YearMonth.from(siteTime.today()));
            case "/chamcongthangtruoc", "/attendancelast" -> attendance(userId, YearMonth.from(siteTime.today()).minusMonths(1));
            default -> text("zalo.commands");
        };
    }

    /** Lower case, no spaces, no Vietnamese marks: "/Lịch tuần tới" becomes "/lichtuantoi". */
    static String plain(String message) {
        String folded = Normalizer.normalize(message == null ? "" : message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        return folded.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String schedule(Long userId, LocalDate monday) {
        String week = DAY.format(monday) + "–" + DAY.format(monday.plusDays(6));
        List<AttendanceCalculator.Row> rows = calculator.rows(List.of(userId),
                siteTime.startOf(monday), siteTime.startOf(monday.plusWeeks(1)));
        if (rows.isEmpty()) {
            return text("zalo.schedule.none", week);
        }
        List<String> lines = new ArrayList<>();
        lines.add(text("zalo.schedule.title", week));
        for (AttendanceCalculator.Row row : rows) {
            lines.add(shiftText(row) + " · " + row.zone());
        }
        return String.join("\n", lines);
    }

    private String attendance(Long userId, YearMonth month) {
        List<AttendanceCalculator.Row> rows = calculator.rows(List.of(userId),
                siteTime.startOf(month.atDay(1)), siteTime.startOf(month.plusMonths(1).atDay(1))).stream()
                .filter(row -> !row.result().has(Flag.UPCOMING))
                .toList();
        String name = MONTH.format(month);
        if (rows.isEmpty()) {
            return text("zalo.attendance.none", name);
        }
        List<String> lines = new ArrayList<>();
        lines.add(text("zalo.attendance.title", name));
        for (AttendanceCalculator.Row row : rows) {
            lines.add(attendanceLine(row));
        }
        ShiftAttendance.Summary total = calculator.summary(rows);
        lines.add("");
        lines.add(text("zalo.attendance.summary", String.valueOf(total.worked()), String.valueOf(total.planned()),
                hours(total.paidHours()), String.valueOf(total.timesLate()), String.valueOf(total.timesEarly()),
                String.valueOf(total.absent()), String.valueOf(total.incomplete())));
        if (total.validOvertimeHours().signum() > 0) {
            lines.add(text("zalo.attendance.overtime", hours(total.validOvertimeHours())));
        }
        OffsetDateTime inMonth = siteTime.startOf(month.atDay(1));
        SafetyScoreService.Score score = scores.inMonth(userId, inMonth);
        if (score != null) {
            lines.add(text("zalo.attendance.score", String.valueOf(score.value()),
                    String.valueOf(score.level().reductionPercent())));
        }
        if (!locks.isLocked(month)) {
            lines.add(text("zalo.attendance.provisional"));
        }
        if (!baseUrl.isBlank()) {
            lines.add(text("zalo.attendance.more", baseUrl.replaceAll("/$", "") + "/attendance/mine?month=" + month));
        }
        return String.join("\n", lines);
    }

    /** "T2 24/11 07:00–17:00 · vào 06:55 · ra 17:05 · Đúng giờ" */
    private String attendanceLine(AttendanceCalculator.Row row) {
        ShiftAttendance.Result result = row.result();
        StringBuilder line = new StringBuilder(shiftText(row));
        if (result.isWorked()) {
            List<ShiftAttendance.Visit> visits = result.visits();
            line.append(" · ").append(text("zalo.attendance.in", TIME.format(siteTime.toLocal(visits.get(0).in()))));
            ShiftAttendance.Visit last = visits.get(visits.size() - 1);
            if (last.out() != null && !last.closedBySystem()) {
                line.append(" · ").append(text("zalo.attendance.out", TIME.format(siteTime.toLocal(last.out()))));
            }
        }
        List<String> flags = new ArrayList<>();
        for (Flag flag : SHOWN) {
            if (result.has(flag)) {
                flags.add(flagText(flag, result));
            }
        }
        if (!flags.isEmpty()) {
            line.append(" · ").append(String.join(", ", flags));
        }
        return line.toString();
    }

    private String shiftText(AttendanceCalculator.Row row) {
        LocalDateTime start = siteTime.toLocal(row.result().shift().start());
        LocalDateTime end = siteTime.toLocal(row.result().shift().end());
        String text = WEEKDAY[start.getDayOfWeek().getValue() - 1] + " " + DAY.format(start) + " "
                + TIME.format(start) + "–" + TIME.format(end);
        return row.result().shift().overtime() ? text + " (" + text("zalo.overtimeShift") + ")" : text;
    }

    /** The same words as the attendance pages (flag.* keys), with the same numbers. */
    private String flagText(Flag flag, ShiftAttendance.Result r) {
        String key = "flag." + flag.name();
        return switch (flag) {
            case LATE -> text(key, String.valueOf(r.minutesLate()));
            case EARLY -> text(key, String.valueOf(r.minutesEarly()));
            case PAID_LEAVE -> text(key, hours(r.paidLeaveHours()));
            case UNPAID_LEAVE -> text(key, hours(r.unpaidLeaveHours()));
            case VALID_OT -> text(key, hours(r.validOvertimeHours()));
            case PENDING_OT -> text(key, hours(r.pendingOvertimeHours()),
                    r.pendingOvertimeEnds() == null ? "-" : DAY_TIME.format(siteTime.toLocal(r.pendingOvertimeEnds())));
            default -> text(key);
        };
    }

    private static String hours(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, SITE);
    }
}
