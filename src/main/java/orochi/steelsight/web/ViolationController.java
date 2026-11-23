package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.calendar.MonthLocks;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.DismissReason;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.ReviewStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.service.SafetyScoreService;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.scheduling.repository.ZoneRepository;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.storage.PhotoAlbum;
import orochi.steelsight.storage.PhotoStorage;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.service.TicketService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** SRS 3.2.16 to 3.2.19: the review queue, a violation's page, the violation list and My Violations. */
@Controller
@RequiredArgsConstructor
public class ViolationController extends PageController {

    private final ViolationService violations;
    private final SafetyScoreService safety;
    private final TicketService tickets;
    private final ZoneRepository zones;
    private final PhotoStorage photos;
    private final MonthLocks locks;
    private final SiteTime siteTime;

    /** My Violations: the month's confirmed violations and reminders, the score and the bonus level. */
    @GetMapping("/violations")
    public String mine(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        YearMonth shown = month == null ? YearMonth.from(siteTime.today()) : month;
        model.addAttribute("month", shown);
        model.addAttribute("months", lastMonths());
        model.addAttribute("violations", violations.listOwn(user.getUserId(), shown, pageOf(page)));
        model.addAttribute("safety", safety.own(user.getUserId(), siteTime.startOf(shown.atDay(1))));
        model.addAttribute("provisional", !locks.isLocked(shown));
        model.addAttribute("violationService", violations);
        model.addAttribute("me", user.getUserId());
        return "violations/list";
    }

    /** The Review Queue: violations still waiting for a decision, oldest first (BR-53). */
    @GetMapping("/violations/review")
    public String review(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestParam(required = false) Long zoneId,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("violations", violations.queue(user.getUserId(), zoneId, day, pageOf(page)));
        model.addAttribute("disputes", violations.countDisputed(user.getUserId()));
        model.addAttribute("zones", zones.findAllByOrderByNameAsc());
        model.addAttribute("zoneId", zoneId);
        model.addAttribute("day", day);
        model.addAttribute("violationService", violations);
        return "violations/review";
    }

    /** The Violation List: every violation of a month with filters and the month's counts. */
    @GetMapping("/violations/all")
    public String all(@AuthenticationPrincipal AuthenticatedUser user,
                      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                      @RequestParam(required = false) Long zoneId,
                      @RequestParam(required = false) String status,
                      @RequestParam(required = false) String person,
                      @RequestParam(defaultValue = "0") int page, Model model) {
        YearMonth shown = month == null ? YearMonth.from(siteTime.today()) : month;
        if (status != null && !status.isBlank() && !status.equals("STRANGER")) {
            try {
                ReviewStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new NotFoundException("Status", status);
            }
        }
        var filter = new ViolationService.ListFilter(shown, zoneId, status, person);
        model.addAttribute("violations", violations.list(user.getUserId(), filter, pageOf(page)));
        model.addAttribute("counts", violations.countMonth(user.getUserId(), shown));
        model.addAttribute("filter", filter);
        model.addAttribute("months", lastMonths());
        model.addAttribute("zones", zones.findAllByOrderByNameAsc());
        model.addAttribute("statuses", List.of("PENDING_REVIEW", "OVERDUE", "CONFIRMED", "REMINDER", "DISMISSED", "STRANGER"));
        return "violations/all";
    }

