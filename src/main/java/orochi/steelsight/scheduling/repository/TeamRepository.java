package orochi.steelsight.scheduling.repository;

import orochi.steelsight.repository.BaseRepository;
import orochi.steelsight.scheduling.model.Team;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends BaseRepository<Team, Long> {

    Optional<Team> findByName(String name);

    List<Team> findAllByManagerId(Long managerId);
}
