package orochi.steelsight.attendance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.repository.BaseRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends BaseRepository<CheckIn, Long> {

    /** The visit a worker is currently on, if any. */
    Optional<CheckIn> findByUserIdAndCheckOutAtIsNull(Long userId);

    List<CheckIn> findAllByUserIdOrderByCheckInAtDesc(Long userId);

    List<CheckIn> findAllByShiftId(Long shiftId);

    /** Everyone on site right now. */
    List<CheckIn> findAllByCheckOutAtIsNull();

    List<CheckIn> findAllByCheckInAtGreaterThanEqualAndCheckInAtLessThan(OffsetDateTime from, OffsetDateTime to);

    @Query("""
            SELECT c FROM CheckIn c
            WHERE c.user.team.manager.id = :managerId AND c.checkInAt >= :from AND c.checkInAt < :to
            """)
    List<CheckIn> findTeamStartedIn(@Param("managerId") Long managerId, @Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to);

    Page<CheckIn> findAllByOrderByCheckInAtDescIdDesc(Pageable page);

    Page<CheckIn> findAllByUserIdOrderByCheckInAtDescIdDesc(Long userId, Pageable page);

    Page<CheckIn> findAllByCheckOutAtIsNullOrderByCheckInAtDescIdDesc(Pageable page);

    @Query("""
            SELECT c FROM CheckIn c
            WHERE c.user.team.manager.id = :managerId AND c.checkOutAt IS NULL
            ORDER BY c.checkInAt DESC, c.id DESC
            """)
    Page<CheckIn> findTeamOnSite(@Param("managerId") Long managerId, Pageable page);

    @Query("""
            SELECT c FROM CheckIn c
            WHERE c.user.team.manager.id = :managerId
            ORDER BY c.checkInAt DESC, c.id DESC
            """)
    Page<CheckIn> findTeamVisits(@Param("managerId") Long managerId, Pageable page);

    @Query("""
            SELECT c FROM CheckIn c
            JOIN c.user u
            JOIN u.team t
            WHERE t.manager.id = :managerId
            ORDER BY c.checkInAt DESC
            """)
    List<CheckIn> findAllByTeamManagerId(@Param("managerId") Long managerId);
}
