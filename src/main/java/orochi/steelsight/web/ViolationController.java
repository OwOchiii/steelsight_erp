package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.service.SafetyScoreService;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.service.TicketService;

@Controller
@RequiredArgsConstructor
public class ViolationController extends PageController {

    public enum ReviewView { DISPUTED, UNIDENTIFIED, ALL }

    private final ViolationService violations;
    private final SafetyScoreService safety;
    private final TicketService tickets;
    private final UserService users;
    private final Display display;

    @GetMapping("/violations")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("violations", violations.listOwn(user.getUserId(), pageOf(page)));
        model.addAttribute("safety", safety.own(user.getUserId()));
        return "violations/list";
    }

    @GetMapping("/violations/review")
    public String review(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestParam(defaultValue = "DISPUTED") ReviewView view,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        Long id = user.getUserId();
        Page<Violation> rows = switch (view) {
            case DISPUTED -> violations.listDisputed(id, pageOf(page));
            case UNIDENTIFIED -> violations.listUnidentified(id, pageOf(page));
            case ALL -> violations.listAll(id, pageOf(page));
        };
        model.addAttribute("violations", rows);
        model.addAttribute("view", view.name());
        return "violations/review";
    }

    @GetMapping("/violations/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        Violation violation = violations.get(user.getUserId(), id);
        boolean canReview = user.can(Permissions.VIOLATION_REVIEW);
        model.addAttribute("violation", violation);
        model.addAttribute("disputeTicket", tickets.findDispute(user.getUserId(), id).orElse(null));
        model.addAttribute("canDispute", violation.canBeDisputedBy(user.getUserId()));
        model.addAttribute("canReview", canReview);
        model.addAttribute("canDecide", canReview && violation.isDisputed() && !violation.belongsTo(user.getUserId()));
        model.addAttribute("reviewList", user.can(Permissions.VIOLATION_READ_ALL));
        if (canReview && !violation.isIdentified()) {
            model.addAttribute("workers", users.listForViolationReview(user.getUserId()));
        }
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

    @PostMapping("/violations/{id}/decision")
    public String decide(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @RequestParam(required = false) DisputeStatus decision,
                         @RequestParam(required = false) String note, RedirectAttributes redirect) {
        return attempt(redirect, "/violations/" + id, "Your decision was saved and the ticket is resolved.",
                () -> violations.decide(user.getUserId(), id, decision, note));
    }

    @PostMapping("/violations/{id}/worker")
    public String assignWorker(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                               @RequestParam(required = false) Long workerId, RedirectAttributes redirect) {
        return attempt(redirect, "/violations/" + id, "The worker was added to this violation.",
                () -> violations.assignWorker(user.getUserId(), id, workerId));
    }
}
