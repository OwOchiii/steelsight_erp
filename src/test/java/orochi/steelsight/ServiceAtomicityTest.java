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
    Long hse;
    Long otherHse;
    Long zone;
    Long camera;
    Long violation;
    Long category;

    @BeforeEach
    void seed() {
        worker = user("TX-W", "WORKER");
        hse = user("TX-H1", "HSE_OFFICER");
        otherHse = user("TX-H2", "HSE_OFFICER");
        zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('TX zone') RETURNING id", Long.class);
        camera = jdbc.queryForObject(
                "INSERT INTO camera (camera_code, zone_id) VALUES ('TX-CAM', ?) RETURNING id", Long.class, zone);
        violation = jdbc.queryForObject("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, 'NO_VEST', now(), 'tx.jpg') RETURNING id""", Long.class, worker, camera);
        category = jdbc.queryForObject(
                "SELECT id FROM ticket_category WHERE category_name = 'PPE dispute'", Long.class);
    }

    @AfterEach
    void cleanUp() {
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
        Long ticket = tickets.openDispute(worker, violation, "s", "d").getId();
        // A temporary constraint makes the ticket half of the decision fail
        jdbc.update("ALTER TABLE ticket ADD CONSTRAINT tx_block_resolve CHECK (status <> 'RESOLVED') NOT VALID");
        try {
            assertThatThrownBy(() -> violationService.decide(hse, violation, DisputeStatus.UPHELD, null))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.update("ALTER TABLE ticket DROP CONSTRAINT tx_block_resolve");
        }

        assertThat(disputeStatus()).isEqualTo("DISPUTED");
        assertThat(jdbc.queryForObject("SELECT status FROM ticket WHERE id = ?", String.class, ticket)).isEqualTo("OPEN");
    }

    @Test
    void twoOfficersDecidingAtOnceCannotBothWin() {
        tickets.openDispute(worker, violation, "s", "d");

        // Officer two loads the violation, officer one commits, then officer two tries to commit
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            violations.findById(violation).orElseThrow();
            jdbc.update("UPDATE violation SET dispute_status = 'UPHELD', reviewed_by = ?, reviewed_at = now(), "
                    + "version = version + 1 WHERE id = ?", hse, violation);
            violationService.decide(otherHse, violation, DisputeStatus.OVERTURNED, null);
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
