package orochi.steelsight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The score and its notices live in SQL, because detection writes violations straight to the database. */
@SpringBootTest
@Import(TestDatabase.class)
@Transactional
class SafetyScoreTest {

    @Autowired JdbcTemplate jdbc;

    Long worker;
    Long camera;

    @BeforeEach
    void seed() {
        worker = user("SCORE-W", "WORKER");
        Long zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('Score zone') RETURNING id", Long.class);
        camera = jdbc.queryForObject("INSERT INTO camera (camera_code, zone_id) VALUES ('SCORE-CAM', ?) RETURNING id", Long.class, zone);
    }

    @Test
    void aWorkerStartsAt100AndOtherRolesHaveNoScore() {
        assertThat(score(worker)).isEqualTo(100);
        assertThat(score(user("SCORE-H", "HSE_OFFICER"))).isNull();
        assertThat(score(user("SCORE-M", "MANAGER"))).isNull();
    }

    @Test
    void repeatsInTheSameHalfHourCountOnce() {
        hit(worker, "NO_VEST", 0);
        hit(worker, "NO_VEST", 0);
        hit(worker, "NO_VEST", 0);
        assertThat(score(worker)).isEqualTo(90);

        hit(worker, "NO_HARD_HAT", 0);
        assertThat(score(worker)).as("another type counts separately").isEqualTo(80);
    }

    @Test
    void theNextHalfHourCountsAgain() {
        hit(worker, "NO_VEST", 0);
        hit(worker, "NO_VEST", 1);
        assertThat(score(worker)).isEqualTo(80);
    }

    @Test
    void anOverturnedViolationGivesItsPointsBack() {
        Long violation = hit(worker, "NO_VEST", 0);
        assertThat(score(worker)).isEqualTo(90);

        jdbc.update("UPDATE violation SET dispute_status = 'OVERTURNED', reviewed_by = ?, reviewed_at = now() WHERE id = ?",
                user("SCORE-H", "HSE_OFFICER"), violation);
        assertThat(score(worker)).isEqualTo(100);
    }

    @Test
    void aDisputedViolationStillCounts() {
        Long violation = hit(worker, "NO_VEST", 0);
        jdbc.update("UPDATE violation SET dispute_status = 'DISPUTED' WHERE id = ?", violation);
        assertThat(score(worker)).isEqualTo(90);
    }

    @Test
    void theScoreResetsAtTheStartOfTheMonthInSiteTime() {
        // 16:59 UTC on 31 Oct is 23:59 on 31 Oct at the site; 17:00 UTC is already 1 Nov there.
        insert(worker, "NO_VEST", "timestamptz '2026-10-31 16:59+00'");
        assertThat(scoreAt(worker, "2026-10-31 16:59:30+00")).isEqualTo(90);
        assertThat(scoreAt(worker, "2026-10-31 17:00:00+00")).isEqualTo(100);

        insert(worker, "NO_HARD_HAT", "timestamptz '2026-10-31 17:00+00'");
        assertThat(scoreAt(worker, "2026-11-15 12:00:00+00")).isEqualTo(90);
    }

    @Test
    void theScoreStopsAtZero() {
        for (int i = 0; i < 12; i++) {
            hit(worker, "NO_VEST", i);
        }
        assertThat(score(worker)).isZero();
    }

    @Test
    void aWarningComesWithTheThirdCountedViolationAndADangerWithTheSixth() {
        hit(worker, "NO_VEST", 0);
        hit(worker, "NO_VEST", 1);
        assertThat(kinds(worker)).isEmpty();

        hit(worker, "NO_VEST", 2);
        assertThat(kinds(worker)).containsExactly("SAFETY_WARNING");

        hit(worker, "NO_VEST", 3);
        hit(worker, "NO_VEST", 4);
        assertThat(kinds(worker)).as("each level is sent once a month").containsExactly("SAFETY_WARNING");

        hit(worker, "NO_VEST", 5);
        assertThat(kinds(worker)).containsExactly("SAFETY_DANGER", "SAFETY_WARNING");
        hit(worker, "NO_VEST", 6);
        assertThat(kinds(worker)).hasSize(2);
    }

    @Test
    void jumpingPastWarningSendsOnlyTheDanger() {
        for (int i = 0; i < 6; i++) {
            jdbc.update("""
                    INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                    VALUES (?, 'NO_VEST', date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh' + make_interval(mins => ?), 'x.jpg')""", camera, 100 + i * 30);
        }
        // HSE names the worker on all of them at once, in one statement
        jdbc.execute("ALTER TABLE violation DISABLE TRIGGER trg_violation_safety_level");
        jdbc.update("UPDATE violation SET user_id = ? WHERE camera_id = ? AND user_id IS NULL", worker, camera);
        jdbc.execute("ALTER TABLE violation ENABLE TRIGGER trg_violation_safety_level");
        hit(worker, "NO_VEST", 0);
        assertThat(kinds(worker)).containsExactly("SAFETY_DANGER");
    }

