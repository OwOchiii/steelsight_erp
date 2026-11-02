package orochi.steelsight.identity.repository;

import orochi.steelsight.identity.model.Permission;
import orochi.steelsight.repository.BaseRepository;

import java.util.Optional;

public interface PermissionRepository extends BaseRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);
}
