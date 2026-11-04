package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;

/** Turns failures from the services into a readable page with the right status code. */
@ControllerAdvice
public class PageExceptionHandler {

    @ExceptionHandler({NotFoundException.class, MethodArgumentTypeMismatchException.class})
    String notFound(Model model, HttpServletResponse response) {
        return page(404, "Page not found", "We couldn't find what you were looking for.", model, response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    String forbidden(Model model, HttpServletResponse response) {
        return page(403, "Not allowed", "You don't have access to this page.", model, response);
    }

    @ExceptionHandler(BusinessRuleException.class)
    String rejected(BusinessRuleException e, Model model, HttpServletResponse response) {
        return page(400, "That didn't work", e.getMessage(), model, response);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    String conflict(Model model, HttpServletResponse response) {
        return page(409, "Someone else changed this", "Go back, reload the page and try again.", model, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(Model model, HttpServletResponse response) {
        return page(413, "Photo too large", "Go back and choose a photo under 5 MB.", model, response);
    }

    private String page(int status, String title, String message, Model model, HttpServletResponse response) {
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("error", title);
        model.addAttribute("message", message);
        return "error";
    }
}
