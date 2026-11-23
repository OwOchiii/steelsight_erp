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

    /** HSE Officers and Admins are office staff; workers and team leaders work on site (BR-06). */
    Long userWithRole(String code, String name, String role) {
        String group = role.equals("HSE_OFFICER") || role.equals("ADMIN") ? "OFFICE" : "SITE";
        return jdbc.queryForObject("""
                INSERT INTO app_user (role_id, employee_code, full_name, password_hash, staff_group)
                SELECT id, ?, ?, 'x', ? FROM role WHERE role_name = ? RETURNING id""", Long.class, code, name, group, role);
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

    /** A violation as the detection app saves it; with a person, also confirmed for them as HSE would (BR-48). */
    Long violation(Long user, Long camera, String type) {
        Long id = jdbc.queryForObject("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, now(), 'page.jpg') RETURNING id""", Long.class, camera, type);
        if (user != null) {
            confirmFor(id, user);
        }
        return id;
    }

    /** CONFIRMED for a site employee, REMINDER for an office one (BR-51). */
    void confirmFor(Long violation, Long user) {
        jdbc.update("""
                UPDATE violation SET user_id = ?, subject = 'EMPLOYEE', reviewed_at = now(),
                       review_status = CASE WHEN (SELECT staff_group FROM app_user WHERE id = ?) = 'OFFICE' THEN 'REMINDER' ELSE 'CONFIRMED' END
                WHERE id = ?""", user, user, violation);
    }

    String textColumn(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }
}
