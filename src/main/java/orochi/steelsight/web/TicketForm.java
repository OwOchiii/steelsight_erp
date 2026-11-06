package orochi.steelsight.web;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketForm {
    private Long categoryId;
    private Long zoneId;
    private String subject;
    private String description;
}
