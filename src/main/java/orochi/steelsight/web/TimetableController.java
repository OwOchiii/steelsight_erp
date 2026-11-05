package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class TimetableController extends PageController {

    private final ShiftService shifts;
    private final SiteTime siteTime;

    @GetMapping("/timetable")
    public String timetable(@AuthenticationPrincipal AuthenticatedUser user,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                            Model model) {
        LocalDate monday = siteTime.mondayOf(week == null ? siteTime.today() : week);
        List<LocalDate> days = monday.datesUntil(monday.plusWeeks(1)).toList();
        List<Shift> own = shifts.listOwnInRange(user.getUserId(), siteTime.startOf(monday), siteTime.startOf(monday.plusWeeks(1)));

        model.addAttribute("monday", monday);
        model.addAttribute("sunday", monday.plusDays(6));
        model.addAttribute("previousWeek", monday.minusWeeks(1));
        model.addAttribute("nextWeek", monday.plusWeeks(1));
        model.addAttribute("today", siteTime.today());
        model.addAttribute("days", days);
        model.addAttribute("cells", ShiftGrid.cells(own, days, siteTime));
        model.addAttribute("hours", ShiftGrid.hours(own));
        model.addAttribute("empty", own.isEmpty());
        return "timetable";
    }
}
