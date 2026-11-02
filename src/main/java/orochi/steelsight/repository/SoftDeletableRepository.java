package orochi.steelsight.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

@NoRepositoryBean
public interface SoftDeletableRepository<T, ID> extends JpaRepository<T, ID> {

    Optional<T> findByIdAndDeletedAtIsNull(ID id);

    List<T> findAllByDeletedAtIsNull();
}
