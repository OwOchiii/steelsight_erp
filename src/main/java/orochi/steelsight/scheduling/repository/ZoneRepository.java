package orochi.steelsight.scheduling.repository;

import orochi.steelsight.repository.BaseRepository;
import orochi.steelsight.scheduling.model.Zone;

import java.util.Optional;

public interface ZoneRepository extends BaseRepository<Zone, Long> {

    Optional<Zone> findByName(String name);
}
