package orochi.steelsight.attendance.repository;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.repository.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends BaseRepository<CheckIn, Long> {

    /** The visit a worker is currently on, if any. */
    Optional<CheckIn> findByUserIdAndCheckOutAtIsNull(Long userId);

    List<CheckIn> findAllByUserIdOrderByCheckInAtDesc(Long userId);

    List<CheckIn> findAllByShiftId(Long shiftId);

    /** Everyone on site right now. */
    List<CheckIn> findAllByCheckOutAtIsNull();

    List<CheckIn> findAllByOrderByCheckInAtDesc(Limit limit);

    @Query("""
            SELECT c FROM CheckIn c
            WHERE c.user.team.manager.id = :managerId AND c.checkOutAt IS NULL
            ORDER BY c.checkInAt DESC
            """)
    List<CheckIn> findTeamOnSite(@Param("managerId") Long managerId);

    @Query("""
            SELECT c FROM CheckIn c
            WHERE c.user.team.manager.id = :managerId
            ORDER BY c.checkInAt DESC
            """)
    List<CheckIn> findTeamVisits(@Param("managerId") Long managerId, Limit limit);

    @Query("""
            SELECT c FROM CheckIn c
            JOIN c.user u
            JOIN u.team t
            WHERE t.manager.id = :managerId
            ORDER BY c.checkInAt DESC
            """)
    List<CheckIn> findAllByTeamManagerId(@Param("managerId") Long managerId);
}