    @GetMapping("/violations/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        Violation violation = violations.get(user.getUserId(), id);
        Ticket appeal = tickets.findDispute(user.getUserId(), id).orElse(null);
        boolean canReview = user.can(Permissions.VIOLATION_REVIEW) && violation.isOpen() && !locks.isLocked(violation.getDetectedAt());
        model.addAttribute("violation", violation);
        model.addAttribute("disputeTicket", appeal);
        model.addAttribute("suggestion", violations.suggestion(violation).orElse(null));
        model.addAttribute("reviewList", user.can(Permissions.VIOLATION_READ_ALL));
        model.addAttribute("canReview", canReview);
        model.addAttribute("dismissReasons", DismissReason.values());
        if (canReview) {
            model.addAttribute("candidateGroups", grouped(violations.candidates(user.getUserId(), violation)));
        }
        String noAppeal = violations.whyNoAppeal(user.getUserId(), violation);
        model.addAttribute("canDispute", violation.belongsTo(user.getUserId()) && noAppeal == null);
        model.addAttribute("noAppealReason", violation.belongsTo(user.getUserId()) ? noAppeal : null);
        if (violation.getReviewStatus() == ReviewStatus.CONFIRMED && violation.isIdentified()) {
            model.addAttribute("appealDeadline", violations.appealDeadline(violation));
        }
        boolean canDecide = violation.isDisputed() && !violation.belongsTo(user.getUserId())
                && appeal != null && tickets.canHandle(user.getUserId(), appeal.getId());
        model.addAttribute("canDecide", canDecide);
        if (canDecide) {
            model.addAttribute("people", violations.candidates(user.getUserId(), violation));
        }
        return "violations/detail";
    }

    /** The evidence photo, only through the ERP after the same check as the page (SRS CM-07). */
    @GetMapping("/violations/{id}/photo")
    public ResponseEntity<Resource> photo(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        Violation violation = violations.get(user.getUserId(), id);
        String path = violation.getSnapshotPath();
        return ResponseEntity.ok()
                .contentType(path.toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(photos.load(PhotoAlbum.EVIDENCE, path));
    }

    @PostMapping("/violations/{id}/confirm")
    public String confirm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @RequestParam(required = false) Long personId,
                          @RequestParam(defaultValue = "false") boolean stranger,
                          @RequestParam(required = false) String note,
                          @RequestParam long version, RedirectAttributes redirect) {
        return attempt(redirect, "/violations/" + id, "The violation was confirmed.",
                () -> violations.confirm(user.getUserId(), id, personId, stranger, note, version));
    }

    @PostMapping("/violations/{id}/dismiss")
    public String dismiss(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @RequestParam(required = false) DismissReason reason,
                          @RequestParam(required = false) String otherReason,
                          @RequestParam long version, RedirectAttributes redirect) {
        return attempt(redirect, "/violations/" + id, "The violation was dismissed.",
                () -> violations.dismiss(user.getUserId(), id, reason, otherReason, version));
    }

    @PostMapping("/violations/{id}/take")
    public String take(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes redirect) {
        return attempt(redirect, "/violations/" + id, "The other officers can see that you are going.",
                () -> violations.take(user.getUserId(), id));
    }

    /** Appeal (BR-64): opens a PPE appeal ticket for the Admin. */
    @PostMapping("/violations/{id}/dispute")
    public String dispute(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @RequestParam(required = false) String reason, RedirectAttributes redirect) {
        try {
            Ticket ticket = tickets.openDispute(user.getUserId(), id, null, reason);
            redirect.addFlashAttribute("success", "Your dispute was sent to the Admin.");
            return "redirect:/tickets/" + ticket.getId();
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/violations/" + id;
        }
    }

    /** The Admin's decision on an appeal, with the HSE facts (BR-65). */
    @PostMapping("/violations/{id}/decision")
    public String decide(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @RequestParam(required = false) DisputeStatus decision,
                         @RequestParam(required = false) String hseFacts,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) Long rightPersonId, RedirectAttributes redirect) {
        return attempt(redirect, "/violations/" + id, "Your decision was saved and the ticket is resolved.",
                () -> tickets.decideAppeal(user.getUserId(), id, decision, hseFacts, note, rightPersonId));
    }

    /** The person list in its groups, in order, for the optgroups of the select. */
    private static java.util.Map<ViolationService.CandidateGroup, List<ViolationService.Candidate>> grouped(
            List<ViolationService.Candidate> candidates) {
        java.util.Map<ViolationService.CandidateGroup, List<ViolationService.Candidate>> groups =
                new java.util.EnumMap<>(ViolationService.CandidateGroup.class);
        candidates.forEach(c -> groups.computeIfAbsent(c.group(), g -> new java.util.ArrayList<>()).add(c));
        return groups;
    }

    private List<YearMonth> lastMonths() {
        YearMonth now = YearMonth.from(siteTime.today());
        return java.util.stream.IntStream.range(0, 12).mapToObj(now::minusMonths).toList();
    }
}
