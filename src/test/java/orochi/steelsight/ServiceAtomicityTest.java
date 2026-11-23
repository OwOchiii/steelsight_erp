package orochi.steelsight;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.ppe.model.DisputeStatus;
import orochi.steelsight.ppe.repository.ViolationRepository;
import orochi.steelsight.ppe.service.ViolationService;
import orochi.steelsight.ticketing.service.TicketService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These tests really commit, because they check what a failed service call leaves behind.
 * They clean up after themselves so the rolled-back tests never see this data.
 */
@SpringBootTest
@Import(TestDatabase.class)
class ServiceAtomicityTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired TicketService tickets;
    @Autowired ViolationService violationService;
    @Autowired ViolationRepository violations;
    @Autowired TransactionTemplate transaction;

    Long worker;
    Long admin;
    Long otherAdmin;
    Long zone;
    Long camera;
    static final String FACTS = "The officer on duty saw the worker without a vest.";
    static final String NOTE = "Decided after checking the photo.";

    Long violation;
    Long category;

    @BeforeEach
    void seed() {
        worker = user("TX-W", "WORKER");
        admin = user("TX-A1", "ADMIN");
        otherAdmin = user("TX-A2", "ADMIN");
        zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('TX zone') RETURNING id", Long.class);
        camera = jdbc.queryForObject(
                "INSERT INTO camera (camera_code, zone_id) VALUES ('TX-CAM', ?) RETURNING id", Long.class, zone);
        violation = jdbc.queryForObject("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, 'NO_VEST', now(), 'tx.jpg') RETURNING id""", Long.class, worker, camera);
        // An HSE Officer confirmed it, so it can be appealed (BR-64)
        jdbc.update("""
                UPDATE violation SET review_status = 'CONFIRMED', subject = 'EMPLOYEE', user_id = ?, reviewed_at = now()
                WHERE id = ?""", worker, violation);
        category = jdbc.queryForObject(
                "SELECT id FROM ticket_category WHERE code = 'PPE_APPEAL'", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM notification WHERE user_id IN (SELECT id FROM app_user WHERE employee_code LIKE 'TX-%')");
        jdbc.update("DELETE FROM ticket WHERE created_by = ?", worker);
        jdbc.update("DELETE FROM violation WHERE id = ?", violation);
        jdbc.update("DELETE FROM camera WHERE id = ?", camera);
        jdbc.update("DELETE FROM zone WHERE id = ?", zone);
        jdbc.update("DELETE FROM app_user WHERE employee_code LIKE 'TX-%'");
    }

    @Test
    void failedTicketInsertLeavesTheViolationUndisputed() {
        // A live ticket already holds this violation, so opening the dispute fails at the ticket step
        jdbc.update("""
                INSERT INTO ticket (category_id, subject, description, created_by, violation_id)
                VALUES (?, 'old', 'old', ?, ?)""", category, worker, violation);

        assertThatThrownBy(() -> tickets.openDispute(worker, violation, "new", "new"))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(disputeStatus()).isEqualTo("NONE");
    }

    @Test
    void failedDecisionLeavesBothTheViolationAndTheTicketUntouched() {
        Long ticket = tickets.openDispute(worker, violation, null, "I wore my vest the whole time").getId();
        // A temporary constraint makes the ticket half of the decision fail
        jdbc.update("ALTER TABLE ticket ADD CONSTRAINT tx_block_resolve CHECK (status <> 'RESOLVED') NOT VALID");
        try {
            assertThatThrownBy(() -> tickets.decideAppeal(admin, violation, DisputeStatus.UPHELD, FACTS, NOTE, null))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.update("ALTER TABLE ticket DROP CONSTRAINT tx_block_resolve");
        }

        assertThat(disputeStatus()).isEqualTo("DISPUTED");
        assertThat(jdbc.queryForObject("SELECT status FROM ticket WHERE id = ?", String.class, ticket)).isEqualTo("OPEN");
    }

    @Test
    void twoAdminsDecidingAtOnceCannotBothWin() {
        tickets.openDispute(worker, violation, null, "I wore my vest the whole time");

        // Admin two loads the violation, admin one commits, then admin two tries to commit
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            violations.findById(violation).orElseThrow();
            jdbc.update("UPDATE violation SET dispute_status = 'UPHELD', appeal_decided_by = ?, appeal_decided_at = now(), "
                    + "version = version + 1 WHERE id = ?", admin, violation);
            tickets.decideAppeal(otherAdmin, violation, DisputeStatus.OVERTURNED, FACTS, NOTE, null);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(disputeStatus()).isEqualTo("DISPUTED");
    }

    private String disputeStatus() {
        return jdbc.queryForObject("SELECT dispute_status FROM violation WHERE id = ?", String.class, violation);
    }

    private Long user(String code, String roleName) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
                SELECT id, ?, 'Test', 'x' FROM role WHERE role_name = ? RETURNING id""", Long.class, code, roleName);
    }
}
