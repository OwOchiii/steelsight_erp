package orochi.steelsight.ppe.repository;

import orochi.steelsight.ppe.model.PpeVest;
import orochi.steelsight.repository.BaseRepository;

import java.util.Optional;

public interface PpeVestRepository extends BaseRepository<PpeVest, Long> {

    Optional<PpeVest> findByVestCode(String vestCode);
}
