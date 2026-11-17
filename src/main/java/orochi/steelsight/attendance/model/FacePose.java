package orochi.steelsight.attendance.model;

/**
 * Which way the worker faces in an enrolment photo. FRONT is the photo on the account
 * (app_user.face_file_path); LEFT and RIGHT are extra angles that help the gate camera.
 */
public enum FacePose {
    FRONT, LEFT, RIGHT;

    /** The extra angles an admin can add after the FRONT photo. */
    public static final java.util.List<FacePose> EXTRA = java.util.List.of(LEFT, RIGHT);

    /** Turns a path segment such as "left" into a pose; anything else is not found. */
    public static java.util.Optional<FacePose> parse(String text) {
        if (text == null) {
            return java.util.Optional.empty();
        }
        for (FacePose pose : values()) {
            if (pose.name().equalsIgnoreCase(text.strip())) {
                return java.util.Optional.of(pose);
            }
        }
        return java.util.Optional.empty();
    }
}
