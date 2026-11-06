package orochi.steelsight.ppe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import orochi.steelsight.identity.model.AppUser;
import orochi.steelsight.identity.repository.AppUserRepository;
import orochi.steelsight.identity.service.AccessPolicy;
import orochi.steelsight.identity.service.Permissions;
import orochi.steelsight.ppe.model.SafetyLevel;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads safety scores. The rules live in the SQL functions of V8, because a database trigger uses them too. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SafetyScoreService {

    private static final RowMapper<Score> SCORE =
            (row, number) -> new Score(row.getInt("score"), SafetyLevel.valueOf(row.getString("level")));

    private static final RowMapper<TimeSlot> TIME_SLOT = (row, number) -> new TimeSlot(
            row.getObject("start_at", OffsetDateTime.class), row.getObject("end_at", OffsetDateTime.class),
            row.getInt("workers"), row.getBigDecimal("worker_hours"), row.getInt("violations"), row.getBigDecimal("per_100_hours"));

    private final AccessPolicy access;
    private final JdbcTemplate jdbc;
    private final AppUserRepository users;

    public record Score(int value, SafetyLevel level) {
    }

    public record WorkerScore(AppUser worker, Score score) {
    }

    /** How one time slot went. @param per100Hours null if the slot has no hours */
    public record TimeSlot(OffsetDateTime startAt, OffsetDateTime endAt, int workers, BigDecimal workerHours,
                           int violations, BigDecimal per100Hours) {
    }

    /** The actor's own score, or null if they aren't a worker. */
    public Score own(Long actorId) {
        access.require(actorId, Permissions.VIOLATION_READ_OWN);
        return find(actorId);
    }

    public Score of(Long actorId, Long workerId) {
        access.require(actorId, Permissions.USER_READ_ALL);
        return find(workerId);
    }

    /** Every worker, lowest score first, whichever team they are on. */
    public Page<WorkerScore> listWorkers(Long actorId, String search, Pageable page) {
        access.require(actorId, Permissions.USER_READ_ALL);
        String like = "%" + (search == null ? "" : search.strip().toLowerCase())
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String matching = """
                FROM safety_scores(now()) s JOIN app_user u ON u.id = s.user_id
                WHERE lower(u.full_name) LIKE ? OR lower(u.employee_code) LIKE ?
                """;
        Long total = jdbc.queryForObject("SELECT count(*) " + matching, Long.class, like, like);
        record Row(Long id, Score score) {
        }
        List<Row> rows = jdbc.query("SELECT u.id, s.score, s.level " + matching + "ORDER BY s.score, u.full_name, u.id LIMIT ? OFFSET ?",
                (row, number) -> new Row(row.getLong("id"), SCORE.mapRow(row, number)),
                like, like, page.getPageSize(), page.getOffset());
        Map<Long, AppUser> found = users.findAllById(rows.stream().map(Row::id).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        List<WorkerScore> workers = rows.stream().map(row -> new WorkerScore(found.get(row.id()), row.score())).toList();
        return new PageImpl<>(workers, page, total == null ? 0 : total);
    }

    /** Time slots that have started in this period, worst first. */
    public List<TimeSlot> listTimeSlots(Long actorId, OffsetDateTime from, OffsetDateTime to) {
        access.require(actorId, Permissions.VIOLATION_READ_ALL);
        return jdbc.query("SELECT * FROM safety_time_slots(?, ?)", TIME_SLOT, from, to);
    }

    private Score find(Long userId) {
        return jdbc.query("SELECT score, level FROM safety_scores(now()) WHERE user_id = ?", SCORE, userId)
                .stream().findFirst().orElse(null);
    }
}
