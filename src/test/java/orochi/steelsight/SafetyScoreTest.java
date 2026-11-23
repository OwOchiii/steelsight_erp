package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.jobs.SiteJobs;

import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SRS BR-56 to BR-58 and BR-70, in safety_scores() (V21): score = max(0, 100 - 5 x N) where N is the
 * CONFIRMED violations of a Site employee in the month, not OVERTURNED; bonus level 0 above 80, 1 at
 * 71-80, 2 at 61-70, 3 at 60 or less. A locked month reads the frozen monthly_score.
 */
@SpringBootTest
@Import(TestDatabase.class)
@Transactional
class SafetyScoreTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired SiteJobs jobs;

    Long worker;
    Long camera;

    @BeforeEach
    void seed() {
        worker = user("SCORE-W", "WORKER", "SITE");
        Long zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('Score zone') RETURNING id", Long.class);
        camera = jdbc.queryForObject("INSERT INTO camera (camera_code, zone_id) VALUES ('SCORE-CAM', ?) RETURNING id", Long.class, zone);
    }

    /** BR-56: only the Site group with the role WORKER or MANAGER has a score. */
    @Test
    void br56_siteWorkersAndTeamLeadersHaveAScoreAndNobodyElse() {
        assertThat(score(worker)).isEqualTo(100);
        assertThat(score(user("SCORE-M", "MANAGER", "SITE"))).isEqualTo(100);
        assertThat(score(user("SCORE-O", "WORKER", "OFFICE"))).isNull();
        assertThat(score(user("SCORE-H", "HSE_OFFICER", "OFFICE"))).isNull();
        assertThat(score(user("SCORE-A", "ADMIN", "OFFICE"))).isNull();
    }

    /** BR-57: each confirmed violation costs 5 points, whatever was missing. */
    @Test
    void br57_eachConfirmedViolationCostsFivePoints() {
        confirmed(worker, "NO_VEST", 0);
        assertThat(score(worker)).isEqualTo(95);
        confirmed(worker, "NO_VEST", 1);       // the same type again counts again: the desktop app already waits 60 s (BR-44)
        confirmed(worker, "NO_HARD_HAT_AND_VEST", 2);
        assertThat(score(worker)).isEqualTo(85);
    }

    @Test
    void br48_aPendingOrDismissedViolationCostsNothing() {
        insert("NO_VEST", "PENDING_REVIEW", 0);
        Long dismissed = insert("NO_VEST", "PENDING_REVIEW", 1);
        jdbc.update("UPDATE violation SET review_status = 'DISMISSED', dismiss_reason = 'FALSE_DETECTION' WHERE id = ?", dismissed);

        assertThat(score(worker)).isEqualTo(100);
    }

    @Test
    void br52_aStrangerCostsNobodyAnything() {
        Long violation = insert("NO_VEST", "PENDING_REVIEW", 0);
        jdbc.update("UPDATE violation SET review_status = 'CONFIRMED', subject = 'STRANGER' WHERE id = ?", violation);

        assertThat(score(worker)).isEqualTo(100);
    }

    @Test
    void br57_anOverturnedViolationGivesItsPointsBackAndADisputedOneStillCounts() {
        Long violation = confirmed(worker, "NO_VEST", 0);
        jdbc.update("UPDATE violation SET dispute_status = 'DISPUTED' WHERE id = ?", violation);
        assertThat(score(worker)).isEqualTo(95);

        jdbc.update("UPDATE violation SET dispute_status = 'OVERTURNED', appeal_decided_by = ?, appeal_decided_at = now() WHERE id = ?",
                user("SCORE-A", "ADMIN", "OFFICE"), violation);
        assertThat(score(worker)).isEqualTo(100);
    }

    @Test
    void br57_theScoreResetsAtTheStartOfTheMonthInSiteTime() {
        // 16:59 UTC on 31 Oct is 23:59 on 31 Oct at the site; 17:00 UTC is already 1 Nov there.
        confirmedAt(worker, "timestamptz '2026-10-31 16:59+00'");
        assertThat(scoreAt(worker, "2026-10-31 16:59:30+00")).isEqualTo(95);
        assertThat(scoreAt(worker, "2026-10-31 17:00:00+00")).isEqualTo(100);
    }

    @Test
    void br57_theScoreStopsAtZero() {
        for (int i = 0; i < 25; i++) {
            confirmed(worker, "NO_VEST", i);
        }
        assertThat(score(worker)).isZero();
    }

    /** BR-58: above 80 level 0; 71-80 level 1; 61-70 level 2; 60 or less level 3. */
    @Test
    void br58_theBonusLevelFollowsTheScore() {
        assertThat(level(worker)).isZero();                 // 100
        for (int i = 0; i < 3; i++) {
            confirmed(worker, "NO_VEST", i);
        }
        assertThat(score(worker)).isEqualTo(85);
        assertThat(level(worker)).isZero();
        confirmed(worker, "NO_VEST", 3);                    // 80
        assertThat(level(worker)).isEqualTo(1);
        confirmed(worker, "NO_VEST", 4);                    // 75
        confirmed(worker, "NO_VEST", 5);                    // 70
        assertThat(level(worker)).isEqualTo(2);
        confirmed(worker, "NO_VEST", 6);                    // 65
        confirmed(worker, "NO_VEST", 7);                    // 60
        assertThat(level(worker)).isEqualTo(3);
        for (int i = 8; i < 25; i++) {
            confirmed(worker, "NO_VEST", i);
        }
        assertThat(level(worker)).as("level 3 is the largest reduction").isEqualTo(3);
    }

    /** BR-70: the lock freezes each score; a later change doesn't alter the locked month. */
    @Test
    void br70_theMonthLockFreezesTheScores() {
        confirmedAt(worker, "timestamptz '2026-09-10 09:00+07'");
        assertThat(scoreAt(worker, "2026-09-15 00:00:00+07")).isEqualTo(95);

        jobs.lock(YearMonth.of(2026, 9));
        Long late = confirmedAt(worker, "timestamptz '2026-09-11 09:00+07'");

        assertThat(scoreAt(worker, "2026-09-15 00:00:00+07")).as("frozen at the lock").isEqualTo(95);
        assertThat(jdbc.queryForObject("SELECT score FROM monthly_score WHERE user_id = ? AND month = '2026-09-01'", Integer.class, worker))
                .isEqualTo(95);
        assertThat(late).isNotNull();
    }

    @Test
    void theDetectionRoleCanInsertAndTheViolationWaitsForReview() {
        jdbc.execute("SET LOCAL ROLE steelsight_detection");
        String insert = """
                INSERT INTO violation (event_id, user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES ('00000000-0000-0000-0000-000000000077', ?, ?, 'NO_VEST', now(), 'x.jpg')
                ON CONFLICT (event_id) DO NOTHING""";
        jdbc.update(insert, worker, camera);
        int retried = jdbc.update(insert, worker, camera);
        jdbc.execute("RESET ROLE");

        assertThat(retried).isZero();
        assertThat(score(worker)).as("not confirmed yet, so no points lost").isEqualTo(100);
        Map<String, Object> row = jdbc.queryForMap("SELECT review_status, suggested_user_id FROM violation WHERE event_id = '00000000-0000-0000-0000-000000000077'");
        assertThat(row.get("review_status")).isEqualTo("PENDING_REVIEW");
        assertThat(row.get("suggested_user_id")).isEqualTo(worker);
    }

    @Test
    void timeSlotsGroupShiftsAndCompareByWorkerHours() {
        Long other = user("SCORE-W2", "WORKER", "SITE");
        Long zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('Slot zone') RETURNING id", Long.class);
        String start = "timestamptz '2026-03-02 08:00+07'";
        String end = "timestamptz '2026-03-02 16:00+07'";
        shift(worker, zone, start, end);
        shift(other, zone, start, end);
        confirmedAt(worker, "timestamptz '2026-03-02 09:00+07'");
        Long dismissed = confirmedAt(worker, "timestamptz '2026-03-02 09:01+07'");
        jdbc.update("UPDATE violation SET review_status = 'DISMISSED', dismiss_reason = 'FALSE_DETECTION', user_id = NULL, subject = NULL WHERE id = ?", dismissed);
        jdbc.update("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, 'NO_VEST', timestamptz '2026-03-02 10:00+07', 'x.jpg')""", camera);
        confirmedAt(worker, "timestamptz '2026-03-02 18:00+07'");

        var slots = jdbc.queryForList("""
                SELECT workers, worker_hours, violations, per_100_hours
                FROM safety_time_slots(timestamptz '2026-03-02 00:00+07', timestamptz '2026-03-09 00:00+07')""");

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).get("workers")).isEqualTo(2);
        assertThat(slots.get(0).get("violations")).as("a dismissed one and one after the shift don't count; a pending one does").isEqualTo(2);
        assertThat(((java.math.BigDecimal) slots.get(0).get("worker_hours")).intValue()).isEqualTo(16);
        assertThat(((java.math.BigDecimal) slots.get(0).get("per_100_hours")).doubleValue()).isEqualTo(12.5);
    }

    private Long user(String code, String role, String group) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (role_id, employee_code, full_name, password_hash, staff_group)
                SELECT id, ?, ?, 'x', ? FROM role WHERE role_name = ? RETURNING id""", Long.class, code, code, group, role);
    }

    private void shift(Long user, Long zone, String start, String end) {
        jdbc.update("INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, " + start + ", " + end + ", ?)",
                user, zone, user);
    }

    private static final String THIS_MONTH = "date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh'";

    private Long confirmed(Long user, String type, int step) {
        Long id = insert(type, "PENDING_REVIEW", step);
        confirm(id, user);
        return id;
    }

    private Long confirmedAt(Long user, String when) {
        Long id = jdbc.queryForObject("INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path) VALUES (?, 'NO_VEST', "
                + when + ", 'x.jpg') RETURNING id", Long.class, camera);
        confirm(id, user);
        return id;
    }

    private Long insert(String type, String status, int step) {
        return jdbc.queryForObject("INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path) VALUES (?, ?, "
                + THIS_MONTH + " + interval '1 minute' + make_interval(mins => " + step + "), 'x.jpg') RETURNING id", Long.class, camera, type);
    }

    private void confirm(Long violation, Long user) {
        jdbc.update("UPDATE violation SET user_id = ?, subject = 'EMPLOYEE', review_status = 'CONFIRMED', reviewed_at = now() WHERE id = ?",
                user, violation);
    }

    private Integer score(Long user) {
        return scoreAt(user, null);
    }

    private Integer level(Long user) {
        return jdbc.query("SELECT level FROM safety_scores(now()) WHERE user_id = ?", (row, n) -> row.getInt(1), user)
                .stream().findFirst().orElse(null);
    }

    private Integer scoreAt(Long user, String at) {
        String moment = at == null ? "now()" : "timestamptz '" + at + "'";
        return jdbc.query("SELECT score FROM safety_scores(" + moment + ") WHERE user_id = ?", (row, n) -> row.getInt(1), user)
                .stream().findFirst().orElse(null);
    }
}
