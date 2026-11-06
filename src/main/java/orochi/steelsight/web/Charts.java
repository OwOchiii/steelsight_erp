package orochi.steelsight.web;

import orochi.steelsight.ppe.model.Violation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns rows into the bars the dashboard draws. The templates only draw, they don't calculate. */
final class Charts {

    /** @param percent the bar's length against the longest bar in its chart */
    record Bar(Object key, long value, int percent) {
    }

    /** One column per day, including days with nothing, because a gap is the good news. */
    static List<Bar> perDay(Collection<Violation> violations, LocalDate firstDay, int days, SiteTime siteTime) {
        Map<LocalDate, Long> counts = violations.stream()
                .collect(Collectors.groupingBy(v -> siteTime.dateOf(v.getDetectedAt()), Collectors.counting()));
        Map<Object, Long> ordered = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = firstDay.plusDays(i);
            ordered.put(day, counts.getOrDefault(day, 0L));
        }
        return bars(ordered);
    }

    /** Largest first, so the biggest problem reads first. */
    static <K> List<Bar> ranked(Collection<Violation> violations, Function<Violation, K> key) {
        Map<K, Long> counts = violations.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
        Map<Object, Long> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().toString()))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return bars(ordered);
    }

    private static List<Bar> bars(Map<Object, Long> values) {
        long largest = values.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Bar> bars = new ArrayList<>();
        values.forEach((key, value) -> bars.add(
                new Bar(key, value, largest == 0 ? 0 : (int) Math.round(value * 100.0 / largest))));
        return bars;
    }

    private Charts() {
    }
}
