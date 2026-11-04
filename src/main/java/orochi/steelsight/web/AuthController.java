package orochi.steelsight.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import orochi.steelsight.security.AuthenticatedUser;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("user", user);
        return "home";
    }
}
