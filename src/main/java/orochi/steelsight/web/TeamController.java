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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.security.AuthenticatedUser;

@Controller
@RequiredArgsConstructor
public class TeamController extends PageController {

    private final UserService users;

    @GetMapping("/team")
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("members", users.listManaged(user.getUserId()));
        return "team/list";
    }

    @GetMapping("/team/{id}/edit")
    public String edit(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        AppUser member = users.getForEdit(user.getUserId(), id);
        ProfileForm form = new ProfileForm();
        form.setFullName(member.getFullName());
        form.setEmail(member.getEmail());
        form.setPhoneNumber(member.getPhoneNumber());
        form.setVersion(member.getVersion());
        model.addAttribute("form", form);
        model.addAttribute("member", member);
        return "team/edit";
    }

    @PostMapping("/team/{id}")
    public String update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @ModelAttribute("form") ProfileForm form, BindingResult binding,
                         Model model, HttpServletResponse response, RedirectAttributes redirect) {
        return submit(binding, model, response, () -> {
            users.updateProfile(user.getUserId(), id, new UserService.Profile(
                    form.getFullName(), form.getEmail(), form.getPhoneNumber()), form.getVersion());
            redirect.addFlashAttribute("success", "The details were saved.");
            return "redirect:/team";
        }, () -> {
            model.addAttribute("member", users.getForEdit(user.getUserId(), id));
            return "team/edit";
        });
    }
}
