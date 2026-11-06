package orochi.steelsight.ppe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;
import orochi.steelsight.scheduling.model.Zone;

@Getter
@Setter
@Entity
@Table(name = "camera")
public class Camera extends IdentifiedModel {

    @Column(name = "camera_code", nullable = false, unique = true)
    private String cameraCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;
}
