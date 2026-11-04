package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.security.AuthenticatedUser;

import java.time.OffsetDateTime;

@Controller
@RequiredArgsConstructor
public class TimetableController extends PageController {

    private final ShiftService shifts;

    @GetMapping("/timetable")
    public String timetable(@AuthenticationPrincipal AuthenticatedUser user,
                            @RequestParam(defaultValue = "0") int page, Model model) {
        OffsetDateTime now = OffsetDateTime.now();
        model.addAttribute("upcoming", shifts.listOwnUpcoming(user.getUserId(), now));
        model.addAttribute("earlier", shifts.listOwnEarlier(user.getUserId(), now, pageOf(page)));
        return "timetable";
    }
}
