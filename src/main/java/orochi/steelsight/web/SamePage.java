package orochi.steelsight.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/** The page a form was sent from, so the person can be taken back to it. */
final class SamePage {

    private SamePage() {
    }

    /**
     * The path and query of the Referer, never its host, so a redirect to it stays on this site.
     * Empty when there is no usable Referer.
     */
    static Optional<String> of(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return Optional.empty();
        }
        try {
            UriComponents uri = UriComponentsBuilder.fromUriString(referer).build();
            String path = uri.getPath();
            // "//host" and "/\host" are read by browsers as another site.
            if (path == null || !path.startsWith("/") || path.startsWith("//") || path.startsWith("/\\")) {
                return Optional.empty();
            }
            String target = uri.getQuery() == null ? path : path + "?" + uri.getQuery();
            // Browsers leave braces unencoded in a query, and a redirect would read them as a URI template.
            return Optional.of(target.replace("{", "%7B").replace("}", "%7D"));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
