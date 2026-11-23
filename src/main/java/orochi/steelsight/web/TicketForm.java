package orochi.steelsight.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import orochi.steelsight.ticketing.model.Direction;
import orochi.steelsight.ticketing.model.LeaveType;

import java.time.LocalTime;

/** The New Ticket form (SRS 3.2.20). The fields after details are used by some categories only. */
@Getter
@Setter
public class TicketForm {
    private Long categoryId;
    private String subject;
    private String description;
    private Long shiftId;
    private Long checkInId;
    private Direction direction;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime gateTime;
    private LeaveType leaveType;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime leaveFrom;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime leaveTo;
}
