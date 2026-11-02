package orochi.steelsight.ppe.model;

import jakarta.persistence.*;
import lombok.Data;
import orochi.steelsight.scheduling.model.Zone;

@Data
@Entity
@Table(name = "camera")
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "camera_code", nullable = false, unique = true)
    private String cameraCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;
}
