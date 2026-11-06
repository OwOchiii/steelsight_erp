package orochi.steelsight.scheduling.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;
import orochi.steelsight.identity.model.AppUser;

@Getter
@Setter
@Entity
@Table(name = "team")
public class Team extends IdentifiedModel {

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private AppUser manager;

    /** How long after a shift starts someone on this team can check in before being tagged late. */
    @Column(name = "late_after_minutes", nullable = false)
    private int lateAfterMinutes = 15;

    public boolean isManagedBy(Long userId) {
        return manager != null && manager.getId().equals(userId);
    }
}
