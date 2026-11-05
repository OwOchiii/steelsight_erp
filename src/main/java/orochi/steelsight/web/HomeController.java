package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.service.SafetyScoreService;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.service.TicketService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** The dashboard. Each block appears only for people whose permissions let them read its data. */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int TREND_DAYS = 14;
    private static final int SHORT_LIST = 5;

    private final ShiftService shifts;
    private final TicketService tickets;
    private final ViolationService violations;
    private final CheckInService checkIns;
    private final SafetyScoreService safety;
    private final SiteTime siteTime;

    /** One day of the signed-in worker's week. */
    public record DayPlan(LocalDate date, boolean today, List<Shift> shifts) {
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("todayDate", siteTime.today());
        if (user.can(Permissions.SHIFT_READ_OWN)) {
            addMyWeek(user.getUserId(), model);
        }
        if (user.can(Permissions.TICKET_READ_OWN)) {
            List<Ticket> own = tickets.listOwn(user.getUserId()).stream().filter(ticket -> !ticket.isResolved()).toList();
            model.addAttribute("openTickets", own.size());
            model.addAttribute("myTickets", own.stream().limit(SHORT_LIST).toList());
        }
        if (user.can(Permissions.VIOLATION_READ_OWN)) {
            List<Violation> own = violations.listOwn(user.getUserId());
            model.addAttribute("activeDisputes", own.stream().filter(Violation::isDisputed).count());
            model.addAttribute("violationCount", own.size());
            model.addAttribute("myViolations", own.stream().limit(SHORT_LIST).toList());
            model.addAttribute("safety", safety.own(user.getUserId()));
        }
        if (user.can(Permissions.TICKET_READ_HANDLED) || user.can(Permissions.TICKET_READ_TEAM) || user.can(Permissions.TICKET_READ_ALL)) {
            model.addAttribute("queueCount", tickets.countUnresolvedInQueue(user.getUserId()));
            model.addAttribute("oldestTickets",
                    tickets.listQueue(user.getUserId(), false, PageRequest.of(0, SHORT_LIST)).getContent());
        }
        if (user.can(Permissions.VIOLATION_READ_ALL)) {
            addSafety(user.getUserId(), model);
        }
        boolean seesShifts = user.can(Permissions.SHIFT_READ_TEAM) || user.can(Permissions.SHIFT_READ_ALL);
        boolean seesVisits = user.can(Permissions.CHECKIN_READ_TEAM) || user.can(Permissions.CHECKIN_READ_ALL);
        if (seesShifts) {
            addToday(user.getUserId(), seesVisits, model);
        }
        if (seesVisits) {
            model.addAttribute("onSite", checkIns.listOnSiteInScope(user.getUserId(), PageRequest.of(0, 1)).getTotalElements());
        }
        return "home";
    }

    private void addMyWeek(Long id, Model model) {
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = siteTime.today();
        LocalDate monday = siteTime.mondayOf(today);
        List<Shift> own = shifts.listOwn(id);
        model.addAttribute("nextShift", own.stream().filter(shift -> shift.isUpcoming(now)).findFirst().orElse(null));

        List<DayPlan> week = new ArrayList<>();
        BigDecimal hours = BigDecimal.ZERO;
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            List<Shift> planned = own.stream()
                    .filter(Shift::isScheduled)
                    .filter(shift -> siteTime.dateOf(shift.getStartAt()).equals(day))
                    .toList();
            week.add(new DayPlan(day, day.equals(today), planned));
            for (Shift shift : planned) {
                hours = hours.add(shift.getDurationHours());
            }
        }
        model.addAttribute("myWeek", week);
        model.addAttribute("myWeekHours", hours);
    }

    private void addSafety(Long id, Model model) {
        LocalDate today = siteTime.today();
        LocalDate firstDay = today.minusDays(TREND_DAYS - 1);
        LocalDate lastWeekStarts = today.minusDays(6);
        List<Violation> recent = violations.listSince(id, siteTime.startOf(firstDay));

        long thisWeek = recent.stream().filter(v -> !siteTime.dateOf(v.getDetectedAt()).isBefore(lastWeekStarts)).count();
        model.addAttribute("violationsThisWeek", thisWeek);
        model.addAttribute("violationsWeekBefore", recent.size() - thisWeek);
        model.addAttribute("violationTotal", recent.size());
        model.addAttribute("violationTrend", Charts.perDay(recent, firstDay, TREND_DAYS, siteTime));
        model.addAttribute("violationsByType", Charts.ranked(recent, Violation::getViolationType));
        model.addAttribute("violationsByZone", Charts.ranked(recent, v -> v.getCamera().getZone().getName()));
        model.addAttribute("disputesToDecide", violations.countDisputed(id));
        model.addAttribute("unidentified", violations.countUnidentified(id));
        model.addAttribute("oldestDisputes", violations.listDisputed(id, PageRequest.of(0, SHORT_LIST)).getContent());
    }

    private void addToday(Long id, boolean seesVisits, Model model) {
        LocalDate today = siteTime.today();
        OffsetDateTime from = siteTime.startOf(today);
        OffsetDateTime to = siteTime.startOf(today.plusDays(1));
        List<Shift> todays = shifts.listManaged(id, from, to);
        model.addAttribute("shiftsToday", todays.stream().filter(Shift::isScheduled).count());
        if (seesVisits) {
            List<TodayRoster.Row> roster = TodayRoster.rows(todays, checkIns.listStartedInScope(id, from, to), OffsetDateTime.now());
            model.addAttribute("roster", roster);
            model.addAttribute("rosterSegments", TodayRoster.segments(roster));
            model.addAttribute("lateToday", roster.stream().filter(row -> row.state() == TodayRoster.State.LATE).count());
            model.addAttribute("missingToday", roster.stream().filter(row -> row.state() == TodayRoster.State.MISSING).count());
        }
    }
}
