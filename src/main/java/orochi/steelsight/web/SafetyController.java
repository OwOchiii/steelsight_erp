package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import orochi.steelsight.ppe.service.SafetyScoreService;
import orochi.steelsight.security.AuthenticatedUser;

import java.time.LocalDate;

/** For HSE: how unsafe each time slot of a week was. */
@Controller
@RequiredArgsConstructor
public class SafetyController extends PageController {

    private final SafetyScoreService safety;
    private final SiteTime siteTime;

    @GetMapping("/safety/shifts")
    public String shifts(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                         Model model) {
        LocalDate monday = siteTime.mondayOf(week == null ? siteTime.today() : week);
        model.addAttribute("monday", monday);
        model.addAttribute("sunday", monday.plusDays(6));
        model.addAttribute("previousWeek", monday.minusWeeks(1));
        model.addAttribute("nextWeek", monday.plusWeeks(1));
        model.addAttribute("slots", safety.listTimeSlots(user.getUserId(), siteTime.startOf(monday), siteTime.startOf(monday.plusWeeks(1))));
        return "safety/shifts";
    }
}
