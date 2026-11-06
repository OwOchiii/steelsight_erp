package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.ppe.service.SafetyScoreService;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;

import java.time.OffsetDateTime;

/** Looking at any worker, whichever team they are on. Changing one is still limited to the manager's own team. */
@Controller
@RequiredArgsConstructor
public class WorkerController extends PageController {

    private static final int RECENT_VISITS = 10;

    private final SafetyScoreService safety;
    private final UserService users;
    private final ViolationService violations;
    private final ShiftService shifts;
    private final CheckInService checkIns;
    private final SiteTime siteTime;

    @GetMapping("/workers")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("workers", safety.listWorkers(user.getUserId(), q, pageOf(page)));
        model.addAttribute("q", q);
        return "workers/list";
    }

    @GetMapping("/workers/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        Long actor = user.getUserId();
        AppUser worker = users.getWorker(actor, id);
        OffsetDateTime monthStart = siteTime.startOf(siteTime.today().withDayOfMonth(1));

        model.addAttribute("worker", worker);
        model.addAttribute("safety", safety.of(actor, id));
        model.addAttribute("violations", violations.listOfWorkerSince(actor, id, monthStart));
        model.addAttribute("upcoming", shifts.listUpcomingOf(actor, id, OffsetDateTime.now()));
        model.addAttribute("visits", checkIns.listRecentOf(actor, id, PageRequest.of(0, RECENT_VISITS)).getContent());
        model.addAttribute("canEdit", users.canEdit(actor, id));
        model.addAttribute("canPlan", users.canPlanShiftFor(actor, id));
        return "workers/detail";
    }
}
