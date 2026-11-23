package orochi.steelsight.identity.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.repository.SoftDeletableRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends SoftDeletableRepository<AppUser, Long> {

    Optional<AppUser> findByEmployeeCodeAndDeletedAtIsNull(String employeeCode);

    /**
     * SELECT ... FOR UPDATE on a live user: holds the row until the transaction ends. A caller that
     * waited behind a soft delete finds no row, because Postgres checks the WHERE again after the wait.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<AppUser> findLiveByIdForUpdate(@Param("id") Long id);

    /**
     * Like require(id), and locked for this transaction, so face-photo changes and the soft delete
     * for one person run one after another, even when they change no column of app_user.
     */
    default AppUser requireForUpdate(Long id) {
        return findLiveByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Record", id));
    }

    Optional<AppUser> findByEmailAndDeletedAtIsNull(String email);

    List<AppUser> findAllByTeamIdAndDeletedAtIsNull(Long teamId);

    List<AppUser> findAllByRoleIdAndDeletedAtIsNull(Long roleId);

    @Query("SELECT u FROM AppUser u WHERE u.deletedAt IS NULL ORDER BY u.fullName, u.id")
    Page<AppUser> findAllLiveByName(Pageable page);

    @Query("SELECT u FROM AppUser u WHERE u.isActive = true AND u.deletedAt IS NULL ORDER BY u.fullName")
    List<AppUser> findAllActiveByName();

    @Query("SELECT u FROM AppUser u WHERE u.role.id = :roleId AND u.isActive = true AND u.deletedAt IS NULL ORDER BY u.fullName")
    List<AppUser> findActiveByRole(@Param("roleId") Long roleId);

    /** Active managers who don't lead a team yet, so each team leader leads one team. */
    @Query("""
            SELECT u FROM AppUser u
            WHERE u.role.roleName = 'MANAGER' AND u.isActive = true AND u.deletedAt IS NULL
              AND NOT EXISTS (SELECT t FROM Team t WHERE t.manager = u)
            ORDER BY u.fullName, u.id
            """)
    List<AppUser> findManagersWithoutATeam();

    List<AppUser> findAllByTeamId(Long teamId);

    /** People on the teams this manager runs, without the manager, who can't manage their own account. */
    @Query("""
            SELECT u FROM AppUser u
            WHERE u.team.manager.id = :managerId AND u.id <> :managerId AND u.deletedAt IS NULL
            ORDER BY u.fullName, u.id
            """)
    List<AppUser> findTeamMembers(@Param("managerId") Long managerId);

    @Query("""
            SELECT u FROM AppUser u
            WHERE u.team.manager.id = :managerId AND u.id <> :managerId AND u.deletedAt IS NULL
            ORDER BY u.fullName, u.id
            """)
    Page<AppUser> findTeamMembers(@Param("managerId") Long managerId, Pageable page);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCodeIgnoreCase(String employeeCode);

    boolean existsByEmail(String email);
}
