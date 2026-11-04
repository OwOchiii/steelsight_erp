package orochi.steelsight.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Forms and pages use the site's local time; the database stores instants. */
@Component
public class SiteTime {

    private final ZoneId zone;

    public SiteTime(@Value("${steelsight.time-zone}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public OffsetDateTime toInstant(LocalDateTime local) {
        return local == null ? null : local.atZone(zone).toOffsetDateTime();
    }

    public LocalDateTime toLocal(OffsetDateTime instant) {
        return instant == null ? null : instant.atZoneSameInstant(zone).toLocalDateTime();
    }

    public OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(zone).toOffsetDateTime();
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public LocalDate dateOf(OffsetDateTime instant) {
        return instant.atZoneSameInstant(zone).toLocalDate();
    }

    public LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
