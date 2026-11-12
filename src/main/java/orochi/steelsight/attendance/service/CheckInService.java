package orochi.steelsight.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.CheckInMethod;
import orochi.steelsight.attendance.model.CheckInCorrection;
import orochi.steelsight.attendance.repository.CheckInCorrectionRepository;
import orochi.steelsight.attendance.repository.CheckInRepository;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.Zone;
import orochi.steelsight.scheduling.repository.ShiftRepository;
import orochi.steelsight.scheduling.repository.ZoneRepository;
import orochi.steelsight.validation.Text;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

/** Face check-ins come from the detection service; this covers viewing them and manual entries. */
@Service
@Transactional
@RequiredArgsConstructor
public class CheckInService {

    private final AccessPolicy access;
    private final CheckInRepository checkIns;
    private final CheckInCorrectionRepository corrections;
    private final AppUserRepository users;
    private final ShiftRepository shifts;
    private final ZoneRepository zones;
    private final AttendanceRules rules;

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

    /** Who is on site now, as far as this user may see: their teams, or everyone with CHECKIN_READ_ALL. */
    @Transactional(readOnly = true)
    public Page<VisitStatus> listOnSiteInScope(Long actorId, Pageable page) {
        Page<CheckIn> visits = access.has(actorId, Permissions.CHECKIN_READ_ALL)
                ? checkIns.findAllByCheckOutAtIsNullOrderByCheckInAtDescIdDesc(page)
                : teamVisits(actorId, () -> checkIns.findTeamOnSite(actorId, page));
        return visits.map(rules::assess);
    }

    @Transactional(readOnly = true)
    public Page<VisitStatus> listRecentInScope(Long actorId, Pageable page) {
        Page<CheckIn> visits = access.has(actorId, Permissions.CHECKIN_READ_ALL)
                ? checkIns.findAllByOrderByCheckInAtDescIdDesc(page)
                : teamVisits(actorId, () -> checkIns.findTeamVisits(actorId, page));
        return visits.map(rules::assess);
    }

    /** One worker's latest visits, for anyone who can look at every worker. */
    @Transactional(readOnly = true)
    public Page<VisitStatus> listRecentOf(Long actorId, Long workerId, Pageable page) {
        access.require(actorId, Permissions.USER_READ_ALL);
        return checkIns.findAllByUserIdOrderByCheckInAtDescIdDesc(workerId, page).map(rules::assess);
    }

    /** Visits that began in this period, as far as this user may see. */
    @Transactional(readOnly = true)
    public List<VisitStatus> listStartedInScope(Long actorId, OffsetDateTime from, OffsetDateTime to) {
        List<CheckIn> visits;
        if (access.has(actorId, Permissions.CHECKIN_READ_ALL)) {
            visits = checkIns.findAllByCheckInAtGreaterThanEqualAndCheckInAtLessThan(from, to);
        } else {
            access.require(actorId, Permissions.CHECKIN_READ_TEAM);
            visits = checkIns.findTeamStartedIn(actorId, from, to);
        }
        return visits.stream().map(rules::assess).toList();
    }

    private Page<CheckIn> teamVisits(Long actorId, Supplier<Page<CheckIn>> query) {
        access.require(actorId, Permissions.CHECKIN_READ_TEAM);
        return query.get();
    }

    @Transactional(readOnly = true)
    public CheckIn getForEdit(Long actorId, Long checkInId) {
        access.require(actorId, Permissions.CHECKIN_EDIT);
        return checkIns.require(checkInId);
    }

    /** Newest first. The same people who can correct a visit can see how it was corrected. */
    @Transactional(readOnly = true)
    public List<CheckInCorrection> listCorrections(Long actorId, Long checkInId) {
        access.require(actorId, Permissions.CHECKIN_EDIT);
        return corrections.findAllByCheckInIdOrderByCorrectedAtDescIdDesc(checkInId);
    }

    @Transactional(readOnly = true)
    public List<Zone> listZones(Long actorId) {
        access.require(actorId, Permissions.CHECKIN_EDIT);
        return zones.findAllByOrderByNameAsc();
    }

    public CheckIn recordManual(Long actorId, ManualCheckIn details) {
        access.require(actorId, Permissions.CHECKIN_EDIT);
        if (details.workerId() == null) {
            throw new BusinessRuleException("Choose a worker");
        }
        requireOrder(details.checkInAt(), details.checkOutAt());

        CheckIn visit = new CheckIn();
        visit.setMethod(CheckInMethod.MANUAL);
        visit.setRecordedBy(users.require(actorId));
        visit.setUser(users.require(details.workerId()));
        visit.setCheckInAt(details.checkInAt());
        visit.setCheckOutAt(details.checkOutAt());
        visit.setNote(Text.optional("A note", details.note(), Text.LONG));
        if (details.zoneId() != null) {
            visit.setZone(zones.require(details.zoneId()));
        }
        if (details.shiftId() != null) {
            Shift shift = shifts.require(details.shiftId());
            if (!shift.getUser().getId().equals(details.workerId())) {
                throw new BusinessRuleException("That shift belongs to another worker");
            }
            visit.setShift(shift);
        }
        return save(visit);
    }

    /** Corrects the times on any visit; a stale form fails. A corrected check-out is no longer an estimate. */
    public CheckIn correct(Long actorId, Long checkInId, OffsetDateTime checkInAt, OffsetDateTime checkOutAt,
                           String note, long expectedVersion) {
        access.require(actorId, Permissions.CHECKIN_EDIT);
        requireOrder(checkInAt, checkOutAt);
        CheckIn visit = checkIns.require(checkInId);
        visit.requireVersion(expectedVersion);
        AppUser corrector = users.require(actorId);
        String newNote = Text.optional("A note", note, Text.LONG);
        CheckInCorrection correction = CheckInCorrection.of(visit, corrector, checkInAt, checkOutAt, newNote);
        visit.setCheckInAt(checkInAt);
        visit.setCheckOutAt(checkOutAt);
        visit.setAutoClosed(false);
        visit.setRecordedBy(corrector);
        visit.setNote(newNote);
        CheckIn saved = save(visit);
        corrections.save(correction);
        return saved;
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

}
