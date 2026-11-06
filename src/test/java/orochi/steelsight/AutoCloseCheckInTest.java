package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Times count from today's midnight at the site, so the results don't depend on when this runs. */
@SpringBootTest
@Import(TestDatabase.class)
@Transactional
class AutoCloseCheckInTest {

    private static final String MIDNIGHT = "(date_trunc('day', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh')";

    @Autowired JdbcTemplate jdbc;

    Long worker;
    Long zone;

    @BeforeEach
    void seed() {
        worker = jdbc.queryForObject("""
                INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
                SELECT id, 'AUTO-W', 'Auto Worker', 'x' FROM role WHERE role_name = 'WORKER' RETURNING id""", Long.class);
        zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('Auto Zone') RETURNING id", Long.class);
    }

    @Test
    void aVisitToAShiftThatEndedTodayStaysOpen() {
        Long visit = visit(shift("- interval '9 hours'", "+ interval '1 minute'"), "- interval '9 hours'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(isOpen(visit)).isTrue();
    }

    @Test
    void aVisitToAShiftThatEndedYesterdayClosesAtTheShiftsEnd() {
        Long visit = visit(shift("- interval '9 hours'", "- interval '1 minute'"), "- interval '9 hours'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(isOpen(visit)).isFalse();
        assertThat(jdbc.queryForObject("SELECT auto_closed AND check_out_at = " + MIDNIGHT + " - interval '1 minute' FROM check_in WHERE id = ?",
                Boolean.class, visit)).isTrue();
    }

    @Test
    void aVisitWithoutAShiftClosesAtMidnightOnceItsDayHasPassed() {
        Long yesterday = visit(null, "- interval '1 hour'");
        jdbc.execute("SELECT close_stale_check_ins()");
        assertThat(jdbc.queryForObject("SELECT auto_closed AND check_out_at = " + MIDNIGHT + " FROM check_in WHERE id = ?",
                Boolean.class, yesterday)).isTrue();

        Long today = visit(null, "+ interval '0 minutes'");
        jdbc.execute("SELECT close_stale_check_ins()");
        assertThat(isOpen(today)).isTrue();
    }

    @Test
    void aCheckInAfterTheShiftEndedStillClosesAfterItStarted() {
        Long visit = visit(shift("- interval '9 hours'", "- interval '2 hours'"), "- interval '1 hour'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(jdbc.queryForObject("SELECT auto_closed AND check_out_at = check_in_at + interval '1 minute' FROM check_in WHERE id = ?",
                Boolean.class, visit)).isTrue();
    }

    @Test
    void aCancelledShiftCountsAsNoShift() {
        Long shift = shift("+ interval '3 days'", "+ interval '4 days'");
        jdbc.update("UPDATE shift SET status = 'CANCELLED' WHERE id = ?", shift);
        Long visit = visit(shift, "- interval '1 hour'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(isOpen(visit)).isFalse();
    }

    private Long shift(String start, String end) {
        return jdbc.queryForObject("INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, "
                + MIDNIGHT + start + ", " + MIDNIGHT + end + ", ?) RETURNING id", Long.class, worker, zone, worker);
    }

    private Long visit(Long shift, String checkIn) {
        return jdbc.queryForObject("INSERT INTO check_in (user_id, shift_id, check_in_at, face_similarity) VALUES (?, ?, "
                + MIDNIGHT + checkIn + ", 90) RETURNING id", Long.class, worker, shift);
    }

    private boolean isOpen(Long visit) {
        return jdbc.queryForObject("SELECT check_out_at IS NULL FROM check_in WHERE id = ?", Boolean.class, visit);
    }
}
