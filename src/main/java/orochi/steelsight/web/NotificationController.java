package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import orochi.steelsight.notification.service.NotificationService;
import orochi.steelsight.security.AuthenticatedUser;

@Controller
@RequiredArgsConstructor
public class NotificationController extends PageController {

    private final NotificationService notifications;

    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("notifications", notifications.listOwn(user.getUserId(), pageOf(page)));
        model.addAttribute("unread", notifications.countUnread(user.getUserId()));
        return "notifications/list";
    }

    @PostMapping("/notifications/{id}/read")
    public String markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        notifications.markRead(user.getUserId(), id);
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    public String markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        notifications.markAllRead(user.getUserId());
        return "redirect:/notifications";
    }
}