    @Test
    void anUnidentifiedViolationCostsNothingUntilHseNamesTheWorker() {
        hit(worker, "NO_VEST", 0);
        hit(worker, "NO_VEST", 1);
        Long unknown = jdbc.queryForObject("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, 'NO_VEST', date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh' + interval '2 hours', 'x.jpg') RETURNING id""", Long.class, camera);
        assertThat(score(worker)).isEqualTo(80);

        jdbc.update("UPDATE violation SET user_id = ? WHERE id = ?", worker, unknown);
        assertThat(score(worker)).isEqualTo(70);
        assertThat(kinds(worker)).containsExactly("SAFETY_WARNING");
    }

    @Test
    void theDetectionRoleCanInsertAndTheTriggerStillWritesTheNotice() {
        hit(worker, "NO_VEST", 0);
        hit(worker, "NO_VEST", 1);
        UUID event = UUID.randomUUID();

        jdbc.execute("SET LOCAL ROLE steelsight_detection");
        String insert = """
                INSERT INTO violation (event_id, user_id, camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, ?, ?, 'NO_VEST', date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh' + interval '2 hours', 'x.jpg')
                ON CONFLICT (event_id) DO NOTHING""";
        jdbc.update(insert, event, worker, camera);
        int retried = jdbc.update(insert, event, worker, camera);
        jdbc.execute("RESET ROLE");

        assertThat(retried).isZero();
        assertThat(kinds(worker)).containsExactly("SAFETY_WARNING");
        assertThat(jdbc.queryForObject("SELECT score FROM notification WHERE user_id = ?", Integer.class, worker)).isEqualTo(70);
    }

    @Test
    void timeSlotsGroupShiftsAndCompareByWorkerHours() {
        Long other = user("SCORE-W2", "WORKER");
        Long zone = jdbc.queryForObject("INSERT INTO zone (name) VALUES ('Slot zone') RETURNING id", Long.class);
        String start = "timestamptz '2026-03-02 08:00+07'";
        String end = "timestamptz '2026-03-02 16:00+07'";
        shift(worker, zone, start, end);
        shift(other, zone, start, end);
        insert(worker, "NO_VEST", "timestamptz '2026-03-02 09:00+07'");
        insert(worker, "NO_VEST", "timestamptz '2026-03-02 09:01+07'");
        jdbc.update("""
                INSERT INTO violation (camera_id, violation_type, detected_at, snapshot_path)
                VALUES (?, 'NO_VEST', timestamptz '2026-03-02 10:00+07', 'x.jpg')""", camera);
        insert(worker, "NO_VEST", "timestamptz '2026-03-02 18:00+07'");

        var slots = jdbc.queryForList("""
                SELECT workers, worker_hours, violations, per_100_hours
                FROM safety_time_slots(timestamptz '2026-03-02 00:00+07', timestamptz '2026-03-09 00:00+07')""");

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).get("workers")).isEqualTo(2);
        assertThat(slots.get(0).get("violations")).as("a repeat and one after the shift don't count; one with no worker does").isEqualTo(2);
        assertThat(((java.math.BigDecimal) slots.get(0).get("worker_hours")).intValue()).isEqualTo(16);
        assertThat(((java.math.BigDecimal) slots.get(0).get("per_100_hours")).doubleValue()).isEqualTo(12.5);
    }

    private Long user(String code, String role) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (role_id, employee_code, full_name, password_hash)
                SELECT id, ?, ?, 'x' FROM role WHERE role_name = ? RETURNING id""", Long.class, code, code, role);
    }

    private void shift(Long user, Long zone, String start, String end) {
        jdbc.update("INSERT INTO shift (user_id, zone_id, start_at, end_at, created_by) VALUES (?, ?, " + start + ", " + end + ", ?)",
                user, zone, user);
    }

    /** Violations in the current month, one 30-minute block per step, so each step counts. */
    private Long hit(Long user, String type, int step) {
        return insert(user, type, "date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh') AT TIME ZONE 'Asia/Ho_Chi_Minh' + interval '1 minute' + make_interval(mins => " + (step * 30) + ")");
    }

    private Long insert(Long user, String type, String when) {
        return jdbc.queryForObject("INSERT INTO violation (user_id, camera_id, violation_type, detected_at, snapshot_path) VALUES (?, ?, ?, "
                + when + ", 'x.jpg') RETURNING id", Long.class, user, camera, type);
    }

    private Integer score(Long user) {
        return jdbc.query("SELECT score FROM safety_scores(now()) WHERE user_id = ?", (row, n) -> row.getInt(1), user)
                .stream().findFirst().orElse(null);
    }

    private Integer scoreAt(Long user, String at) {
        return jdbc.query("SELECT score FROM safety_scores(timestamptz '" + at + "') WHERE user_id = ?", (row, n) -> row.getInt(1), user)
                .stream().findFirst().orElse(null);
    }

    private List<String> kinds(Long user) {
        return jdbc.queryForList("SELECT kind FROM notification WHERE user_id = ? ORDER BY kind", String.class, user);
    }
}
