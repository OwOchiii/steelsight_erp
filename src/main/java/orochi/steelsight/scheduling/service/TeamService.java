package orochi.steelsight.scheduling.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.validation.Text;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class TeamService {

    private static final int MAX_LATE_AFTER_MINUTES = 240;

    private final AccessPolicy access;
    private final TeamRepository teams;
    private final AppUserRepository users;

    /** Each team has one leader, and a leader leads one team. */
    public Team create(Long actorId, String name, Long managerId) {
        access.require(actorId, Permissions.USER_MANAGE);
        Team team = new Team();
        team.setName(Text.required("A team name", name, Text.SHORT));
        team.setManager(freeLeader(managerId, null));
        try {
            return teams.saveAndFlush(team);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("A team with that name already exists");
        }
    }

    public Team changeManager(Long actorId, Long teamId, Long managerId) {
        access.require(actorId, Permissions.USER_MANAGE);
        Team team = teams.require(teamId);
        team.setManager(freeLeader(managerId, teamId));
        return teams.saveAndFlush(team);
    }

    /** Only an empty team, so nobody is left without a leader by accident. */
    public void delete(Long actorId, Long teamId) {
        access.require(actorId, Permissions.USER_MANAGE);
        Team team = teams.require(teamId);
        if (!users.findAllByTeamIdAndDeletedAtIsNull(teamId).isEmpty()) {
            throw new BusinessRuleException("Move this team's people to another team first");
        }
        // Deleted accounts keep their history but needn't keep the team.
        users.findAllByTeamId(teamId).forEach(gone -> gone.setTeam(null));
        users.flush();
        teams.delete(team);
    }

    /** Managers who can be given a team, because they don't lead one yet. */
    @Transactional(readOnly = true)
    public List<AppUser> listLeaderCandidates(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return users.findManagersWithoutATeam();
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countMembers(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return teams.countMembers().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /** The teams this user runs shifts for, or every team with USER_MANAGE. */
    @Transactional(readOnly = true)
    public List<Team> listManaged(Long actorId) {
        if (access.has(actorId, Permissions.USER_MANAGE)) {
            return teams.findAll(Sort.by("name"));
        }
        access.require(actorId, Permissions.SHIFT_UPDATE_TEAM);
        return teams.findAllByManagerId(actorId);
    }

    /** An admin can change any team; a manager only a team they run. */
    public Team setLateAfterMinutes(Long actorId, Long teamId, Integer minutes) {
        Team team = teams.require(teamId);
        if (!access.has(actorId, Permissions.USER_MANAGE)) {
            access.require(actorId, Permissions.SHIFT_UPDATE_TEAM);
            if (!team.isManagedBy(actorId)) {
                throw new AccessDeniedException("This is another manager's team");
            }
        }
        if (minutes == null || minutes < 0 || minutes > MAX_LATE_AFTER_MINUTES) {
            throw new BusinessRuleException("Enter between 0 and " + MAX_LATE_AFTER_MINUTES + " minutes");
        }
        team.setLateAfterMinutes(minutes);
        return teams.saveAndFlush(team);
    }

    private AppUser freeLeader(Long managerId, Long teamId) {
        if (managerId == null) {
            throw new BusinessRuleException("Choose a leader for the team");
        }
        AppUser leader = manager(managerId);
        boolean leadsAnother = teams.findAllByManagerId(managerId).stream().anyMatch(t -> !t.getId().equals(teamId));
        if (leadsAnother) {
            throw new BusinessRuleException("This manager already leads another team");
        }
        return leader;
    }

    private AppUser manager(Long id) {
        AppUser manager = users.require(id);
        if (!"MANAGER".equals(manager.getRole().getRoleName())) {
            throw new BusinessRuleException("A team's manager must have the MANAGER role");
        }
        return manager;
    }
}
