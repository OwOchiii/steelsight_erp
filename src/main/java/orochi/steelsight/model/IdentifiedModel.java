package orochi.steelsight.model;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.Hibernate;

/**
 * Every entity's id, plus identity based only on it. Comparing or printing fields would walk lazy
 * relations and loop forever on cycles such as Team.manager and AppUser.team.
 */
@Getter
@MappedSuperclass
public abstract class IdentifiedModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false;
        Long otherId = ((IdentifiedModel) other).getId();
        return getId() != null && getId().equals(otherId);
    }

    /** Constant per class, because the id changes when a new entity is saved. */
    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }

    @Override
    public String toString() {
        return Hibernate.getClass(this).getSimpleName() + "(id=" + getId() + ")";
    }
}
