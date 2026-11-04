package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.ticketing.model.TicketStatus;
import orochi.steelsight.ticketing.service.TicketService;

import java.time.OffsetDateTime;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ShiftService shifts;
    private final TicketService tickets;
    private final ViolationService violations;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("user", user);
        OffsetDateTime now = OffsetDateTime.now();
        if (user.can(Permissions.SHIFT_READ_OWN)) {
            model.addAttribute("nextShift", shifts.listOwn(user.getUserId()).stream()
                    .filter(shift -> shift.getStatus() == ShiftStatus.SCHEDULED && shift.getEndAt().isAfter(now))
                    .findFirst().orElse(null));
        }
        if (user.can(Permissions.TICKET_READ_OWN)) {
            model.addAttribute("openTickets", tickets.listOwn(user.getUserId()).stream()
                    .filter(ticket -> ticket.getStatus() != TicketStatus.RESOLVED).count());
        }
        if (user.can(Permissions.VIOLATION_READ_OWN)) {
            model.addAttribute("activeDisputes", violations.listOwn(user.getUserId()).stream()
                    .filter(violation -> violation.getDisputeStatus() == DisputeStatus.DISPUTED).count());
            model.addAttribute("violationCount", violations.listOwn(user.getUserId()).size());
        }
        return "home";
    }
}
