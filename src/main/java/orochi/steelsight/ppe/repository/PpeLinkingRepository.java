package orochi.steelsight.ppe.repository;

import orochi.steelsight.ppe.model.PpeLinking;
import orochi.steelsight.repository.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface PpeLinkingRepository extends BaseRepository<PpeLinking, Long> {

    Optional<PpeLinking> findByPpeVestIdAndUnlinkedAtIsNull(Long ppeVestId);

    Optional<PpeLinking> findByUserIdAndUnlinkedAtIsNull(Long userId);

    List<PpeLinking> findAllByUserId(Long userId);
}
