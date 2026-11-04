package orochi.steelsight.identity.service;

/** Permission codes from the permission table. */
public final class Permissions {

    public static final String TICKET_CREATE = "TICKET_CREATE";
    public static final String TICKET_READ_OWN = "TICKET_READ_OWN";
    public static final String TICKET_READ_HANDLED = "TICKET_READ_HANDLED";
    public static final String TICKET_READ_ALL = "TICKET_READ_ALL";
    public static final String SHIFT_READ_OWN = "SHIFT_READ_OWN";
    public static final String SHIFT_READ_TEAM = "SHIFT_READ_TEAM";
    public static final String SHIFT_READ_ALL = "SHIFT_READ_ALL";
    public static final String VIOLATION_READ_OWN = "VIOLATION_READ_OWN";
    public static final String VIOLATION_READ_ALL = "VIOLATION_READ_ALL";
    public static final String VIOLATION_REVIEW = "VIOLATION_REVIEW";
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String CHECKIN_READ_OWN = "CHECKIN_READ_OWN";
    public static final String CHECKIN_READ_TEAM = "CHECKIN_READ_TEAM";
    public static final String CHECKIN_READ_ALL = "CHECKIN_READ_ALL";
    public static final String CHECKIN_EDIT = "CHECKIN_EDIT";

    private Permissions() {
    }
}
