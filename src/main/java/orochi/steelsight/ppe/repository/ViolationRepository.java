package orochi.steelsight.ppe.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.repository.BaseRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ViolationRepository extends BaseRepository<Violation, Long> {

    List<Violation> findAllByUserIdOrderByDetectedAtDesc(Long userId);

    List<Violation> findAllByDisputeStatus(DisputeStatus disputeStatus);

    List<Violation> findAllByUserIdAndDisputeStatus(Long userId, DisputeStatus disputeStatus);

    /** The HSE queue: violations nobody has been assigned to yet. */
    List<Violation> findAllByUserIsNullOrderByDetectedAtDesc();

    Page<Violation> findAllByUserIsNullOrderByDetectedAtDescIdDesc(Pageable page);

    Page<Violation> findAllByUserIdOrderByDetectedAtDescIdDesc(Long userId, Pageable page);

    List<Violation> findAllByUserIdAndDetectedAtGreaterThanEqualOrderByDetectedAtDescIdDesc(Long userId, OffsetDateTime from);

    /** The oldest dispute comes first: it has waited longest. */
    Page<Violation> findAllByDisputeStatusOrderByDetectedAtAscIdAsc(DisputeStatus disputeStatus, Pageable page);

    Page<Violation> findAllByOrderByDetectedAtDescIdDesc(Pageable page);

    /** For the dashboard's charts, which need each violation's zone. */
    @Query("""
            SELECT v FROM Violation v
            JOIN FETCH v.camera c
            JOIN FETCH c.zone
            WHERE v.detectedAt >= :from
            """)
    List<Violation> findDetectedSince(@Param("from") OffsetDateTime from);

    long countByDisputeStatus(DisputeStatus disputeStatus);

    long countByUserIsNull();
}
