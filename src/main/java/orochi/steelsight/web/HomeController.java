package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.service.TicketService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ShiftService shifts;
    private final TicketService tickets;
    private final ViolationService violations;
    private final CheckInService checkIns;
    private final SiteTime siteTime;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        Long id = user.getUserId();
        model.addAttribute("user", user);
        OffsetDateTime now = OffsetDateTime.now();
        if (user.can(Permissions.SHIFT_READ_OWN)) {
            model.addAttribute("nextShift", shifts.listOwn(id).stream()
                    .filter(shift -> shift.isUpcoming(now)).findFirst().orElse(null));
        }
        if (user.can(Permissions.TICKET_READ_OWN)) {
            model.addAttribute("openTickets", tickets.listOwn(id).stream().filter(ticket -> !ticket.isResolved()).count());
        }
        if (user.can(Permissions.VIOLATION_READ_OWN)) {
            List<Violation> own = violations.listOwn(id);
            model.addAttribute("activeDisputes", own.stream().filter(Violation::isDisputed).count());
            model.addAttribute("violationCount", own.size());
        }
        if (user.can(Permissions.TICKET_READ_HANDLED) || user.can(Permissions.TICKET_READ_ALL)) {
            model.addAttribute("queueCount", tickets.countUnresolvedInQueue(id));
        }
        if (user.can(Permissions.VIOLATION_READ_ALL)) {
            model.addAttribute("disputesToDecide", violations.countDisputed(id));
            model.addAttribute("unidentified", violations.countUnidentified(id));
        }
        if (user.can(Permissions.SHIFT_READ_TEAM) || user.can(Permissions.SHIFT_READ_ALL)) {
            LocalDate today = siteTime.today();
            List<Shift> todays = shifts.listManaged(id, siteTime.startOf(today), siteTime.startOf(today.plusDays(1)));
            model.addAttribute("shiftsToday", todays.stream().filter(Shift::isScheduled).count());
        }
        if (user.can(Permissions.CHECKIN_READ_TEAM) || user.can(Permissions.CHECKIN_READ_ALL)) {
            model.addAttribute("onSite", checkIns.listOnSiteInScope(id).size());
        }
        return "home";
    }
}
