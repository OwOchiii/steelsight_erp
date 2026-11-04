package orochi.steelsight.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.CheckInMethod;
import orochi.steelsight.attendance.repository.CheckInRepository;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.NotFoundException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.repository.ShiftRepository;
import orochi.steelsight.scheduling.repository.ZoneRepository;

import java.time.OffsetDateTime;
import java.util.List;

/** Face check-ins come from the detection service; this covers viewing them and manual entries. */
@Service
@Transactional
@RequiredArgsConstructor
public class CheckInService {

    private final AccessPolicy access;
    private final CheckInRepository checkIns;
    private final AppUserRepository users;
    private final ShiftRepository shifts;
    private final ZoneRepository zones;

    public record ManualCheckIn(Long workerId, Long shiftId, Long zoneId, OffsetDateTime checkInAt,
                                OffsetDateTime checkOutAt, String note) {
    }

    @Transactional(readOnly = true)
    public List<CheckIn> listOwn(Long actorId) {
        access.require(actorId, Permissions.CHECKIN_READ_OWN);
        return checkIns.findAllByUserIdOrderByCheckInAtDesc(actorId);
    }

    @Transactional(readOnly = true)
    public List<CheckIn> listTeam(Long actorId) {
        access.require(actorId, Permissions.CHECKIN_READ_TEAM);
        return checkIns.findAllByTeamManagerId(actorId);
    }

    @Transactional(readOnly = true)
    public List<CheckIn> listOnSite(Long actorId) {
        access.require(actorId, Permissions.CHECKIN_READ_ALL);
        return checkIns.findAllByCheckOutAtIsNull();
    }

    public CheckIn recordManual(Long actorId, ManualCheckIn details) {
        access.require(actorId, Permissions.CHECKIN_EDIT);
        requireOrder(details.checkInAt(), details.checkOutAt());

        CheckIn visit = new CheckIn();
        visit.setMethod(CheckInMethod.MANUAL);
        visit.setRecordedBy(user(actorId));
        visit.setUser(user(details.workerId()));
        visit.setCheckInAt(details.checkInAt());
        visit.setCheckOutAt(details.checkOutAt());
        visit.setNote(details.note());
        if (details.zoneId() != null) {
            visit.setZone(zones.findById(details.zoneId())
                    .orElseThrow(() -> new NotFoundException("Zone", details.zoneId())));
        }
        if (details.shiftId() != null) {
            Shift shift = shifts.findById(details.shiftId())
                    .orElseThrow(() -> new NotFoundException("Shift", details.shiftId()));
            if (!shift.getUser().getId().equals(details.workerId())) {
                throw new BusinessRuleException("That shift belongs to another worker");
            }
            visit.setShift(shift);
        }
        return save(visit);
    }

    /** Corrects the times on any visit. A corrected check-out is no longer an estimate. */
    public CheckIn correct(Long actorId, Long checkInId, OffsetDateTime checkInAt, OffsetDateTime checkOutAt, String note) {
        access.require(actorId, Permissions.CHECKIN_EDIT);
        requireOrder(checkInAt, checkOutAt);
        CheckIn visit = checkIns.findById(checkInId).orElseThrow(() -> new NotFoundException("Check-in", checkInId));
        visit.setCheckInAt(checkInAt);
        visit.setCheckOutAt(checkOutAt);
        visit.setAutoClosed(false);
        visit.setRecordedBy(user(actorId));
        visit.setNote(note);
        return save(visit);
    }

    private void requireOrder(OffsetDateTime checkInAt, OffsetDateTime checkOutAt) {
        if (checkInAt == null) {
            throw new BusinessRuleException("A check-in time is required");
        }
        if (checkOutAt != null && !checkOutAt.isAfter(checkInAt)) {
            throw new BusinessRuleException("Check-out must be after check-in");
        }
    }

    private CheckIn save(CheckIn visit) {
        try {
            return checkIns.saveAndFlush(visit);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("This worker is already checked in");
        }
    }

    private AppUser user(Long id) {
        return users.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("User", id));
    }
}
