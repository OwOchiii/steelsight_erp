package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.service.AttendanceCalculator;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.attendance.service.PendingOvertimeService;
import orochi.steelsight.attendance.service.ShiftAttendance;
import orochi.steelsight.attendance.service.VisitStatus;
import orochi.steelsight.calendar.MonthLocks;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.service.TicketService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SRS 3.2.12 to 3.2.15: My Attendance, Team Attendance, the Attendance List and Form, and Pending
 * Overtime. Every number comes from AttendanceCalculator, the same as the monthly report.
 */
@Controller
@RequiredArgsConstructor
public class AttendanceController extends PageController {

    public enum Flag { INCOMPLETE, LATE, EARLY, MANUAL, NO_SHIFT }

    private final CheckInService checkIns;
    private final AttendanceCalculator calculator;
    private final PendingOvertimeService overtime;
    private final UserService users;
    private final TicketService tickets;
    private final TeamRepository teams;
    private final MonthLocks locks;
    private final SiteTime siteTime;

    /** My Attendance: one row per shift and the month's summary (SRS 3.2.12). */
    @GetMapping("/attendance/mine")
    public String mine(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
        YearMonth shown = month == null ? YearMonth.from(siteTime.today()) : month;
        checkIns.listOwn(user.getUserId());   // permission check: CHECKIN_READ_OWN
        List<AttendanceCalculator.Row> rows = calculator.rows(List.of(user.getUserId()),
                siteTime.startOf(shown.atDay(1)), siteTime.startOf(shown.plusMonths(1).atDay(1)));
        model.addAttribute("month", shown);
        model.addAttribute("months", lastMonths());
        model.addAttribute("rows", rows);
        model.addAttribute("summary", calculator.summary(rows));
        model.addAttribute("provisional", !locks.isLocked(shown));
        model.addAttribute("now", OffsetDateTime.now());
        model.addAttribute("openRequests", openRequests(user.getUserId()));
        return "attendance/mine";
    }

    /** Team Attendance: a team leader reads the attendance of his team, by day or by month (SRS 3.2.13). */
    @GetMapping("/attendance/team")
    public String team(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(defaultValue = "DAY") String view,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
        List<AppUser> members = users.listTeamMembers(user.getUserId());
        Map<Long, AppUser> byId = members.stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        boolean monthView = "MONTH".equals(view);
        LocalDate shownDay = day == null ? siteTime.today() : day;
        YearMonth shownMonth = month == null ? YearMonth.from(siteTime.today()) : month;
        OffsetDateTime from = monthView ? siteTime.startOf(shownMonth.atDay(1)) : siteTime.startOf(shownDay);
        OffsetDateTime to = monthView ? siteTime.startOf(shownMonth.plusMonths(1).atDay(1)) : siteTime.startOf(shownDay.plusDays(1));
        List<AttendanceCalculator.Row> rows = calculator.rows(byId.keySet(), from, to);
        model.addAttribute("monthView", monthView);
        model.addAttribute("day", shownDay);
        model.addAttribute("month", shownMonth);
        model.addAttribute("months", lastMonths());
        model.addAttribute("members", byId);
        model.addAttribute("rows", rows);
        if (monthView) {
            Map<Long, List<AttendanceCalculator.Row>> perPerson = rows.stream()
                    .collect(Collectors.groupingBy(AttendanceCalculator.Row::userId));
            model.addAttribute("summaries", members.stream().collect(Collectors.toMap(AppUser::getId,
                    m -> calculator.summary(perPerson.getOrDefault(m.getId(), List.of())))));
            model.addAttribute("people", members);
        }
        model.addAttribute("pending", overtime.listOpen(user.getUserId(), null));
        return "attendance/team";
    }

