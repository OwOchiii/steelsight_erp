package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;

/** Turns failures from the services into a readable page with the right status code. */
@ControllerAdvice
public class PageExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    @ExceptionHandler({NotFoundException.class, MethodArgumentTypeMismatchException.class})
    String notFound(Exception e, Model model, HttpServletResponse response) {
        log.info("Not found: {}", e.getMessage());
        return page(404, "Page not found", "We couldn't find what you were looking for.", model, response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    String forbidden(AccessDeniedException e, Model model, HttpServletResponse response) {
        log.warn("Access denied: {}", e.getMessage());
        return page(403, "Not allowed", "You don't have access to this page.", model, response);
    }

    @ExceptionHandler(BusinessRuleException.class)
    String rejected(BusinessRuleException e, Model model, HttpServletResponse response) {
        log.info("Rule broken: {}", e.getMessage());
        return page(400, "That didn't work", e.getMessage(), model, response);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    String conflict(OptimisticLockingFailureException e, Model model, HttpServletResponse response) {
        log.warn("Edit conflict: {}", e.getMessage());
        return page(409, "Someone else changed this", "Go back, reload the page and try again.", model, response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(MaxUploadSizeExceededException e, Model model, HttpServletResponse response) {
        log.info("Upload too large: {}", e.getMessage());
        return page(413, "Photo too large", "Go back and choose a photo under 5 MB.", model, response);
    }

    /** Everything else. Framework errors keep their status; the rest are bugs and get a stack trace. */
    @ExceptionHandler(Exception.class)
    String unexpected(Exception e, Model model, HttpServletResponse response) {
        if (e instanceof ErrorResponse rejectedByFramework) {
            int status = rejectedByFramework.getStatusCode().value();
            log.info("Request rejected ({}): {}", status, e.getMessage());
            return status == 404
                    ? page(404, "Page not found", "We couldn't find what you were looking for.", model, response)
                    : page(status, "That didn't work", "Go back and try again.", model, response);
        }
        log.error("Unexpected failure", e);
        model.addAttribute("reference", MDC.get(RequestLogFilter.REQUEST_ID));
        return page(500, "Something went wrong", "This is a problem on our side.", model, response);
    }

    private String page(int status, String title, String message, Model model, HttpServletResponse response) {
        response.setStatus(status);
        response.setHeader("HX-Push-Url", "false");
        model.addAttribute("status", status);
        model.addAttribute("error", title);
        model.addAttribute("message", message);
        return "error";
    }
}
