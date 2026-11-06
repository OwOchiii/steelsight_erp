package orochi.steelsight.ppe.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;
import orochi.steelsight.identity.model.AppUser;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "ppe_linking")
public class PpeLinking extends IdentifiedModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ppe_vest_id", nullable = false)
    private PpeVest ppeVest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt = OffsetDateTime.now();

    @Column(name = "unlinked_at")
    private OffsetDateTime unlinkedAt;

    public boolean isActive() {
        return unlinkedAt == null;
    }
}
