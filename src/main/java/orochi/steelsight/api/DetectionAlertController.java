package orochi.steelsight.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import orochi.steelsight.notification.service.SafetyAlertService;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** The detection software calls this when it counts too many violations. An API key guards /api/. */
@RestController
@RequestMapping("/api/v1/detection")
@RequiredArgsConstructor
public class DetectionAlertController {

    private static final int CAMERA_CODE_LENGTH = 100;

    private final SafetyAlertService alerts;

    public record AlertRequest(UUID eventId, Integer violationCount, Integer windowSeconds,
                               OffsetDateTime detectedAt, String cameraCode) {
    }

    @PostMapping("/alerts")
    public ResponseEntity<Map<String, String>> alert(@RequestBody AlertRequest request) {
        String problem = problemWith(request);
        if (problem != null) {
            return ResponseEntity.badRequest().body(Map.of("error", problem));
        }
        boolean created = alerts.record(new SafetyAlertService.NewAlert(request.eventId(), request.violationCount(),
                request.windowSeconds(), request.detectedAt(), request.cameraCode()));
        return created
                ? ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "created"))
                : ResponseEntity.ok(Map.of("status", "duplicate"));
    }

    private String problemWith(AlertRequest request) {
        if (request.eventId() == null) {
            return "eventId is required";
        }
        if (request.violationCount() == null || request.violationCount() < 1) {
            return "violationCount must be at least 1";
        }
        if (request.windowSeconds() == null || request.windowSeconds() < 1) {
            return "windowSeconds must be at least 1";
        }
        if (request.detectedAt() == null) {
            return "detectedAt is required";
        }
        if (request.cameraCode() != null && request.cameraCode().length() > CAMERA_CODE_LENGTH) {
            return "cameraCode is too long";
        }
        return null;
    }
}
