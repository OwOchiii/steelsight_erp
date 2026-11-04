package orochi.steelsight.scheduling.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.repository.TeamRepository;

@Service
@Transactional
@RequiredArgsConstructor
public class TeamService {

    private final AccessPolicy access;
    private final TeamRepository teams;
    private final AppUserRepository users;

    public Team create(Long actorId, String name, Long managerId) {
        access.require(actorId, Permissions.USER_MANAGE);
        Team team = new Team();
        team.setName(name);
        team.setManager(manager(managerId));
        try {
            return teams.saveAndFlush(team);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("A team with that name already exists");
        }
    }

    public Team changeManager(Long actorId, Long teamId, Long managerId) {
        access.require(actorId, Permissions.USER_MANAGE);
        Team team = teams.findById(teamId).orElseThrow(() -> new NotFoundException("Team", teamId));
        team.setManager(manager(managerId));
        return team;
    }

    private AppUser manager(Long id) {
        AppUser manager = users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("User", id));
        if (!"MANAGER".equals(manager.getRole().getRoleName())) {
            throw new BusinessRuleException("A team's manager must have the MANAGER role");
        }
        return manager;
    }
}
