package orochi.steelsight.ppe.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.ReviewStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.repository.BaseRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface ViolationRepository extends BaseRepository<Violation, Long>, JpaSpecificationExecutor<Violation> {

    /** A person's own violations that a decision put on them: confirmed or reminders (SRS 3.2.19). */
    @Query("""
            SELECT v FROM Violation v
            WHERE v.user.id = :userId AND v.reviewStatus IN :statuses
              AND v.detectedAt >= :from AND v.detectedAt < :to
            ORDER BY v.detectedAt DESC, v.id DESC
            """)
    Page<Violation> findOwnInPeriod(@Param("userId") Long userId, @Param("statuses") Collection<ReviewStatus> statuses,
                                    @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, Pageable page);

    @Query("""
            SELECT v FROM Violation v
            WHERE v.user.id = :userId AND v.reviewStatus IN :statuses
            ORDER BY v.detectedAt DESC, v.id DESC
            """)
    List<Violation> findOwn(@Param("userId") Long userId, @Param("statuses") Collection<ReviewStatus> statuses);

    List<Violation> findAllByUserIdAndDetectedAtGreaterThanEqualOrderByDetectedAtDescIdDesc(Long userId, OffsetDateTime from);

    /** The oldest appeal comes first: it has waited longest. */
    Page<Violation> findAllByDisputeStatusOrderByDetectedAtAscIdAsc(DisputeStatus disputeStatus, Pageable page);

    /** For the dashboard's charts, which need each violation's zone. */
    @Query("""
            SELECT v FROM Violation v
            JOIN FETCH v.zone
            WHERE v.detectedAt >= :from
            """)
    List<Violation> findDetectedSince(@Param("from") OffsetDateTime from);

    long countByDisputeStatus(DisputeStatus disputeStatus);

    long countByReviewStatusIn(Collection<ReviewStatus> statuses);

    long countByReviewStatus(ReviewStatus status);
}
