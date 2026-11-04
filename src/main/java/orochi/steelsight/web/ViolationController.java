package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.service.TicketService;

@Controller
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violations;
    private final TicketService tickets;
    private final Display display;

    @GetMapping("/violations")
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("violations", violations.listOwn(user.getUserId()));
        return "violations/list";
    }

    @GetMapping("/violations/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        model.addAttribute("violation", violations.get(user.getUserId(), id));
        model.addAttribute("disputeTicket", tickets.findDispute(user.getUserId(), id).orElse(null));
        model.addAttribute("userId", user.getUserId());
        return "violations/detail";
    }

    @PostMapping("/violations/{id}/dispute")
    public String dispute(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        Violation violation = violations.get(user.getUserId(), id);
        String subject = "Dispute: " + display.label(violation.getViolationType());
        try {
            Ticket ticket = tickets.openDispute(user.getUserId(), id, subject, reason);
            redirect.addFlashAttribute("success", "Your dispute was sent to the HSE team.");
            return "redirect:/tickets/" + ticket.getId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/violations/" + id;
        }
    }
}
