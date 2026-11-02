package orochi.steelsight.ticketing.repository;

import orochi.steelsight.repository.SoftDeletableRepository;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.model.TicketStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends SoftDeletableRepository<Ticket, Long> {

    List<Ticket> findAllByCreatedByIdAndDeletedAtIsNull(Long userId);

    List<Ticket> findAllByCreatedByIdAndStatusAndDeletedAtIsNull(Long userId, TicketStatus status);

    List<Ticket> findAllByAssigneeIdAndDeletedAtIsNull(Long assigneeId);

    @Query("""
            SELECT t FROM Ticket t
            JOIN t.category c
            WHERE c.handlerRole.id = :roleId
            AND t.deletedAt IS NULL
            """)
    List<Ticket> findAllByHandlerRoleId(@Param("roleId") Long roleId);

    @Query("""
            SELECT t FROM Ticket t
            JOIN t.category c
            WHERE c.handlerRole.id = :roleId
            AND t.status = :status
            AND t.deletedAt IS NULL
            """)
    List<Ticket> findAllByHandlerRoleIdAndStatus(
            @Param("roleId") Long roleId,
            @Param("status") TicketStatus status
    );
}
