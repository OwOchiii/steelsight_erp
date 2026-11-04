package orochi.steelsight.identity.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.repository.SoftDeletableRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends SoftDeletableRepository<AppUser, Long> {

    Optional<AppUser> findByEmployeeCodeAndDeletedAtIsNull(String employeeCode);

    Optional<AppUser> findByEmailAndDeletedAtIsNull(String email);

    List<AppUser> findAllByTeamIdAndDeletedAtIsNull(Long teamId);

    List<AppUser> findAllByRoleIdAndDeletedAtIsNull(Long roleId);

    @Query("SELECT u FROM AppUser u WHERE u.deletedAt IS NULL ORDER BY u.fullName, u.id")
    Page<AppUser> findAllLiveByName(Pageable page);

    @Query("SELECT u FROM AppUser u WHERE u.isActive = true AND u.deletedAt IS NULL ORDER BY u.fullName")
    List<AppUser> findAllActiveByName();

    @Query("SELECT u FROM AppUser u WHERE u.role.id = :roleId AND u.isActive = true AND u.deletedAt IS NULL ORDER BY u.fullName")
    List<AppUser> findActiveByRole(@Param("roleId") Long roleId);

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

    boolean existsByEmail(String email);
}
