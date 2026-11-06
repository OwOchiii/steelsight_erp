package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.security.AuthenticatedUser;
import orochi.steelsight.security.PasswordChangeRequiredFilter;

/** The signed-in person's own account. */
@Controller
@RequiredArgsConstructor
public class AccountController extends PageController {

    private final UserService users;

    @GetMapping(PasswordChangeRequiredFilter.PAGE)
    public String passwordForm(HttpSession session, Model model) {
        model.addAttribute("form", new PasswordForm());
        return showPasswordForm(session, model);
    }

    @PostMapping(PasswordChangeRequiredFilter.PAGE)
    public String changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                 @ModelAttribute("form") PasswordForm form, BindingResult binding,
                                 HttpSession session, Model model, HttpServletResponse response,
                                 RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            if (form.getNewPassword() == null || !form.getNewPassword().equals(form.getConfirmPassword())) {
                throw new BusinessRuleException("The new passwords don't match");
            }
            users.changeOwnPassword(user.getUserId(), form.getCurrentPassword(), form.getNewPassword());
            session.removeAttribute(PasswordChangeRequiredFilter.SESSION_FLAG);
            redirect.addFlashAttribute("success", "Your password was changed.");
            return "redirect:/";
        }, () -> {
            // Passwords are never sent back to the browser.
            model.addAttribute("form", new PasswordForm());
            return showPasswordForm(session, model);
        });
    }

    private String showPasswordForm(HttpSession session, Model model) {
        model.addAttribute("forcedPasswordChange", session.getAttribute(PasswordChangeRequiredFilter.SESSION_FLAG) != null);
        return "account/password";
    }
}
