package orochi.steelsight.scheduling.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.Zone;
import orochi.steelsight.scheduling.model.ZoneKind;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.scheduling.repository.ZoneRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Monitoring zones (SRS 3.2.23, CR-03): the Admin gives each zone the name every alert uses, and each
 * work area its team, so HSE picks a violator from that team first (the team's decision on zones).
 * Zones and cameras themselves come from the setup script.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ZoneService {

    private final AccessPolicy access;
    private final ZoneRepository zones;
    private final TeamRepository teams;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Zone> list(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return zones.findAllByOrderByNameAsc();
    }

    /** Camera codes of each zone, in code order. */
    @Transactional(readOnly = true)
    public Map<Long, String> cameras(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return jdbc.query("SELECT zone_id, camera_code FROM camera ORDER BY camera_code", (row, n) ->
                        Map.entry(row.getLong(1), row.getString(2))).stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.joining(", "))));
    }

    /** BR-55, MSG48: 2 to 50 characters and unique. Old violations show the new name. */
    public Zone rename(Long actorId, Long zoneId, String name) {
        access.require(actorId, Permissions.USER_MANAGE);
        String clean = name == null ? "" : name.strip();
        if (clean.length() < 2 || clean.length() > 50) {
            throw new BusinessRuleException("A zone name has 2 to 50 characters");
        }
        Zone zone = zones.require(zoneId);
        boolean taken = zones.findAll().stream().anyMatch(other -> !other.getId().equals(zoneId) && other.getName().equalsIgnoreCase(clean));
        if (taken) {
            throw new BusinessRuleException("A zone with this name already exists.");
        }
        zone.setName(clean);
        try {
            return zones.saveAndFlush(zone);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("A zone with this name already exists.");
        }
    }

    /** Only a work area belongs to a team; the gate zones belong to nobody. */
    public Zone assignTeam(Long actorId, Long zoneId, Long teamId) {
        access.require(actorId, Permissions.USER_MANAGE);
        Zone zone = zones.require(zoneId);
        if (teamId != null && zone.getKind() != ZoneKind.WORK_AREA) {
            throw new BusinessRuleException("Only a work area can belong to a team");
        }
        zone.setTeam(teamId == null ? null : teams.require(teamId));
        return zones.saveAndFlush(zone);
    }
}
