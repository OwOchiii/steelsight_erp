package orochi.steelsight.scheduling.repository;

import orochi.steelsight.repository.BaseRepository;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ShiftRepository extends BaseRepository<Shift, Long> {

    List<Shift> findAllByUserId(Long userId);

    List<Shift> findAllByUserIdOrderByStartAtAsc(Long userId);

    List<Shift> findAllByUserIdAndStatus(Long userId, ShiftStatus status);

    List<Shift> findAllByStartAtBetween(OffsetDateTime from, OffsetDateTime to);

    @Query("""
            SELECT s FROM Shift s
            WHERE s.user.id = :userId AND s.status = orochi.steelsight.scheduling.model.ShiftStatus.SCHEDULED AND s.endAt > :now
            ORDER BY s.startAt, s.id
            """)
    List<Shift> findUpcoming(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    @Query("""
            SELECT s FROM Shift s
            WHERE s.user.id = :userId AND (s.status <> orochi.steelsight.scheduling.model.ShiftStatus.SCHEDULED OR s.endAt <= :now)
            ORDER BY s.startAt DESC, s.id DESC
            """)
    Page<Shift> findEarlier(@Param("userId") Long userId, @Param("now") OffsetDateTime now, Pageable page);

    /** Shifts starting in [from, to), so a shift at midnight belongs to one week only. */
    List<Shift> findAllByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(OffsetDateTime from, OffsetDateTime to);

    @Query("""
            SELECT s FROM Shift s
            WHERE s.user.team.manager.id = :managerId
            AND s.startAt >= :from AND s.startAt < :to
            ORDER BY s.startAt
            """)
    List<Shift> findTeamShiftsStartingIn(
            @Param("managerId") Long managerId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
            SELECT s FROM Shift s
            JOIN s.user u
            JOIN u.team t
            WHERE t.manager.id = :managerId
            """)
    List<Shift> findAllByTeamManagerId(@Param("managerId") Long managerId);

    @Query("""
            SELECT s FROM Shift s
            JOIN s.user u
            JOIN u.team t
            WHERE t.manager.id = :managerId
            AND s.startAt BETWEEN :from AND :to
            """)
    List<Shift> findAllByTeamManagerIdAndDateRange(
            @Param("managerId") Long managerId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );
}
