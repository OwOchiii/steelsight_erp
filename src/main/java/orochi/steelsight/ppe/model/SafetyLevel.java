package orochi.steelsight.ppe.model;

/** SRS BR-58: the monthly safety bonus level. SteelSight stores the level; accounting works out the money. */
public enum SafetyLevel {
    LEVEL_0(0), LEVEL_1(2), LEVEL_2(4), LEVEL_3(6);

    private final int reductionPercent;

    SafetyLevel(int reductionPercent) {
        this.reductionPercent = reductionPercent;
    }

    public static SafetyLevel of(int number) {
        return values()[number];
    }

    public int number() {
        return ordinal();
    }

    public int reductionPercent() {
        return reductionPercent;
    }
}
