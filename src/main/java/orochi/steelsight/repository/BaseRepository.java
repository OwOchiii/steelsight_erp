package orochi.steelsight.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.model.IdentifiedModel;

@NoRepositoryBean
public interface BaseRepository<T extends IdentifiedModel, ID> extends JpaRepository<T, ID> {

    /** How many rows a list page shows, so a long history can't make a page huge. */
    Limit LIST_LIMIT = Limit.of(100);

    /** The record, or NotFoundException, which the pages turn into a 404. */
    default T require(ID id) {
        return findById(id).orElseThrow(() -> new NotFoundException("Record", id));
    }
}
