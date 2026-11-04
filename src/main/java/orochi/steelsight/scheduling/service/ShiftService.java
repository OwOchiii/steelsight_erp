package orochi.steelsight.scheduling.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.model.Zone;
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
        requireComplete(details);
        access.requireCanAssignShift(actorId, details.workerId());
        Shift shift = new Shift();
        shift.setCreatedBy(users.require(actorId));
        apply(shift, details);
        return save(shift);
    }

    /** The version comes from the form the user loaded, so a stale edit fails instead of overwriting. */
    public Shift update(Long actorId, Long shiftId, ShiftDetails details, long expectedVersion) {
        requireComplete(details);
        access.requireCanEditShift(actorId, shiftId);
        access.requireCanAssignShift(actorId, details.workerId());
        Shift shift = scheduled(shiftId);
        shift.requireVersion(expectedVersion);
        shift.setUpdatedBy(users.require(actorId));
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
        return shifts.findAllByUserIdOrderByStartAtAsc(actorId);
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

    @Transactional(readOnly = true)
    public List<Shift> listOwnUpcoming(Long actorId, OffsetDateTime now) {
        access.require(actorId, Permissions.SHIFT_READ_OWN);
        return shifts.findUpcoming(actorId, now);
    }

    @Transactional(readOnly = true)
    public Page<Shift> listOwnEarlier(Long actorId, OffsetDateTime now, Pageable page) {
        access.require(actorId, Permissions.SHIFT_READ_OWN);
        return shifts.findEarlier(actorId, now, page);
    }

    /** The shifts this user manages: their teams', or everyone's with SHIFT_READ_ALL. */
    @Transactional(readOnly = true)
    public List<Shift> listManaged(Long actorId, OffsetDateTime from, OffsetDateTime to) {
        if (access.has(actorId, Permissions.SHIFT_READ_ALL)) {
            return shifts.findAllByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(from, to);
        }
        access.require(actorId, Permissions.SHIFT_READ_TEAM);
        return shifts.findTeamShiftsStartingIn(actorId, from, to);
    }

    @Transactional(readOnly = true)
    public Shift getForEdit(Long actorId, Long shiftId) {
        access.requireCanEditShift(actorId, shiftId);
        return shifts.require(shiftId);
    }

    @Transactional(readOnly = true)
    public List<Zone> listZones(Long actorId) {
        access.requireAny(actorId, Permissions.SHIFT_UPDATE_TEAM, Permissions.SHIFT_UPDATE_ALL);
        return zones.findAllByOrderByNameAsc();
    }

    private void requireComplete(ShiftDetails details) {
        if (details.workerId() == null) {
            throw new BusinessRuleException("Choose a worker");
        }
        if (details.zoneId() == null) {
            throw new BusinessRuleException("Choose a zone");
        }
        if (details.startAt() == null || details.endAt() == null) {
            throw new BusinessRuleException("Enter when the shift starts and ends");
        }
    }

    private Shift finish(Long actorId, Long shiftId, ShiftStatus status) {
        access.requireCanEditShift(actorId, shiftId);
        Shift shift = scheduled(shiftId);
        shift.setStatus(status);
        shift.setUpdatedBy(users.require(actorId));
        return shifts.saveAndFlush(shift);
    }

    private Shift scheduled(Long shiftId) {
        Shift shift = shifts.require(shiftId);
        if (!shift.isScheduled()) {
            throw new BusinessRuleException("Only a scheduled shift can be changed");
        }
        return shift;
    }

    private void apply(Shift shift, ShiftDetails details) {
        if (!details.endAt().isAfter(details.startAt())) {
            throw new BusinessRuleException("A shift must end after it starts");
        }
        shift.setUser(users.require(details.workerId()));
        shift.setZone(zones.require(details.zoneId()));
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

}
