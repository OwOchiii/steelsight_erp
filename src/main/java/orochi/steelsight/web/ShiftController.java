package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** Planning shifts for other people. A person's own shifts are on the timetable page. */
@Controller
@RequiredArgsConstructor
public class ShiftController extends PageController {

    private static final LocalTime USUAL_START = LocalTime.of(8, 0);
    private static final LocalTime USUAL_END = LocalTime.of(17, 0);

    private final ShiftService shifts;
    private final UserService users;
    private final SiteTime siteTime;

    @GetMapping("/shifts")
    public String week(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        LocalDate monday = siteTime.mondayOf(week == null ? siteTime.today() : week);
        List<LocalDate> days = monday.datesUntil(monday.plusWeeks(1)).toList();
        List<Shift> planned = shifts.listManaged(user.getUserId(), siteTime.startOf(monday), siteTime.startOf(monday.plusWeeks(1)));
        boolean canPlan = user.can(Permissions.SHIFT_UPDATE_TEAM) || user.can(Permissions.SHIFT_UPDATE_ALL);
        List<AppUser> workers = canPlan ? users.listShiftWorkers(user.getUserId()) : List.of();

        model.addAttribute("monday", monday);
        model.addAttribute("sunday", monday.plusDays(6));
        model.addAttribute("previousWeek", monday.minusWeeks(1));
        model.addAttribute("nextWeek", monday.plusWeeks(1));
        model.addAttribute("today", siteTime.today());
        model.addAttribute("days", days);
        model.addAttribute("canPlan", canPlan);
        model.addAttribute("rows", pageOf(ShiftGrid.rows(workers, planned, days, siteTime), page));
        return "shifts/week";
    }

    /** A date comes from an empty cell of the grid, and fills in the usual 08:00 to 17:00 to edit from. */
    @GetMapping("/shifts/new")
    public String newShift(@AuthenticationPrincipal AuthenticatedUser user,
                           @RequestParam(required = false) Long workerId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           Model model) {
        ShiftForm form = new ShiftForm();
        form.setWorkerId(workerId);
        if (date != null) {
            form.setStartAt(date.atTime(USUAL_START));
            form.setEndAt(date.atTime(USUAL_END));
        }
        model.addAttribute("form", form);
        return showForm(user, model);
    }

    @PostMapping("/shifts")
    public String create(@AuthenticationPrincipal AuthenticatedUser user,
                         @ModelAttribute("form") ShiftForm form, BindingResult binding,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            Shift shift = shifts.create(user.getUserId(), details(form));
            redirect.addFlashAttribute("success", "The shift was added.");
            return redirectToWeekOf(shift);
        }, () -> showForm(user, model));
    }

    @GetMapping("/shifts/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        Shift shift = shifts.getForEdit(user.getUserId(), id);
        ShiftForm form = new ShiftForm();
        form.setWorkerId(shift.getUser().getId());
        form.setZoneId(shift.getZone().getId());
        form.setStartAt(siteTime.toLocal(shift.getStartAt()));
        form.setEndAt(siteTime.toLocal(shift.getEndAt()));
        form.setVersion(shift.getVersion());
        model.addAttribute("form", form);
        return showEditForm(user, id, model);
    }

    @PostMapping("/shifts/{id}")
    public String update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @ModelAttribute("form") ShiftForm form, BindingResult binding,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            Shift shift = shifts.update(user.getUserId(), id, details(form), form.getVersion());
            redirect.addFlashAttribute("success", "The shift was saved.");
            return redirectToWeekOf(shift);
        }, () -> showEditForm(user, id, model));
    }

    @PostMapping("/shifts/{id}/cancel")
    public String cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes redirect) {
        return attempt(redirect, "/shifts", "The shift was cancelled.", () -> shifts.cancel(user.getUserId(), id));
    }

    @PostMapping("/shifts/{id}/complete")
    public String complete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes redirect) {
        return attempt(redirect, "/shifts", "The shift was marked completed.", () -> shifts.complete(user.getUserId(), id));
    }

    private ShiftService.ShiftDetails details(ShiftForm form) {
        return new ShiftService.ShiftDetails(form.getWorkerId(), form.getZoneId(),
                siteTime.toInstant(form.getStartAt()), siteTime.toInstant(form.getEndAt()));
    }

    private String redirectToWeekOf(Shift shift) {
        return "redirect:/shifts?week=" + siteTime.dateOf(shift.getStartAt());
    }

    private String showForm(AuthenticatedUser user, Model model) {
        model.addAttribute("workers", users.listShiftWorkers(user.getUserId()));
        model.addAttribute("zones", shifts.listZones(user.getUserId()));
        return "shifts/form";
    }

    private String showEditForm(AuthenticatedUser user, Long id, Model model) {
        Shift shift = shifts.getForEdit(user.getUserId(), id);
        model.addAttribute("shift", shift);
        model.addAttribute("started", shift.hasStarted(OffsetDateTime.now()));
        return showForm(user, model);
    }
}
