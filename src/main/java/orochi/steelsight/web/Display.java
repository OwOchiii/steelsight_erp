package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatting used by the templates, as ${@display.dateTime(value)}. */
@Component("display")
@RequiredArgsConstructor
public class Display {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final SiteTime siteTime;

    public String date(OffsetDateTime value) {
        return value == null ? "" : date(siteTime.dateOf(value));
    }

    public String date(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    /** "Mon", for the head of a schedule column. */
    public String weekday(LocalDate value) {
        return WEEKDAY.format(value);
    }

    /** "2 Nov", for the head of a schedule column. */
    public String dayMonth(LocalDate value) {
        return DAY_MONTH.format(value);
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

    /** A link to another page of the current list, keeping its other filters. */
    public String pageLink(int number) {
        UriComponents link = ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", Math.max(number, 0)).build();
        return link.getPath() + "?" + link.getQuery();
    }
}
