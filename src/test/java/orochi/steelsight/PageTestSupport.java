package orochi.steelsight;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.security.AuthenticatedUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/** Sign-in and test data shared by the page tests. */
abstract class PageTestSupport {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired TransactionTemplate transaction;
    @Autowired jakarta.persistence.EntityManager em;

    /** Writes out what the pages changed, so plain SQL in the test can see it. */
    void flush() {
        em.flush();
    }

    RequestPostProcessor as(Long userId) {
        return transaction.execute(status -> user(new AuthenticatedUser(users.findById(userId).orElseThrow())));
    }

    Long userWithRole(String code, String name, String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
                SELECT id, ?, ?, 'x' FROM role WHERE role_name = ? RETURNING id""", Long.class, code, name, role);
    }

    Long team(String name, Long manager) {
        return jdbc.queryForObject("INSERT INTO team (name, manager_id) VALUES (?, ?) RETURNING id", Long.class, name, manager);
    }

    void joinTeam(Long user, Long team) {
        jdbc.update("UPDATE app_user SET team_id = ? WHERE id = ?", team, user);
    }

    Long zone(String name) {
        return jdbc.queryForObject("INSERT INTO zone (name) VALUES (?) RETURNING id", Long.class, name);
    }

    Long camera(String code, Long zone) {
        return jdbc.queryForObject("INSERT INTO camera (camera_code, zone_id) VALUES (?, ?) RETURNING id", Long.class, code, zone);
    }

    Long category(String name) {
        return jdbc.queryForObject("SELECT id FROM ticket_category WHERE category_name = ?", Long.class, name);
    }

    Long ticket(Long category, Long creator, String subject) {
        return jdbc.queryForObject("""
                INSERT INTO ticket (category_id, subject, description, created_by)
                VALUES (?, ?, 'details', ?) RETURNING id""", Long.class, category, subject, creator);
    }

    Long shift(Long user, Long zone, String start, String end) {
        return jdbc.queryForObject("INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, "
                + start + ", " + end + ", ?) RETURNING id", Long.class, user, zone, user);
    }

    Long violation(Long user, Long camera, String type) {
        return jdbc.queryForObject("""
                INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, ?, now(), 'page.jpg') RETURNING id""", Long.class, user, camera, type);
    }

    String textColumn(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
