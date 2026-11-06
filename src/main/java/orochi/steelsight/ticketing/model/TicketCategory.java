package orochi.steelsight.ticketing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;
import orochi.steelsight.identity.model.Role;

@Getter
@Setter
@Entity
@Table(name = "ticket_category")
public class TicketCategory extends IdentifiedModel {

    @Column(name = "category_name", nullable = false, unique = true)
    private String categoryName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handler_role_id", nullable = false)
    private Role handlerRole;
}
