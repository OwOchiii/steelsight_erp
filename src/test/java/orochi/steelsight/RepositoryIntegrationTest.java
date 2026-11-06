package orochi.steelsight;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import orochi.steelsight.attendance.model.CheckIn;
import orochi.steelsight.attendance.model.CheckInMethod;
import orochi.steelsight.attendance.model.EmbeddingStatus;
import orochi.steelsight.attendance.model.FaceEmbedding;
import orochi.steelsight.attendance.repository.CheckInRepository;
import orochi.steelsight.attendance.repository.FaceEmbeddingRepository;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.repository.RoleRepository;
import orochi.steelsight.ppe.model.Camera;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.model.PpeLinking;
import orochi.steelsight.ppe.model.PpeVest;
import orochi.steelsight.ppe.model.Violation;
import orochi.steelsight.ppe.model.ViolationType;
import orochi.steelsight.ppe.repository.CameraRepository;
import orochi.steelsight.ppe.repository.PpeLinkingRepository;
import orochi.steelsight.ppe.repository.PpeVestRepository;
import orochi.steelsight.ppe.repository.ViolationRepository;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.Team;
import orochi.steelsight.scheduling.model.Zone;
import orochi.steelsight.scheduling.repository.ShiftRepository;
import orochi.steelsight.scheduling.repository.TeamRepository;
import orochi.steelsight.scheduling.repository.ZoneRepository;
import orochi.steelsight.ticketing.model.Ticket;
import orochi.steelsight.ticketing.model.TicketStatus;
import orochi.steelsight.ticketing.repository.TicketCategoryRepository;
import orochi.steelsight.ticketing.repository.TicketRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestDatabase.class)
class RepositoryIntegrationTest {

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired RoleRepository roles;
    @Autowired AppUserRepository users;
    @Autowired TeamRepository teams;
    @Autowired ZoneRepository zones;
    @Autowired ShiftRepository shifts;
    @Autowired CameraRepository cameras;
    @Autowired PpeVestRepository vests;
    @Autowired PpeLinkingRepository links;
    @Autowired ViolationRepository violations;
    @Autowired CheckInRepository checkIns;
    @Autowired FaceEmbeddingRepository embeddings;
    @Autowired TicketRepository tickets;
    @Autowired TicketCategoryRepository categories;

    AppUser admin;
    AppUser hse;
    AppUser manager;
    AppUser otherManager;
    AppUser worker;
    AppUser otherWorker;
    Zone zone;
    Camera camera;

    @BeforeEach
    void seed() {
        admin = user("A1", "ADMIN");
        hse = user("H1", "HSE_OFFICER");
        manager = user("M1", "MANAGER");
        otherManager = user("M2", "MANAGER");
        worker = user("W1", "WORKER");
        otherWorker = user("W2", "WORKER");

        worker.setTeam(team("Team one", manager));
        otherWorker.setTeam(team("Team two", otherManager));

        zone = new Zone();
        zone.setName("Test zone");
        zones.save(zone);

        camera = new Camera();
        camera.setCameraCode("CAM-T");
        camera.setZone(zone);
        cameras.save(camera);
        em.flush();
    }

    // --- entity identity -------------------------------------------------------------------

    @Test
    void managerInOwnTeamDoesNotOverflowTheStack() {
        manager.setTeam(worker.getTeam());
        users.saveAndFlush(manager);

        assertThat(manager.hashCode()).isEqualTo(new AppUser().hashCode());
        assertThat(manager.toString()).isEqualTo("AppUser(id=" + manager.getId() + ")");
        assertThat(manager.getTeam().getManager()).isEqualTo(manager);
    }

    @Test
    void entitiesAreEqualById() {
        Long id = worker.getId();
        em.flush();
        em.clear();

        AppUser proxy = em.getReference(AppUser.class, id);
        AppUser loaded = users.findById(id).orElseThrow();

        assertThat(proxy).isEqualTo(loaded).hasSameHashCodeAs(loaded);
        assertThat(new AppUser()).isNotEqualTo(new AppUser());
    }

    // --- violations ------------------------------------------------------------------------

