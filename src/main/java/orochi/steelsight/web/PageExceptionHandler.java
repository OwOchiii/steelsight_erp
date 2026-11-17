package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.support.RequestContextUtils;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;

/**
 * Turns failures from the services into something readable. When a form sent by POST breaks a rule,
 * the person goes back to the page they sent it from, with the problem shown there. Otherwise they get
 * the error page with the right status code.
 */
@ControllerAdvice
public class PageExceptionHandler {

    static final String STALE_EDIT =
            "Someone else changed this while you were editing. Reload the page, check their changes and try again.";
    static final String BAD_VALUE = "One of the values isn't valid. Check the form and try again.";

    private static final Logger log = LoggerFactory.getLogger(PageExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    String notFound(Exception e, Model model, HttpServletResponse response) {
        log.info("Not found: {}", e.getMessage());
        return page(404, "Page not found", "We couldn't find what you were looking for.", model, response);
    }

    /** A bad id in the address is a page that doesn't exist; a bad value sent by a form is a mistake to fix. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    String badValue(MethodArgumentTypeMismatchException e, HttpServletRequest request, Model model,
                    HttpServletResponse response) {
        if (e.getParameter().hasParameterAnnotation(PathVariable.class) || !isPost(request)) {
            return notFound(e, model, response);
        }
        log.info("Form value unreadable: {}", e.getName());
        return backOrPage(request, 400, "That didn't work", BAD_VALUE, model, response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    String missingValue(MissingServletRequestParameterException e, HttpServletRequest request, Model model,
                        HttpServletResponse response) {
        log.info("Form value missing: {}", e.getParameterName());
        return backOrPage(request, 400, "That didn't work", BAD_VALUE, model, response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    String forbidden(AccessDeniedException e, Model model, HttpServletResponse response) {
        log.warn("Access denied: {}", e.getMessage());
        return page(403, "Not allowed", "You don't have access to this page.", model, response);
    }

    @ExceptionHandler(BusinessRuleException.class)
    String rejected(BusinessRuleException e, HttpServletRequest request, Model model, HttpServletResponse response) {
        log.info("Rule broken: {}", e.getMessage());
        return backOrPage(request, 400, "That didn't work", e.getMessage(), model, response);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    String conflict(OptimisticLockingFailureException e, HttpServletRequest request, Model model,
                    HttpServletResponse response) {
        log.warn("Edit conflict: {}", e.getMessage());
        return backOrPage(request, 409, "Someone else changed this", STALE_EDIT, model, response);
    }

    /** Uploads over the container's ceiling are usually stopped by MultipartLimitFilter first. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    String tooLarge(MaxUploadSizeExceededException e, HttpServletRequest request, Model model,
                    HttpServletResponse response) {
        log.info("Upload too large: {}", e.getMessage());
        return backOrPage(request, 413, "Photo too large", "The photo must be 5 MB or smaller", model, response);
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

    /** Back to the form's page with the problem shown there, when that page is known. */
    private String backOrPage(HttpServletRequest request, int status, String title, String message,
                              Model model, HttpServletResponse response) {
        if (isPost(request)) {
            var back = SamePage.of(request);
            if (back.isPresent()) {
                RequestContextUtils.getOutputFlashMap(request).put("error", message);
                return "redirect:" + back.get();
            }
        }
        return page(status, title, message, model, response);
    }

    private static boolean isPost(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
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
