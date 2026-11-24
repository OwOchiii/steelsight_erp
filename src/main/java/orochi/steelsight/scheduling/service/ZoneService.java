package orochi.steelsight.scheduling.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.Camera;
import orochi.steelsight.ppe.model.CameraRole;
import orochi.steelsight.ppe.repository.CameraRepository;
import orochi.steelsight.scheduling.model.Zone;
import orochi.steelsight.scheduling.model.ZoneKind;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.scheduling.repository.ZoneRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Monitoring zones (SRS 3.2.23, CR-03): the Admin gives each zone the name every alert uses, and each
 * work area its team, so HSE picks a violator from that team first (the team's decision on zones).
 * The Admin also adds zones and links cameras to them by the code the detection app sends.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ZoneService {

    private final AccessPolicy access;
    private final ZoneRepository zones;
    private final TeamRepository teams;
    private final CameraRepository cameraRepository;
    private final JdbcTemplate jdbc;

    /** What the detection app may send as a camera code, such as CAM-B2. */
    private static final Pattern CAMERA_CODE = Pattern.compile("[A-Za-z0-9._-]{2,50}");

    @Transactional(readOnly = true)
    public List<Zone> list(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return zones.findAllByOrderByNameAsc();
    }

    /** The cameras of each zone, in code order. */
    @Transactional(readOnly = true)
    public Map<Long, List<Camera>> cameras(Long actorId) {
        access.require(actorId, Permissions.USER_MANAGE);
        return cameraRepository.findAll().stream()
                .sorted(Comparator.comparing(Camera::getCameraCode))
                .collect(Collectors.groupingBy(camera -> camera.getZone().getId()));
    }

    /**
     * Links the camera with this code to a zone: a new code adds the camera, a known one moves it.
     * The code is the one entered for the camera in the detection app. A gate attendance zone's cameras
     * record check-ins (BR-27); every other camera records PPE violations. Records already made keep
     * the zone they were made in.
     */
    public Camera linkCamera(Long actorId, String code, Long zoneId) {
        access.require(actorId, Permissions.USER_MANAGE);
        String clean = code == null ? "" : code.strip();
        if (!CAMERA_CODE.matcher(clean).matches()) {
            throw new BusinessRuleException("A camera code has 2 to 50 letters, digits, dots, dashes or underscores");
        }
        if (zoneId == null) {
            throw new BusinessRuleException("Choose the zone the camera watches");
        }
        Zone zone = zones.require(zoneId);
        Camera camera = cameraRepository.findByCameraCode(clean).orElseGet(Camera::new);
        camera.setCameraCode(clean);
        camera.setZone(zone);
        camera.setRole(zone.getKind() == ZoneKind.GATE_ATTENDANCE ? CameraRole.ATTENDANCE : CameraRole.PPE);
        try {
            return cameraRepository.saveAndFlush(camera);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("A camera with this code already exists.");
        }
    }

    /** Removes a camera that never recorded anything; one with records can only move to another zone. */
    public void removeCamera(Long actorId, Long cameraId) {
        access.require(actorId, Permissions.USER_MANAGE);
        Camera camera = cameraRepository.require(cameraId);
        Integer records = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM violation WHERE camera_id = ?) + (SELECT count(*) FROM check_in WHERE camera_id = ?)""",
                Integer.class, cameraId, cameraId);
        if (records != null && records > 0) {
            throw new BusinessRuleException("This camera has records, so it can't be removed. Link it to another zone instead.");
        }
        cameraRepository.delete(camera);
        cameraRepository.flush();
    }

    /** The Admin adds a zone. Only a work area can have a team; the gate zones belong to nobody. */
    public Zone create(Long actorId, String name, ZoneKind kind, Long teamId) {
        access.require(actorId, Permissions.USER_MANAGE);
        if (kind == null) {
            throw new BusinessRuleException("Choose what the zone is for");
        }
        if (teamId != null && kind != ZoneKind.WORK_AREA) {
            throw new BusinessRuleException("Only a work area can belong to a team");
        }
        Zone zone = new Zone();
        zone.setName(availableName(null, name));
        zone.setKind(kind);
        zone.setTeam(teamId == null ? null : teams.require(teamId));
        return saveUnique(zone);
    }

    /** BR-55, MSG48: 2 to 50 characters and unique. Old violations show the new name. */
    public Zone rename(Long actorId, Long zoneId, String name) {
        access.require(actorId, Permissions.USER_MANAGE);
        Zone zone = zones.require(zoneId);
        zone.setName(availableName(zoneId, name));
        return saveUnique(zone);
    }

    /** The name without outer spaces, if it has 2 to 50 characters and no other zone uses it. */
    private String availableName(Long zoneId, String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.length() < 2 || clean.length() > 50) {
            throw new BusinessRuleException("A zone name has 2 to 50 characters");
        }
        boolean taken = zones.findAll().stream().anyMatch(other -> !other.getId().equals(zoneId) && other.getName().equalsIgnoreCase(clean));
        if (taken) {
            throw new BusinessRuleException("A zone with this name already exists.");
        }
        return clean;
    }

    /** The unique index still decides when two people save the same name at once. */
    private Zone saveUnique(Zone zone) {
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
