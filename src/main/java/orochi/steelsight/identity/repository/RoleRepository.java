package orochi.steelsight.identity.repository;

import orochi.steelsight.identity.model.Role;
import orochi.steelsight.repository.BaseRepository;

import java.util.Optional;

public interface RoleRepository extends BaseRepository<Role, Long> {

    Optional<Role> findByRoleName(String roleName);
}