    @Test
    void vestReadFillsInTheWorkerAndFindsThemByColumn() {
        Violation saved = violations.saveAndFlush(violation(link("V-1", worker)));
        em.refresh(saved);

        assertThat(saved.getUser()).isEqualTo(worker);
        assertThat(violations.findAllByUserIdOrderByDetectedAtDesc(worker.getId())).containsExactly(saved);
    }

    @Test
    void unidentifiedViolationQueuesForHseAndCanBeAssignedWithoutAVest() {
        Violation saved = violations.saveAndFlush(violation(null));
        assertThat(violations.findAllByUserIsNullOrderByDetectedAtDesc()).containsExactly(saved);

        saved.setUser(worker);
        violations.saveAndFlush(saved);

        assertThat(violations.findAllByUserIsNullOrderByDetectedAtDesc()).isEmpty();
        assertThat(violations.findAllByUserIdOrderByDetectedAtDesc(worker.getId())).containsExactly(saved);
        assertThat(violations.findAllByUserIdAndDisputeStatus(worker.getId(), DisputeStatus.NONE)).containsExactly(saved);
    }

    @Test
    void staleEditOfAViolationFailsInsteadOfOverwriting() {
        Violation saved = violations.saveAndFlush(violation(link("V-2", worker)));
        Long id = saved.getId();
        Long staleVersion = saved.getVersion();
        em.flush();
        em.clear();

        // Another officer decides first
        jdbc.update("UPDATE violation SET dispute_status = 'DISPUTED', version = version + 1 WHERE id = ?", id);

        Violation stale = new Violation();
        org.springframework.test.util.ReflectionTestUtils.setField(stale, "id", id);
        stale.setVersion(staleVersion);
        stale.setCamera(camera);
        stale.setViolationType(ViolationType.NO_VEST);
        stale.setDetectedAt(OffsetDateTime.now());
        stale.setSnapshotPath("stale.jpg");

        assertThatThrownBy(() -> violations.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // --- shifts ----------------------------------------------------------------------------

    @Test
    void shiftGetsItsComputedDurationAndOverlapsAreRefused() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        Shift saved = shifts.saveAndFlush(shift(worker, start, start.plusHours(8)));

        assertThat(saved.getDurationHours()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(shifts.findAllByTeamManagerId(manager.getId())).containsExactly(saved);
        assertThat(shifts.findAllByTeamManagerId(otherManager.getId())).isEmpty();

        assertThatThrownBy(() -> shifts.saveAndFlush(shift(worker, start.plusHours(1), start.plusHours(2))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- check-ins -------------------------------------------------------------------------

    @Test
    void openVisitIsFoundAndAWorkerCannotCheckInTwice() {
        CheckIn visit = checkIns.saveAndFlush(faceCheckIn(worker));

        assertThat(checkIns.findByUserIdAndCheckOutAtIsNull(worker.getId())).contains(visit);
        assertThat(checkIns.findAllByCheckOutAtIsNull()).containsExactly(visit);
        assertThat(checkIns.findAllByTeamManagerId(manager.getId())).containsExactly(visit);
        assertThat(checkIns.findAllByUserIdOrderByCheckInAtDesc(worker.getId())).containsExactly(visit);

        assertThatThrownBy(() -> checkIns.saveAndFlush(faceCheckIn(worker)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void manualCheckInMustSayWhoRecordedIt() {
        CheckIn visit = new CheckIn();
        visit.setUser(worker);
        visit.setMethod(CheckInMethod.MANUAL);

        assertThatThrownBy(() -> checkIns.saveAndFlush(visit))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void visitCannotPointAtAnotherWorkersShift() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        Shift theirs = shifts.saveAndFlush(shift(otherWorker, start, start.plusHours(8)));

        CheckIn visit = faceCheckIn(worker);
        visit.setShift(theirs);

        assertThatThrownBy(() -> checkIns.saveAndFlush(visit)).hasMessageContaining("does not belong to user");
    }

    @Test
    void visitCannotPointAtAnotherWorkersVest() {
        CheckIn visit = faceCheckIn(worker);
        visit.setPpeLinking(link("V-3", otherWorker));

        assertThatThrownBy(() -> checkIns.saveAndFlush(visit)).hasMessageContaining("does not belong to user");
    }

    // --- face embeddings -------------------------------------------------------------------

    @Test
    void newPhotoResetsEnrolmentAndClearsTheOldVector() {
        embeddings.queueForEnrolment(worker.getId(), "first.jpg");
        jdbc.update("""
                UPDATE face_embedding SET status = 'READY', model_name = 'buffalo_l',
                       embedding = array_fill(0.1, ARRAY[512])::vector WHERE user_id = ?""", worker.getId());

        embeddings.queueForEnrolment(worker.getId(), "second.jpg");

        FaceEmbedding row = embeddings.findByUserId(worker.getId()).orElseThrow();
        assertThat(row.getSourceFilePath()).isEqualTo("second.jpg");
        assertThat(row.getStatus()).isEqualTo(EmbeddingStatus.PENDING);
        assertThat(row.getModelName()).isNull();
        assertThat(embeddings.findAllByStatus(EmbeddingStatus.PENDING)).containsExactly(row);
        assertThat(jdbc.queryForObject("SELECT embedding IS NULL FROM face_embedding WHERE user_id = ?",
                Boolean.class, worker.getId())).isTrue();
    }

    @Test
    void softDeletingAWorkerErasesTheirEmbedding() {
        embeddings.queueForEnrolment(worker.getId(), "face.jpg");

        AppUser reloaded = users.findById(worker.getId()).orElseThrow();
        reloaded.setDeletedAt(OffsetDateTime.now());
        users.saveAndFlush(reloaded);

        assertThat(embeddings.findByUserId(worker.getId())).isEmpty();
        assertThat(users.findByIdAndDeletedAtIsNull(worker.getId())).isEmpty();
    }

    // --- tickets ---------------------------------------------------------------------------

    @Test
    void ticketCannotHalfResolve() {
        Ticket noResolver = ticket("Equipment request", null);
        noResolver.setStatus(TicketStatus.RESOLVED);
        noResolver.setResolvedAt(OffsetDateTime.now());
        assertThatThrownBy(() -> tickets.saveAndFlush(noResolver)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void openTicketCannotCarryAResolver() {
        Ticket open = ticket("Equipment request", null);
        open.setResolver(manager);
        assertThatThrownBy(() -> tickets.saveAndFlush(open)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void violationHasOnlyOneLiveDisputeTicket() {
        Violation disputed = violations.saveAndFlush(violation(link("V-4", worker)));
        Ticket first = tickets.saveAndFlush(ticket("PPE dispute", disputed));

        assertThatThrownBy(() -> {
            tickets.saveAndFlush(ticket("PPE dispute", disputed));
        }).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(first.getId()).isNotNull();
    }

    @Test
    void softDeletedDisputeFreesTheViolation() {
        Violation disputed = violations.saveAndFlush(violation(link("V-5", worker)));
        Ticket first = tickets.saveAndFlush(ticket("PPE dispute", disputed));
        first.setDeletedAt(OffsetDateTime.now());
        tickets.saveAndFlush(first);

        Ticket second = tickets.saveAndFlush(ticket("PPE dispute", disputed));

        assertThat(tickets.findAllByHandlerRoleId(hse.getRole().getId())).containsExactly(second);
    }

    // --- permission and scope --------------------------------------------------------------

    @Test
    void managerEditsOnlyTheirTeamsShiftsAndAdminEditsAny() {
        OffsetDateTime start = OffsetDateTime.now().plusDays(1);
        Long shift = shifts.saveAndFlush(shift(worker, start, start.plusHours(8))).getId();

        assertThat(call("can_edit_shift", manager, shift)).isTrue();
        assertThat(call("can_edit_shift", otherManager, shift)).isFalse();
        assertThat(call("can_edit_shift", hse, shift)).isFalse();
        assertThat(call("can_edit_shift", worker, shift)).isFalse();
        assertThat(call("can_edit_shift", admin, shift)).isTrue();
    }

    @Test
    void ticketsAreEditedByTheHandlingRoleAndAdmin() {
        Long hseTicket = tickets.saveAndFlush(ticket("Safety concern", null)).getId();
        Long adminTicket = tickets.saveAndFlush(ticket("Equipment request", null)).getId();

        assertThat(call("can_update_ticket", hse, hseTicket)).isTrue();
        assertThat(call("can_update_ticket", hse, adminTicket)).isFalse();
        assertThat(call("can_update_ticket", manager, adminTicket)).isFalse();
        assertThat(call("can_update_ticket", manager, hseTicket)).isFalse();
        assertThat(call("can_update_ticket", worker, hseTicket)).isFalse();
        assertThat(call("can_update_ticket", admin, hseTicket)).isTrue();
        assertThat(call("can_update_ticket", admin, adminTicket)).isTrue();
    }

    @Test
    void onlyAnAdminEditsPeople() {
        assertThat(call("can_update_user", manager, worker.getId())).isFalse();
        assertThat(call("can_update_user", manager, otherWorker.getId())).isFalse();
        assertThat(call("can_update_user", manager, manager.getId())).isFalse();
        assertThat(call("can_update_user", hse, worker.getId())).isFalse();
        assertThat(call("can_update_user", admin, otherWorker.getId())).isTrue();
    }

    @Test
    void adminHoldsEveryPermission() {
        Integer missing = jdbc.queryForObject("""
                SELECT count(*)::int FROM permission p
                WHERE NOT has_permission(?, p.code)""", Integer.class, admin.getId());

        assertThat(missing).isZero();
    }

    @Test
    void everyPublicTableHasRowLevelSecurity() {
        assertThat(jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND NOT rowsecurity", String.class))
                .isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT rowsecurity FROM pg_tables WHERE schemaname = 'public' AND tablename = 'flyway_schema_history'",
                Boolean.class)).isTrue();
    }

    // --- helpers ---------------------------------------------------------------------------

    private Boolean call(String function, AppUser who, Long target) {
        em.flush();
        return jdbc.queryForObject("SELECT " + function + "(?, ?)", Boolean.class, who.getId(), target);
    }

    private AppUser user(String code, String roleName) {
        AppUser user = new AppUser();
        user.setRole(roles.findByRoleName(roleName).orElseThrow());
        user.setEmployeeCode(code);
        user.setFullName("Test " + code);
        user.setPasswordHash("x");
        return users.save(user);
    }

    private Team team(String name, AppUser teamManager) {
        Team team = new Team();
        team.setName(name);
        team.setManager(teamManager);
        return teams.save(team);
    }

    private PpeLinking link(String vestCode, AppUser wearer) {
        PpeVest vest = new PpeVest();
        vest.setVestCode(vestCode);
        vests.save(vest);

        PpeLinking link = new PpeLinking();
        link.setPpeVest(vest);
        link.setUser(wearer);
        return links.saveAndFlush(link);
    }

    private Violation violation(PpeLinking link) {
        Violation violation = new Violation();
        violation.setPpeLinking(link);
        violation.setCamera(camera);
        violation.setViolationType(ViolationType.NO_HARD_HAT);
        violation.setDetectedAt(OffsetDateTime.now());
        violation.setSnapshotPath("snapshot.jpg");
        return violation;
    }

    private Shift shift(AppUser who, OffsetDateTime start, OffsetDateTime end) {
        Shift shift = new Shift();
        shift.setUser(who);
        shift.setZone(zone);
        shift.setStartAt(start);
        shift.setEndAt(end);
        shift.setCreatedBy(manager);
        return shift;
    }

    private CheckIn faceCheckIn(AppUser who) {
        CheckIn visit = new CheckIn();
        visit.setUser(who);
        visit.setFaceSimilarity(BigDecimal.valueOf(90));
        return visit;
    }

    private Ticket ticket(String category, Violation violation) {
        Ticket ticket = new Ticket();
        ticket.setCategory(categories.findByCategoryName(category).orElseThrow());
        ticket.setSubject("Subject");
        ticket.setDescription("Description");
        ticket.setCreatedBy(worker);
        ticket.setViolation(violation);
        return ticket;
    }
}
