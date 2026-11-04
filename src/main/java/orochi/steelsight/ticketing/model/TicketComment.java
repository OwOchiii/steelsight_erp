package orochi.steelsight.ticketing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import orochi.steelsight.model.IdentifiedModel;
import orochi.steelsight.identity.model.AppUser;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "ticket_comment")
public class TicketComment extends IdentifiedModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static TicketComment on(Ticket ticket, AppUser author, String body) {
        TicketComment comment = new TicketComment();
        comment.ticket = ticket;
        comment.author = author;
        comment.body = body;
        return comment;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
