package orochi.steelsight.ticketing.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.model.SoftDeletableModel;
import orochi.steelsight.ppe.model.Violation;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ticket")
public class Ticket extends SoftDeletableModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private TicketCategory category;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolver_id")
    private AppUser resolver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "violation_id")
    private Violation violation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;
}
