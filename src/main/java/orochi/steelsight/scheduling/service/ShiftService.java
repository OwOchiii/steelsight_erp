package orochi.steelsight.scheduling.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.calendar.MonthLocks;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.notification.service.Notices;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftKind;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.model.Zone;
import orochi.steelsight.scheduling.repository.ShiftRepository;
import orochi.steelsight.scheduling.repository.ZoneRepository;
import orochi.steelsight.web.SiteTime;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Planning shifts (SRS UC-05, 3.2.9, 3.2.10, BR-21 to BR-24, BR-37):
 * <ul>
 *   <li>a Manager plans only REGULAR shifts of his own team, and only for weeks whose schedule hasn't
 *       been sent yet (before Saturday 18:00 of the week before); after that only the Admin changes them;</li>
 *   <li>only the Admin creates OVERTIME shifts, always with a reason of 10 to 500 characters;</li>
 *   <li>a shift lasts 30 minutes to 12 hours and starts and ends on the same day;</li>
 *   <li>a shift that has started can't be added, edited or cancelled, except an OVERTIME shift the
 *       Admin adds to make pending overtime valid within its 48 hours.</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ShiftService {

    public static final LocalTime MORNING_START = LocalTime.of(7, 0);
    public static final LocalTime MORNING_END = LocalTime.of(11, 30);
    public static final LocalTime AFTERNOON_START = LocalTime.of(13, 30);
    public static final LocalTime AFTERNOON_END = LocalTime.of(17, 30);
    public static final LocalTime EVENING_START = LocalTime.of(18, 0);
    public static final LocalTime EVENING_END = LocalTime.of(22, 0);
    static final Duration SHORTEST = Duration.ofMinutes(30);
    static final Duration LONGEST = Duration.ofHours(12);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final AccessPolicy access;
    private final ShiftRepository shifts;
    private final ZoneRepository zones;
    private final AppUserRepository users;
    private final Notices notices;
    private final SiteTime siteTime;
    private final MonthLocks locks;
    private final JdbcTemplate jdbc;

    /** pendingOvertimeId: the pending overtime this OVERTIME shift makes valid (SRS 3.2.15). */
    public record ShiftDetails(Long workerId, Long zoneId, OffsetDateTime startAt, OffsetDateTime endAt,
                               ShiftKind kind, String reason, Long pendingOvertimeId) {

        public ShiftDetails(Long workerId, Long zoneId, OffsetDateTime startAt, OffsetDateTime endAt) {
            this(workerId, zoneId, startAt, endAt, ShiftKind.REGULAR, null, null);
        }
    }

    /** The result of Copy last week: how many were copied, and the copies skipped with why. */
    public record CopyResult(int copied, List<String> skipped) {
    }

    public Shift create(Long actorId, ShiftDetails details) {
        requireComplete(details);
        access.requireCanAssignShift(actorId, details.workerId());
        ShiftKind kind = details.kind() == null ? ShiftKind.REGULAR : details.kind();
        boolean fromPending = details.pendingOvertimeId() != null;
        requireAllowed(actorId, kind, details.startAt(), fromPending);
        Shift shift = new Shift();
        shift.setCreatedBy(users.require(actorId));
        apply(shift, details, kind);
        Shift saved = save(shift);
        if (fromPending) {
            makeOvertimeValid(details.pendingOvertimeId(), saved);
        }
        notices.shiftAdded(saved);
        return saved;
    }

    /** The version comes from the form the user loaded, so a stale edit fails instead of overwriting. */
    public Shift update(Long actorId, Long shiftId, ShiftDetails details, long expectedVersion) {
        requireComplete(details);
        access.requireCanEditShift(actorId, shiftId);
        access.requireCanAssignShift(actorId, details.workerId());
        Shift shift = scheduled(shiftId);
        shift.requireVersion(expectedVersion);
        ShiftKind kind = details.kind() == null ? shift.getKind() : details.kind();
        requireAllowed(actorId, shift.getKind(), shift.getStartAt(), false);
        requireAllowed(actorId, kind, details.startAt(), false);
        Notices.ShiftBefore before = notices.before(shift);
        shift.setUpdatedBy(users.require(actorId));
        apply(shift, details, kind);
        Shift saved = save(shift);
        notices.shiftChanged(before, saved);
        return saved;
    }

    /** Only before the shift starts (MSG28). */
    public Shift cancel(Long actorId, Long shiftId) {
        access.requireCanEditShift(actorId, shiftId);
        Shift shift = scheduled(shiftId);
        requireAllowed(actorId, shift.getKind(), shift.getStartAt(), false);
        shift.setStatus(ShiftStatus.CANCELLED);
        shift.setUpdatedBy(users.require(actorId));
        Shift saved = shifts.saveAndFlush(shift);
        notices.shiftCancelled(saved);
        return saved;
    }

    /**
     * Copies the REGULAR shifts of the week before into this week, for the people the actor plans.
     * Copies that would overlap, start in the past or fall in a sent week are skipped and listed.
     */
    public CopyResult copyLastWeek(Long actorId, LocalDate anyDayOfWeek) {
        access.requireAny(actorId, Permissions.SHIFT_UPDATE_TEAM, Permissions.SHIFT_UPDATE_ALL);
        LocalDate monday = siteTime.mondayOf(anyDayOfWeek);
        List<Shift> source = listManaged(actorId, siteTime.startOf(monday.minusWeeks(1)), siteTime.startOf(monday)).stream()
                .filter(s -> s.getStatus() != ShiftStatus.CANCELLED && s.getKind() == ShiftKind.REGULAR)
                .toList();
        int copied = 0;
        List<String> skipped = new ArrayList<>();
        for (Shift old : source) {
            ShiftDetails copy = new ShiftDetails(old.getUser().getId(), old.getZone().getId(),
                    old.getStartAt().plusWeeks(1), old.getEndAt().plusWeeks(1), ShiftKind.REGULAR, null, null);
            try {
                create(actorId, copy);
                copied++;
            } catch (BusinessRuleException | AccessDeniedException e) {
                skipped.add(old.getUser().getFullName() + " " + WHEN.format(siteTime.toLocal(copy.startAt())) + ": " + e.getMessage());
            }
        }
        return new CopyResult(copied, skipped);
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

    /** The actor's own shifts starting in [from, to). */
    @Transactional(readOnly = true)
    public List<Shift> listOwnInRange(Long actorId, OffsetDateTime from, OffsetDateTime to) {
        access.require(actorId, Permissions.SHIFT_READ_OWN);
        return shifts.findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(actorId, from, to);
    }

    /** One of the actor's own shifts. */
    @Transactional(readOnly = true)
    public Shift getOwn(Long actorId, Long shiftId) {
        access.require(actorId, Permissions.SHIFT_READ_OWN);
        Shift shift = shifts.require(shiftId);
        if (!shift.getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("This is someone else's shift");
        }
        return shift;
    }

    @Transactional(readOnly = true)
    public List<Shift> listUpcomingOf(Long actorId, Long workerId, OffsetDateTime now) {
        access.require(actorId, Permissions.USER_READ_ALL);
        return shifts.findUpcoming(workerId, now);
    }

    /** The shifts this user manages: their team's, or everyone's with SHIFT_READ_ALL. */
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

    /** When the schedule of the week of this day is sent: Saturday 18:00 of the week before (BR-18). */
    public OffsetDateTime publishedAt(LocalDate day) {
        LocalDate monday = siteTime.mondayOf(day);
        return siteTime.toInstant(monday.minusDays(2).atTime(18, 0));
    }

    /** Whether a Manager may still plan the week of this day (BR-23). */
    public boolean isOpenForManagers(LocalDate day) {
        return OffsetDateTime.now().isBefore(publishedAt(day));
    }

    // --- rules ---------------------------------------------------------------------------------------

    private void requireAllowed(Long actorId, ShiftKind kind, OffsetDateTime startAt, boolean fromPendingOvertime) {
        boolean admin = access.has(actorId, Permissions.SHIFT_UPDATE_ALL);
        if (kind == ShiftKind.OVERTIME && !admin) {
            throw new BusinessRuleException("Only the Admin plans overtime shifts");
        }
        if (!admin && !isOpenForManagers(siteTime.dateOf(startAt))) {
            throw new BusinessRuleException("The schedule of this week has been sent. Only the Admin can change it now.");
        }
        if (!startAt.isAfter(OffsetDateTime.now()) && !(fromPendingOvertime && admin && kind == ShiftKind.OVERTIME)) {
            throw new BusinessRuleException("This shift has already started and cannot be changed.");
        }
        locks.requireOpen(startAt);
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

    private Shift scheduled(Long shiftId) {
        Shift shift = shifts.require(shiftId);
        if (!shift.isScheduled()) {
            throw new BusinessRuleException("Only a scheduled shift can be changed");
        }
        return shift;
    }

    /** BR-21: 30 minutes to 12 hours, the same day; OVERTIME needs a reason (MSG26, MSG29). */
    private void apply(Shift shift, ShiftDetails details, ShiftKind kind) {
        Duration length = Duration.between(details.startAt(), details.endAt());
        boolean sameDay = siteTime.dateOf(details.startAt()).equals(siteTime.dateOf(details.endAt().minusNanos(1)));
        if (!details.endAt().isAfter(details.startAt()) || length.compareTo(SHORTEST) < 0
                || length.compareTo(LONGEST) > 0 || !sameDay) {
            throw new BusinessRuleException("The end must be after the start and the shift must last 30 minutes to 12 hours.");
        }
        String reason = details.reason() == null ? "" : details.reason().strip();
        if (kind == ShiftKind.OVERTIME && (reason.length() < 10 || reason.length() > 500)) {
            throw new BusinessRuleException("Write the reason for the overtime (10 to 500 characters).");
        }
        AppUser person = users.require(details.workerId());
        shift.setUser(person);
        shift.setZone(zones.require(details.zoneId()));
        shift.setStartAt(details.startAt());
        shift.setEndAt(details.endAt());
        shift.setKind(kind);
        shift.setReason(kind == ShiftKind.OVERTIME ? reason : null);
        Shift clash = shifts.findAllByUserIdOrderByStartAtAsc(person.getId()).stream()
                .filter(other -> other.getStatus() != ShiftStatus.CANCELLED && !other.equals(shift))
                .filter(other -> other.getStartAt().isBefore(details.endAt()) && other.getEndAt().isAfter(details.startAt()))
                .findFirst().orElse(null);
        if (clash != null) {
            throw new BusinessRuleException(String.format("This person already has a shift from %s to %s.",
                    WHEN.format(siteTime.toLocal(clash.getStartAt())), WHEN.format(siteTime.toLocal(clash.getEndAt()))));
        }
    }

    /** BR-37: pending overtime becomes valid when an OVERTIME shift covers it within 48 hours. */
    private void makeOvertimeValid(Long pendingId, Shift overtime) {
        var rows = jdbc.queryForList(
                "SELECT status, expires_at, decided_at, user_id, start_at, end_at FROM pending_overtime WHERE id = ? FOR UPDATE", pendingId);
        if (rows.isEmpty()) {
            throw new BusinessRuleException("This pending overtime does not exist");
        }
        var row = rows.get(0);
        if (((Number) row.get("user_id")).longValue() != overtime.getUser().getId()) {
            throw new BusinessRuleException("The overtime shift must be for the same person");
        }
        boolean expired = ((java.sql.Timestamp) row.get("expires_at")).toInstant().isBefore(java.time.Instant.now());
        if (!"PENDING".equals(row.get("status")) || expired) {
            throw new BusinessRuleException("This pending overtime was cancelled and can no longer be made valid.");
        }
        jdbc.update("""
                UPDATE pending_overtime SET status = 'VALID', decided_at = now(), overtime_shift_id = ?
                WHERE id = ?""", overtime.getId(), pendingId);
    }

    private Shift save(Shift shift) {
        try {
            return shifts.saveAndFlush(shift);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("This worker already has a shift at that time");
        }
    }
}