    /** The Attendance List of HSE and the Admin: a day or a month, a person and a flag (SRS 3.2.14). */
    @GetMapping("/attendance")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(defaultValue = "false") boolean onSite,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       @RequestParam(required = false) String person,
                       @RequestParam(required = false) Flag flag,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("onSite", onSite);
        if (onSite || !user.can(Permissions.CHECKIN_READ_ALL)) {
            model.addAttribute("visits", onSite
                    ? checkIns.listOnSiteInScope(user.getUserId(), pageOf(page))
                    : checkIns.listRecentInScope(user.getUserId(), pageOf(page)));
            model.addAttribute("filters", false);
            return "attendance/list";
        }
        OffsetDateTime from;
        OffsetDateTime to;
        if (month != null) {
            from = siteTime.startOf(month.atDay(1));
            to = siteTime.startOf(month.plusMonths(1).atDay(1));
        } else {
            LocalDate shown = day == null ? siteTime.today() : day;
            from = siteTime.startOf(shown);
            to = siteTime.startOf(shown.plusDays(1));
            model.addAttribute("day", shown);
        }
        List<VisitStatus> visits = checkIns.listBetween(user.getUserId(), from, to, person).stream()
                .filter(v -> flag == null || matches(v, flag)).toList();
        model.addAttribute("visits", pageOf(visits, page));
        model.addAttribute("filters", true);
        model.addAttribute("month", month);
        model.addAttribute("person", person);
        model.addAttribute("flag", flag);
        model.addAttribute("flags", Flag.values());
        return "attendance/list";
    }

    private static boolean matches(VisitStatus visit, Flag flag) {
        return switch (flag) {
            case INCOMPLETE -> visit.visit().isIncomplete();
            case LATE -> visit.isLate();
            case EARLY -> visit.isEarly();
            case MANUAL -> visit.visit().getMethod().name().equals("MANUAL");
            case NO_SHIFT -> !visit.hasShift();
        };
    }

    /** From a ticket the form opens with the person and the time at the gate filled in (SRS 3.2.22). */
    @GetMapping("/attendance/new")
    public String newCheckIn(@AuthenticationPrincipal AuthenticatedUser user,
                             @RequestParam(required = false) Long ticket, Model model) {
        CheckInForm form = new CheckInForm();
        if (ticket != null) {
            Ticket asked = tickets.get(user.getUserId(), ticket);
            form.setTicketId(ticket);
            form.setWorkerId(asked.getCreatedBy().getId());
            if (asked.getGateTime() != null && asked.getDirection() != null && asked.getDirection().name().equals("IN")) {
                form.setCheckInAt(siteTime.toLocal(asked.getGateTime()));
            }
            if (asked.getGateTime() != null && asked.getDirection() != null && asked.getDirection().name().equals("OUT")) {
                form.setCheckOutAt(siteTime.toLocal(asked.getGateTime()));
                if (asked.getShift() != null) {
                    form.setCheckInAt(siteTime.toLocal(asked.getShift().getStartAt()));
                }
            }
            form.setNote("Attendance error ticket #" + ticket);
            model.addAttribute("ticket", asked);
        }
        model.addAttribute("form", form);
        return showNewForm(user, model);
    }

    @PostMapping("/attendance")
    public String record(@AuthenticationPrincipal AuthenticatedUser user,
                         @ModelAttribute("form") CheckInForm form, BindingResult binding,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            checkIns.recordManual(user.getUserId(), new CheckInService.ManualCheckIn(
                    form.getWorkerId(), null, form.getZoneId(),
                    siteTime.toInstant(form.getCheckInAt()), siteTime.toInstant(form.getCheckOutAt()), form.getNote(),
                    form.getTicketId()));
            redirect.addFlashAttribute("success", "The check-in was added.");
            return form.getTicketId() == null ? "redirect:/attendance" : "redirect:/tickets/" + form.getTicketId();
        }, () -> showNewForm(user, model));
    }

    @GetMapping("/attendance/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                       @RequestParam(required = false) Long ticket, Model model) {
        CheckIn visit = checkIns.getForEdit(user.getUserId(), id);
        CheckInForm form = new CheckInForm();
        form.setCheckInAt(siteTime.toLocal(visit.getCheckInAt()));
        form.setCheckOutAt(visit.isAutoClosed() ? null : siteTime.toLocal(visit.getCheckOutAt()));
        form.setVersion(visit.getVersion());
        form.setTicketId(ticket);
        model.addAttribute("form", form);
        return showEditForm(user, visit, model);
    }

    @PostMapping("/attendance/{id}")
    public String correct(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @ModelAttribute("form") CheckInForm form, BindingResult binding,
                          Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            checkIns.correct(user.getUserId(), id, siteTime.toInstant(form.getCheckInAt()),
                    siteTime.toInstant(form.getCheckOutAt()), form.getNote(), form.getVersion(), form.getTicketId());
            redirect.addFlashAttribute("success", "The check-in was corrected.");
            return form.getTicketId() == null ? "redirect:/attendance" : "redirect:/tickets/" + form.getTicketId();
        }, () -> showEditForm(user, checkIns.getForEdit(user.getUserId(), id), model));
    }

    /** Pending Overtime: the Admin adds an overtime shift or cancels (SRS 3.2.15). */
    @GetMapping("/overtime")
    public String pendingOvertime(@AuthenticationPrincipal AuthenticatedUser user,
                                  @RequestParam(required = false) Long teamId, Model model) {
        // SRS 3.1.3: the Pending Overtime screen is the Admin's; a team leader sees his team's on Team Attendance
        if (!user.can(Permissions.SHIFT_UPDATE_ALL)) {
            throw new org.springframework.security.access.AccessDeniedException("Pending overtime is for the Admin");
        }
        model.addAttribute("rows", overtime.listOpen(user.getUserId(), teamId));
        model.addAttribute("teams", teams.findAll());
        model.addAttribute("teamId", teamId);
        return "attendance/overtime";
    }

    @PostMapping("/overtime/cancel")
    public String cancelOvertime(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam(required = false) List<Long> ids, RedirectAttributes redirect) {
        return attempt(redirect, "/overtime", "The selected pending overtime was cancelled.",
                () -> overtime.cancel(user.getUserId(), ids));
    }

    @PostMapping("/overtime/{id}/note")
    public String overtimeNote(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                               @RequestParam(required = false) String note, RedirectAttributes redirect) {
        return attempt(redirect, "/attendance/team", "Saved successfully.",
                () -> overtime.leaderNote(user.getUserId(), id, note));
    }

    private String showEditForm(AuthenticatedUser user, CheckIn visit, Model model) {
        model.addAttribute("visit", visit);
        model.addAttribute("corrections", checkIns.listCorrections(user.getUserId(), visit.getId()));
        return "attendance/form";
    }

    private String showNewForm(AuthenticatedUser user, Model model) {
        model.addAttribute("workers", users.listForManualCheckIn(user.getUserId()));
        model.addAttribute("zones", checkIns.listZones(user.getUserId()));
        return "attendance/form";
    }

    /** "Request sent" instead of a button: the shifts with an open ticket of the user. */
    private Map<Long, Long> openRequests(Long actorId) {
        return tickets.listOwn(actorId).stream()
                .filter(t -> t.isUnresolved() && t.getShift() != null)
                .collect(Collectors.toMap(t -> t.getShift().getId(), Ticket::getId, (a, b) -> a));
    }

    private List<YearMonth> lastMonths() {
        YearMonth now = YearMonth.from(siteTime.today());
        return java.util.stream.IntStream.range(0, 12).mapToObj(now::minusMonths).toList();
    }

    /** For the templates: whether a flag is set on a shift's result. */
    public static boolean has(ShiftAttendance.Result result, String flag) {
        return result.has(ShiftAttendance.Flag.valueOf(flag));
    }
}
