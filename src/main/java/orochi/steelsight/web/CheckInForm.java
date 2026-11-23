package orochi.steelsight.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** The Attendance Form (SRS 3.2.14). note is the reason, 10 to 500 characters (BR-39). */
@Getter
@Setter
public class CheckInForm {
    private Long workerId;
    private Long zoneId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime checkInAt;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime checkOutAt;
    private String note;
    private Long ticketId;
    private long version;
}
