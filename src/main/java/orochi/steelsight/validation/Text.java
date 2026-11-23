package orochi.steelsight.validation;

import orochi.steelsight.exception.BusinessRuleException;

/** Rules for text typed into forms. Messages are shown to the user. */
public final class Text {

    public static final int LONG = 4000;
    public static final int SHORT = 200;

    private Text() {
    }

    public static String required(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(field + " is required");
        }
        return optional(field, value, maxLength);
    }

    /** Required, and between min and max characters after trimming (SRS CM-02, CM-03). */
    public static String sized(String field, String value, int min, int max) {
        String text = required(field, value, Integer.MAX_VALUE);
        if (text.length() < min || text.length() > max) {
            throw new BusinessRuleException(field + " must have " + min + " to " + max + " characters");
        }
        return text;
    }

    /** Blank becomes null, so it can't collide on a unique column or show as an empty value. Line breaks become \n. */
    public static String optional(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // Browsers count a line break as one character for maxlength but send it as \r\n.
        String trimmed = value.replace("\r\n", "\n").strip();
        if (trimmed.length() > maxLength) {
            throw new BusinessRuleException(field + " can be at most " + maxLength + " characters");
        }
        return trimmed;
    }
}
