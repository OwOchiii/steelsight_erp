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
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.security.AuthenticatedUser;

/** Managers read their teams' visits; HSE sees everyone's and can add or correct one by hand. */
@Controller
@RequiredArgsConstructor
public class AttendanceController extends PageController {

    private final CheckInService checkIns;
    private final UserService users;
    private final SiteTime siteTime;

    @GetMapping("/attendance")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(defaultValue = "false") boolean onSite,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("onSite", onSite);
        model.addAttribute("visits", onSite
                ? checkIns.listOnSiteInScope(user.getUserId(), pageOf(page))
                : checkIns.listRecentInScope(user.getUserId(), pageOf(page)));
        return "attendance/list";
    }

    @GetMapping("/attendance/new")
    public String newCheckIn(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("form", new CheckInForm());
        return showNewForm(user, model);
    }

    @PostMapping("/attendance")
    public String record(@AuthenticationPrincipal AuthenticatedUser user,
                         @ModelAttribute("form") CheckInForm form, BindingResult binding,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            checkIns.recordManual(user.getUserId(), new CheckInService.ManualCheckIn(
                    form.getWorkerId(), null, form.getZoneId(),
                    siteTime.toInstant(form.getCheckInAt()), siteTime.toInstant(form.getCheckOutAt()), form.getNote()));
            redirect.addFlashAttribute("success", "The check-in was added.");
            return "redirect:/attendance";
        }, () -> showNewForm(user, model));
    }

    @GetMapping("/attendance/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        CheckIn visit = checkIns.getForEdit(user.getUserId(), id);
        CheckInForm form = new CheckInForm();
        form.setCheckInAt(siteTime.toLocal(visit.getCheckInAt()));
        form.setCheckOutAt(siteTime.toLocal(visit.getCheckOutAt()));
        form.setNote(visit.getNote());
        form.setVersion(visit.getVersion());
        model.addAttribute("form", form);
        return showEditForm(user, visit, model);
    }

    @PostMapping("/attendance/{id}")
    public String correct(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          @ModelAttribute("form") CheckInForm form, BindingResult binding,
                          Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            checkIns.correct(user.getUserId(), id, siteTime.toInstant(form.getCheckInAt()),
                    siteTime.toInstant(form.getCheckOutAt()), form.getNote(), form.getVersion());
            redirect.addFlashAttribute("success", "The check-in was corrected.");
            return "redirect:/attendance";
        }, () -> showEditForm(user, checkIns.getForEdit(user.getUserId(), id), model));
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
}
