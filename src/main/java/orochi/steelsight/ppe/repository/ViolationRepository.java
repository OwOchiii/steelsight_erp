package orochi.steelsight.ppe.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<Violation> findAllByUserIsNullOrderByDetectedAtDescIdDesc(Pageable page);

    Page<Violation> findAllByUserIdOrderByDetectedAtDescIdDesc(Long userId, Pageable page);

    /** The oldest dispute comes first: it has waited longest. */
    Page<Violation> findAllByDisputeStatusOrderByDetectedAtAscIdAsc(DisputeStatus disputeStatus, Pageable page);

    Page<Violation> findAllByOrderByDetectedAtDescIdDesc(Pageable page);

    long countByDisputeStatus(DisputeStatus disputeStatus);

    long countByUserIsNull();
}
