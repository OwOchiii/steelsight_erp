package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.exception.BusinessRuleException;

import java.util.function.Supplier;

/** The two ways a page handles a rule the user broke. Other failures go to PageExceptionHandler. */
abstract class PageController {

    /** For a button: runs the action, then goes to the target page with a message saying how it went. */
    protected String attempt(RedirectAttributes redirect, String target, String successMessage, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("success", successMessage);
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + target;
    }

    /** For a form: returns the action's view, or shows the form again with what was typed and the problem. */
    protected String submit(BindingResult binding, Model model, HttpServletResponse response,
                            Supplier<String> action, Supplier<String> showForm) {
        try {
            if (binding.hasErrors()) {
                throw new BusinessRuleException("One of the values isn't valid. Check the form and try again.");
            }
            return action.get();
        } catch (BusinessRuleException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("error", e.getMessage());
            return showForm.get();
        }
    }
}
