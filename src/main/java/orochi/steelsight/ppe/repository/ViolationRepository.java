package orochi.steelsight.ppe.repository;

import org.springframework.data.domain.Limit;
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

    List<Violation> findAllByUserIsNullOrderByDetectedAtDesc(Limit limit);

    /** The oldest dispute comes first: it has waited longest. */
    List<Violation> findAllByDisputeStatusOrderByDetectedAtAsc(DisputeStatus disputeStatus, Limit limit);

    List<Violation> findAllByOrderByDetectedAtDesc(Limit limit);

    long countByDisputeStatus(DisputeStatus disputeStatus);

    long countByUserIsNull();
}
