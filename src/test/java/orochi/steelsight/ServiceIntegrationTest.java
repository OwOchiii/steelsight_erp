package orochi.steelsight;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.EmbeddingStatus;
import orochi.steelsight.attendance.repository.FaceEmbeddingRepository;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.attendance.service.CheckInService.ManualCheckIn;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.StaleEditException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.identity.service.UserService.NewUser;
import orochi.steelsight.identity.service.UserService.Profile;
import orochi.steelsight.ppe.model.Camera;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.model.ViolationType;
import orochi.steelsight.ppe.repository.CameraRepository;
import orochi.steelsight.ppe.repository.ViolationRepository;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.model.Zone;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.scheduling.repository.ZoneRepository;
import orochi.steelsight.scheduling.service.ShiftService;
import orochi.steelsight.scheduling.service.ShiftService.ShiftDetails;
import orochi.steelsight.scheduling.service.TeamService;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.model.TicketStatus;
import orochi.steelsight.ticketing.repository.TicketCategoryRepository;
import orochi.steelsight.ticketing.service.TicketService;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Each test rolls back. Rollback of a failed service call is covered by ServiceAtomicityTest. */
@SpringBootTest
@Import(TestDatabase.class)
@Transactional
class ServiceIntegrationTest {

    @Autowired EntityManager em;
    @Autowired UserService userService;
    @Autowired TeamService teamService;
    @Autowired ShiftService shiftService;
    @Autowired TicketService ticketService;
    @Autowired ViolationService violationService;
    @Autowired CheckInService checkInService;
    @Autowired AppUserRepository users;
    @Autowired RoleRepository roles;
    @Autowired TeamRepository teams;
    @Autowired ZoneRepository zones;
    @Autowired CameraRepository cameras;
    @Autowired ViolationRepository violations;
    @Autowired TicketCategoryRepository categories;
    @Autowired FaceEmbeddingRepository embeddings;
    @Autowired PasswordEncoder passwordEncoder;

    Long admin;
    Long hse;
    Long otherHse;
    Long manager;
    Long otherManager;
    Long worker;
    Long otherWorker;
    Long zone;
    Camera camera;
    OffsetDateTime start = OffsetDateTime.now().plusDays(1);

    @BeforeEach
    void seed() {
        admin = user("A1", "ADMIN", null);
        hse = user("H1", "HSE_OFFICER", null);
        otherHse = user("H2", "HSE_OFFICER", null);
        manager = user("M1", "MANAGER", null);
        otherManager = user("M2", "MANAGER", null);
        worker = user("W1", "WORKER", team("Team one", manager));
        otherWorker = user("W2", "WORKER", team("Team two", otherManager));

        Zone z = new Zone();
        z.setName("Test zone");
        zone = zones.save(z).getId();

        camera = new Camera();
        camera.setCameraCode("CAM-T");
        camera.setZone(z);
        cameras.save(camera);
        em.flush();
    }

    // --- users -----------------------------------------------------------------------------

