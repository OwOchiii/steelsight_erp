package orochi.steelsight.web;

import orochi.steelsight.attendance.service.VisitStatus;
import orochi.steelsight.scheduling.model.Shift;
import orochi.steelsight.scheduling.model.ShiftStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Today's planned shifts against who actually came. */
final class TodayRoster {

    /** In the order a manager should look at them: people who haven't come matter most. */
    enum State { MISSING, LATE, ON_TIME, NOT_STARTED }

    record Row(Shift shift, VisitStatus visit, State state) {
    }

    /** @param percent the segment's share of today's shifts */
    record Segment(State state, long count, int percent) {
    }

    static List<Row> rows(List<Shift> shifts, List<VisitStatus> visits, OffsetDateTime now) {
        List<Row> rows = new ArrayList<>();
        for (Shift shift : shifts) {
            if (shift.getStatus() == ShiftStatus.CANCELLED) {
                continue;
            }
            VisitStatus visit = visitFor(shift, visits).orElse(null);
            rows.add(new Row(shift, visit, stateOf(shift, visit, now)));
        }
        rows.sort(Comparator.comparing(Row::state).thenComparing(row -> row.shift().getStartAt()));
        return rows;
    }

    static List<Segment> segments(List<Row> rows) {
        Map<State, Long> counts = new EnumMap<>(State.class);
        rows.forEach(row -> counts.merge(row.state(), 1L, Long::sum));
        List<Segment> segments = new ArrayList<>();
        for (State state : List.of(State.ON_TIME, State.LATE, State.MISSING, State.NOT_STARTED)) {
            long count = counts.getOrDefault(state, 0L);
            if (count > 0) {
                segments.add(new Segment(state, count, (int) Math.round(count * 100.0 / rows.size())));
            }
        }
        return segments;
    }

    /** The visit linked to this shift; failing that, the worker's visit today, as a manual entry may have no link. */
    private static Optional<VisitStatus> visitFor(Shift shift, List<VisitStatus> visits) {
        Optional<VisitStatus> linked = visits.stream()
                .filter(status -> status.visit().getShift() != null
                        && status.visit().getShift().getId().equals(shift.getId()))
                .findFirst();
        return linked.isPresent() ? linked : visits.stream()
                .filter(status -> status.visit().getShift() == null
                        && status.visit().getUser().getId().equals(shift.getUser().getId()))
                .findFirst();
    }

    private static State stateOf(Shift shift, VisitStatus visit, OffsetDateTime now) {
        if (visit != null) {
            return visit.isLate() ? State.LATE : State.ON_TIME;
        }
        return shift.hasStarted(now) ? State.MISSING : State.NOT_STARTED;
    }

    private TodayRoster() {
    }
}
