package orochi.steelsight.identity.repository;

import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.repository.SoftDeletableRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends SoftDeletableRepository<AppUser, Long> {

    Optional<AppUser> findByEmployeeCodeAndDeletedAtIsNull(String employeeCode);

    Optional<AppUser> findByEmailAndDeletedAtIsNull(String email);

    List<AppUser> findAllByTeamIdAndDeletedAtIsNull(Long teamId);

    List<AppUser> findAllByRoleIdAndDeletedAtIsNull(Long roleId);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmail(String email);
}
