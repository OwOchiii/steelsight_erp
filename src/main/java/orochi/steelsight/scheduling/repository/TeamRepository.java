package orochi.steelsight.scheduling.repository;

import orochi.steelsight.repository.BaseRepository;
import orochi.steelsight.scheduling.model.Team;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends BaseRepository<Team, Long> {

    Optional<Team> findByName(String name);

    List<Team> findAllByManagerId(Long managerId);

    /** How many people, not counting deleted ones, are on each team that has anyone. */
    @Query("SELECT u.team.id, count(u) FROM AppUser u WHERE u.team IS NOT NULL AND u.deletedAt IS NULL GROUP BY u.team.id")
    List<Object[]> countMembers();
}
