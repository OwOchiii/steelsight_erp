package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
import orochi.steelsight.identity.service.CompanySettings;
import orochi.steelsight.scheduling.model.ZoneKind;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.scheduling.service.ZoneService;
import orochi.steelsight.security.AuthenticatedUser;

/** The Admin's Zones page (SRS 3.2.23) and Company Settings (SRS 3.2.25). */
@Controller
@RequiredArgsConstructor
public class AdminSettingsController extends PageController {

    private final ZoneService zones;
    private final CompanySettings settings;
    private final TeamRepository teams;

    @GetMapping("/zones")
    public String zones(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("zones", zones.list(user.getUserId()));
        model.addAttribute("cameras", zones.cameras(user.getUserId()));
        model.addAttribute("teams", teams.findAll());
        return "admin/zones";
    }

    @GetMapping("/zones/new")
    public String newZone(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("form", new ZoneForm());
        return showNewZone(user, model);
    }

    @PostMapping("/zones/new")
    public String createZone(@AuthenticationPrincipal AuthenticatedUser user,
                             @ModelAttribute("form") ZoneForm form, BindingResult binding,
                             Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            zones.create(user.getUserId(), form.getName(), form.getKind(), form.getTeamId());
            redirect.addFlashAttribute("success", "The zone was added.");
            return "redirect:/zones";
        }, () -> showNewZone(user, model));
    }

    private String showNewZone(AuthenticatedUser user, Model model) {
        zones.list(user.getUserId());   // only the Admin sees the form
        model.addAttribute("kinds", ZoneKind.values());
        model.addAttribute("teams", teams.findAll());
        return "admin/zone-new";
    }

    @PostMapping("/zones/cameras")
    public String linkCamera(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam(required = false) String code,
                             @RequestParam(required = false) Long zoneId, RedirectAttributes redirect) {
        return attempt(redirect, "/zones", "The camera was linked to the zone.", () -> zones.linkCamera(user.getUserId(), code, zoneId));
    }

    @PostMapping("/zones/cameras/{id}/remove")
    public String removeCamera(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes redirect) {
        return attempt(redirect, "/zones", "The camera was removed.", () -> zones.removeCamera(user.getUserId(), id));
    }

    @PostMapping("/zones/{id}")
    public String rename(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @RequestParam(required = false) String name, RedirectAttributes redirect) {
        return attempt(redirect, "/zones", "Saved successfully.", () -> zones.rename(user.getUserId(), id, name));
    }

    @PostMapping("/zones/{id}/team")
    public String team(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                       @RequestParam(required = false) Long teamId, RedirectAttributes redirect) {
        return attempt(redirect, "/zones", "Saved successfully.", () -> zones.assignTeam(user.getUserId(), id, teamId));
    }

    @GetMapping("/settings")
    public String settings(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("halfDay", settings.halfDayForUnexplained(user.getUserId()));
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@AuthenticationPrincipal AuthenticatedUser user,
                               @RequestParam(defaultValue = "false") boolean halfDay, RedirectAttributes redirect) {
        return attempt(redirect, "/settings", "Saved successfully.", () -> settings.setHalfDayForUnexplained(user.getUserId(), halfDay));
    }
}
