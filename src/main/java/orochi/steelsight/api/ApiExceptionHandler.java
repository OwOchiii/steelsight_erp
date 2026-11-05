package orochi.steelsight.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Software calls the API, so failures are JSON rather than the error page people get. */
@Order(0)
@RestControllerAdvice(assignableTypes = DetectionAlertController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        log.info("API request unreadable: {}", e.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.badRequest().body(Map.of("error", "The request body is not valid"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception e) {
        if (e instanceof ErrorResponse rejected) {
            return ResponseEntity.status(rejected.getStatusCode()).body(Map.of("error", "The request was rejected"));
        }
        log.error("API failure", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Something went wrong"));
    }
}
