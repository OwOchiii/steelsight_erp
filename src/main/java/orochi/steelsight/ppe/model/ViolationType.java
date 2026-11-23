package orochi.steelsight.ppe.model;

/**
 * SRS BR-45: one type per violation; both items missing is one NO_HARD_HAT_AND_VEST. NO_GLOVES and
 * NO_HARNESS are out of scope (OR-04) but kept, because older rows and the current detection app use them.
 */
public enum ViolationType {
    NO_HARD_HAT, NO_VEST, NO_HARD_HAT_AND_VEST, NO_GLOVES, NO_HARNESS
}
