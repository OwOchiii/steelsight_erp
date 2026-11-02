package orochi.steelsight.ticketing.model;

import jakarta.persistence.*;
import lombok.Data;
import orochi.steelsight.identity.model.Role;

@Data
@Entity
@Table(name = "ticket_category")
public class TicketCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_name", nullable = false, unique = true)
    private String categoryName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handler_role_id", nullable = false)
    private Role handlerRole;
}
