package orochi.steelsight.web;

import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.scheduling.model.ZoneKind;

/** The Admin's New Zone form: the name alerts use, what the zone is for and, for a work area, its team. */
@Getter
@Setter
public class ZoneForm {
    private String name;
    private ZoneKind kind = ZoneKind.WORK_AREA;
    private Long teamId;
}
