package orochi.steelsight.scheduling.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import orochi.steelsight.model.IdentifiedModel;

@Getter
@Setter
@Entity
@Table(name = "zone")
public class Zone extends IdentifiedModel {

    /** The name every alert, report and Zalo message uses; the Admin can change it (SRS CR-03). */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * GATE-IN, GATE-PPE, AREA-A ...: the database gives it when the zone is added and refuses any change,
     * because the desktop app's camera settings name the zone by it (SRS 3.2.23, 3.2.30; V26).
     */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private String code;

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
