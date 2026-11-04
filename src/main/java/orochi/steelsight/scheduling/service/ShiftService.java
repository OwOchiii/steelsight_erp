package orochi.steelsight.scheduling.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.exception.StaleEditException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.repository.ShiftRepository;
import orochi.steelsight.scheduling.repository.ZoneRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ShiftService {

    private final AccessPolicy access;
    private final ShiftRepository shifts;
    private final ZoneRepository zones;
    private final AppUserRepository users;

    public record ShiftDetails(Long workerId, Long zoneId, OffsetDateTime startAt, OffsetDateTime endAt) {
    }

    public Shift create(Long actorId, ShiftDetails details) {
        access.requireCanAssignShift(actorId, details.workerId());
        Shift shift = new Shift();
        shift.setCreatedBy(user(actorId));
        apply(shift, details);
        return save(shift);
    }

    /** The version comes from the form the user loaded, so a stale edit fails instead of overwriting. */
    public Shift update(Long actorId, Long shiftId, ShiftDetails details, long expectedVersion) {
        access.requireCanEditShift(actorId, shiftId);
        access.requireCanAssignShift(actorId, details.workerId());
        Shift shift = scheduled(shiftId);
        StaleEditException.check("Shift", shiftId, shift.getVersion(), expectedVersion);
        shift.setUpdatedBy(user(actorId));
        apply(shift, details);
        return save(shift);
    }

    public Shift cancel(Long actorId, Long shiftId) {
        return finish(actorId, shiftId, ShiftStatus.CANCELLED);
    }

    public Shift complete(Long actorId, Long shiftId) {
        return finish(actorId, shiftId, ShiftStatus.COMPLETED);
    }

    @Transactional(readOnly = true)
    public List<Shift> listOwn(Long actorId) {
        access.require(actorId, Permissions.SHIFT_READ_OWN);
        return shifts.findAllByUserId(actorId);
    }

    @Transactional(readOnly = true)
    public List<Shift> listTeam(Long actorId, OffsetDateTime from, OffsetDateTime to) {
        access.require(actorId, Permissions.SHIFT_READ_TEAM);
        return shifts.findAllByTeamManagerIdAndDateRange(actorId, from, to);
    }

    @Transactional(readOnly = true)
    public List<Shift> listAll(Long actorId, OffsetDateTime from, OffsetDateTime to) {
        access.require(actorId, Permissions.SHIFT_READ_ALL);
        return shifts.findAllByStartAtBetween(from, to);
    }

    private Shift finish(Long actorId, Long shiftId, ShiftStatus status) {
        access.requireCanEditShift(actorId, shiftId);
        Shift shift = scheduled(shiftId);
        shift.setStatus(status);
        shift.setUpdatedBy(user(actorId));
        return shift;
    }

    private Shift scheduled(Long shiftId) {
        Shift shift = shifts.findById(shiftId).orElseThrow(() -> new NotFoundException("Shift", shiftId));
        if (shift.getStatus() != ShiftStatus.SCHEDULED) {
            throw new BusinessRuleException("Only a scheduled shift can be changed");
        }
        return shift;
    }

    private void apply(Shift shift, ShiftDetails details) {
        if (!details.endAt().isAfter(details.startAt())) {
            throw new BusinessRuleException("A shift must end after it starts");
        }
        shift.setUser(user(details.workerId()));
        shift.setZone(zones.findById(details.zoneId())
                .orElseThrow(() -> new NotFoundException("Zone", details.zoneId())));
        shift.setStartAt(details.startAt());
        shift.setEndAt(details.endAt());
    }

    private Shift save(Shift shift) {
        try {
            return shifts.saveAndFlush(shift);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("This worker already has a shift at that time");
        }
    }

    private AppUser user(Long id) {
        return users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
