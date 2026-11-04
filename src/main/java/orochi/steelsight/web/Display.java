package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import orochi.steelsight.repository.BaseRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;

/** Formatting used by the templates, as ${@display.dateTime(value)}. */
@Component("display")
@RequiredArgsConstructor
public class Display {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final SiteTime siteTime;

    public String date(OffsetDateTime value) {
        return value == null ? "" : date(siteTime.dateOf(value));
    }

    public String date(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    public String time(OffsetDateTime value) {
        return value == null ? "" : TIME.format(siteTime.toLocal(value));
    }

    public String dateTime(OffsetDateTime value) {
        return value == null ? "" : date(value) + ", " + time(value);
    }

    /** The database computes shift length with many decimals: 8.0000 becomes "8", 7.5000 stays "7.5". */
    public String hours(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** NO_HARD_HAT becomes "No hard hat". */
    public String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String words = value.name().replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /** The class that colours a status badge. */
    public String badge(Enum<?> value) {
        return value == null ? "" : "badge-" + value.name().toLowerCase(Locale.ENGLISH);
    }

    /** True when a list stopped at the row limit, so the page can say there may be more. */
    public boolean atLimit(Collection<?> rows) {
        return rows.size() >= BaseRepository.LIST_LIMIT.max();
    }
}
