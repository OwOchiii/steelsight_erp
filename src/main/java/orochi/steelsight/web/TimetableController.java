package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class TimetableController {

    private final ShiftService shifts;

    @GetMapping("/timetable")
    public String timetable(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        OffsetDateTime now = OffsetDateTime.now();
        List<Shift> own = shifts.listOwn(user.getUserId());
        model.addAttribute("upcoming", own.stream().filter(shift -> shift.isUpcoming(now)).toList());
        model.addAttribute("earlier", own.stream().filter(shift -> !shift.isUpcoming(now))
                .sorted(Comparator.comparing(Shift::getStartAt).reversed()).toList());
        return "timetable";
    }
}
