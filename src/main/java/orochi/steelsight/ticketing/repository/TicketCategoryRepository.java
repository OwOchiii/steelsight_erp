package orochi.steelsight.ticketing.repository;

import orochi.steelsight.repository.BaseRepository;
import orochi.steelsight.ticketing.model.TicketCategory;

import java.util.List;
import java.util.Optional;

public interface TicketCategoryRepository extends BaseRepository<TicketCategory, Long> {

    Optional<TicketCategory> findByCategoryName(String categoryName);

    Optional<TicketCategory> findByCode(orochi.steelsight.ticketing.model.CategoryCode code);

    java.util.List<TicketCategory> findAllByActiveTrueOrderByIdAsc();

    List<TicketCategory> findAllByHandlerRoleId(Long roleId);
}
