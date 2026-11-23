package orochi.steelsight;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.EmbeddingStatus;
import orochi.steelsight.attendance.model.FacePose;
import orochi.steelsight.attendance.repository.FaceEmbeddingRepository;
import orochi.steelsight.attendance.service.CheckInService;
import orochi.steelsight.attendance.service.CheckInService.ManualCheckIn;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.exception.StaleEditException;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.model.StaffGroup;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.identity.service.UserService;
import orochi.steelsight.identity.service.UserService.NewUser;
import orochi.steelsight.identity.service.UserService.Profile;
import orochi.steelsight.ppe.model.Camera;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ticketing.model.TicketOutcome;
import orochi.steelsight.ppe.model.ReviewStatus;
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
    @Autowired orochi.steelsight.jobs.SiteJobs siteJobs;
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
    Long otherAdmin;
    Long hse;
    Long manager;
    Long otherManager;
    Long worker;
    Long otherWorker;
    Long zone;
    Camera camera;
    /** 06:00 on the Monday two weeks ahead: a week whose schedule hasn't been sent (BR-23). */
    OffsetDateTime start = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusWeeks(2)
            .with(java.time.DayOfWeek.MONDAY).atTime(6, 0).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime();

    @BeforeEach
    void seed() {
        admin = user("A1", "ADMIN", null);
        otherAdmin = user("A2", "ADMIN", null);
        hse = user("H1", "HSE_OFFICER", null);
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
        NewUser details = new NewUser("W-9", "New Worker", "w9@test.local", "0900000009", "secret-pw", "WORKER", null, StaffGroup.OFFICE, true);

        AppUser created = userService.create(admin, details);

        assertThat(created.getPasswordHash()).isNotEqualTo("secret-pw");
        assertThat(passwordEncoder.matches("secret-pw", created.getPasswordHash())).isTrue();
        assertThatThrownBy(() -> userService.create(manager, details)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateEmployeeCodeIsARuleError() {
        NewUser details = new NewUser("W1", "Copy", null, null, "long-enough-pw", "WORKER", null);

        assertThatThrownBy(() -> userService.create(admin, details)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyAnAdminEditsDetailsAndAManagerCannotEvenEditTheirOwnTeam() {
        long version = users.findById(worker).orElseThrow().getVersion();
        Profile profile = new Profile("Renamed", null, "0912345678");

        assertThatThrownBy(() -> userService.updateProfile(manager, worker, profile, version))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.updateProfile(manager, otherWorker, profile, 0))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(userService.updateProfile(admin, worker, profile, version).getFullName()).isEqualTo("Renamed");
        assertThatThrownBy(() -> userService.changeRole(manager, worker, "ADMIN"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void staleProfileEditFails() {
        long stale = users.findById(worker).orElseThrow().getVersion() + 1;
        Profile profile = new Profile("Renamed", null, null);

        assertThatThrownBy(() -> userService.updateProfile(admin, worker, profile, stale))
                .isInstanceOf(StaleEditException.class);
    }

    @Test
    void newPhotoIsSavedAndQueuedTogether() {
        AppUser updated = userService.changeFacePhoto(admin, worker, "w1.jpg");

        assertThat(updated.getFaceFilePath()).isEqualTo("w1.jpg");
        assertThat(embeddings.findFrontByUserId(worker).orElseThrow().getStatus()).isEqualTo(EmbeddingStatus.PENDING);
        assertThat(userService.faceStatuses(admin, java.util.List.of(worker, otherWorker)))
                .containsExactly(java.util.Map.entry(worker, EmbeddingStatus.PENDING));
    }

    @Test
    void extraAnglesNeedTheFrontPhotoFirstAndDontChangeTheListStatus() {
        assertThatThrownBy(() -> userService.changeFaceAngle(admin, worker, FacePose.LEFT, "l.jpg"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userService.changeFaceAngle(admin, worker, FacePose.FRONT, "f.jpg"))
                .isInstanceOf(BusinessRuleException.class);

        userService.changeFacePhoto(admin, worker, "w1.jpg");
        userService.changeFaceAngle(admin, worker, FacePose.LEFT, "l.jpg");

        assertThat(userService.faceAngles(admin, worker)).containsOnlyKeys(FacePose.LEFT);
        assertThat(userService.faceAnglePath(admin, worker, FacePose.LEFT)).isEqualTo("l.jpg");
        assertThat(userService.faceStatuses(admin, java.util.List.of(worker)))
                .containsExactly(java.util.Map.entry(worker, EmbeddingStatus.PENDING));

        userService.removeFaceAngle(admin, worker, FacePose.LEFT);
        assertThat(userService.faceAngles(admin, worker)).isEmpty();
        assertThat(embeddings.findFrontByUserId(worker)).isPresent();
    }

    @Test
    void onlyAnAdminHandlesFacePhotos() {
        assertThatThrownBy(() -> userService.changeFacePhoto(manager, worker, "w1.jpg"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.faceEnrolment(manager, worker)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.facePhotoPath(manager, worker)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.changeFaceAngle(manager, worker, FacePose.LEFT, "l.jpg"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.faceAngles(manager, worker)).isInstanceOf(AccessDeniedException.class);
        assertThat(embeddings.findAllByUserIdOrderByPose(worker)).isEmpty();
    }

    @Test
    void createChecksWhatTheAdminTyped() {
        String phone = "0900000008";
        assertThatThrownBy(() -> userService.create(admin, new NewUser("has space", "New Person", null, phone, "long-enough-pw", "WORKER", null, StaffGroup.OFFICE, true)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("mployee code");
        assertThatThrownBy(() -> userService.create(admin, new NewUser("W-8", "New Person", null, phone, "short", "WORKER", null, StaffGroup.OFFICE, true)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("at least 8");
        assertThatThrownBy(() -> userService.create(admin, new NewUser("W-8", " ", null, phone, "long-enough-pw", "WORKER", null, StaffGroup.OFFICE, true)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> userService.create(admin, new NewUser("W-8", "New Person", "nope", phone, "long-enough-pw", "WORKER", null, StaffGroup.OFFICE, true)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("email");
        assertThatThrownBy(() -> userService.create(admin, new NewUser("W-8", "New Person", null, phone, "long-enough-pw", "", null, StaffGroup.OFFICE, true)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("role");
        assertThatThrownBy(() -> userService.create(admin, new NewUser("W-8", "New Person", null, phone, "é".repeat(40), "WORKER", null, StaffGroup.OFFICE, true)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("too long");
    }

    @Test
    void createQueuesTheFacePhotoAndTheStartingPasswordMustBeChanged() {
        AppUser created = userService.create(admin,
                new NewUser(" w-8 ", "New Face", null, "0900000018", "long-enough-pw", "WORKER", null, StaffGroup.OFFICE, true), "w8.jpg");

        assertThat(created.getEmployeeCode()).isEqualTo("W-8");   // stored in upper case (BR-06)
        assertThat(created.getFaceFilePath()).isEqualTo("w8.jpg");
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(embeddings.findFrontByUserId(created.getId()).orElseThrow().getStatus()).isEqualTo(EmbeddingStatus.PENDING);
    }

    @Test
    void aResetPasswordMustBeChangedAndTheNewOneMustDiffer() {
        userService.resetPassword(admin, worker, "first-pw");
        assertThat(users.findById(worker).orElseThrow().isMustChangePassword()).isTrue();

        assertThatThrownBy(() -> userService.changeOwnPassword(worker, "first-pw", "first-pw"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userService.changeOwnPassword(worker, "first-pw", "short"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userService.resetPassword(admin, worker, "short"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userService.resetPassword(manager, worker, "long-enough-pw"))
                .isInstanceOf(AccessDeniedException.class);

        userService.changeOwnPassword(worker, "first-pw", "second-pw");
        assertThat(users.findById(worker).orElseThrow().isMustChangePassword()).isFalse();
    }

    @Test
    void softDeleteErasesTheEmbedding() {
        userService.changeFacePhoto(admin, worker, "w1.jpg");

        userService.softDelete(admin, worker);
        em.flush();

        assertThat(embeddings.findAllByUserIdOrderByPose(worker)).isEmpty();
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
        Long free = user("M9", "MANAGER", null);
        assertThat(teamService.create(admin, "Team three", free).getManager().getId()).isEqualTo(free);
        assertThatThrownBy(() -> teamService.create(admin, "Team four", manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already leads");
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

        // A shift becomes COMPLETED by itself once it has ended; nobody marks it.
        siteJobs.hourly(java.time.LocalDateTime.now());
        em.clear();
        assertThat(em.find(Shift.class, first.getId()).getStatus()).isEqualTo(ShiftStatus.SCHEDULED);
        em.createNativeQuery("UPDATE shift SET start_at = now() - interval '9 hours', end_at = now() - interval '1 hour' WHERE id = :id")
                .setParameter("id", first.getId()).executeUpdate();
        siteJobs.hourly(java.time.LocalDateTime.now());
        em.clear();

        Shift done = em.find(Shift.class, first.getId());
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
    void everyTicketGoesToTheAdminAndOnlyAnAdminCanResolveIt() {
        Ticket ticket = ticketService.create(worker, category("Other"), "Broken rail", "Level 2 rail is loose");
        em.flush();

        assertThat(ticketService.listOwn(worker)).containsExactly(ticket);
        assertThat(ticketService.listHandled(admin)).contains(ticket);
        assertThatThrownBy(() -> ticketService.listHandled(hse)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.listHandled(manager)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.resolve(hse, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.resolve(manager, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.resolve(worker, ticket.getId())).isInstanceOf(AccessDeniedException.class);

        Ticket resolved = ticketService.resolve(admin, ticket.getId());
        assertThat(resolved.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolved.getResolver().getId()).isEqualTo(admin);
        assertThat(resolved.getResolvedAt()).isNotNull();
    }

    @Test
    void aTicketKeepsItsPhoto() {
        TicketService.NewTicket details = new TicketService.NewTicket(
                category("Other"), "Broken rail", "Level 2 rail is loose", "tickets/rail.jpg", null, null, null, null, null, null, null);

        Ticket ticket = ticketService.create(worker, details);

        assertThat(ticket.getPhotoPath()).isEqualTo("tickets/rail.jpg");
    }

    @Test
    void adminCanWorkAnyTicket() {
        Ticket ticket = ticketService.create(worker, category("Other"), "New gloves", "Need new gloves for the steel yard");

        assertThat(ticketService.listAll(admin)).contains(ticket);
        assertThat(ticketService.resolve(admin, ticket.getId()).getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    /** SRS UC-24 EX-01. */
    @Test
    void anAdminCannotHandleTheirOwnTicket() {
        Ticket ticket = ticketService.create(admin, category("Other"), "Laptop", "Mine is broken");
        em.flush();

        assertThat(ticketService.canHandle(admin, ticket.getId())).isFalse();
        assertThatThrownBy(() -> ticketService.resolve(admin, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.assign(otherAdmin, ticket.getId(), admin))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(ticketService.resolve(otherAdmin, ticket.getId()).getStatus()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void ticketsStayPrivateToTheirCreatorTheirTeamLeaderAndTheAdmin() {
        Ticket ticket = ticketService.create(worker, category("Other"), "New gloves", "Need new gloves for the steel yard");
        em.flush();

        assertThat(ticketService.get(worker, ticket.getId())).isEqualTo(ticket);
        assertThat(ticketService.get(admin, ticket.getId())).isEqualTo(ticket);
        assertThat(ticketService.get(manager, ticket.getId())).isEqualTo(ticket);   // leads the worker's team
        assertThatThrownBy(() -> ticketService.get(otherManager, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.get(otherWorker, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.get(hse, ticket.getId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theTeamLeaderListsAndCommentsOnTeamTicketsButCannotDecide() {
        Ticket ticket = ticketService.create(worker, category("Other"), "Friday off", "Family wedding on Friday");
        em.flush();

        assertThat(ticketService.listTeam(manager, PageRequest.of(0, 20)).getContent()).containsExactly(ticket);
        assertThat(ticketService.listTeam(otherManager, PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThatThrownBy(() -> ticketService.listTeam(worker, PageRequest.of(0, 20))).isInstanceOf(AccessDeniedException.class);

        ticketService.addComment(manager, ticket.getId(), "He asked me last week");
        assertThatThrownBy(() -> ticketService.addComment(otherManager, ticket.getId(), "Hi"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.resolve(manager, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.assign(manager, ticket.getId(), manager)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assigneeMustBeAbleToHandleTheTicket() {
        Ticket ticket = ticketService.create(worker, category("Other"), "Oil spill", "Oil on the floor near the gate");

        assertThatThrownBy(() -> ticketService.assign(admin, ticket.getId(), hse))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ticketService.assign(admin, ticket.getId(), manager))
                .isInstanceOf(BusinessRuleException.class);

        Ticket assigned = ticketService.assign(admin, ticket.getId(), otherAdmin);
        assertThat(assigned.getAssignee().getId()).isEqualTo(otherAdmin);
        assertThat(assigned.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    @Test
    void commentsComeFromTheCreatorTheLeaderOrTheAdminAndStopWhenResolved() {
        Ticket ticket = ticketService.create(worker, category("Other"), "Oil spill", "Oil on the floor near the gate");
        ticketService.addComment(worker, ticket.getId(), "Still there");
        ticketService.addComment(admin, ticket.getId(), "Sending someone");
        em.flush();

        assertThat(ticketService.listComments(admin, ticket.getId())).hasSize(2);
        assertThatThrownBy(() -> ticketService.addComment(otherWorker, ticket.getId(), "Hello"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.addComment(hse, ticket.getId(), "Hello"))
                .isInstanceOf(AccessDeniedException.class);

        ticketService.resolve(admin, ticket.getId());
        assertThatThrownBy(() -> ticketService.addComment(worker, ticket.getId(), "Thanks"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void softDeletedTicketDisappears() {
        Ticket ticket = ticketService.create(worker, category("Other"), "Oil spill", "Oil on the floor near the gate");
        em.flush();
        assertThatThrownBy(() -> ticketService.softDelete(manager, ticket.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.softDelete(hse, ticket.getId())).isInstanceOf(AccessDeniedException.class);

        ticketService.softDelete(admin, ticket.getId());
        em.flush();

        assertThat(ticketService.listOwn(worker)).isEmpty();
        assertThatThrownBy(() -> ticketService.get(worker, ticket.getId())).isInstanceOf(AccessDeniedException.class);
    }

    // --- disputes --------------------------------------------------------------------------

    @Test
    void workerAppealsAndTheAdminDecidesWithTicketResolvedTogether() {
        Violation violation = violation(worker);

        Ticket ticket = ticketService.openDispute(worker, violation.getId(), "Not me", "I wore my hard hat the whole time");
        assertThat(violation.getDisputeStatus()).isEqualTo(DisputeStatus.DISPUTED);
        assertThat(ticketService.listHandled(admin)).contains(ticket);

        Violation decided = ticketService.decideAppeal(admin, violation.getId(), DisputeStatus.OVERTURNED,
                "The officer on duty was not sure who it was.", "The image is unclear, points returned.", null);
        em.flush();

        assertThat(decided.getDisputeStatus()).isEqualTo(DisputeStatus.OVERTURNED);
        assertThat(decided.getAppealDecidedBy().getId()).isEqualTo(admin);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(ticket.getOutcome()).isEqualTo(TicketOutcome.OVERTURNED);
        assertThat(ticket.getResolver().getId()).isEqualTo(admin);
        assertThat(ticketService.listComments(worker, ticket.getId())).hasSize(1);
    }

    @Test
    void onlyTheOwnerAppealsAndOnlyOnce() {
        Violation violation = violation(worker);

        assertThatThrownBy(() -> ticketService.openDispute(otherWorker, violation.getId(), null, "I wore my vest the whole time"))
                .isInstanceOf(AccessDeniedException.class);

        ticketService.openDispute(worker, violation.getId(), null, "I wore my vest the whole time");
        assertThatThrownBy(() -> ticketService.openDispute(worker, violation.getId(), null, "I wore my vest the whole time"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anAppealTicketCanOnlyEndThroughADecision() {
        Violation violation = violation(worker);
        Ticket ticket = ticketService.openDispute(worker, violation.getId(), null, "I wore my vest the whole time");
        em.flush();

        assertThatThrownBy(() -> ticketService.create(worker, category("PPE appeal"), "Appeal", "Some details here"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ticketService.resolve(admin, ticket.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ticketService.softDelete(admin, ticket.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyTheAdminDecidesAnAppealAndTheOutcomeMustBeValid() {
        Violation violation = violation(worker);
        ticketService.openDispute(worker, violation.getId(), null, "I wore my vest the whole time");
        String facts = "The officer saw the worker without a hard hat.";
        String note = "Checked the photo with the officer.";

        assertThatThrownBy(() -> ticketService.decideAppeal(hse, violation.getId(), DisputeStatus.UPHELD, facts, note, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.decideAppeal(manager, violation.getId(), DisputeStatus.UPHELD, facts, note, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.decideAppeal(worker, violation.getId(), DisputeStatus.OVERTURNED, facts, note, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ticketService.decideAppeal(admin, violation.getId(), DisputeStatus.NONE, facts, note, null))
                .isInstanceOf(BusinessRuleException.class);

        ticketService.decideAppeal(admin, violation.getId(), DisputeStatus.UPHELD, facts, note, null);
        assertThatThrownBy(() -> ticketService.decideAppeal(otherAdmin, violation.getId(), DisputeStatus.OVERTURNED, facts, note, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    /** BR-51: an office employee's violation is a reminder, which cannot be appealed. */
    @Test
    void anOfficeEmployeesViolationIsAReminderThatCannotBeAppealed() {
        Violation own = violation(admin);

        assertThat(own.getReviewStatus()).isEqualTo(ReviewStatus.REMINDER);
        assertThatThrownBy(() -> ticketService.openDispute(admin, own.getId(), null, "I wore my vest the whole time"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reminder");
    }

    /** BR-48: HSE confirms the person; the worker sees it only then. */
    @Test
    void hseConfirmsTheWorkerThenTheWorkerSeesIt() {
        Violation unknown = violation(null);
        em.flush();

        assertThat(violationService.queue(hse, null, null, Pageable.unpaged())).containsExactly(unknown);
        assertThatThrownBy(() -> violationService.queue(worker, null, null, Pageable.unpaged())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> violationService.confirm(manager, unknown.getId(), worker, false, null, unknown.getVersion()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> violationService.get(worker, unknown.getId())).isInstanceOf(AccessDeniedException.class);

        violationService.confirm(hse, unknown.getId(), worker, false, null, unknown.getVersion());
        em.flush();

        assertThat(violationService.queue(hse, null, null, Pageable.unpaged())).isEmpty();
        assertThat(violationService.listOwn(worker)).containsExactly(unknown);
        assertThat(violationService.get(worker, unknown.getId())).isEqualTo(unknown);
        assertThatThrownBy(() -> violationService.get(otherWorker, unknown.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> violationService.confirm(hse, unknown.getId(), otherWorker, false, null, unknown.getVersion()))
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
        CheckIn visit = checkInService.recordManual(hse, new ManualCheckIn(worker, null, null, in, in.plusHours(1), "Gate camera was down"));
        visit.setAutoClosed(true);
        em.flush();

        CheckIn fixed = checkInService.correct(hse, visit.getId(), in, in.plusHours(8), "Left at 17:00 by the side gate", visit.getVersion());

        assertThat(fixed.isAutoClosed()).isFalse();
        assertThat(fixed.getNote()).isEqualTo("Left at 17:00 by the side gate");
    }

    @Test
    void aCorrectionKeepsTheOldValuesAndWhoChangedThem() {
        OffsetDateTime in = OffsetDateTime.now().minusHours(9).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        CheckIn visit = checkInService.recordManual(hse, new ManualCheckIn(worker, null, null, in, in.plusHours(1), "Typed wrong time"));
        em.flush();

        checkInService.correct(hse, visit.getId(), in, in.plusHours(8), "Left at 17:00 by the side gate", visit.getVersion());
        em.flush();

        Object kept = em.createNativeQuery("""
                        SELECT count(*) FROM check_in_correction
                        WHERE check_in_id = :visit AND corrected_by = :hse AND old_recorded_by = :hse
                          AND old_check_in_at = :in AND old_check_out_at = :oldOut AND new_check_out_at = :newOut
                          AND old_note = 'Typed wrong time' AND new_note = 'Left at 17:00 by the side gate' AND NOT old_auto_closed""")
                .setParameter("visit", visit.getId()).setParameter("hse", hse).setParameter("in", in)
                .setParameter("oldOut", in.plusHours(1)).setParameter("newOut", in.plusHours(8))
                .getSingleResult();
        assertThat(((Number) kept).intValue()).isEqualTo(1);
    }

    @Test
    void aStaleCorrectionFailsEvenIfOnlyPlainSqlChangedTheVisit() {
        OffsetDateTime in = OffsetDateTime.now().minusHours(9);
        CheckIn visit = checkInService.recordManual(hse, new ManualCheckIn(worker, null, null, in, null, "Gate camera was down"));
        long versionInForm = visit.getVersion();
        // What the detection service does when a worker checks out
        em.createNativeQuery("UPDATE check_in SET check_out_at = now() WHERE id = :id").setParameter("id", visit.getId()).executeUpdate();
        em.clear();

        assertThatThrownBy(() -> checkInService.correct(hse, visit.getId(), in, in.plusHours(1), "Typed the wrong time", versionInForm))
                .isInstanceOf(StaleEditException.class);
    }

    @Test
    void checkInReadsFollowPermissions() {
        CheckIn visit = checkInService.recordManual(hse, new ManualCheckIn(worker, null, null, OffsetDateTime.now(), null, "Gate camera was down"));
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
        if (roleName.equals("HSE_OFFICER") || roleName.equals("ADMIN")) {
            user.setStaffGroup(StaffGroup.OFFICE);
        }
        return users.saveAndFlush(user).getId();
    }

    private Team team(String name, Long managerId) {
        Team team = new Team();
        team.setName(name);
        team.setManager(users.findById(managerId).orElseThrow());
        return teams.save(team);
    }

    /** Saved as the detection app does; with a person, confirmed for them by HSE (BR-48). */
    private Violation violation(Long who) {
        Violation violation = new Violation();
        violation.setCamera(camera);
        violation.setViolationType(ViolationType.NO_HARD_HAT);
        violation.setDetectedAt(OffsetDateTime.now());
        violation.setSnapshotPath("snapshot.jpg");
        Violation saved = violations.saveAndFlush(violation);
        em.refresh(saved);
        if (who != null) {
            violationService.confirm(hse, saved.getId(), who, false, null, saved.getVersion());
            em.flush();
            em.refresh(saved);
        }
        return saved;
    }
}
