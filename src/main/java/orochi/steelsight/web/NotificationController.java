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
    private final orochi.steelsight.notification.service.NoticeText text;

    @GetMapping("/notifications")
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        var shown = notifications.listOwn(user.getUserId(), pageOf(page));
        model.addAttribute("notifications", shown);
        model.addAttribute("texts", shown.stream().collect(java.util.stream.Collectors.toMap(
                n -> n.getId(), n -> text.render(n, org.springframework.context.i18n.LocaleContextHolder.getLocale()))));
        model.addAttribute("deliveries", notifications.deliveries(shown.stream().map(n -> n.getId()).toList()));
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
