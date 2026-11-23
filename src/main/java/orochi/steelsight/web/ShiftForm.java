package orochi.steelsight.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import orochi.steelsight.scheduling.model.ShiftKind;

import java.time.LocalDate;
import java.time.LocalTime;

/** The Shift Form (SRS 3.2.10): a frame fills in the start and end, which can still be changed. */
@Getter
@Setter
public class ShiftForm {
    private Long workerId;
    private Long zoneId;
    private ShiftKind kind = ShiftKind.REGULAR;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate day;
    /** HH:mm, as an input of type time sends and shows it; the locale style (7:00 AM) can't be shown there. */
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime start;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime end;
    private String reason;
    private Long pendingOvertimeId;
    private long version;
}
