package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import orochi.steelsight.exception.BusinessRuleException;

import java.util.List;
import java.util.function.Supplier;

/** The two ways a page handles a rule the user broke. Other failures go to PageExceptionHandler. */
abstract class PageController {

    private static final Logger log = LoggerFactory.getLogger(PageController.class);

    private static final int PAGE_SIZE = 20;

    /** A list page from the page number in the URL, which starts at 0. */
    protected Pageable pageOf(int number) {
        return PageRequest.of(Math.max(number, 0), PAGE_SIZE);
    }

    /** One page of a list that is already in memory, for rows built by the page itself. */
    protected <T> Page<T> pageOf(List<T> rows, int number) {
        Pageable page = pageOf(number);
        int from = (int) Math.min(page.getOffset(), rows.size());
        int to = Math.min(from + page.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), page, rows.size());
    }

    /** For a button: runs the action, then goes to the target page with a message saying how it went. */
    protected String attempt(RedirectAttributes redirect, String target, String successMessage, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("success", successMessage);
        } catch (BusinessRuleException e) {
            log.info("Rule broken: {}", e.getMessage());
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + target;
    }

    /** For a form: returns the action's view, or shows the form again with what was typed and the problem. */
    protected String submit(BindingResult binding, Model model, HttpServletResponse response,
                            Supplier<String> action, Supplier<String> showForm) {
        try {
            if (binding.hasErrors()) {
                // Field names only: what was typed can be private.
                log.info("Form invalid, fields: {}", binding.getFieldErrors().stream().map(FieldError::getField).toList());
                throw new BusinessRuleException("One of the values isn't valid. Check the form and try again.");
            }
            return action.get();
        } catch (BusinessRuleException e) {
            log.info("Rule broken: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setHeader("HX-Push-Url", "false");
            model.addAttribute("error", e.getMessage());
            return showForm.get();
        }
    }
}