    @Test
    void adminCreatesUsersWithAHashedPasswordAndOthersCannot() {
        NewUser details = new NewUser("W9", "New Worker", "w9@test.local", null, "secret-pw", "WORKER", null);

        AppUser created = userService.create(admin, details);

        assertThat(created.getPasswordHash()).isNotEqualTo("secret-pw");
        assertThat(passwordEncoder.matches("secret-pw", created.getPasswordHash())).isTrue();
        assertThatThrownBy(() -> userService.create(manager, details)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateEmployeeCodeIsARuleError() {
        NewUser details = new NewUser("W1", "Copy", null, null, "pw", "WORKER", null);

        assertThatThrownBy(() -> userService.create(admin, details)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void managerEditsOnlyTheirOwnTeamAndCannotChangeRoles() {
        long version = users.findById(worker).orElseThrow().getVersion();
        Profile profile = new Profile("Renamed", null, "0123");

        assertThat(userService.updateProfile(manager, worker, profile, version).getFullName()).isEqualTo("Renamed");
        assertThatThrownBy(() -> userService.updateProfile(manager, otherWorker, profile, 0))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.changeRole(manager, worker, "ADMIN"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void staleProfileEditFails() {
        long stale = users.findById(worker).orElseThrow().getVersion() + 1;
        Profile profile = new Profile("Renamed", null, null);

        assertThatThrownBy(() -> userService.updateProfile(manager, worker, profile, stale))
                .isInstanceOf(StaleEditException.class);
    }

    @Test
    void newPhotoIsSavedAndQueuedTogether() {
        AppUser updated = userService.changeFacePhoto(manager, worker, "faces/w1.jpg");

        assertThat(updated.getFaceFilePath()).isEqualTo("faces/w1.jpg");
        assertThat(embeddings.findByUserId(worker).orElseThrow().getStatus()).isEqualTo(EmbeddingStatus.PENDING);
    }

    @Test
    void softDeleteErasesTheEmbedding() {
        userService.changeFacePhoto(admin, worker, "faces/w1.jpg");

        userService.softDelete(admin, worker);
        em.flush();

        assertThat(embeddings.findByUserId(worker)).isEmpty();
        assertThat(users.findByIdAndDeletedAtIsNull(worker)).isEmpty();
    }

    @Test
    void cannotDeleteYourselfOrAManagerWhoStillRunsATeam() {
        assertThatThrownBy(() -> userService.softDelete(admin, admin)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userService.softDelete(admin, manager)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userService.changeRole(admin, manager, "WORKER")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void passwordChangeNeedsTheCurrentPassword() {
        userService.resetPassword(admin, worker, "first-pw");

        assertThatThrownBy(() -> userService.changeOwnPassword(worker, "wrong", "second-pw"))
                .isInstanceOf(BusinessRuleException.class);
        userService.changeOwnPassword(worker, "first-pw", "second-pw");

        assertThat(passwordEncoder.matches("second-pw", users.findById(worker).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void teamManagerMustBeAManager() {
        assertThatThrownBy(() -> teamService.create(admin, "Team three", worker))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(teamService.create(admin, "Team three", manager).getManager().getId()).isEqualTo(manager);
    }

    // --- shifts ----------------------------------------------------------------------------

    @Test
    void managerSchedulesOnlyTheirTeamAndAdminSchedulesAnyone() {
        Shift own = shiftService.create(manager, shift(worker, 0, 8));

        assertThat(own.getCreatedBy().getId()).isEqualTo(manager);
        assertThat(shiftService.create(admin, shift(otherWorker, 0, 8)).getId()).isNotNull();
        assertThatThrownBy(() -> shiftService.create(manager, shift(otherWorker, 10, 18)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> shiftService.create(hse, shift(worker, 10, 18)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void managerCannotMoveAShiftToAnotherTeamsWorker() {
        Shift own = shiftService.create(manager, shift(worker, 0, 8));

        assertThatThrownBy(() -> shiftService.update(manager, own.getId(), shift(otherWorker, 0, 8), own.getVersion()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void overlappingShiftIsARuleErrorAndCancellingFreesTheTime() {
        Shift first = shiftService.create(manager, shift(worker, 0, 8));
        shiftService.cancel(manager, first.getId());
        em.flush();

        assertThat(shiftService.create(manager, shift(worker, 1, 2)).getId()).isNotNull();
        assertThatThrownBy(() -> shiftService.create(manager, shift(worker, 1, 3)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void finishedShiftIsLockedAndStaleEditsFail() {
        Shift first = shiftService.create(manager, shift(worker, 0, 8));
        long version = first.getVersion();

        assertThatThrownBy(() -> shiftService.update(manager, first.getId(), shift(worker, 1, 9), version + 1))
                .isInstanceOf(StaleEditException.class);

        Shift done = shiftService.complete(manager, first.getId());
        assertThat(done.getStatus()).isEqualTo(ShiftStatus.COMPLETED);
        assertThatThrownBy(() -> shiftService.cancel(manager, first.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shiftReadsFollowPermissions() {
        Shift own = shiftService.create(manager, shift(worker, 0, 8));
        em.flush();
        OffsetDateTime from = start.minusDays(1);
        OffsetDateTime to = start.plusDays(1);

        assertThat(shiftService.listOwn(worker)).containsExactly(own);
        assertThat(shiftService.listTeam(manager, from, to)).containsExactly(own);
        assertThat(shiftService.listTeam(otherManager, from, to)).isEmpty();
        assertThat(shiftService.listAll(admin, from, to)).contains(own);
        assertThatThrownBy(() -> shiftService.listTeam(worker, from, to)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> shiftService.listAll(manager, from, to)).isInstanceOf(AccessDeniedException.class);
    }

    // --- tickets ---------------------------------------------------------------------------

    @Test
    void safetyConcernGoesToHseAndOnlyHandlersCanResolveIt() {
        Ticket ticket = ticketService.create(worker, category("Safety concern"), "Broken rail", "Level 2 rail is loose");
        em.flush();

        assertThat(ticketService.listOwn(worker)).containsExactly(ticket);
        assertThat(ticketService.listHandled(hse)).containsExactly(ticket);
        assertThat(ticketService.listHandled(manager)).isEmpty();
        assertThatThrownBy(() -> ticketService.resolve(manager, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.resolve(worker, ticket.getId())).isInstanceOf(AccessDeniedException.class);

        Ticket resolved = ticketService.resolve(hse, ticket.getId());
        assertThat(resolved.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolved.getResolver().getId()).isEqualTo(hse);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void safetyConcernCanSayWhereTheHazardIsAndShowIt() {
        TicketService.NewTicket details = new TicketService.NewTicket(
                category("Safety concern"), "Broken rail", "Level 2 rail is loose", zone, "tickets/rail.jpg");

        Ticket ticket = ticketService.create(worker, details);

        assertThat(ticket.getZone().getId()).isEqualTo(zone);
        assertThat(ticket.getPhotoPath()).isEqualTo("tickets/rail.jpg");
    }

    @Test
    void adminCanWorkAnyTicket() {
        Ticket ticket = ticketService.create(worker, category("Equipment request"), "Gloves", "Need new gloves");

        assertThat(ticketService.listAll(admin)).contains(ticket);
        assertThat(ticketService.resolve(admin, ticket.getId()).getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void ticketsStayPrivateToTheirCreatorAndHandlers() {
        Ticket ticket = ticketService.create(worker, category("Equipment request"), "Gloves", "Need new gloves");
        em.flush();

        assertThat(ticketService.get(manager, ticket.getId())).isEqualTo(ticket);
        assertThatThrownBy(() -> ticketService.get(otherWorker, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.get(hse, ticket.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assigneeMustBeAbleToHandleTheTicket() {
        Ticket ticket = ticketService.create(worker, category("Safety concern"), "Spill", "Oil on the floor");

        assertThatThrownBy(() -> ticketService.assign(hse, ticket.getId(), manager))
                .isInstanceOf(BusinessRuleException.class);

        Ticket assigned = ticketService.assign(hse, ticket.getId(), otherHse);
        assertThat(assigned.getAssignee().getId()).isEqualTo(otherHse);
        assertThat(assigned.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void commentsComeFromTheCreatorOrHandlersAndStopWhenResolved() {
        Ticket ticket = ticketService.create(worker, category("Safety concern"), "Spill", "Oil on the floor");
        ticketService.addComment(worker, ticket.getId(), "Still there");
        ticketService.addComment(hse, ticket.getId(), "On my way");
        em.flush();

        assertThat(ticketService.listComments(hse, ticket.getId())).hasSize(2);
        assertThatThrownBy(() -> ticketService.addComment(otherWorker, ticket.getId(), "Hello"))
                .isInstanceOf(AccessDeniedException.class);

        ticketService.resolve(hse, ticket.getId());
        assertThatThrownBy(() -> ticketService.addComment(worker, ticket.getId(), "Thanks"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void softDeletedTicketDisappears() {
        Ticket ticket = ticketService.create(worker, category("Safety concern"), "Spill", "Oil on the floor");
        em.flush();
        assertThatThrownBy(() -> ticketService.softDelete(manager, ticket.getId())).isInstanceOf(AccessDeniedException.class);

        ticketService.softDelete(hse, ticket.getId());
        em.flush();

        assertThat(ticketService.listOwn(worker)).isEmpty();
        assertThatThrownBy(() -> ticketService.get(worker, ticket.getId())).isInstanceOf(AccessDeniedException.class);
    }

    // --- disputes --------------------------------------------------------------------------

    @Test
    void workerDisputesAndHseDecidesWithTicketResolvedTogether() {
        Violation violation = violation(worker);

        Ticket ticket = ticketService.openDispute(worker, violation.getId(), "Not me", "I wore my hard hat");
        assertThat(violation.getDisputeStatus()).isEqualTo(DisputeStatus.DISPUTED);
        assertThat(ticketService.listHandled(hse)).containsExactly(ticket);

        Violation decided = violationService.decide(hse, violation.getId(), DisputeStatus.OVERTURNED, "The image is unclear");
        em.flush();

        assertThat(decided.getDisputeStatus()).isEqualTo(DisputeStatus.OVERTURNED);
        assertThat(decided.getReviewedBy().getId()).isEqualTo(hse);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getResolver().getId()).isEqualTo(hse);
        assertThat(ticketService.listComments(worker, ticket.getId())).hasSize(1);
    }

    @Test
    void onlyTheOwnerDisputesAndOnlyOnce() {
        Violation violation = violation(worker);

        assertThatThrownBy(() -> ticketService.openDispute(otherWorker, violation.getId(), "s", "d"))
                .isInstanceOf(AccessDeniedException.class);

        ticketService.openDispute(worker, violation.getId(), "s", "d");
        assertThatThrownBy(() -> ticketService.openDispute(worker, violation.getId(), "s", "d"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void disputeTicketCanOnlyEndThroughADecision() {
        Violation violation = violation(worker);
        Ticket ticket = ticketService.openDispute(worker, violation.getId(), "s", "d");
        em.flush();

        assertThatThrownBy(() -> ticketService.create(worker, category("PPE dispute"), "s", "d"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ticketService.resolve(hse, ticket.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ticketService.softDelete(hse, ticket.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void decisionsNeedReviewRightsAndAValidOutcome() {
        Violation violation = violation(worker);
        ticketService.openDispute(worker, violation.getId(), "s", "d");

        assertThatThrownBy(() -> violationService.decide(manager, violation.getId(), DisputeStatus.UPHELD, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> violationService.decide(worker, violation.getId(), DisputeStatus.OVERTURNED, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> violationService.decide(hse, violation.getId(), DisputeStatus.NONE, null))
                .isInstanceOf(BusinessRuleException.class);

        violationService.decide(hse, violation.getId(), DisputeStatus.UPHELD, null);
        assertThatThrownBy(() -> violationService.decide(otherHse, violation.getId(), DisputeStatus.OVERTURNED, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void hseCannotDecideTheirOwnViolation() {
        Violation own = violation(hse);
        ticketService.openDispute(hse, own.getId(), "s", "d");

        assertThatThrownBy(() -> violationService.decide(hse, own.getId(), DisputeStatus.OVERTURNED, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(violationService.decide(otherHse, own.getId(), DisputeStatus.OVERTURNED, null).getDisputeStatus())
                .isEqualTo(DisputeStatus.OVERTURNED);
    }

    @Test
    void hseAssignsUnidentifiedViolationsThenTheWorkerSeesThem() {
        Violation unknown = violation(null);
        em.flush();

        assertThat(violationService.listUnidentified(hse, Pageable.unpaged())).containsExactly(unknown);
        assertThatThrownBy(() -> violationService.listUnidentified(worker, Pageable.unpaged())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> violationService.assignWorker(manager, unknown.getId(), worker))
                .isInstanceOf(AccessDeniedException.class);

        violationService.assignWorker(hse, unknown.getId(), worker);

        assertThat(violationService.listUnidentified(hse, Pageable.unpaged())).isEmpty();
        assertThat(violationService.listOwn(worker)).containsExactly(unknown);
        assertThat(violationService.get(worker, unknown.getId())).isEqualTo(unknown);
        assertThatThrownBy(() -> violationService.get(otherWorker, unknown.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> violationService.assignWorker(hse, unknown.getId(), otherWorker))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- check-ins -------------------------------------------------------------------------

    @Test
    void hseRecordsManualCheckInsAndManagersCannot() {
        ManualCheckIn details = new ManualCheckIn(worker, null, zone, OffsetDateTime.now(), null, "Camera was down");

        CheckIn visit = checkInService.recordManual(hse, details);

        assertThat(visit.getRecordedBy().getId()).isEqualTo(hse);
        assertThat(checkInService.listOnSite(hse)).containsExactly(visit);
        assertThatThrownBy(() -> checkInService.recordManual(manager, details)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> checkInService.recordManual(hse, details)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void manualCheckInRejectsAnotherWorkersShiftAndBadTimes() {
        Shift theirs = shiftService.create(otherManager, shift(otherWorker, 0, 8));
        OffsetDateTime now = OffsetDateTime.now();

        assertThatThrownBy(() -> checkInService.recordManual(hse, new ManualCheckIn(worker, theirs.getId(), null, now, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> checkInService.recordManual(hse, new ManualCheckIn(worker, null, null, now, now.minusHours(1), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void correctionClosesAnAutoClosedVisitProperly() {
        OffsetDateTime in = OffsetDateTime.now().minusHours(9);
        CheckIn visit = checkInService.recordManual(hse, new ManualCheckIn(worker, null, null, in, in.plusHours(1), null));
        visit.setAutoClosed(true);
        em.flush();

        CheckIn fixed = checkInService.correct(hse, visit.getId(), in, in.plusHours(8), "Left at 17:00", visit.getVersion());

        assertThat(fixed.isAutoClosed()).isFalse();
        assertThat(fixed.getNote()).isEqualTo("Left at 17:00");
    }

    @Test
    void aStaleCorrectionFailsEvenIfOnlyPlainSqlChangedTheVisit() {
        OffsetDateTime in = OffsetDateTime.now().minusHours(9);
        CheckIn visit = checkInService.recordManual(hse, new ManualCheckIn(worker, null, null, in, null, null));
        long versionInForm = visit.getVersion();
        // What the detection service does when a worker checks out
        em.createNativeQuery("UPDATE check_in SET check_out_at = now() WHERE id = :id").setParameter("id", visit.getId()).executeUpdate();
        em.clear();

        assertThatThrownBy(() -> checkInService.correct(hse, visit.getId(), in, in.plusHours(1), null, versionInForm))
                .isInstanceOf(StaleEditException.class);
    }

    @Test
    void checkInReadsFollowPermissions() {
        CheckIn visit = checkInService.recordManual(hse, new ManualCheckIn(worker, null, null, OffsetDateTime.now(), null, null));
        em.flush();

        assertThat(checkInService.listOwn(worker)).containsExactly(visit);
        assertThat(checkInService.listTeam(manager)).containsExactly(visit);
        assertThat(checkInService.listTeam(otherManager)).isEmpty();
        assertThatThrownBy(() -> checkInService.listTeam(worker)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> checkInService.listOnSite(manager)).isInstanceOf(AccessDeniedException.class);
    }

    // --- helpers ---------------------------------------------------------------------------

    private ShiftDetails shift(Long who, int fromHour, int toHour) {
        return new ShiftDetails(who, zone, start.plusHours(fromHour), start.plusHours(toHour));
    }

    private Long category(String name) {
        return categories.findByCategoryName(name).orElseThrow().getId();
    }

    private Long user(String code, String roleName, Team team) {
        AppUser user = new AppUser();
        user.setRole(roles.findByRoleName(roleName).orElseThrow());
        user.setTeam(team);
        user.setEmployeeCode(code);
        user.setFullName("Test " + code);
        user.setPasswordHash("x");
        return users.saveAndFlush(user).getId();
    }

    private Team team(String name, Long managerId) {
        Team team = new Team();
        team.setName(name);
        team.setManager(users.findById(managerId).orElseThrow());
        return teams.save(team);
    }

    private Violation violation(Long who) {
        Violation violation = new Violation();
        violation.setUser(who == null ? null : users.findById(who).orElseThrow());
        violation.setCamera(camera);
        violation.setViolationType(ViolationType.NO_HARD_HAT);
        violation.setDetectedAt(OffsetDateTime.now());
        violation.setSnapshotPath("snapshot.jpg");
        return violations.saveAndFlush(violation);
    }
}
