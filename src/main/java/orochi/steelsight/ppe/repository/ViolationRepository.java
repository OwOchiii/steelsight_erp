package orochi.steelsight.ppe.repository;

import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ViolationRepository extends BaseRepository<Violation, Long> {

    @Query("""
            SELECT v FROM Violation v
            WHERE v.ppeLinking.user.id = :userId
            ORDER BY v.detectedAt DESC
            """)
    List<Violation> findAllByUserId(@Param("userId") Long userId);

    List<Violation> findAllByDisputeStatus(DisputeStatus disputeStatus);

    @Query("""
            SELECT v FROM Violation v
            WHERE v.ppeLinking.user.id = :userId
            AND v.disputeStatus = :status
            """)
    List<Violation> findAllByUserIdAndDisputeStatus(
            @Param("userId") Long userId,
            @Param("status") DisputeStatus status
    );
}
