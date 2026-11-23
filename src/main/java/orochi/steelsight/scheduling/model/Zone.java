package orochi.steelsight.scheduling.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;

@Getter
@Setter
@Entity
@Table(name = "zone")
public class Zone extends IdentifiedModel {

    /** The name every alert, report and Zalo message uses; the Admin can change it (SRS CR-03). */
    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ZoneKind kind = ZoneKind.WORK_AREA;

    /** The team that works in this area; HSE picks a violator from it first. Only work areas have one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    public boolean isGatePpe() {
        return kind == ZoneKind.GATE_PPE;
    }
}
