package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SRS BR-35, run by close_stale_check_ins() (the detection app and the jobs call it): a visit with no
 * check-out is flagged Incomplete 20 minutes after its shift ends, and closed by the system 4 hours after,
 * so the next scan is a check-in. The time it writes is never paid. A visit with no shift closes once its
 * day has passed, counted from today's midnight so the tests don't depend on when they run.
 */
@SpringBootTest
@Import(TestDatabase.class)
@Transactional
class AutoCloseCheckInTest {

    private static final String NOW = "now()";
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
    void aVisitIsNotFlaggedWithinTwentyMinutesOfTheShiftEnd() {
        Long visit = visit(shift(NOW, "- interval '8 hours'", "- interval '19 minutes'"), NOW, "- interval '8 hours'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(isOpen(visit)).isTrue();
        assertThat(isIncomplete(visit)).isFalse();
    }

    @Test
    void aVisitIsFlaggedIncompleteTwentyMinutesAfterTheShiftButStaysOpen() {
        Long visit = visit(shift(NOW, "- interval '8 hours'", "- interval '21 minutes'"), NOW, "- interval '8 hours'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(isOpen(visit)).isTrue();
        assertThat(isIncomplete(visit)).isTrue();
    }

    @Test
    void aVisitClosesFourHoursAfterTheShiftWithoutGuessingACheckOut() {
        Long visit = visit(shift(NOW, "- interval '12 hours'", "- interval '4 hours 1 minute'"), NOW, "- interval '12 hours'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(isOpen(visit)).isFalse();
        assertThat(isIncomplete(visit)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT c.auto_closed AND c.check_out_at = s.end_at + interval '4 hours'
                FROM check_in c JOIN shift s ON s.id = c.shift_id WHERE c.id = ?""", Boolean.class, visit)).isTrue();
    }

    @Test
    void aVisitStillOpensWithinFourHours() {
        Long visit = visit(shift(NOW, "- interval '11 hours'", "- interval '3 hours 59 minutes'"), NOW, "- interval '11 hours'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(isOpen(visit)).isTrue();
    }

    @Test
    void aVisitWithoutAShiftClosesAtMidnightOnceItsDayHasPassed() {
        Long yesterday = visit(null, MIDNIGHT, "- interval '1 hour'");
        jdbc.execute("SELECT close_stale_check_ins()");
        assertThat(jdbc.queryForObject("SELECT auto_closed AND check_out_at = " + MIDNIGHT + " FROM check_in WHERE id = ?",
                Boolean.class, yesterday)).isTrue();

        Long today = visit(null, MIDNIGHT, "+ interval '0 minutes'");
        jdbc.execute("SELECT close_stale_check_ins()");
        assertThat(isOpen(today)).isTrue();
    }

    @Test
    void aCancelledShiftCountsAsNoShift() {
        Long shift = shift(MIDNIGHT, "+ interval '3 days'", "+ interval '3 days 8 hours'");
        jdbc.update("UPDATE shift SET status = 'CANCELLED' WHERE id = ?", shift);
        Long visit = visit(shift, MIDNIGHT, "- interval '1 hour'");

        jdbc.execute("SELECT close_stale_check_ins()");

        assertThat(isOpen(visit)).isFalse();
    }

    /** SRS 3.2.35: a check-in closes a visit still open from an earlier day, so the gate never blocks. */
    @Test
    void aNewCheckInClosesAVisitLeftOpenFromAnEarlierDay() {
        Long old = visit(null, MIDNIGHT, "- interval '3 hours'");
        Long today = visit(null, NOW, "");

        assertThat(isOpen(old)).isFalse();
        assertThat(isIncomplete(old)).isTrue();
        assertThat(isOpen(today)).isTrue();
    }

    private Long shift(String base, String start, String end) {
        return jdbc.queryForObject("INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, "
                + base + start + ", " + base + end + ", ?) RETURNING id", Long.class, worker, zone, worker);
    }

    private Long visit(Long shift, String base, String checkIn) {
        return jdbc.queryForObject("INSERT INTO check_in (user_id, shift_id, check_in_at, face_similarity) VALUES (?, ?, "
                + base + checkIn + ", 90) RETURNING id", Long.class, worker, shift);
    }

    private boolean isIncomplete(Long visit) {
        return jdbc.queryForObject("SELECT incomplete FROM check_in WHERE id = ?", Boolean.class, visit);
    }

    private boolean isOpen(Long visit) {
        return jdbc.queryForObject("SELECT check_out_at IS NULL FROM check_in WHERE id = ?", Boolean.class, visit);
    }
}
