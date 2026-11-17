package orochi.steelsight.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Failures that happen before a controller runs, in a filter or in Tomcat itself, end up here. Spring
 * Boot's own error controller answers those with JSON unless the browser asks for HTML, and htmx
 * doesn't ask, so people saw raw JSON. Pages always get the error page; only /api/ gets JSON.
 */
@Controller
public class PageErrorController implements ErrorController {

    @RequestMapping("/error")
    public ModelAndView error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        int status = status(request);
        response.setStatus(status);
        if (fromApi(request)) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(status >= 500
                    ? "{\"error\":\"Something went wrong\"}"
                    : "{\"error\":\"The request was rejected\"}");
            return null;
        }
        response.setHeader("HX-Push-Url", "false");
        ModelAndView page = new ModelAndView("error");
        page.addObject("status", status);
        switch (status) {
            case HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE ->
                    explain(page, "Photo too large", "Go back and choose a photo under 5 MB.");
            case HttpServletResponse.SC_FORBIDDEN -> {
                if (request.getAttribute(WebAttributes.ACCESS_DENIED_403) instanceof CsrfException) {
                    // The form's security token no longer matches: the session ended while the page was open.
                    explain(page, "This page has expired", "Go back, reload the page and try again.");
                } else {
                    explain(page, "Not allowed", "You don't have access to this page.");
                }
            }
            case HttpServletResponse.SC_NOT_FOUND ->
                    explain(page, "Page not found", "We couldn't find what you were looking for.");
            default -> {
                if (status >= 500) {
                    explain(page, "Something went wrong", "This is a problem on our side.");
                    page.addObject("reference", MDC.get(RequestLogFilter.REQUEST_ID));
                } else {
                    explain(page, "That didn't work", "Go back and try again.");
                }
            }
        }
        return page;
    }

    private static void explain(ModelAndView page, String title, String message) {
        page.addObject("error", title);
        page.addObject("message", message);
    }

    private static int status(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return code instanceof Integer number ? number : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    private static boolean fromApi(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = uri == null ? "" : uri.toString().substring(request.getContextPath().length());
        return path.startsWith("/api/");
    }
}
