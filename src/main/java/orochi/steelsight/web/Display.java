package orochi.steelsight.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/** Formatting used by the templates, as ${@display.dateTime(value)}. */
@Component("display")
@RequiredArgsConstructor
public class Display {

    private static final Locale VI = Locale.forLanguageTag("vi");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_VI = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", VI);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY_VI = DateTimeFormatter.ofPattern("EEE", VI);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH_VI = DateTimeFormatter.ofPattern("dd/MM", VI);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final SiteTime siteTime;
    private final MessageSource messages;

    public String date(OffsetDateTime value) {
        return value == null ? "" : date(siteTime.dateOf(value));
    }

    public String date(LocalDate value) {
        return value == null ? "" : (vietnamese() ? DATE_VI : DATE).format(value);
    }

    /** "Mon", for the head of a schedule column. */
    public String weekday(LocalDate value) {
        return (vietnamese() ? WEEKDAY_VI : WEEKDAY).format(value);
    }

    /** "2 Nov", for the head of a schedule column. */
    public String dayMonth(LocalDate value) {
        return (vietnamese() ? DAY_MONTH_VI : DAY_MONTH).format(value);
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

    /** NO_HARD_HAT becomes "No hard hat", or its translation from messages_vi.properties. */
    public String label(Enum<?> value) {
        if (value == null) {
            return "";
        }
        String words = value.name().replace('_', ' ').toLowerCase(Locale.ENGLISH);
        String english = Character.toUpperCase(words.charAt(0)) + words.substring(1);
        return translate("enum." + value.name(), english);
    }

    /** HSE_OFFICER becomes "HSE officer". Each role has its own label in the message files. */
    public String role(String name) {
        return name == null ? "" : translate("role." + name, name);
    }

    /** Category names are data, but the standard ones have translations. */
    public String category(String name) {
        return name == null ? "" : translate("category." + slug(name), name);
    }

    /**
     * Shows a message written in English in the code, such as a broken rule, in the user's language.
     * The code keeps English so the logs stay readable. "Enter between 0 and 240" is looked up as
     * msg.enter-between-n-and-n with the numbers as arguments. A missing translation shows the English.
     */
    public String message(String english) {
        if (english == null) {
            return "";
        }
        Object[] numbers = NUMBER.matcher(english).results().map(MatchResult::group).toArray();
        String key = "msg." + slug(NUMBER.matcher(english).replaceAll("n"));
        return messages.getMessage(key, numbers, english, LocaleContextHolder.getLocale());
    }

    /** "3 d" or "5 h": how long something has been waiting. */
    public String waiting(OffsetDateTime since) {
        if (since == null) {
            return "";
        }
        Duration waited = Duration.between(since, OffsetDateTime.now());
        Locale locale = LocaleContextHolder.getLocale();
        if (waited.toDays() >= 1) {
            return messages.getMessage("waiting.days", new Object[]{waited.toDays()}, locale);
        }
        return messages.getMessage("waiting.hours", new Object[]{Math.max(waited.toHours(), 0)}, locale);
    }

    private String translate(String key, String fallback) {
        return messages.getMessage(key, null, fallback, LocaleContextHolder.getLocale());
    }

    private boolean vietnamese() {
        return "vi".equals(LocaleContextHolder.getLocale().getLanguage());
    }

    private static String slug(String text) {
        return text.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
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
