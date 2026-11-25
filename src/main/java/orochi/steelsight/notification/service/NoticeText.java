package orochi.steelsight.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import orochi.steelsight.notification.model.Notification;
import orochi.steelsight.notification.model.NotificationKind;
import orochi.steelsight.web.SiteTime;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a notice's kind and parameters into words, in the reader's language on a page and in
 * Vietnamese on Zalo (SRS BR-72). Parameters are stored as JSON so the words are never stored.
 */
@Component
@RequiredArgsConstructor
public class NoticeText {

    static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MessageSource messages;
    private final SiteTime siteTime;

    public static String json(Map<String, ?> params) {
        return JSON.writeValueAsString(params == null ? Map.of() : params);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> params(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JSON.readValue(json, LinkedHashMap.class);
    }

    public String render(Notification notice, Locale locale) {
        return render(notice.getKind(), params(notice.getParams()), locale);
    }

    public String render(NotificationKind kind, Map<String, Object> params, Locale locale) {
        List<String> names = kind.params();
        Object[] args = new Object[names.size()];
        for (int i = 0; i < names.size(); i++) {
            args[i] = format(names.get(i), params.get(names.get(i)), locale);
        }
        String key = "notice." + kind.name();
        if (names.contains("shift") && isBlank(params.get("shift"))) {
            key = key + ".noShift";     // a gate visit with no shift: "Check-in at 07:58 (no shift)."
        }
        String text = messages.getMessage(key, args, null, locale);
        if (text == null) {
            text = messages.getMessage("notice." + kind.name(), args, kind.name(), locale);
        }
        if (kind == NotificationKind.VIOLATION_ALERT && Boolean.TRUE.equals(params.get("nobodyOnDuty"))) {
            text = text + " " + messages.getMessage("notice.nobodyOnDuty", null, "", locale);
        }
        return text;
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private Object format(String name, Object value, Locale locale) {
        if (value == null || "".equals(value)) {
            return messages.getMessage("notice.none", null, "-", locale);
        }
        if (name.equals("type") || name.equals("outcome") || name.equals("level")) {
            String key = name.equals("level") ? "enum.LEVEL_" + value : "enum." + value;
            return messages.getMessage(key, null, String.valueOf(value), locale);
        }
        if (name.endsWith("At") || name.equals("appealUntil")) {
            try {
                return WHEN.format(siteTime.toLocal(OffsetDateTime.parse(String.valueOf(value))));
            } catch (RuntimeException e) {
                return value;
            }
        }
        return value;
    }
}
