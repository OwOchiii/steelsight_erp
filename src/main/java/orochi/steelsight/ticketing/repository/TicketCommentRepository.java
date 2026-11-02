package orochi.steelsight.ticketing.repository;

import orochi.steelsight.repository.BaseRepository;
import orochi.steelsight.ticketing.model.TicketComment;

import java.util.List;

public interface TicketCommentRepository extends BaseRepository<TicketComment, Long> {

    List<TicketComment> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
