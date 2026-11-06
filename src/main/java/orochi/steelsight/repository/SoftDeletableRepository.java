package orochi.steelsight.repository;

import org.springframework.data.repository.NoRepositoryBean;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.model.SoftDeletableModel;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface SoftDeletableRepository<T extends SoftDeletableModel, ID> extends BaseRepository<T, ID> {

    Optional<T> findByIdAndDeletedAtIsNull(ID id);

    List<T> findAllByDeletedAtIsNull();

    /** A soft-deleted record counts as not found. */
    @Override
    default T require(ID id) {
        return findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("Record", id));
    }
}
