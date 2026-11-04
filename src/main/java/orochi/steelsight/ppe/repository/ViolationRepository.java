package orochi.steelsight.ppe.repository;

import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.repository.BaseRepository;

import java.util.List;

public interface ViolationRepository extends BaseRepository<Violation, Long> {

    List<Violation> findAllByUserIdOrderByDetectedAtDesc(Long userId);

    List<Violation> findAllByDisputeStatus(DisputeStatus disputeStatus);

    List<Violation> findAllByUserIdAndDisputeStatus(Long userId, DisputeStatus disputeStatus);

    /** The HSE queue: violations nobody has been assigned to yet. */
    List<Violation> findAllByUserIsNullOrderByDetectedAtDesc();
}
