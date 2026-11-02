package orochi.steelsight.ppe.model;

import jakarta.persistence.*;
import lombok.Data;
import orochi.steelsight.identity.model.AppUser;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "ppe_linking")
public class PpeLinking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ppe_vest_id", nullable = false)
    private PpeVest ppeVest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;

    @Column(name = "unlinked_at")
    private OffsetDateTime unlinkedAt;

    public boolean isActive() {
        return unlinkedAt == null;
    }
}
