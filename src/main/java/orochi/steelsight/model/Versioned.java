package orochi.steelsight.model;

import orochi.steelsight.exception.StaleEditException;

/** A record edited through a form, protected by a version column. */
public interface Versioned {

    Long getVersion();

    /** Fails if someone else saved this record after the user's form was loaded. */
    default void requireVersion(long versionInForm) {
        if (getVersion() == null || getVersion() != versionInForm) {
            throw new StaleEditException(this + " was changed by someone else");
        }
    }
}
